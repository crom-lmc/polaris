package com.laimengchao.sso.ehr.module.leave.service;

import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveQuotaVO;
import com.laimengchao.sso.ehr.module.leave.vo.QuotaLogVO;
import java.math.BigDecimal;
import java.util.List;

public interface LeaveQuotaService {

    LeaveQuota getById(Long quotaId);

    /** 按「员工 + 类型 + 周期起始日」取额度；year 只作为周期换算参数，不再参与查询 */
    LeaveQuota find(Long employeeId, String leaveType, int year);

    /** 取额度行；不占额度的类型返回 null。缺失时按规则默认额度自动建行（并发靠唯一键兜底）。 */
    LeaveQuota getOrCreate(Long employeeId, String leaveType, int year);

    BigDecimal available(LeaveQuota q);

    /** 结转剩余 = 结转进来 - 结转已消耗（消耗优先扣结转） */
    BigDecimal carryoverRemaining(LeaveQuota q);

    /**
     * 签署自动放弃上年结转剩余：写签署记录（幂等 uk 员工+年度），额度总额减去放弃天数、
     * 结转占用同步增加（视同已消耗），并写 SETTLE 流水。
     */
    BigDecimal waiveCarryover(LeaveQuota q, Long operatorId);

    /** 是否已签署过某年度结转放弃 */
    boolean hasWaived(Long employeeId, int year);

    LeaveQuotaVO toVO(LeaveQuota q);

    /** 某员工某年度的全部占额度类型（缺行的按默认额度补建） */
    List<LeaveQuotaVO> listForEmployee(Long employeeId, int year);

    void initForEmployee(Long employeeId, int year);

    /** 提交请假时冻结额度；额度不足抛出业务错误，让请假单与冻结一起回滚 */
    void freezeForLeave(LeaveRequest leave, Long operatorId);

    /** 审批通过：冻结转已用 */
    void consumeForLeave(LeaveRequest leave, Long operatorId);

    /** 驳回 / 撤回 / 终止：释放冻结 */
    void releaseForLeave(LeaveRequest leave, Long operatorId);

    /** 销假提前返岗：退回多请的天数（写 RELEASE 流水，幂等键按请假单唯一） */
    void returnDays(LeaveRequest leave, BigDecimal returnDays, Long operatorId);

    /** 额度调整审批通过：delta 可为负，调整后总额度不得低于已用 + 冻结 */
    void adjust(Long quotaId, BigDecimal delta, Long operatorId, String remark, String bizKey);

    List<QuotaLogVO> logs(Long quotaId);
}
