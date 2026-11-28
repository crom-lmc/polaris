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
import com.laimengchao.sso.contract.module.contract.entity.ContractSign;
import com.laimengchao.sso.contract.module.contract.mapper.ContractAcceptanceMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractMaterialMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractProjectMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractSignMapper;
import com.laimengchao.sso.contract.module.contract.support.ContractAttachments;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 合同清单：金额由 qty × price 派生，保存前按项目预算校验，到货验收累加 deliveredQty。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractItemServiceImpl implements ContractItemService {

    private static final String EMBED_ALLOW_OVER = "contract.budget.allow-over";
    private static final String POSTED = "POSTED";
    /** 采购计划转入清单行的备注前缀，作为来源标记（前端据此禁删，后端据此拦截） */
    static final String PLAN_ITEM_REMARK_PREFIX = "由采购计划 ";
    private static final String PLAN_ITEM_DELETE_DENIED = "采购计划转入的清单行不允许删除";

    private final ContractItemMapper mapper;
    private final ContractAcceptanceMapper acceptanceMapper;
    private final ContractSignMapper signMapper;
    private final ContractMaterialMapper materialMapper;
    private final ContractProjectMapper projectMapper;
    private final ContractService contractService;
    private final BizNoService bizNoService;
    private final EmbedPointService embedPointService;
    private final FileApi fileApi;
    private final DataScopeService dataScopeService;
    private final com.laimengchao.sso.finance.client.api.ReceivableApi receivableApi;

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
        ensureEditable(header);
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
            entity.setSignedQty(BigDecimal.ZERO);
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
        // 采购计划转入行始终保留来源备注前缀，避免编辑时被抹掉后绕过禁删校验
        String planPrefix = req.getId() != null && entity.getRemark() != null
                && entity.getRemark().startsWith(PLAN_ITEM_REMARK_PREFIX) ? entity.getRemark() : null;
        entity.setRemark(planPrefix != null ? planPrefix : nvl(req.getRemark(), ""));
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
        ensureEditable(header);
        if (entity.getRemark() != null && entity.getRemark().startsWith(PLAN_ITEM_REMARK_PREFIX)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID, PLAN_ITEM_DELETE_DENIED);
        }
        if (entity.getSignedQty() != null && entity.getSignedQty().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_STATUS_INVALID, "已签收清单行禁止删除");
        }
        if (entity.getDeliveredQty() != null && entity.getDeliveredQty().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_STATUS_INVALID, "已验收清单行禁止删除");
        }
        mapper.deleteById(id);
    }

    /**
     * 验收：采购=到货验收（合格/不合格，累加交付）；销售=客户验收（通过/不通过，通过才累加交付并推应收）。
     * 本方法只做本地落库；销售通过的推应收在事务提交后由 afterCommit 回调执行，失败不回滚验收、记录 ar_error 供重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.AcceptanceVO accept(Long id, ContractRequests.AcceptRequest req) {
        ContractItem item = require(id);
        ContractHeader header = contractService.requireByNo(item.getContractNo());
        dataScopeService.assertCanAccessOrg(header.getOrgCode());
        ensureAcceptable(header);
        // 整单验收完成（全部清单行 deliveredQty>=qty）后不再接受新验收：前端隐藏入口，服务端兜底防绕过
        List<ContractItem> allItems = mapper.selectList(new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, header.getNo()));
        if (allItems != null && !allItems.isEmpty() && allItems.stream().allMatch(this::fullyAccepted)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID, "合同清单已全部验收完成，无需再验收");
        }
        if (req == null || req.getQty() == null || req.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_QTY_INVALID);
        }
        // 验收日期必填：前端只是可见入口，服务端硬校验防止绕过（历史 date 为空默认今天的逻辑已废弃）
        if (req.getDate() == null) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_DATE_REQUIRED);
        }
        BigDecimal delivered = item.getDeliveredQty() == null ? BigDecimal.ZERO : item.getDeliveredQty();
        BigDecimal ordered = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
        if (delivered.add(req.getQty()).compareTo(ordered) > 0) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_QTY_INVALID);
        }
        boolean sales = ContractServiceImpl.SALES.equals(header.getType());
        // 结果归一化：采购「合格/不合格」，销售「通过/不通过」；通过/合格才累加交付
        String result = normalizeResult(req.getResult(), sales);
        boolean accepted = isAcceptedResult(result, sales);
        if (accepted) {
            item.setDeliveredQty(delivered.add(req.getQty()));
            item.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(item);
        }
        ContractAcceptance acceptance = new ContractAcceptance();
        acceptance.setNo(bizNoService.next(BizNoServiceImpl.TABLE_ACCEPTANCE));
        acceptance.setContractNo(header.getNo());
        acceptance.setItemId(item.getId());
        acceptance.setMaterialCode(item.getMaterialCode());
        acceptance.setQty(req.getQty());
        acceptance.setDate(req.getDate());
        acceptance.setResult(result);
        acceptance.setCustomerNo(sales && req.getCustomerNo() != null ? req.getCustomerNo().trim() : "");
        acceptance.setStatus("DRAFT");
        acceptance.setOrgCode(header.getOrgCode());
        acceptance.setFileIds(ContractAttachments.join(req.getFileIds()));
        acceptance.setCreatedAt(LocalDateTime.now());
        acceptanceMapper.insert(acceptance);
        log.info("合同清单验收：contractNo={} itemId={} qty={} acceptanceNo={} result={} sales={}",
                header.getNo(), item.getId(), req.getQty(), acceptance.getNo(), result, sales);
        // 首次验收自动推进：已生效 → 执行中；乐观锁保证幂等，后续验收不会再触发
        contractService.advanceIfStatus(header.getNo(), "EFFECTIVE", "EXECUTING");
        // 销售验收通过：事务提交后推应收（不通过不推），失败不回滚验收
        if (sales && accepted) {
            final Long acceptanceId = acceptance.getId();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                pushReceivable(acceptanceId);
                            } catch (RuntimeException e) {
                                // 验收已落库：推应收失败只记录，用户可在验收记录里重试
                                log.error("销售验收推应收失败：acceptanceId={} no={}", acceptanceId, acceptance.getNo(), e);
                            }
                        }
                    });
        }
        return toAcceptanceVO(acceptance);
    }

    /**
     * 到货签收：与验收完全独立——仅累加 signedQty（到货款放行依据），不动 deliveredQty（验收款放行依据）。
     * 签收不判质量结果、不推应收；已生效/执行中可签收，首次签收同样推进 EFFECTIVE→EXECUTING。
     */
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.SignVO sign(Long id, ContractRequests.SignRequest req) {
        ContractItem item = require(id);
        ContractHeader header = contractService.requireByNo(item.getContractNo());
        dataScopeService.assertCanAccessOrg(header.getOrgCode());
        ensureAcceptable(header);
        if (req == null || req.getQty() == null || req.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ContractErrorCode.SIGN_QTY_INVALID);
        }
        if (req.getDate() == null) {
            throw new BizException(ContractErrorCode.SIGN_DATE_REQUIRED);
        }
        BigDecimal signed = item.getSignedQty() == null ? BigDecimal.ZERO : item.getSignedQty();
        BigDecimal ordered = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
        if (signed.add(req.getQty()).compareTo(ordered) > 0) {
            throw new BizException(ContractErrorCode.SIGN_QTY_INVALID);
        }
        item.setSignedQty(signed.add(req.getQty()));
        item.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(item);
        ContractSign sign = new ContractSign();
        sign.setNo(bizNoService.next(BizNoServiceImpl.TABLE_SIGN));
        sign.setContractNo(header.getNo());
        sign.setItemId(item.getId());
        sign.setMaterialCode(item.getMaterialCode());
        sign.setQty(req.getQty());
        sign.setDate(req.getDate());
        sign.setSigner(nvl(com.laimengchao.sso.utils.AccountContext.getUsername(), ""));
        sign.setRemark(nvl(req.getRemark(), ""));
        sign.setStatus("DRAFT");
        sign.setOrgCode(header.getOrgCode());
        sign.setFileIds(ContractAttachments.join(req.getFileIds()));
        sign.setCreatedAt(LocalDateTime.now());
        signMapper.insert(sign);
        log.info("合同清单签收：contractNo={} itemId={} qty={} signNo={}",
                header.getNo(), item.getId(), req.getQty(), sign.getNo());
        // 首次签收自动推进：已生效 → 执行中（与验收同口径，签收同样是实际业务动作）
        contractService.advanceIfStatus(header.getNo(), "EFFECTIVE", "EXECUTING");
        return toSignVO(sign);
    }

    public List<ContractVOs.SignVO> signs(String contractNo) {
        ContractHeader header = contractService.requireByNo(contractNo);
        LambdaQueryWrapper<ContractSign> qw = new LambdaQueryWrapper<ContractSign>()
                .eq(ContractSign::getContractNo, header.getNo())
                .orderByDesc(ContractSign::getId);
        dataScopeService.applyOrgScope(qw, ContractSign::getOrgCode);
        return signMapper.selectList(qw).stream().map(this::toSignVO).toList();
    }

    /** 结果归一化：采购「合格/不合格」，销售「通过/不通过」；兼容历史 PASS/OK/FAIL/NG 与中文合格 */
    private String normalizeResult(String raw, boolean sales) {
        String v = nvl(raw, sales ? "通过" : "合格");
        boolean positive = "PASS".equalsIgnoreCase(v) || "OK".equalsIgnoreCase(v)
                || "合格".equals(v) || "通过".equals(v);
        boolean negative = "FAIL".equalsIgnoreCase(v) || "NG".equalsIgnoreCase(v)
                || "不合格".equals(v) || "不通过".equals(v);
        if (positive) return sales ? "通过" : "合格";
        if (negative) return sales ? "不通过" : "不合格";
        return sales ? "不通过" : "不合格"; // 未知值按不通过/不合格处理（fail-closed）
    }

    private boolean isAcceptedResult(String result, boolean sales) {
        return sales ? "通过".equals(result) : "合格".equals(result);
    }

    /**
     * 销售验收通过推应收：调用财务域 ReceivableApi.create（internal token），按该批验收金额生成一张应收单。
     * 幂等：ar_no 非空直接返回；失败写 ar_error 供重试。no @Transactional——由调用方（afterCommit / 重试端点）控制。
     */
    public void pushReceivable(Long acceptanceId) {
        ContractAcceptance a = acceptanceMapper.selectById(acceptanceId);
        if (a == null) {
            return;
        }
        if (a.getArNo() != null && !a.getArNo().isBlank()) {
            return; // 幂等：已推过
        }
        if (!"通过".equals(a.getResult())) {
            return; // 不通过 / 采购验收不推应收，重试端点也拦住
        }
        ContractHeader header;
        ContractItem item;
        try {
            header = contractService.requireByNo(a.getContractNo());
            item = mapper.selectById(a.getItemId());
        } catch (RuntimeException e) {
            markArError(acceptanceId, "合同/清单不存在：" + e.getMessage());
            return;
        }
        // 该批验收金额 = 验收数量 × 清单单价
        BigDecimal price = item == null || item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        BigDecimal amount = a.getQty() == null ? BigDecimal.ZERO : a.getQty().multiply(price);
        if (amount.signum() <= 0) {
            markArError(acceptanceId, "验收金额小于等于 0，无法推应收");
            return;
        }
        com.laimengchao.sso.finance.client.dto.ReceivableCreateRequest dto =
                new com.laimengchao.sso.finance.client.dto.ReceivableCreateRequest();
        dto.setParty(header.getPartnerCode());
        dto.setAmount(amount);
        dto.setContractNo(header.getNo());
        dto.setSummary("销售客户验收 " + a.getNo() + (a.getCustomerNo() == null || a.getCustomerNo().isBlank()
                ? "" : "（客户单号 " + a.getCustomerNo() + "）"));
        dto.setDate(a.getDate() == null ? null : a.getDate().toString());
        dto.setOrgCode(header.getOrgCode());
        try {
            var resp = receivableApi.create(dto);
            if (resp == null || !resp.ok() || resp.getData() == null
                    || resp.getData().getNo() == null || resp.getData().getNo().isBlank()) {
                throw new RuntimeException(resp == null ? "无响应" : "返回空单号");
            }
            ContractAcceptance upd = new ContractAcceptance();
            upd.setId(acceptanceId);
            upd.setArNo(resp.getData().getNo());
            upd.setArError("");
            upd.setUpdatedAt(LocalDateTime.now());
            acceptanceMapper.updateById(upd);
            log.info("销售验收推应收成功：acceptanceNo={} arNo={}", a.getNo(), resp.getData().getNo());
        } catch (RuntimeException e) {
            markArError(acceptanceId, e.getMessage() == null ? "推应收失败" : e.getMessage());
            throw e;
        }
    }

    private void markArError(Long acceptanceId, String reason) {
        ContractAcceptance upd = new ContractAcceptance();
        upd.setId(acceptanceId);
        upd.setArError(reason == null ? "" : reason.length() > 300 ? reason.substring(0, 300) : reason);
        upd.setUpdatedAt(LocalDateTime.now());
        acceptanceMapper.updateById(upd);
    }

    /**
     * 销售合同完成推进：财务应收全部核销后回调本方法。
     * 条件：清单全部验收完成 + 所有通过的验收单都已推应收（arNo 非空）；未达条件幂等跳过。
     * 注意：财务侧核销回调明天对接，本方法先就位，internal 端点由财务核销完成后投递。
     */
    public void tryCompleteOnArSettled(String contractNo) {
        if (contractNo == null || contractNo.isBlank()) {
            return;
        }
        ContractHeader header;
        try {
            header = contractService.requireByNo(contractNo);
        } catch (RuntimeException e) {
            return; // internal 调用无 dataScope，合同不存在直接跳过
        }
        if (!ContractServiceImpl.SALES.equals(header.getType())) {
            return; // 仅销售合同走应收闭环
        }
        List<ContractItem> items = mapper.selectList(new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, contractNo));
        if (items == null || items.isEmpty() || !items.stream().allMatch(this::fullyAccepted)) {
            return;
        }
        List<ContractAcceptance> acceptances = acceptanceMapper.selectList(new LambdaQueryWrapper<ContractAcceptance>()
                .eq(ContractAcceptance::getContractNo, contractNo));
        if (acceptances == null || acceptances.isEmpty()) {
            return;
        }
        // 所有「通过」的验收单都必须已推应收；有未推的（含失败）不推进
        boolean allPushed = acceptances.stream()
                .filter(a -> "通过".equals(a.getResult()))
                .allMatch(a -> a.getArNo() != null && !a.getArNo().isBlank());
        if (!allPushed) {
            return;
        }
        contractService.advanceIfStatus(contractNo, "EXECUTING", "COMPLETED");
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
        return markPosted(no, null);
    }

    /** 财务域过账后回写验收单状态与凭证号 */
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.AcceptanceVO markPosted(String no, String voucherNo) {
        ContractAcceptance entity = requireAcceptance(no);
        if (POSTED.equals(entity.getStatus()) && (voucherNo == null || voucherNo.isBlank())) {
            return toAcceptanceVO(entity);
        }
        entity.setStatus(POSTED);
        if (voucherNo != null && !voucherNo.isBlank()) {
            entity.setVoucherNo(voucherNo.trim());
        }
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

    /** 清单增删改仅草稿允许：审批通过后合同金额锁定，变更需走变更流程，不能在此直接改清单。 */
    private void ensureEditable(ContractHeader header) {
        if (!"DRAFT".equals(header.getStatus())) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID,
                    "合同已提交审批，清单已锁定；如需变更请走合同变更流程");
        }
    }

    /** 到货验收仅已生效/执行中允许：草稿未审批、审批中、完成/终止都不能验收。 */
    private void ensureAcceptable(ContractHeader header) {
        String s = header.getStatus();
        if (!"EFFECTIVE".equals(s) && !"EXECUTING".equals(s)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID,
                    "合同状态不支持验收，仅已生效或执行中的合同可验收");
        }
    }

    /** 清单行是否验收完成：qty<=0 视为完成，否则 deliveredQty>=qty。与付款门槛同口径。 */
    private boolean fullyAccepted(ContractItem item) {
        BigDecimal qty = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal delivered = item.getDeliveredQty() == null ? BigDecimal.ZERO : item.getDeliveredQty();
        return delivered.compareTo(qty) >= 0;
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
        vo.setSignedQty(e.getSignedQty());
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
        vo.setCustomerNo(e.getCustomerNo());
        vo.setResult(e.getResult());
        vo.setArNo(e.getArNo());
        vo.setArError(e.getArError());
        vo.setStatus(e.getStatus());
        vo.setVoucherNo(e.getVoucherNo());
        vo.setOrgCode(e.getOrgCode());
        List<String> fileIds = ContractAttachments.split(e.getFileIds());
        vo.setFileIds(fileIds);
        vo.setAttachmentUrls(ContractAttachments.signedUrls(fileIds, fileApi));
        return vo;
    }

    private ContractVOs.SignVO toSignVO(ContractSign e) {
        ContractVOs.SignVO vo = new ContractVOs.SignVO();
        vo.setId(e.getId());
        vo.setNo(e.getNo());
        vo.setContractNo(e.getContractNo());
        vo.setItemId(e.getItemId());
        vo.setMaterialCode(e.getMaterialCode());
        vo.setQty(e.getQty());
        vo.setDate(e.getDate() == null ? null : e.getDate().toString());
        vo.setSigner(e.getSigner());
        vo.setRemark(e.getRemark());
        vo.setStatus(e.getStatus());
        vo.setOrgCode(e.getOrgCode());
        List<String> fileIds = ContractAttachments.split(e.getFileIds());
        vo.setFileIds(fileIds);
        vo.setAttachmentUrls(ContractAttachments.signedUrls(fileIds, fileApi));
        return vo;
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
