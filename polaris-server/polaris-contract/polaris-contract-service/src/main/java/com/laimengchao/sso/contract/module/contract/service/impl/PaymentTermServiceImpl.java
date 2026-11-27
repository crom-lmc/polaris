package com.laimengchao.sso.contract.module.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentPlan;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentTerm;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPaymentPlanMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPaymentTermMapper;
import com.laimengchao.sso.contract.module.contract.service.ContractService;
import com.laimengchao.sso.contract.module.contract.service.PaymentTermService;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 合同付款节点配置：仅草稿可改；比例合计 100%、必含验收款；金额按清单合计 × 比例锁定。 */
@Service
@RequiredArgsConstructor
public class PaymentTermServiceImpl implements PaymentTermService {

    /** 允许的付款阶段，与 settings 字典 contract.payment_stage 一致（已移除 OTHER） */
    public static final Set<String> STAGES = Set.of("PREPAY", "GOODS", "ACCEPT", "WARRANTY");
    /** 非 REJECTED 的计划金额计入节点已申请 */
    private static final Set<String> APPLIED_STATUS = Set.of("WAITING", "PENDING", "APPROVED", "POSTED", "PAID");

    private final ContractPaymentTermMapper mapper;
    private final ContractPaymentPlanMapper planMapper;
    private final ContractItemMapper itemMapper;
    private final ContractService contractService;
    private final DataScopeService dataScopeService;

    public List<ContractVOs.PaymentTermVO> list(String contractNo) {
        ContractHeader header = contractService.requireByNo(contractNo);
        return decorate(entities(header.getNo()));
    }

    public List<ContractPaymentTerm> entities(String contractNo) {
        return mapper.selectList(new LambdaQueryWrapper<ContractPaymentTerm>()
                .eq(ContractPaymentTerm::getContractNo, contractNo)
                .orderByAsc(ContractPaymentTerm::getCreatedAt));
    }

    public ContractPaymentTerm require(Long id) {
        ContractPaymentTerm entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.PAYMENT_TERM_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ContractVOs.PaymentTermVO> save(String contractNo, ContractRequests.PaymentTermSaveBatch req) {
        ContractHeader header = contractService.requireByNo(contractNo);
        dataScopeService.assertCanAccessOrg(header.getOrgCode());
        if (!"DRAFT".equals(header.getStatus())) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID, "仅草稿状态的合同可配置付款节点");
        }
        List<ContractRequests.PaymentTermSave> terms = req == null ? null : req.getTerms();
        if (terms == null || terms.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请至少配置一个付款节点");
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasAccept = false;
        Set<String> seen = new HashSet<>();
        for (ContractRequests.PaymentTermSave t : terms) {
            if (t == null || !STAGES.contains(t.getStage())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "付款阶段只能是预付款/到货款/验收款/质保金");
            }
            if (!seen.add(t.getStage())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "付款节点阶段不能重复：" + t.getStage());
            }
            if (t.getRatio() == null || t.getRatio().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "付款节点比例必须大于 0");
            }
            total = total.add(t.getRatio());
            if ("ACCEPT".equals(t.getStage())) {
                hasAccept = true;
            }
        }
        if (total.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "付款节点比例合计必须为 100%");
        }
        if (!hasAccept) {
            throw new BizException(ErrorCode.PARAM_ERROR, "付款节点必须包含验收款");
        }
        // 金额按当前合同清单合计 × 比例锁定；清单变动后需重新保存节点确认金额
        BigDecimal contractAmount = contractAmount(contractNo);
        LocalDateTime now = LocalDateTime.now();
        List<Long> keepIds = new ArrayList<>();
        for (ContractRequests.PaymentTermSave t : terms) {
            ContractPaymentTerm entity;
            boolean isNew = t.getId() == null;
            if (isNew) {
                entity = new ContractPaymentTerm();
                entity.setContractNo(contractNo);
                entity.setOrgCode(header.getOrgCode());
                entity.setCreatedAt(now);
            } else {
                entity = mapper.selectById(t.getId());
                if (entity == null || !contractNo.equals(entity.getContractNo())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "付款节点不属于该合同");
                }
                keepIds.add(entity.getId());
            }
            entity.setStage(t.getStage());
            entity.setRatio(t.getRatio());
            entity.setAmount(contractAmount.multiply(t.getRatio())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            entity.setDueDate(t.getDueDate());
            entity.setRemark(t.getRemark() == null ? "" : t.getRemark());
            entity.setUpdatedAt(now);
            if (isNew) {
                mapper.insert(entity); // BaseEntity ASSIGN_ID 自动雪花主键
                keepIds.add(entity.getId());
            } else {
                mapper.updateById(entity);
            }
        }
        // 一次性替换：本次未提交的旧节点逻辑删除（@TableLogic 自动带 deleted=0）
        LambdaUpdateWrapper<ContractPaymentTerm> del = new LambdaUpdateWrapper<ContractPaymentTerm>()
                .eq(ContractPaymentTerm::getContractNo, contractNo)
                .set(ContractPaymentTerm::getDeleted, 1)
                .set(ContractPaymentTerm::getUpdatedAt, now);
        if (!keepIds.isEmpty()) {
            del.notIn(ContractPaymentTerm::getId, keepIds);
        }
        mapper.update(null, del);
        return list(contractNo);
    }

    /** 合同清单金额合计（与 ContractServiceImpl.decorate 同口径） */
    private BigDecimal contractAmount(String contractNo) {
        List<ContractItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ContractItem>().eq(ContractItem::getContractNo, contractNo));
        return items == null ? BigDecimal.ZERO : items.stream()
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 节点列表 → VO，带出已申请/可申请金额 */
    private List<ContractVOs.PaymentTermVO> decorate(List<ContractPaymentTerm> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, BigDecimal> applied = new java.util.HashMap<>();
        List<ContractPaymentPlan> plans = planMapper.selectList(new LambdaQueryWrapper<ContractPaymentPlan>()
                .in(ContractPaymentPlan::getTermId, entities.stream().map(ContractPaymentTerm::getId).toList()));
        if (plans != null) {
            for (ContractPaymentPlan p : plans) {
                if (p.getTermId() != null && p.getAmount() != null && APPLIED_STATUS.contains(p.getStatus())) {
                    applied.merge(p.getTermId(), p.getAmount(), BigDecimal::add);
                }
            }
        }
        List<ContractVOs.PaymentTermVO> out = new ArrayList<>();
        for (ContractPaymentTerm e : entities) {
            ContractVOs.PaymentTermVO vo = new ContractVOs.PaymentTermVO();
            vo.setId(e.getId());
            vo.setContractNo(e.getContractNo());
            vo.setStage(e.getStage());
            vo.setRatio(e.getRatio());
            vo.setAmount(e.getAmount());
            BigDecimal used = applied.getOrDefault(e.getId(), BigDecimal.ZERO);
            vo.setAppliedAmount(used);
            BigDecimal amount = e.getAmount() == null ? BigDecimal.ZERO : e.getAmount();
            vo.setAvailableAmount(amount.subtract(used).max(BigDecimal.ZERO));
            vo.setDueDate(e.getDueDate() == null ? null : e.getDueDate().toString());
            vo.setRemark(e.getRemark());
            vo.setOrgCode(e.getOrgCode());
            out.add(vo);
        }
        return out;
    }
}
