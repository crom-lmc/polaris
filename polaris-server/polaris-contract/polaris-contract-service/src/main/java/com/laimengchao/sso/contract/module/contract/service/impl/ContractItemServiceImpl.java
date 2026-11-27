package com.laimengchao.sso.contract.module.contract.service.impl;

import com.laimengchao.sso.contract.common.impl.BizNoServiceImpl;
import com.laimengchao.sso.contract.module.contract.service.ContractItemService;
import com.laimengchao.sso.contract.module.contract.service.ContractService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.contract.common.BizNoService;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.common.EmbedPointService;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractAcceptance;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.entity.ContractProject;
import com.laimengchao.sso.contract.module.contract.mapper.ContractAcceptanceMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractMaterialMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractProjectMapper;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 合同清单：金额由 qty × price 派生，保存前按项目预算校验，到货验收累加 deliveredQty。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractItemServiceImpl implements ContractItemService {

    private static final String EMBED_ALLOW_OVER = "contract.budget.allow-over";
    private static final String POSTED = "POSTED";

    private final ContractItemMapper mapper;
    private final ContractAcceptanceMapper acceptanceMapper;
    private final ContractMaterialMapper materialMapper;
    private final ContractProjectMapper projectMapper;
    private final ContractService contractService;
    private final BizNoService bizNoService;
    private final EmbedPointService embedPointService;
    private final DataScopeService dataScopeService;

    public List<ContractVOs.ItemVO> list(String contractNo) {
        ContractHeader header = contractService.requireByNo(contractNo);
        LambdaQueryWrapper<ContractItem> qw = new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, header.getNo())
                .orderByAsc(ContractItem::getLineNo);
        dataScopeService.applyOrgScope(qw, ContractItem::getOrgCode);
        return mapper.selectList(qw).stream().map(this::toVO).toList();
    }

    public ContractVOs.ItemVO detail(Long id) {
        return toVO(require(id));
    }

    public ContractItem require(Long id) {
        ContractItem entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.ITEM_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.ItemVO save(String contractNo, ContractRequests.ItemSave req) {
        ContractHeader header = contractService.requireByNo(contractNo);
        dataScopeService.assertCanAccessOrg(header.getOrgCode());
        ensureContractOpen(header);
        if (req == null || req.getQty() == null || req.getPrice() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "数量与单价不能为空");
        }
        String materialCode = req.getMaterialCode() == null ? "" : req.getMaterialCode().trim();
        if (materialCode.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "物料编码不能为空");
        }
        LambdaQueryWrapper<ContractMaterial> materialQw = new LambdaQueryWrapper<ContractMaterial>()
                .eq(ContractMaterial::getCode, materialCode).last(" LIMIT 1");
        dataScopeService.applyOrgScope(materialQw, ContractMaterial::getOrgCode);
        ContractMaterial material = materialMapper.selectOne(materialQw);
        if (material == null) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        ContractItem entity;
        if (req.getId() != null) {
            entity = require(req.getId());
            if (!header.getNo().equals(entity.getContractNo())) {
                throw new BizException(ContractErrorCode.ITEM_NOT_FOUND);
            }
        } else {
            entity = new ContractItem();
            entity.setContractNo(header.getNo());
            entity.setLineNo(req.getLineNo() == null ? nextLineNo(header.getNo()) : req.getLineNo());
            entity.setDeliveredQty(BigDecimal.ZERO);
            entity.setOrgCode(header.getOrgCode());
            entity.setCreatedAt(LocalDateTime.now());
        }
        BigDecimal oldAmount = entity.getAmount() == null ? BigDecimal.ZERO : entity.getAmount();
        entity.setMaterialCode(materialCode);
        entity.setMaterialName(nvl(req.getMaterialName(), material.getName()));
        entity.setSpec(nvl(req.getSpec(), material.getSpec()));
        entity.setUnit(nvl(req.getUnit(), material.getUnit()));
        entity.setQty(req.getQty());
        entity.setPrice(req.getPrice());
        entity.setAmount(req.getAmount() == null
                ? req.getQty().multiply(req.getPrice()) : req.getAmount());
        entity.setRemark(nvl(req.getRemark(), ""));
        entity.setUpdatedAt(LocalDateTime.now());
        checkBudget(header, oldAmount, entity.getAmount());
        if (req.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractItem entity = require(id);
        ContractHeader header = contractService.requireByNo(entity.getContractNo());
        ensureContractOpen(header);
        if (entity.getDeliveredQty() != null && entity.getDeliveredQty().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_STATUS_INVALID, "已验收清单行禁止删除");
        }
        mapper.deleteById(id);
    }

    /** 到货验收：增加 deliveredQty，并生成 JD 验收单（单号走 settings） */
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.AcceptanceVO accept(Long id, ContractRequests.AcceptRequest req) {
        ContractItem item = require(id);
        ContractHeader header = contractService.requireByNo(item.getContractNo());
        dataScopeService.assertCanAccessOrg(header.getOrgCode());
        if (req == null || req.getQty() == null || req.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_QTY_INVALID);
        }
        BigDecimal delivered = item.getDeliveredQty() == null ? BigDecimal.ZERO : item.getDeliveredQty();
        BigDecimal ordered = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
        if (delivered.add(req.getQty()).compareTo(ordered) > 0) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_QTY_INVALID);
        }
        item.setDeliveredQty(delivered.add(req.getQty()));
        item.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(item);
        ContractAcceptance acceptance = new ContractAcceptance();
        acceptance.setNo(bizNoService.next(BizNoServiceImpl.TABLE_ACCEPTANCE));
        acceptance.setContractNo(header.getNo());
        acceptance.setItemId(item.getId());
        acceptance.setMaterialCode(item.getMaterialCode());
        acceptance.setQty(req.getQty());
        acceptance.setDate(req.getDate() == null ? LocalDate.now() : req.getDate());
        acceptance.setResult(nvl(req.getResult(), "合格"));
        acceptance.setStatus("DRAFT");
        acceptance.setOrgCode(header.getOrgCode());
        acceptance.setCreatedAt(LocalDateTime.now());
        acceptanceMapper.insert(acceptance);
        log.info("合同清单验收：contractNo={} itemId={} qty={} acceptanceNo={}",
                header.getNo(), item.getId(), req.getQty(), acceptance.getNo());
        return toAcceptanceVO(acceptance);
    }

    public List<ContractVOs.AcceptanceVO> acceptances(String contractNo) {
        ContractHeader header = contractService.requireByNo(contractNo);
        LambdaQueryWrapper<ContractAcceptance> qw = new LambdaQueryWrapper<ContractAcceptance>()
                .eq(ContractAcceptance::getContractNo, header.getNo())
                .orderByDesc(ContractAcceptance::getId);
        dataScopeService.applyOrgScope(qw, ContractAcceptance::getOrgCode);
        return acceptanceMapper.selectList(qw).stream().map(this::toAcceptanceVO).toList();
    }

    public ContractAcceptance requireAcceptance(String no) {
        if (no == null || no.isBlank()) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_NOT_FOUND);
        }
        LambdaQueryWrapper<ContractAcceptance> qw = new LambdaQueryWrapper<ContractAcceptance>()
                .eq(ContractAcceptance::getNo, no.trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractAcceptance::getOrgCode);
        ContractAcceptance entity = acceptanceMapper.selectOne(qw);
        if (entity == null) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_NOT_FOUND);
        }
        return entity;
    }

    /** 财务域过账后回写验收单状态 */
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.AcceptanceVO markPosted(String no) {
        ContractAcceptance entity = requireAcceptance(no);
        if (POSTED.equals(entity.getStatus())) {
            return toAcceptanceVO(entity);
        }
        entity.setStatus(POSTED);
        entity.setUpdatedAt(LocalDateTime.now());
        acceptanceMapper.updateById(entity);
        return toAcceptanceVO(entity);
    }

    /** 超预算校验：埋点 contract.budget.allow-over 默认 false，读取失败 fail-closed 拦截 */
    private void checkBudget(ContractHeader header, BigDecimal oldAmount, BigDecimal newAmount) {
        if (header.getProjectNo() == null || header.getProjectNo().isBlank()) {
            return;
        }
        LambdaQueryWrapper<ContractProject> qw = new LambdaQueryWrapper<ContractProject>()
                .eq(ContractProject::getCode, header.getProjectNo()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractProject::getOrgCode);
        ContractProject project = projectMapper.selectOne(qw);
        if (project == null || project.getBudgetAmount() == null
                || project.getBudgetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal total = currentTotal(header.getNo()).subtract(oldAmount).add(newAmount);
        if (total.compareTo(project.getBudgetAmount()) <= 0) {
            return;
        }
        // 埋点读取失败返回 false → 拦截（fail-closed）
        boolean allowOver = embedPointService.bool(EMBED_ALLOW_OVER, false);
        if (allowOver) {
            log.warn("合同 {} 金额 {} 超过项目 {} 预算 {}，埋点允许放行并记录留痕",
                    header.getNo(), total, project.getCode(), project.getBudgetAmount());
            return;
        }
        throw new BizException(ContractErrorCode.BUDGET_EXCEEDED,
                "合同金额 " + total + " 超过项目预算 " + project.getBudgetAmount() + "，清单保存已拦截");
    }

    private BigDecimal currentTotal(String contractNo) {
        LambdaQueryWrapper<ContractItem> qw = new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, contractNo).select(ContractItem::getAmount);
        dataScopeService.applyOrgScope(qw, ContractItem::getOrgCode);
        return mapper.selectList(qw).stream()
                .map(e -> e.getAmount() == null ? BigDecimal.ZERO : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int nextLineNo(String contractNo) {
        LambdaQueryWrapper<ContractItem> qw = new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, contractNo).select(ContractItem::getLineNo);
        dataScopeService.applyOrgScope(qw, ContractItem::getOrgCode);
        return mapper.selectList(qw).stream()
                .map(ContractItem::getLineNo).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private void ensureContractOpen(ContractHeader header) {
        if ("COMPLETED".equals(header.getStatus()) || "TERMINATED".equals(header.getStatus())) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID);
        }
    }

    private ContractVOs.ItemVO toVO(ContractItem e) {
        ContractVOs.ItemVO vo = new ContractVOs.ItemVO();
        vo.setId(e.getId());
        vo.setContractNo(e.getContractNo());
        vo.setLineNo(e.getLineNo());
        vo.setCode(e.getMaterialCode());
        vo.setMaterialCode(e.getMaterialCode());
        vo.setMaterialName(e.getMaterialName());
        vo.setSpec(e.getSpec());
        vo.setUnit(e.getUnit());
        vo.setQty(e.getQty());
        vo.setPrice(e.getPrice());
        vo.setAmount(e.getAmount());
        vo.setDeliveredQty(e.getDeliveredQty());
        vo.setRemark(e.getRemark());
        vo.setOrgCode(e.getOrgCode());
        return vo;
    }

    private ContractVOs.AcceptanceVO toAcceptanceVO(ContractAcceptance e) {
        ContractVOs.AcceptanceVO vo = new ContractVOs.AcceptanceVO();
        vo.setId(e.getId());
        vo.setNo(e.getNo());
        vo.setContractNo(e.getContractNo());
        vo.setItemId(e.getItemId());
        vo.setMaterialCode(e.getMaterialCode());
        vo.setQty(e.getQty());
        vo.setDate(e.getDate() == null ? null : e.getDate().toString());
        vo.setResult(e.getResult());
        vo.setStatus(e.getStatus());
        vo.setOrgCode(e.getOrgCode());
        return vo;
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
