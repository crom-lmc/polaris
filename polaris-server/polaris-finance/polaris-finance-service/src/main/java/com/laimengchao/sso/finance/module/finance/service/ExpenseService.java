package com.laimengchao.sso.finance.module.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.common.BizNoService;
import com.laimengchao.sso.finance.common.EmbedPointService;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinExpense;
import com.laimengchao.sso.finance.module.finance.mapper.FinExpenseMapper;
import com.laimengchao.sso.finance.module.finance.support.FinanceApprovalLauncher;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.DataScopeService;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 费用报销单：草稿 → 提交 → 审批（可自动过账）→ 过账 → 付款。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String POSTED = "POSTED";
    public static final String PAID = "PAID";

    private final FinExpenseMapper expenseMapper;
    private final BizNoService bizNoService;
    private final EmbedPointService embedPointService;
    private final VoucherService voucherService;
    private final BudgetService budgetService;
    private final FinanceApprovalLauncher approvalLauncher;
    private final DataScopeService dataScopeService;

    /** 走审批引擎的 bizType；超高级审批线时用带 senior 的模板（双 bizType 模式，同 EHR 招聘） */
    public static final String BIZ_EXPENSE = "finance.expense";
    public static final String BIZ_EXPENSE_SENIOR = "finance.expense.senior";

    @Value("${finance.internal-base-url:http://127.0.0.1:9014}")
    private String internalBaseUrl;

    public R.Page<FinanceVOs.ExpenseVO> page(String status, String keyword, String dept,
                                              LocalDate from, LocalDate to, int page, int size) {
        LambdaQueryWrapper<FinExpense> w = query(status, keyword, dept, from, to);
        w.orderByDesc(FinExpense::getDate).orderByDesc(FinExpense::getId);
        var p = expenseMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(page, 1), Math.max(size, 1)), w);
        List<FinanceVOs.ExpenseVO> rows = new ArrayList<>();
        for (FinExpense e : p.getRecords()) {
            rows.add(toVO(e));
        }
        return R.Page.of(rows, p.getTotal());
    }

    private LambdaQueryWrapper<FinExpense> query(String status, String keyword, String dept,
                                                  LocalDate from, LocalDate to) {
        LambdaQueryWrapper<FinExpense> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinExpense::getOrgCode);
        w.eq(StringUtils.hasText(status), FinExpense::getStatus, status.trim())
                .eq(StringUtils.hasText(dept), FinExpense::getDept, dept.trim())
                .ge(from != null, FinExpense::getDate, from)
                .le(to != null, FinExpense::getDate, to);
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.like(FinExpense::getNo, k)
                    .or().like(FinExpense::getApplicant, k)
                    .or().like(FinExpense::getReason, k)
                    .or().like(FinExpense::getVoucherNo, k));
        }
        return w;
    }

    public FinanceVOs.ExpenseVO detail(Long id) {
        FinExpense e = require(id);
        dataScopeService.assertCanAccessOrg(e.getOrgCode());
        return toVO(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.ExpenseVO create(FinanceRequests.ExpenseSave req) {
        validate(req);
        FinExpense e = new FinExpense();
        e.setNo(bizNoService.next(BizNoService.TABLE_EXPENSE));
        apply(e, req);
        e.setStatus(DRAFT);
        e.setOrgCode(dataScopeService.orgCodeOf(req.getOrgCode()));
        expenseMapper.insert(e);
        return toVO(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.ExpenseVO update(Long id, FinanceRequests.ExpenseSave req) {
        FinExpense e = require(id);
        dataScopeService.assertCanAccessOrg(e.getOrgCode());
        if (!DRAFT.equals(e.getStatus()) && !REJECTED.equals(e.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅草稿或已驳回的报销单可编辑");
        }
        validate(req);
        apply(e, req);
        expenseMapper.updateById(e);
        return toVO(e);
    }

    /**
     * 提交：草稿 / 已驳回 → 待审批。
     * 金额达到埋点 finance.expense.approval-amount 时走统一审批引擎（双 bizType 模板分流），
     * 未达阈值维持原有简单审批（PENDING 后由有权限者直接 approve）。
     */
    public void submit(Long id) {
        FinExpense e = require(id);
        dataScopeService.assertCanAccessOrg(e.getOrgCode());
        if (!DRAFT.equals(e.getStatus()) && !REJECTED.equals(e.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅草稿或已驳回的报销单可提交");
        }
        boolean needApproval = e.getAmount() != null
                && e.getAmount().compareTo(java.math.BigDecimal.valueOf(embedPointService.expenseApprovalAmount())) >= 0;
        if (!needApproval) {
            e.setStatus(PENDING);
            expenseMapper.updateById(e);
            return;
        }
        // 三段式：先把本地状态提交为 PENDING，再远程发起审批，避免同步回调读到未提交行。
        e.setStatus(PENDING);
        expenseMapper.updateById(e);
        approvalLauncher.launch(() -> buildApprovalStart(e));
    }

    /** 构造审批发起请求：金额作为业务点，超阈值选 senior 模板多一级审批 */
    private ApprovalStartDTO buildApprovalStart(FinExpense e) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        boolean senior = e.getAmount().compareTo(
                java.math.BigDecimal.valueOf(embedPointService.expenseApprovalAmount())) > 0;
        dto.setBizType(senior ? BIZ_EXPENSE_SENIOR : BIZ_EXPENSE);
        dto.setBizId(String.valueOf(e.getId()));
        dto.setBizNo(e.getNo());
        dto.setTitle("费用报销 " + e.getNo() + " " + e.getAmount() + " 元");
        dto.setApplicantName(e.getApplicant());
        dto.setSubjectId(String.valueOf(e.getId()));
        dto.setBizIdKind("DOC");
        java.util.Map<String, Object> form = new java.util.LinkedHashMap<>();
        form.put("expenseId", String.valueOf(e.getId()));
        form.put("no", e.getNo());
        form.put("amount", e.getAmount());
        form.put("dept", e.getDept());
        form.put("reason", e.getReason());
        form.put("applicant", e.getApplicant());
        dto.setFormData(form);
        dto.setCallbackUrl(internalBaseUrl + "/internal/finance/approval-callback");
        return dto;
    }

    /**
     * 审批通过：待审批 → 已通过；
     * 若埋点 finance.voucher.auto-post=true，则直接生成凭证并过账（POSTED）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        FinExpense e = require(id);
        dataScopeService.assertCanAccessOrg(e.getOrgCode());
        if (!PENDING.equals(e.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅待审批的报销单可审批");
        }
        if (embedPointService.autoPost()) {
            postInternal(e);
        } else {
            e.setStatus(APPROVED);
            expenseMapper.updateById(e);
        }
    }

    /**
     * 审批中心终审回调：通过 → APPROVED（若自动过账埋点开启则直接过账），驳回/撤回/终止 → REJECTED。
     * 幂等：仅 PENDING 状态才更新，重复投递返回 false 由 Controller 以 200 吃掉。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handleApprovalResult(String bizId, Boolean approved, Integer result) {
        Long id;
        try {
            id = Long.valueOf(bizId);
        } catch (NumberFormatException e) {
            log.warn("报销审批回调 bizId 非数字：{}", bizId);
            return false;
        }
        FinExpense e = expenseMapper.selectById(id);
        if (e == null || !PENDING.equals(e.getStatus())) {
            log.info("报销审批回调忽略：id={} status={}", id, e == null ? "deleted" : e.getStatus());
            return false;
        }
        boolean pass = Boolean.TRUE.equals(approved) || Integer.valueOf(1).equals(result);
        if (pass) {
            if (embedPointService.autoPost()) {
                postInternal(e);
            } else {
                expenseMapper.update(null, new LambdaUpdateWrapper<FinExpense>()
                        .eq(FinExpense::getId, id).eq(FinExpense::getStatus, PENDING)
                        .set(FinExpense::getStatus, APPROVED));
            }
        } else {
            expenseMapper.update(null, new LambdaUpdateWrapper<FinExpense>()
                    .eq(FinExpense::getId, id).eq(FinExpense::getStatus, PENDING)
                    .set(FinExpense::getStatus, REJECTED));
        }
        return true;
    }

    /** 驳回：待审批 → 已驳回 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) {
        FinExpense e = changeStatus(id, PENDING, REJECTED, REJECTED);
        expenseMapper.updateById(e);
    }

    /** 过账：已通过 → 生成凭证并 POSTED */
    @Transactional(rollbackFor = Exception.class)
    public void post(Long id) {
        FinExpense e = require(id);
        dataScopeService.assertCanAccessOrg(e.getOrgCode());
        if (!APPROVED.equals(e.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅已通过的报销单可过账");
        }
        postInternal(e);
    }

    /** 付款：已入账 → 已付款 */
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long id) {
        FinExpense e = changeStatus(id, POSTED, PAID, PAID);
        expenseMapper.updateById(e);
    }

    /** 凭证生成 + 状态回写（幂等由 VoucherService 保证） */
    private void postInternal(FinExpense e) {
        // 预算强控模式（finance.budget.control-mode=STRICT）在过账前拦截超预算；
        // REMIND 或未配置 department 时只提醒不拦截，避免历史数据无法过账。
        if ("STRICT".equals(embedPointService.budgetControlMode())
                && StringUtils.hasText(e.getDept())) {
            Integer year = e.getDate() == null ? LocalDate.now().getYear() : e.getDate().getYear();
            if (budgetService.available(e.getDept(), year).compareTo(e.getAmount()) < 0) {
                throw new BizException(FinanceErrorCode.BUDGET_STRICT_EXCEEDED,
                        "部门 " + e.getDept() + " " + year + " 年可用预算不足以覆盖本笔报销 " + e.getAmount());
            }
        }
        var voucher = voucherService.createAndPost("EXPENSE", e.getNo(), e.getReason(), e.getSubject(),
                e.getAmount(), e.getDate(), e.getApplicant(), e.getDept(), null, e.getOrgCode());
        expenseMapper.update(null, new LambdaUpdateWrapper<FinExpense>()
                .eq(FinExpense::getId, e.getId())
                .set(FinExpense::getVoucherNo, voucher.getNo())
                .set(FinExpense::getStatus, POSTED));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinExpense e = require(id);
        dataScopeService.assertCanAccessOrg(e.getOrgCode());
        if (!DRAFT.equals(e.getStatus()) && !REJECTED.equals(e.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅草稿或已驳回的报销单可删除");
        }
        expenseMapper.deleteById(id);
    }

    private FinExpense changeStatus(Long id, String from1, String from2, String target) {
        FinExpense e = require(id);
        dataScopeService.assertCanAccessOrg(e.getOrgCode());
        boolean ok = from1.equals(e.getStatus()) || from2.equals(e.getStatus());
        if (!ok) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID,
                    "当前状态 " + e.getStatus() + " 不支持该操作");
        }
        e.setStatus(target);
        return e;
    }

    private void validate(FinanceRequests.ExpenseSave req) {
        if (req == null || !StringUtils.hasText(req.getApplicant())
                || !StringUtils.hasText(req.getReason()) || !StringUtils.hasText(req.getSubject())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "申请人、事由与科目不能为空");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报销金额必须大于零");
        }
    }

    private void apply(FinExpense e, FinanceRequests.ExpenseSave req) {
        e.setApplicant(req.getApplicant().trim());
        e.setReason(req.getReason().trim());
        e.setAmount(req.getAmount());
        e.setSubject(req.getSubject().trim());
        e.setDept(StringUtils.hasText(req.getDept()) ? req.getDept().trim() : "");
        e.setDate(req.getDate() == null ? LocalDate.now() : req.getDate());
    }

    private FinExpense require(Long id) {
        FinExpense e = id == null ? null : expenseMapper.selectById(id);
        if (e == null) {
            throw new BizException(FinanceErrorCode.EXPENSE_NOT_FOUND);
        }
        return e;
    }

    private FinanceVOs.ExpenseVO toVO(FinExpense e) {
        FinanceVOs.ExpenseVO vo = new FinanceVOs.ExpenseVO();
        vo.setId(e.getId());
        vo.setNo(e.getNo());
        vo.setApplicant(e.getApplicant());
        vo.setReason(e.getReason());
        vo.setAmount(e.getAmount());
        vo.setDept(e.getDept());
        vo.setSubject(e.getSubject());
        vo.setStatus(e.getStatus());
        vo.setVoucherNo(e.getVoucherNo());
        vo.setDate(e.getDate());
        vo.setOrgCode(e.getOrgCode());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
