package com.laimengchao.sso.ehr.module.leave.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.leave.dto.EarlyReturnRequest;
import com.laimengchao.sso.ehr.module.leave.dto.LeaveApplyRequest;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveDelegateOptionsVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeavePolicyVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeavePreviewVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveVO;

import java.util.List;
import java.util.Map;

public interface LeaveService {

    /**
     * 当前可用的 请假类型 → 中文名（LinkedHashMap，保持配置中心里的排序）。
     * 审批中心聚合待办时也要用，所以放开成 public。
     */
    Map<String, String> typeNames();

    String typeName(String type);

    /** 当前员工所在城市的婚假 / 产假 / 陪产假政策（前端据此锁定天数与计算方式） */
    LeavePolicyVO cityPolicy();

    /** 按开始日期试算结束日期：城市政策类假期天数固定，由开始日期自动带出结束日期 */
    LeavePolicyVO plan(String typeRaw, String startDateRaw);

    /**
     * 申请弹窗「本次请假时长」试算：与提交走同一个 {@link LeaveDaysCalculator}，
     * 城市政策类假期直接返回政策固定天数，保证展示值与最终落库值口径一致。
     */
    LeavePreviewVO preview(String typeRaw, String startDateRaw, String endDateRaw, String startTime, String endTime, String dayModeRaw);

    R.Page<LeaveVO> page(String scope, Integer status, int page, int size);

    /**
     * 提交请假：<b>三段式</b>——① 本地单据先落库并提交 ② 向审批中心发起实例
     * ③ 仅当实例仍在进行中才回写实例号。
     * <p>
     * 不能把三步塞进同一个 {@code @Transactional}：settings 发起时若模板没解析到审批人 /
     * 条件全假，实例会在 start() 里当场终态，并在事务提交后同步回调本服务；
     * 旧写法下回调线程读不到尚未提交的本地行，按「业务行不存在」正常返回，
     * 审批单标记回调成功、业务表却永远卡在「待审批」。本地先提交后回调必能读到行。
     */
    LeaveVO apply(LeaveApplyRequest req);

    /**
     * 请假审批终审回调。必须幂等：回调失败会退避重试，同一结果可能投递多次。
     * <p>
     * 通过 → 1；驳回 → 2；撤回 / 终止 → 3。两种情况都清掉实例号，
     * 否则列表会一直显示「请假审批中」。
     */
    void handleLeaveApprovalResult(ApprovalCallbackRequest req);

    /**
     * 撤销自己的请假：先把审批中心的在途单撤回，再改本地状态。
     * <p>
     * <b>刻意不加 @Transactional</b>：settings 处理撤回时会同步回调本服务，回调线程要
     * UPDATE 同一行 ehr_leave；若本方法持有该行写锁去等 settings 响应，两边会互相等待
     * 直到锁超时。这里先远程撤回（回调里已经把状态写成 3），再幂等地补一次本地写入。
     */
    LeaveVO cancel(Long id);

    /**
     * 请假弹窗的「审批转办」选项：当前登录人是部门负责人时该字段必填，
     * 候选人为在职且有账号的其他员工（带组织名，方便同名区分）。
     */
    LeaveDelegateOptionsVO delegateOptions();

    /** 当前登录人对应的员工档案ID（额度/团队视图用） */
    Long myEmployeeId();

    /** 我的请假时间冲突：提交前给前端提示已占用的时段 */
    List<LeaveVO> conflicts(String startRaw, String endRaw);

    /**
     * 团队请假视图：按组织子树 + 日期区间查询待审批 / 已通过记录。
     * 有审批列表权限或超管可查任意组织；普通主管只能查自己组织范围内。
     */
    List<LeaveVO> team(Long orgId, String fromRaw, String toRaw);

    /**
     * 提前返岗销假：按实际返岗日期重算天数，退回多请的额度，状态置 4。
     * 只允许对「已通过」的单据操作一次；本人或 HR 可操作。
     */
    LeaveVO earlyReturn(Long id, EarlyReturnRequest req);
}
