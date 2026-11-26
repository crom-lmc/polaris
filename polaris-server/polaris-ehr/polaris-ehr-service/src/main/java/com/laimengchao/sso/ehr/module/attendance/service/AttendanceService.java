package com.laimengchao.sso.ehr.module.attendance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.attendance.dto.OvertimeRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.Overtime;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceMonthVO;
import com.laimengchao.sso.ehr.module.attendance.vo.OvertimePreviewVO;
import com.laimengchao.sso.ehr.module.attendance.vo.OvertimeVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;

import java.math.BigDecimal;
import java.util.List;

public interface AttendanceService {

    OvertimeVO submit(OvertimeRequest req);

    OvertimePreviewVO preview(OvertimeRequest req);

    void cancel(Long id);

    /** 我的 / 团队 / 全部列表；功能权限与数据范围同时生效，避免越权。 */
    R.Page<OvertimeVO> list(String scope, Long employeeId, String month, int page, int size);

    void handleApprovalResult(ApprovalCallbackRequest req);

    /**
     * 加班转调休（所有日类型统一 1:1）：每次结算时按当年全部已结算加班统一计算，
     * 避免逐笔累加导致精度漂移（3h/8+4h/8+1h/8 分别四舍五入 ≠ 8h/8 一次性计算）。
     */
    void grantCompensatory(Overtime o, BigDecimal effectiveHours);

    List<AttendanceMonthVO> monthly(String month, Long employeeId);

    AttendanceMonthVO confirm(String month, Long employeeId);

    /** 解锁已确认的月度考勤；必须填写原因，解锁后立即按日结果重算汇总。 */
    AttendanceMonthVO unlock(String month, Long employeeId, String reason);
}
