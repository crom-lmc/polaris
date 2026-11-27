package com.laimengchao.sso.contract.module.contract.service.impl;

import com.laimengchao.sso.contract.module.contract.service.PaymentPlanService;
import com.laimengchao.sso.contract.module.contract.service.ContractService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.contract.common.BizNoService;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.common.EmbedPointService;
import com.laimengchao.sso.contract.common.impl.BizNoServiceImpl;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractAcceptance;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentPlan;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentTerm;
import com.laimengchao.sso.contract.module.contract.mapper.ContractAcceptanceMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPaymentPlanMapper;
import com.laimengchao.sso.contract.module.contract.support.ContractApprovalLauncher;
import com.laimengchao.sso.contract.module.contract.support.ContractAttachments;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.contract.security.DataScopeService;
import com.laimengchao.sso.file.client.api.FileApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 合同付款计划：发起付款申请进审批中心，终审通过后置已审批待付；来源单据号由财务域生成后回写。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPlanServiceImpl implements PaymentPlanService {

    private static final Set<String> PAID_STATUS = Set.of("PAID", "POSTED");
    private static final Set<String> IN_FLIGHT_STATUS = Set.of("PENDING", "APPROVED");
    /** 计入付款节点「已申请」的状态：驳回释放额度，其余均占用 */
    private static final Set<String> TERM_APPLIED_STATUS = Set.of("WAITING", "PENDING", "APPROVED", "POSTED", "PAID");

    /** 付款计划审批 bizType；超升级线用 senior 模板多一级审批 */
    public static final String BIZ_PAYMENT = "contract.payment";
    public static final String BIZ_PAYMENT_SENIOR = "contract.payment.senior";

    private final ContractPaymentPlanMapper mapper;
    private final ContractAcceptanceMapper acceptanceMapper;
    private final ContractItemMapper itemMapper;
    private final com.laimengchao.sso.contract.module.contract.mapper.ContractPaymentTermMapper paymentTermMapper;
    private final ContractService contractService;
    private final com.laimengchao.sso.contract.module.contract.service.PaymentTermService paymentTermService;
    private final EmbedPointService embedPointService;
    private final DataScopeService dataScopeService;
    private final FileApi fileApi;
    private final ContractApprovalLauncher approvalLauncher;
    private final com.laimengchao.sso.contract.module.contract.support.ContractApprovalInfoSupport approvalInfoSupport;
    private final BizNoService bizNoService;

    @Value("${contract.internal-base-url:http://127.0.0.1:9015}")
    private String internalBaseUrl;

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
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        // 引用节点：阶段由节点决定（忽略请求体 stage），金额受节点可用余额约束
        ContractPaymentTerm term = resolveTerm(header, req.getTermId());
        String stage = term != null ? term.getStage() : nvl(req.getStage(), "");
        if (stage.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择付款阶段");
        }
        if (req.getDueDate() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择应付日期");
        }
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "本期付款金额必须大于 0");
        }
        // GOODS 必须选清单行，且都属本合同；其余阶段忽略 itemIds
        String itemIds = "";
        if ("GOODS".equals(stage)) {
            itemIds = validateGoodsItems(header, req.getItemIds());
        }
        BigDecimal limit = term != null ? termAvailable(term, null) : remainingAmount(header, null);
        if (limit.signum() <= 0) {
            throw new BizException(ContractErrorCode.PAYMENT_TERM_LIMIT, "该付款节点可申请金额为 0，不能新增付款计划");
        }
        if (req.getAmount().compareTo(limit) > 0) {
            throw new BizException(term != null ? ContractErrorCode.PAYMENT_TERM_LIMIT : ErrorCode.PARAM_ERROR,
                    (term != null ? "超出该付款节点可申请金额 " : "本期付款金额不能超过合同剩余金额 ") + limit);
        }
        ContractPaymentPlan entity = new ContractPaymentPlan();
        entity.setNo(bizNoService.next(BizNoServiceImpl.TABLE_PAYMENT_PLAN));
        entity.setContractNo(header.getNo());
        entity.setStage(stage);
        entity.setTermId(term == null ? null : term.getId());
        entity.setItemIds(itemIds);
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

    /**
     * 节点引用校验：合同已配置付款节点时 MUST 引用节点（termId 必填），不再自由录入阶段/金额；
     * 仅存量无节点合同允许 termId 为空沿用原全验收逻辑（方案「存量数据处理」口径）。
     */
    private ContractPaymentTerm resolveTerm(ContractHeader header, Long termId) {
        Long termCount = paymentTermMapper.selectCount(new LambdaQueryWrapper<ContractPaymentTerm>()
                .eq(ContractPaymentTerm::getContractNo, header.getNo()));
        boolean hasTerms = termCount != null && termCount > 0;
        if (termId == null) {
            if (hasTerms) {
                throw new BizException(ContractErrorCode.PAYMENT_TERM_REQUIRED);
            }
            return null;
        }
        ContractPaymentTerm term = paymentTermService.require(termId);
        if (!header.getNo().equals(term.getContractNo())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "付款节点不属于该合同");
        }
        return term;
    }

    /** 节点可用余额 = 节点金额 - 该节点已申请金额（非 REJECTED）；excludePlanId 用于编辑时排除自身 */
    private BigDecimal termAvailable(ContractPaymentTerm term, Long excludePlanId) {
        BigDecimal used = BigDecimal.ZERO;
        List<ContractPaymentPlan> plans = mapper.selectList(new LambdaQueryWrapper<ContractPaymentPlan>()
                .eq(ContractPaymentPlan::getTermId, term.getId()));
        if (plans != null) {
            for (ContractPaymentPlan p : plans) {
                if ((excludePlanId == null || !excludePlanId.equals(p.getId()))
                        && p.getAmount() != null && TERM_APPLIED_STATUS.contains(p.getStatus())) {
                    used = used.add(p.getAmount());
                }
            }
        }
        BigDecimal amount = term.getAmount() == null ? BigDecimal.ZERO : term.getAmount();
        return amount.subtract(used).max(BigDecimal.ZERO);
    }

    /** GOODS 清单行校验：非空、逗号分隔均为数字、都属本合同且交付数量 > 0；返回规范化 ID 串 */
    private String validateGoodsItems(ContractHeader header, String itemIds) {
        if (itemIds == null || itemIds.isBlank()) {
            throw new BizException(ContractErrorCode.GOODS_ITEMS_REQUIRED);
        }
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (String part : itemIds.split(",")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "清单行 ID 不合法");
            }
        }
        if (ids.isEmpty()) {
            throw new BizException(ContractErrorCode.GOODS_ITEMS_REQUIRED);
        }
        List<ContractItem> items = itemMapper.selectBatchIds(ids);
        if (items == null || items.size() != ids.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所选清单行不存在");
        }
        for (ContractItem item : items) {
            if (!header.getNo().equals(item.getContractNo())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "所选清单行不属于该合同");
            }
            BigDecimal delivered = item.getDeliveredQty() == null ? BigDecimal.ZERO : item.getDeliveredQty();
            if (delivered.signum() <= 0) {
                throw new BizException(ContractErrorCode.ACCEPTANCE_REQUIRED, "所选清单行尚未验收合格");
            }
        }
        return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.PaymentPlanSave req) {
        ContractPaymentPlan entity = require(id);
        // 仅待申请（未发起审批）的计划可编辑：发起后金额/阶段即锁定，变更须另行走变更流程
        if (!"WAITING".equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PAYMENT_PLAN_NOT_FOUND, "仅待申请计划可编辑");
        }
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        ContractHeader header = contractService.requireByNo(entity.getContractNo());
        // 节点引用以请求为准；未传时沿用原节点（阶段由原节点决定，不接受请求体覆盖 stage）
        Long termId = req.getTermId() != null ? req.getTermId() : entity.getTermId();
        ContractPaymentTerm term = resolveTerm(header, termId);
        String stage = term != null ? term.getStage() : entity.getStage();
        BigDecimal amount = req.getAmount() == null ? entity.getAmount() : req.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "本期付款金额必须大于 0");
        }
        String itemIds = entity.getItemIds() == null ? "" : entity.getItemIds();
        if ("GOODS".equals(stage)) {
            String requested = req.getItemIds() != null ? req.getItemIds() : entity.getItemIds();
            itemIds = validateGoodsItems(header, requested);
        }
        BigDecimal limit = term != null ? termAvailable(term, id) : remainingAmount(header, id);
        if (amount.compareTo(limit) > 0) {
            throw new BizException(term != null ? ContractErrorCode.PAYMENT_TERM_LIMIT : ErrorCode.PARAM_ERROR,
                    (term != null ? "超出该付款节点可申请金额 " : "本期付款金额不能超过合同剩余金额 ") + limit);
        }
        entity.setStage(stage);
        entity.setTermId(term == null ? null : term.getId());
        entity.setItemIds(itemIds);
        entity.setDueDate(req.getDueDate() == null ? entity.getDueDate() : req.getDueDate());
        entity.setAmount(amount);
        if (req.getSummary() != null) {
            entity.setSummary(req.getSummary().trim());
        }
        if (req.getFileIds() != null) {
            entity.setFileIds(ContractAttachments.join(req.getFileIds()));
        }
        // 状态是流程结果，编辑 MUST NOT 改状态；仅待申请可编辑，状态保持 WAITING
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
     * 发起付款申请：置 PENDING 并进审批中心；终审通过后由回调推进 APPROVED。
     * 此处不用事务：审批中心回调可能同步到达，必须保证申请行先落库再发起审批。
     */
    public ContractVOs.PaymentPlanVO apply(Long id, ContractRequests.PaymentApplyRequest req) {
        ContractPaymentPlan entity = require(id);
        String beforeStatus = entity.getStatus();
        if (!"WAITING".equals(beforeStatus) && !"REJECTED".equals(beforeStatus)) {
            throw new BizException(ContractErrorCode.PAYMENT_PLAN_NOT_FOUND, "当前付款计划状态不支持发起申请");
        }
        // 合同必须已生效并执行中；草稿/审批中/完成/终止均不能发起付款
        ContractHeader header = contractService.requireByNo(entity.getContractNo());
        String contractStatus = header.getStatus();
        if (!"EFFECTIVE".equals(contractStatus) && !"EXECUTING".equals(contractStatus)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID,
                    "合同状态不支持付款，仅已生效或执行中的合同可发起付款申请");
        }
        // 付款门槛按节点阶段分流；termId 为空的存量计划沿用「全验收」原逻辑
        ContractPaymentTerm term = entity.getTermId() == null ? null : resolveTerm(header, entity.getTermId());
        String stage = term != null ? term.getStage() : entity.getStage();
        List<ContractItem> items = itemMapper.selectList(new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, entity.getContractNo()));
        boolean allAccepted = items != null && !items.isEmpty() && items.stream().allMatch(this::fullyAccepted);
        boolean needsAcceptance = term == null || "GOODS".equals(stage) || "ACCEPT".equals(stage);
        if (needsAcceptance) {
            if ("ACCEPT".equals(stage) || term == null) {
                // 验收款 / 存量：全部清单行验收完成
                if (!allAccepted) {
                    throw new BizException(ContractErrorCode.ACCEPTANCE_REQUIRED,
                            "合同清单尚未全部验收完成，验收完成后才能发起付款申请");
                }
            } else {
                // 到货款：所选清单行已有合格验收（已交付 > 0），按行/按批放行
                validateGoodsItems(header, entity.getItemIds());
            }
            // 埋点 contract.acceptance.required-before-pay 仅对 GOODS / ACCEPT（含存量）生效；
            // 不校验验收单 status=POSTED——过账在应付单之后，要求先过账会死锁。
            if (embedPointService.acceptanceRequiredBeforePay()) {
                Long accepted = acceptanceMapper.selectCount(new LambdaQueryWrapper<ContractAcceptance>()
                        .eq(ContractAcceptance::getContractNo, entity.getContractNo())
                        .in(ContractAcceptance::getResult, "合格", "通过", "PASS", "OK")
                        .last("limit 1"));
                if (accepted == null || accepted == 0) {
                    throw new BizException(ContractErrorCode.ACCEPTANCE_REQUIRED,
                            "埋点要求付款前完成验收：合同 " + entity.getContractNo() + " 尚无合格验收单");
                }
            }
        } else if ("WARRANTY".equals(stage)) {
            // 质保金：质保到期日非空且 <= 今天；不重复要求验收
            if (header.getWarrantyDueDate() == null
                    || header.getWarrantyDueDate().isAfter(java.time.LocalDate.now())) {
                throw new BizException(ContractErrorCode.WARRANTY_NOT_DUE);
            }
        }
        // PREPAY：仅合同状态校验（上面已做），生效即可发起，跳过验收
        // 金额上限复核（防节点金额/清单变动后旧计划超额）：引用节点按节点余额，存量按合同剩余
        BigDecimal cap = term != null ? termAvailable(term, entity.getId()) : remainingAmount(header, entity.getId());
        if (entity.getAmount() != null && entity.getAmount().compareTo(cap) > 0) {
            throw new BizException(term != null ? ContractErrorCode.PAYMENT_TERM_LIMIT : ErrorCode.PARAM_ERROR,
                    "本期付款金额 " + entity.getAmount() + " 超出可申请金额 " + cap);
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
        // 先落库再发起：审批终审可能同步回调，避免回调读到未提交行
        try {
            approvalLauncher.launch(() -> buildApprovalStart(entity, header));
        } catch (RuntimeException e) {
            // 审批发起失败必须退回原状态，否则会卡在 PENDING 且不允许重试
            mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ContractPaymentPlan>()
                    .eq(ContractPaymentPlan::getId, entity.getId())
                    .eq(ContractPaymentPlan::getStatus, "PENDING")
                    .set(ContractPaymentPlan::getStatus, beforeStatus));
            entity.setStatus(beforeStatus);
            throw e;
        }
        return toVO(entity);
    }

    /** 构造付款计划审批发起请求；dept 取自合同归口部门（组织名或编码均可） */
    private ApprovalStartDTO buildApprovalStart(ContractPaymentPlan entity, ContractHeader header) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        boolean senior = entity.getAmount().compareTo(
                BigDecimal.valueOf(embedPointService.paymentApprovalAmount())) > 0;
        dto.setBizType(senior ? BIZ_PAYMENT_SENIOR : BIZ_PAYMENT);
        dto.setBizId(String.valueOf(entity.getId()));
        dto.setBizNo(entity.getContractNo() + "-" + entity.getId());
        // applicant_id 在 appr_instance 非空无默认值；不传会让 settings 侧 INSERT 失败返回 500
        Long applicantId = com.laimengchao.sso.utils.AccountContext.getAccountId();
        if (applicantId == null) {
            throw new BizException(ContractErrorCode.APPROVAL_START_FAILED, "无法获取当前登录人，请重新登录后再发起审批");
        }
        dto.setApplicantId(applicantId);
        dto.setApplicantName(com.laimengchao.sso.utils.AccountContext.getUsername());
        dto.setTitle("付款计划 " + entity.getContractNo() + " " + nvl(entity.getStage(), "") + " "
                + entity.getAmount() + " 元");
        dto.setSubjectId(String.valueOf(header.getId()));
        dto.setBizIdKind("DOC");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("paymentId", String.valueOf(entity.getId()));
        form.put("contractNo", entity.getContractNo());
        form.put("contractName", header.getName());
        form.put("amount", entity.getAmount());
        form.put("stage", entity.getStage());
        form.put("dueDate", entity.getDueDate() == null ? "" : entity.getDueDate().toString());
        form.put("dept", header.getDept() == null || header.getDept().isBlank()
                ? header.getOrgCode() : header.getDept());
        form.put("partnerCode", header.getPartnerCode());
        dto.setFormData(form);
        dto.setCallbackUrl(internalBaseUrl + "/internal/contract/approval-callback");
        return dto;
    }

    /**
     * 审批中心终审回调：通过 PENDING→APPROVED，驳回/撤回/终止 PENDING→REJECTED。
     * internal 调用无登录上下文，不套 dataScope；仅 PENDING 才更新，重复投递返回 false。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handleApprovalResult(String bizId, Boolean approved, Integer result) {
        Long id;
        try {
            id = Long.valueOf(bizId);
        } catch (NumberFormatException e) {
            log.warn("付款计划审批回调 bizId 非数字：{}", bizId);
            return false;
        }
        ContractPaymentPlan entity = mapper.selectById(id);
        if (entity == null || !"PENDING".equals(entity.getStatus())) {
            log.info("付款计划审批回调忽略：id={} status={}", id,
                    entity == null ? "deleted" : entity.getStatus());
            return false;
        }
        boolean pass = Boolean.TRUE.equals(approved) || Integer.valueOf(1).equals(result);
        return mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ContractPaymentPlan>()
                .eq(ContractPaymentPlan::getId, id)
                .eq(ContractPaymentPlan::getStatus, "PENDING")
                .set(ContractPaymentPlan::getStatus, pass ? "APPROVED" : "REJECTED")) > 0;
    }

    @Override
    public ContractVOs.ApprovalInfoVO approvalInfo(Long id) {
        require(id); // 复用数据权限校验，未发起的付款计划也允许查（返回 null）
        // 同一付款计划可能因金额是否超升级线走 contract.payment / contract.payment.senior 两套模板，
        // 共享 Support 两处都查并取雪花 ID 最大的那张。
        com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO found =
                approvalInfoSupport.latest(String.valueOf(id), BIZ_PAYMENT, BIZ_PAYMENT_SENIOR);
        return approvalInfoSupport.toVO(found);
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
        vo.setNo(e.getNo());
        vo.setContractNo(e.getContractNo());
        vo.setStage(e.getStage());
        vo.setDueDate(e.getDueDate() == null ? null : e.getDueDate().toString());
        if (e.getTermId() != null) {
            vo.setTermId(String.valueOf(e.getTermId()));
            ContractPaymentTerm term = paymentTermMapper.selectById(e.getTermId());
            if (term != null) {
                vo.setTermName(termName(term.getStage()));
            }
        }
        vo.setItemIds(e.getItemIds());
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
            ContractHeader h = contractService.requireByNo(e.getContractNo());
            vo.setContractName(h.getName());
            vo.setWarrantyDueDate(h.getWarrantyDueDate() == null ? null : h.getWarrantyDueDate().toString());
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

    /** 付款节点阶段中文名（与字典 contract.payment_stage 一致） */
    private String termName(String stage) {
        return java.util.Map.of("PREPAY", "预付款", "GOODS", "到货款", "ACCEPT", "验收款", "WARRANTY", "质保金")
                .getOrDefault(stage, stage);
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
