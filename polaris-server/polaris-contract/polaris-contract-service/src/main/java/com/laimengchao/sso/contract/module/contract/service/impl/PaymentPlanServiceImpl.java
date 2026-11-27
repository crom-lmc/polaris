package com.laimengchao.sso.contract.module.contract.service.impl;

import com.laimengchao.sso.contract.module.contract.service.PaymentPlanService;
import com.laimengchao.sso.contract.module.contract.service.ContractService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.common.EmbedPointService;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractAcceptance;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentPlan;
import com.laimengchao.sso.contract.module.contract.mapper.ContractAcceptanceMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPaymentPlanMapper;
import com.laimengchao.sso.contract.module.contract.support.ContractAttachments;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import com.laimengchao.sso.file.client.api.FileApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 合同付款计划：发起付款申请置为待审批；来源单据号由财务域生成后回写。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPlanServiceImpl implements PaymentPlanService {

    private static final Set<String> PAID_STATUS = Set.of("PAID", "POSTED");
    private static final Set<String> IN_FLIGHT_STATUS = Set.of("PENDING", "APPROVED");

    private final ContractPaymentPlanMapper mapper;
    private final ContractAcceptanceMapper acceptanceMapper;
    private final ContractItemMapper itemMapper;
    private final ContractService contractService;
    private final EmbedPointService embedPointService;
    private final DataScopeService dataScopeService;
    private final FileApi fileApi;

    public List<ContractVOs.PaymentPlanVO> list(String contractNo) {
        ContractHeader header = contractService.requireByNo(contractNo);
        LambdaQueryWrapper<ContractPaymentPlan> qw = new LambdaQueryWrapper<ContractPaymentPlan>()
                .eq(ContractPaymentPlan::getContractNo, header.getNo())
                .orderByAsc(ContractPaymentPlan::getDueDate);
        dataScopeService.applyOrgScope(qw, ContractPaymentPlan::getOrgCode);
        return mapper.selectList(qw).stream().map(this::toVO).toList();
    }

    public ContractVOs.PaymentPlanVO detail(Long id) {
        return toVO(require(id));
    }

    public ContractPaymentPlan require(Long id) {
        ContractPaymentPlan entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.PAYMENT_PLAN_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.PaymentPlanVO save(String contractNo, ContractRequests.PaymentPlanSave req) {
        ContractHeader header = contractService.requireByNo(contractNo);
        dataScopeService.assertCanAccessOrg(header.getOrgCode());
        if (req == null || req.getStage() == null || req.getStage().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择付款阶段");
        }
        if (req.getDueDate() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择应付日期");
        }
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "本期付款金额必须大于 0");
        }
        BigDecimal remaining = remainingAmount(header, null);
        if (remaining.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "合同剩余金额为 0，不能新增付款计划");
        }
        if (req.getAmount().compareTo(remaining) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "本期付款金额不能超过合同剩余金额 " + remaining);
        }
        ContractPaymentPlan entity = new ContractPaymentPlan();
        entity.setContractNo(header.getNo());
        entity.setStage(req.getStage().trim());
        entity.setDueDate(req.getDueDate());
        entity.setAmount(req.getAmount());
        entity.setSummary(nvl(req.getSummary(), ""));
        entity.setFileIds(ContractAttachments.join(req.getFileIds()));
        entity.setStatus(nvl(req.getStatus(), "WAITING"));
        entity.setOrgCode(header.getOrgCode());
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.PaymentPlanSave req) {
        ContractPaymentPlan entity = require(id);
        if ("PAID".equals(entity.getStatus()) || "POSTED".equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PAYMENT_PLAN_NOT_FOUND, "已完成付款的计划禁止修改");
        }
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (req.getStage() != null && req.getStage().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择付款阶段");
        }
        BigDecimal amount = req.getAmount() == null ? entity.getAmount() : req.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "本期付款金额必须大于 0");
        }
        BigDecimal remaining = remainingAmount(contractService.requireByNo(entity.getContractNo()), id);
        if (amount.compareTo(remaining) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "本期付款金额不能超过合同剩余金额 " + remaining);
        }
        entity.setStage(nvl(req.getStage(), entity.getStage()));
        entity.setDueDate(req.getDueDate() == null ? entity.getDueDate() : req.getDueDate());
        entity.setAmount(amount);
        if (req.getSummary() != null) {
            entity.setSummary(req.getSummary().trim());
        }
        if (req.getFileIds() != null) {
            entity.setFileIds(ContractAttachments.join(req.getFileIds()));
        }
        entity.setStatus(nvl(req.getStatus(), entity.getStatus()));
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractPaymentPlan entity = require(id);
        if (!"WAITING".equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PAYMENT_PLAN_NOT_FOUND, "仅待申请计划可删除");
        }
        mapper.deleteById(id);
    }

    /**
     * 发起付款申请：仅推进状态；sourceDocNo 由财务域建应付/付款单后回写，
     * 合同域不自造 YS/FK 单号。
     */
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.PaymentPlanVO apply(Long id, ContractRequests.PaymentApplyRequest req) {
        ContractPaymentPlan entity = require(id);
        if (!"WAITING".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PAYMENT_PLAN_NOT_FOUND, "当前付款计划状态不支持发起申请");
        }
        // 合同必须已生效并执行中；草稿/审批中/完成/终止均不能发起付款
        ContractHeader header = contractService.requireByNo(entity.getContractNo());
        String contractStatus = header.getStatus();
        if (!"EFFECTIVE".equals(contractStatus) && !"EXECUTING".equals(contractStatus)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID,
                    "合同状态不支持付款，仅已生效或执行中的合同可发起付款申请");
        }
        // 付款门槛：全部清单行须验收完成（deliveredQty >= qty），未验收完不允许付款
        List<ContractItem> items = itemMapper.selectList(new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, entity.getContractNo()));
        boolean allAccepted = items != null && !items.isEmpty() && items.stream().allMatch(this::fullyAccepted);
        if (!allAccepted) {
            throw new BizException(ContractErrorCode.ACCEPTANCE_REQUIRED,
                    "合同清单尚未全部验收完成，验收完成后才能发起付款申请");
        }
        // 埋点 contract.acceptance.required-before-pay 默认 true：付款申请前必须有合格且已过账的验收单。
        // 埋点读取失败时按 true 兜底（fail-closed），与财务 internal token 口径一致。
        if (embedPointService.acceptanceRequiredBeforePay()) {
            Long accepted = acceptanceMapper.selectCount(new LambdaQueryWrapper<ContractAcceptance>()
                    .eq(ContractAcceptance::getContractNo, entity.getContractNo())
                    .eq(ContractAcceptance::getResult, "合格")
                    .eq(ContractAcceptance::getStatus, "POSTED")
                    .last("limit 1"));
            if (accepted == null || accepted == 0) {
                throw new BizException(ContractErrorCode.ACCEPTANCE_REQUIRED,
                        "埋点要求付款前完成验收：合同 " + entity.getContractNo() + " 尚无合格且已过账的验收单");
            }
        }
        if (req != null) {
            if (req.getSourceDocNo() != null && !req.getSourceDocNo().isBlank()) {
                entity.setSourceDocNo(req.getSourceDocNo().trim());
            }
            if (req.getSummary() != null && !req.getSummary().isBlank()) {
                entity.setSummary(req.getSummary().trim());
            }
            if (req.getFileIds() != null) {
                entity.setFileIds(ContractAttachments.join(req.getFileIds()));
            }
        }
        entity.setStatus("PENDING");
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 财务域回写来源单据号、凭证号与状态（YS/FK 与凭证号由财务域规则生成）。
     * internal token 准入，无登录上下文，故不套 dataScope assert；按 status 白名单幂等推进。
     */
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.PaymentPlanVO writeBack(Long id, String sourceDocNo, String voucherNo, String status) {
        ContractPaymentPlan entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.PAYMENT_PLAN_NOT_FOUND);
        }
        if (sourceDocNo != null && !sourceDocNo.isBlank()) {
            entity.setSourceDocNo(sourceDocNo.trim());
        }
        if (voucherNo != null && !voucherNo.isBlank()) {
            entity.setVoucherNo(voucherNo.trim());
        }
        if (status != null && !status.isBlank()) {
            entity.setStatus(status.trim());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        // 付款结清自动完成：合同所有付款计划均 PAID/POSTED 且清单全部验收完成时，EXECUTING → COMPLETED
        tryCompleteOnSettled(entity.getContractNo());
        return toVO(entity);
    }

    /** 清单行是否验收完成：数量已交付 >= 合同数量（无数量视为完成，避免 0 数量行卡住付款）。 */
    private boolean fullyAccepted(ContractItem item) {
        BigDecimal qty = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal delivered = item.getDeliveredQty() == null ? BigDecimal.ZERO : item.getDeliveredQty();
        return delivered.compareTo(qty) >= 0;
    }

    /** 所有付款计划结清 + 清单验收完成 → 自动推进合同为已完成（未达条件幂等跳过）。 */
    private void tryCompleteOnSettled(String contractNo) {
        if (contractNo == null || contractNo.isBlank()) {
            return;
        }
        List<ContractPaymentPlan> plans = mapper.selectList(new LambdaQueryWrapper<ContractPaymentPlan>()
                .eq(ContractPaymentPlan::getContractNo, contractNo));
        if (plans == null || plans.isEmpty()) {
            return;
        }
        boolean allSettled = plans.stream().allMatch(p -> PAID_STATUS.contains(p.getStatus()));
        if (!allSettled) {
            return;
        }
        List<ContractItem> items = itemMapper.selectList(new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, contractNo));
        boolean allAccepted = items != null && !items.isEmpty() && items.stream().allMatch(this::fullyAccepted);
        if (!allAccepted) {
            return;
        }
        contractService.advanceIfStatus(contractNo, "EXECUTING", "COMPLETED");
    }

    private ContractVOs.PaymentPlanVO toVO(ContractPaymentPlan e) {
        ContractVOs.PaymentPlanVO vo = new ContractVOs.PaymentPlanVO();
        vo.setId(e.getId());
        vo.setContractNo(e.getContractNo());
        vo.setStage(e.getStage());
        vo.setDueDate(e.getDueDate() == null ? null : e.getDueDate().toString());
        vo.setAmount(e.getAmount());
        vo.setSummary(e.getSummary());
        vo.setSourceDocNo(e.getSourceDocNo());
        vo.setVoucherNo(e.getVoucherNo());
        vo.setStatus(e.getStatus());
        vo.setOrgCode(e.getOrgCode());
        java.util.List<String> fileIds = ContractAttachments.split(e.getFileIds());
        vo.setFileIds(fileIds);
        vo.setAttachmentUrls(ContractAttachments.signedUrls(fileIds, fileApi));
        // 付款到期提醒：天数来自埋点；仅未完成（非 PAID/POSTED）的计划参与判定
        int remind = embedPointService.paymentRemindDays();
        vo.setRemindDays(remind);
        boolean finished = "PAID".equals(e.getStatus()) || "POSTED".equals(e.getStatus());
        if (!finished && e.getDueDate() != null) {
            java.time.LocalDate due = e.getDueDate();
            java.time.LocalDate today = java.time.LocalDate.now();
            vo.setOverdue(due.isBefore(today));
            vo.setDueSoon(!vo.isOverdue() && !due.isAfter(today.plusDays(remind)));
        }
        try {
            vo.setContractName(contractService.requireByNo(e.getContractNo()).getName());
        } catch (BizException ignored) {
            // 合同不在当前数据范围时不泄露名称，仅保留编号
        }
        return vo;
    }

    /**
     * 合同剩余金额：合同金额 - 已付 - 在途，与合同台账「待付款金额」同口径。
     * 更新时把当前计划（未付款、未在途）金额加回，避免把自己算进占用。
     */
    private BigDecimal remainingAmount(ContractHeader header, Long excludePlanId) {
        List<ContractVOs.ContractVO> decorated = contractService.decorate(List.of(header));
        BigDecimal remaining = decorated.isEmpty() || decorated.get(0).getPendingAmount() == null
                ? BigDecimal.ZERO : decorated.get(0).getPendingAmount();
        if (excludePlanId != null) {
            ContractPaymentPlan own = mapper.selectById(excludePlanId);
            if (own != null && header.getNo().equals(own.getContractNo()) && own.getAmount() != null
                    && !PAID_STATUS.contains(own.getStatus()) && !IN_FLIGHT_STATUS.contains(own.getStatus())) {
                remaining = remaining.add(own.getAmount());
            }
        }
        return remaining.max(BigDecimal.ZERO);
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
