package com.laimengchao.sso.finance.module.finance.service.impl;

import com.laimengchao.sso.finance.common.impl.BizNoServiceImpl;
import com.laimengchao.sso.finance.module.finance.service.ExpenseService;
import com.laimengchao.sso.finance.module.finance.service.BudgetService;
import com.laimengchao.sso.finance.module.finance.service.VoucherService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.client.api.EhrOrgApi;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.SignUrlRequest;
import com.laimengchao.sso.finance.common.BizNoService;
import com.laimengchao.sso.finance.common.EmbedPointService;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinExpense;
import com.laimengchao.sso.finance.module.finance.entity.FinSubject;
import com.laimengchao.sso.finance.module.finance.mapper.FinExpenseMapper;
import com.laimengchao.sso.finance.module.finance.mapper.FinSubjectMapper;
import com.laimengchao.sso.finance.module.finance.support.FinanceApprovalLauncher;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.DataScopeService;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.dto.DictItemDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 费用报销单：草稿 → 提交 → 审批（可自动过账）→ 过账 → 付款。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

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
    /** 报销类型字典：字典项 remark 承载「类型 → 会计科目」映射（JSON：subjectCode/subjectName） */
    public static final String EXPENSE_TYPE_DICT = "finance.expense_type";
    /** 报销凭证统一文件服务策略键；file_storage_policy 必须有启用策略，否则上传报 8102 */
    public static final String FILE_BIZ_TYPE = "finance_expense_attachment";
    public static final String FILE_OWNER_TYPE = "FIN_EXPENSE";
    private static final long SIGN_EXPIRE_SECONDS = 900L;
    private static final String SUBJECT_ENABLED = "ENABLED";

    private final FinSubjectMapper subjectMapper;
    private final SettingsClient settingsClient;
    private final EhrOrgApi ehrOrgApi;
    private final FileApi fileApi;
    private final ObjectMapper objectMapper;

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

    /**
     * 发起报销的默认申请人/归属部门。服务端按账号 ID 在 EHR 员工与组织树中反查，
     * 避免前端用 /api/auth/me 的雪花 ID 做本地匹配时丢精度导致部门静默为空。
     */
    @Override
    public FinanceVOs.ExpenseProfileVO myProfile() {
        FinanceVOs.ExpenseProfileVO vo = new FinanceVOs.ExpenseProfileVO();
        Long accountId = AccountContext.getAccountId();
        if (accountId == null) {
            log.warn("报销默认部门解析失败：当前账号上下文为空");
            return vo;
        }
        String account = String.valueOf(accountId);
        Map<String, Object> emp = activeEmployees().stream()
                .filter(e -> account.equals(text(e.get("id"))))
                .findFirst().orElse(null);
        if (emp == null) {
            log.warn("报销默认部门解析失败：EHR 未找到账号 {} 对应的在职员工", account);
            return vo;
        }
        vo.setApplicant(text(emp.get("name")));
        String orgId = text(emp.get("orgId"));
        if (!StringUtils.hasText(orgId)) {
            log.warn("报销默认部门解析失败：账号 {} 对应员工未维护所属组织", account);
            return vo;
        }
        Map<String, Object> org = orgTree().stream()
                .filter(n -> orgId.equals(text(n.get("id"))))
                .findFirst().orElse(null);
        if (org == null) {
            log.warn("报销默认部门解析失败：EHR 组织 {} 不存在", orgId);
            return vo;
        }
        vo.setDept(text(org.get("name")));
        return vo;
    }

    private List<Map<String, Object>> activeEmployees() {
        try {
            var resp = ehrOrgApi.activeEmployees();
            return resp != null && resp.ok() && resp.getData() != null ? resp.getData() : List.of();
        } catch (Exception e) {
            log.warn("报销默认申请人读取 EHR 在职员工失败：{}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> orgTree() {
        try {
            var resp = ehrOrgApi.tree();
            return resp != null && resp.ok() && resp.getData() != null ? resp.getData() : List.of();
        } catch (Exception e) {
            log.warn("报销默认部门读取 EHR 组织树失败：{}", e.getMessage());
            return List.of();
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private LambdaQueryWrapper<FinExpense> query(String status, String keyword, String dept,
                                                  LocalDate from, LocalDate to) {
        LambdaQueryWrapper<FinExpense> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinExpense::getOrgCode);
        w.eq(StringUtils.hasText(status), FinExpense::getStatus, (status == null ? null : status.trim()))
                .eq(StringUtils.hasText(dept), FinExpense::getDept, (dept == null ? null : dept.trim()))
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
        fillDefaults(req);
        SubjectRef subject = resolveSubject(req.getExpenseType());
        validate(req);
        FinExpense e = new FinExpense();
        e.setNo(bizNoService.next(BizNoServiceImpl.TABLE_EXPENSE));
        apply(e, req, subject);
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
        fillDefaults(req);
        SubjectRef subject = resolveSubject(req.getExpenseType());
        validate(req);
        apply(e, req, subject);
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
        form.put("expenseType", e.getExpenseType());
        form.put("subject", e.getSubject());
        form.put("fileIds", fileIdsOf(e.getFileIds()));
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

    /** 申请人/归属部门由服务端按当前账号从 EHR 补齐，客户端不得再靠 accountId 本地匹配 */
    private void fillDefaults(FinanceRequests.ExpenseSave req) {
        if (req == null) {
            return;
        }
        if (StringUtils.hasText(req.getApplicant()) && StringUtils.hasText(req.getDept())) {
            return;
        }
        FinanceVOs.ExpenseProfileVO profile = myProfile();
        if (!StringUtils.hasText(req.getApplicant())) {
            req.setApplicant(profile.getApplicant());
        }
        if (!StringUtils.hasText(req.getDept())) {
            req.setDept(profile.getDept());
        }
    }

    private record SubjectRef(String code, String name) {
    }

    /** 报销类型 → 会计科目：映射只维护在 settings 字典项 remark，财务服务不落本地配置表 */
    private SubjectRef resolveSubject(String expenseType) {
        if (!StringUtils.hasText(expenseType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择报销类型");
        }
        String type = expenseType.trim();
        DictItemDTO item = settingsClient.items(EXPENSE_TYPE_DICT).stream()
                .filter(i -> type.equals(i.getValue()))
                .findFirst().orElse(null);
        if (item == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报销类型无效或已停用：" + type);
        }
        String code = subjectCodeOf(item.getRemark());
        if (!StringUtils.hasText(code)) {
            throw new BizException(FinanceErrorCode.EXPENSE_TYPE_SUBJECT_MISSING);
        }
        FinSubject subject = subjectMapper.selectOne(new LambdaQueryWrapper<FinSubject>()
                .eq(FinSubject::getCode, code)
                .eq(FinSubject::getStatus, SUBJECT_ENABLED)
                .last("limit 1"));
        if (subject == null) {
            throw new BizException(FinanceErrorCode.EXPENSE_TYPE_SUBJECT_MISSING,
                    "报销类型 " + type + " 映射的科目 " + code + " 不存在或未启用");
        }
        return new SubjectRef(code, subject.getName());
    }

    /**
     * 字典项 remark 优先按 JSON 读 subjectCode；兼容早期直接写科目编码的纯文本。
     */
    private String subjectCodeOf(String remark) {
        if (!StringUtils.hasText(remark)) {
            return "";
        }
        String raw = remark.trim();
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isObject()) {
                JsonNode code = node.get("subjectCode");
                return code == null || code.isNull() ? "" : code.asText().trim();
            }
            if (node.isTextual()) {
                return node.asText().trim();
            }
        } catch (Exception ignored) {
            // 裸科目编码不是合法 JSON，直接按文本解析
        }
        return raw;
    }

    private String dictLabel(String type) {
        if (!StringUtils.hasText(type)) {
            return "";
        }
        return settingsClient.items(EXPENSE_TYPE_DICT).stream()
                .filter(i -> type.trim().equals(i.getValue()))
                .findFirst().map(DictItemDTO::getLabel).orElse("");
    }

    private static String normalizeFileIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return String.join(",", ids.stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList());
    }

    private static List<String> fileIdsOf(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim)
                .filter(StringUtils::hasText).distinct().toList();
    }

    private void validate(FinanceRequests.ExpenseSave req) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (!StringUtils.hasText(req.getApplicant()) || !StringUtils.hasText(req.getReason())
                || !StringUtils.hasText(req.getDept())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "申请人、事由与归属部门不能为空");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报销金额必须大于零");
        }
        if (req.getFileIds() == null || req.getFileIds().stream().noneMatch(StringUtils::hasText)) {
            throw new BizException(FinanceErrorCode.EXPENSE_ATTACHMENT_REQUIRED);
        }
    }

    private void apply(FinExpense e, FinanceRequests.ExpenseSave req, SubjectRef subject) {
        e.setApplicant(req.getApplicant().trim());
        e.setExpenseType(req.getExpenseType().trim());
        e.setReason(req.getReason().trim());
        e.setAmount(req.getAmount());
        e.setDept(req.getDept().trim());
        e.setSubject(subject.name());
        e.setSubjectCode(subject.code());
        e.setExpenseReason(req.getExpenseReason() == null ? "" : req.getExpenseReason().trim());
        e.setInvoiceType(req.getInvoiceType() == null ? "" : req.getInvoiceType().trim());
        e.setTaxRate(req.getTaxRate());
        e.setTaxAmount(req.getTaxAmount());
        e.setFileIds(normalizeFileIds(req.getFileIds()));
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
        vo.setExpenseType(e.getExpenseType());
        vo.setExpenseTypeName(dictLabel(e.getExpenseType()));
        vo.setReason(e.getReason());
        vo.setAmount(e.getAmount());
        vo.setDept(e.getDept());
        vo.setSubject(e.getSubject());
        vo.setSubjectCode(e.getSubjectCode());
        vo.setExpenseReason(e.getExpenseReason());
        vo.setInvoiceType(e.getInvoiceType());
        vo.setTaxRate(e.getTaxRate());
        vo.setTaxAmount(e.getTaxAmount());
        vo.setStatus(e.getStatus());
        vo.setVoucherNo(e.getVoucherNo());
        vo.setDate(e.getDate());
        vo.setOrgCode(e.getOrgCode());
        List<String> fileIds = fileIdsOf(e.getFileIds());
        vo.setFileIds(fileIds);
        List<String> urls = new ArrayList<>();
        SignUrlRequest signReq = new SignUrlRequest();
        signReq.setPurpose("VIEW");
        signReq.setExpiresSeconds(SIGN_EXPIRE_SECONDS);
        for (String fileId : fileIds) {
            try {
                var resp = fileApi.sign(fileId, signReq);
                if (resp != null && resp.ok() && resp.getData() != null && resp.getData().getUrl() != null) {
                    urls.add(resp.getData().getUrl());
                }
            } catch (Exception ex) {
                log.warn("报销凭证签名失败：fileId={} {}", fileId, ex.getMessage());
            }
        }
        vo.setAttachmentUrls(urls);
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
