package com.laimengchao.sso.ehr.module.attendance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceRuleRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRule;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRuleSegment;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceRuleVO;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;

import java.util.List;

public interface AttendanceRuleService {

    /** 命中的规则与时间段 */
    public record ResolvedRule(AttendanceRule rule, List<AttendanceRuleSegment> segments) {
    }

    R.Page<AttendanceRuleVO> page(String keyword, int page, int size);

    AttendanceRuleVO detail(Long id);

    AttendanceRuleVO save(Long id, AttendanceRuleRequest req);

    void delete(Long id);

    AttendanceRuleVO toggle(Long id);

    /** 按员工解析规则：员工 &gt; 最近祖先部门 &gt; 全员 &gt; DEFAULT。 */
    ResolvedRule resolveRule(Employee emp);

    List<AttendanceRuleSegment> segmentsOf(Long ruleId);
}
