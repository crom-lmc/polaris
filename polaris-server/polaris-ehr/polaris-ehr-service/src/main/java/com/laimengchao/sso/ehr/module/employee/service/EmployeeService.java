package com.laimengchao.sso.ehr.module.employee.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.dto.*;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeLookupDetailVO;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeLookupVO;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeVO;
import com.laimengchao.sso.ehr.module.employee.vo.HandoverCandidateVO;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeService {

    R.Page<EmployeeVO> page(String keyword, Long orgId, Integer status, int page, int size);

    /** 详情：先校验数据范围（越权 403），再按敏感权限决定明文/掩码，并记审计 */
    EmployeeVO detail(Long id);

    /** 查看敏感明文：数据范围 + 敏感权限双校验，必留 PLAIN 审计 */
    EmployeeVO sensitiveView(Long id);

    /** 导出共用数据行：只包含当前数据范围内的人，敏感字段按权限掩码 */
    List<EmployeeVO> exportRows(String keyword, Long orgId, Integer status);

    /** 导出 CSV：只包含当前数据范围内的人，敏感字段按权限掩码，并记 EXPORT 审计 */
    String exportCsv(String keyword, Long orgId, Integer status);

    /** 当前登录人对应的员工档案（请假模块用；超管/HR 可能没有档案） */
    EmployeeVO current(Long accountId);

    /**
     * 交接人候选：当前登录人所在「子公司」范围内在职且已开户的同事（没有子公司时退到整个集团）。
     * <p>
     * 单独开这个口子是因为普通员工没有 {@code employee:view}，拿不到人员列表，
     * 而他提交离职申请时必须选一个「能登录确认交接」的同事。只返回在职 + 已开户的人，
     * 与前端 HandoverEditor 的过滤口径一致，也避免把全公司电话邮箱暴露出去。
     */
    List<HandoverCandidateVO> handoverCandidates(Long accountId);

    EmployeeVO create(EmployeeRequest req);

    /**
     * 招聘录用审批通过后自动建档：只落一条「待入职」草稿，不开户、不发起入职审批，
     * 剩余个人信息与入职材料由 HR 在人员档案里补齐后走现有入职流程。
     * 与 {@link #create} 共用自动工号逻辑，但不做完整性校验（候选人信息本来就不全）。
     */
    Long createPendingArchive(String name, String mobile, String email, Long orgId, String position, LocalDate hireDate, String remark);

    /** 招聘/门户转入建档：带出候选人字段，source_offer_id 唯一兜底重复建档 */
    Long createPendingArchive(PendingArchiveCommand cmd);

    EmployeeVO update(Long id, EmployeeRequest req);

    /**
     * 删除档案：仅限「待入职」草稿。
     * <p>
     * 一旦提交入职（20）就意味着人已确定到岗、账号已开通、审批痕迹已生成，
     * 再删档会让审批链、账号、组织人数全部对不上；这类人员只能走减员（离职）流程，
     * 保证档案与账号只「停用」不「消失」。
     */
    void delete(Long id);

    /**
     * 应急补开户：正常流程里账号由人事主管终审通过时自动开通，无需人工点。
     * 仅用于历史数据（流程改造前入职、账号为空）或极端情况下的人工兜底，接口保留但前端不放入口。
     */
    EmployeeVO openAccount(Long id);

    /**
     * 办理离职：档案置离职 + 记录日期原因，并按需停用统一账号。
     * <p>
     * 为什么停用账号放在这里而不是让人去账号中心手动点：离职最常见的漏操作就是
     * 「档案标了离职、账号还能登」，把所有系统一起停掉才算办完。
     * 账号停用失败会抛异常回滚状态，避免出现「档案离职了、账号还活着」的半截状态。
     */
    EmployeeVO resign(Long id, ResignRequest req);

    /**
     * 关闭账号：离职 / 减员审批通过后的最后一步，档案从「待关闭账号」置为「已离职」。
     * <p>
     * 审批回调只进入「待关闭账号」，等 HR 在人员列表确认后才真正停用统一账号；
     * 停用失败直接抛异常回滚状态，不留下「档案已离职、账号还能登」的半截数据。
     */
    EmployeeVO closeAccount(Long id);

    /**
     * 复职：回到在职并清空离职信息；默认重新启用账号。
     * <p>
     * resignDate 需要显式 set null：MyBatis-Plus 的 updateById 默认跳过 null 字段，
     * 直接给实体赋值清不掉列，复职后会一直挂着上次的离职日期。
     * <p>
     * 直办接口统一回到 <b>20 入职审批中</b>（复职后重走一次入职审批，复用原账号）；
     * 走 {@code ehr.rehire} 审批通过的回调则用 {@link #reinstateByApproval}，直接回到试用期/在职。
     */
    EmployeeVO reinstate(Long id, ReinstateRequest req);

    /**
     * 复职审批通过后的落地：审批本身就是「能不能回来」的结论，
     * 不再让人重走一遍入职审批，直接回到试用期（免试用期则直接在职）。
     * 试用期起算与在职状态与 {@code /onboard} 保持一致。
     */
    EmployeeVO reinstateByApproval(Long id, Boolean enableAccount, String remark);

    /**
     * 待入职草稿提交：<b>三段式</b>——① 先本地置「入职审批中」并提交 ② 向审批中心发起实例
     * ③ 仅当实例仍在进行中才回写实例号。发起确认失败时把状态退回「待入职」，允许重新提交。
     * <p>
     * 这里<b>不开户</b>：账号留到终审通过时统一开通，避免「审批还没过就先把号建出来」。
     */
    EmployeeVO submitDraft(Long id);

    /**
     * 员工快捷查询（控制台使用）：按工号精确 / 姓名模糊查，最多 10 条。
     * 只返回姓名、职位、邮件，任何隐私字段都不出库。
     */
    List<EmployeeLookupVO> lookupEmployees(String keyword);

    /** 员工快捷查询详情：姓名 / 工号 / 账号 / 性别 / 职位 / 部门 / 邮件，同样不含隐私字段 */
    EmployeeLookupDetailVO lookupEmployeeDetail(Long employeeId);

    /**
     * 入职审批办理（兼容老前端）：把「通过/驳回」转给审批中心，由它推进流程。
     * <p>
     * <b>刻意不加 @Transactional</b>：settings 在审批到终态时会同步回调本服务，
     * 回调线程要 UPDATE 同一行 ehr_employee；若本方法持有该行的写锁去等 settings 响应，
     * 两边就会互相等待直到锁超时。这里只做「转发 + 回读」，全程不占锁。
     * <p>
     * 状态流转不再由这里决定 —— 终态由回调改，中间步骤的推进是审批中心的事。
     */
    EmployeeVO approveOnboard(Long id, boolean pass, String note);

    /**
     * 接收审批中心回传的入职审批终态。
     * <p>
     * <b>必须幂等</b>：审批中心回调失败会退避重试，同一结果可能投递多次。
     * 两道防重 —— ① 审批单号对不上（员工重提交过，旧单迟到的回调）直接忽略；
     * ② 状态已经是 25 及之后说明处理过了，不再重复开户。
     * <p>
     * 通过 → 「入职审批通过」（25）+ 开户；驳回 → 回「待入职」（10）。
     * 开户失败<b>不回滚状态</b>：审批确实过了，回滚会制造「单子过了人还在审批中」的死局，
     * 改为记日志 + 在审批备注里留一句，由 HR 在人员列表点「开户」补办。
     */
    void handleOnboardApprovalResult(ApprovalCallbackRequest req);

    /**
     * 办理入职：仅「入职审批通过」（25）可调，由 HR 在人员列表点「入职」确认到岗。
     * <p>
     * 审批阶段已开通统一账号，这里只做状态流转：免试用期 → 直接正式（40）；
     * 否则进入试用期（30）。不重复开户，也不重新计算预计转正时间（以建档时为准）。
     */
    EmployeeVO onboard(Long id);

    /**
     * 试用期员工自助提交转正申请：不再本地模拟两步审批，改为向审批中心发起
     * {@code ehr.regularize} 实例，步骤 / 审批人 / 条件全部由模板决定。
     * 本人或 HR 均可提交。
     * <p>
     * 申请内容（自评、业绩、理由、后续计划）落在 {@code ehr_regularize_apply} 上，
     * 校验 / 发起 / 回调都由 {@link com.laimengchao.sso.ehr.module.approval.service.RegularizeApplyService} 负责，
     * 这里只做一层薄转发，保留老接口路径与返回类型不变。
     */
    EmployeeVO applyRegularize(Long id);

    /** 带申请内容的转正申请（req 可为空，等价于只点一下按钮的老行为） */
    EmployeeVO applyRegularize(Long id, com.laimengchao.sso.ehr.module.approval.dto.RegularizeApplyRequest req);

    /**
     * 转正审批终审回调。必须幂等：审批中心回调失败会退避重试，同一结果可能投递多次。
     * <p>
     * 通过 → 正式在职（40）+ regularizeStatus=3；驳回/撤回/终止 → 留在试用期、regularizeStatus=-1。
     * 两种情况都要清掉在途标记，否则人员列表会一直显示「转正审批中」。
     * <p>
     * 实际处理委托给 {@link com.laimengchao.sso.ehr.module.approval.service.RegularizeApplyService}
     * （那里有申请单，能按申请行定位并留档）；本方法保留是为了兼容既有调用方。
     */
    void handleRegularizeApprovalResult(ApprovalCallbackRequest req);
}
