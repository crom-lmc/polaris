package com.laimengchao.sso.ehr.module.attendance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendancePunchRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendancePunch;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceDailyVO;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendancePunchVO;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface AttendanceDailyService {

    /** 生成指定日期的全员日结果，并刷新当月汇总。 */
    int generate(LocalDate date);

    /** 全量重算指定月份的全员月度汇总（兜底下沉任务用）。 */
    int recalcMonthAll(YearMonth ym);

    /**
     * 请假状态变化（审批通过 / 撤销 / 销假）后，重算该员工指定日期区间内
     * <b>已生成</b>的日结果，并刷新对应月度汇总。
     * <p>
     * 只刷新已有的行，不给尚未生成（如未来日期）补行，避免凭空产生缺卡提醒；
     * 已确认锁定的月份按既有规则跳过。日结果是派生数据，调用方需自行兜底异常。
     */
    void recalculate(Long employeeId, LocalDate from, LocalDate to);

    R.Page<AttendanceDailyVO> page(String month, Long employeeId, String status, int page, int size);

    /** 当前登录员工本人某月的日结果。 */
    List<AttendanceDailyVO> my(String month);

    R.Page<AttendancePunchVO> punches(Long employeeId, LocalDate date, int page, int size);

    /** HR 手工补录打卡：落 source=MANUAL，然后重算当天。 */
    AttendancePunch addPunch(AttendancePunchRequest req);

    void recalcMonth(Employee emp, YearMonth ym);
}
