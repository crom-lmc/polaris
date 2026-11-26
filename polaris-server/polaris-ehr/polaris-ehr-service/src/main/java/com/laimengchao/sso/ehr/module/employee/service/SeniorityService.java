package com.laimengchao.sso.ehr.module.employee.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.dto.SeniorityConfirmRequest;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeSeniorityRecord;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeSenioritySegment;
import com.laimengchao.sso.ehr.module.employee.service.impl.SeniorityServiceImpl;
import com.laimengchao.sso.ehr.module.employee.vo.SeniorityVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SeniorityService {

    /** 司龄列表：keyword 匹配姓名/工号；employeeId 可选精确筛选 */
    R.Page<SeniorityVO> page(String keyword, Long employeeId, int page, int size);

    /** 某员工的司龄核定历史（生效日倒序） */
    List<EmployeeSeniorityRecord> records(Long employeeId);

    /** 某员工最近一次核定的「前司工作段」（用于核定弹窗回显、可继续增删） */
    List<EmployeeSenioritySegment> latestSegments(Long employeeId);

    /**
     * 核定司龄：追加一条核定记录，并回写员工档案上的司龄起算日 / 首次工作日期 / 当前司龄。
     * <p>
     * 传了 segments 时：前司年限 = 各段合并后的天数合计（断档不计、重叠只算一次），
     * 首次参加工作日期 = 最早一段的开始日；没传 segments 时沿用旧的手工前司年限。
     */
    SeniorityVO confirm(Long employeeId, SeniorityConfirmRequest req);

    /** 天数 → 年，按 365.25 天/年向下保留 1 位 */
    static BigDecimal yearsBetween(LocalDate from, LocalDate to) {
        return SeniorityServiceImpl.yearsBetween(from, to);
    }
}
