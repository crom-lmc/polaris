package com.laimengchao.sso.ehr.module.employee.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.service.LeaveCityPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录人城市查询（内部接口，供会议室管理等服务按当前人城市过滤房间）。
 * <p>
 * 城市口径与请假城市政策一致：取员工所属二级单位（depth=2）的 city，下级沿 path 继承。
 * 这里复用 {@link LeaveCityPolicyService#cityOf(Employee)}，不重写继承算法。
 * <p>
 * 走 {@code /internal/**}，凭 X-Internal-Token 准入，不经网关；无需 {@code @Permit}。
 */
@Slf4j
@RestController
@RequestMapping("/internal/ehr")
@RequiredArgsConstructor
public class InternalEmployeeController {

    private final EmployeeMapper employeeMapper;
    private final LeaveCityPolicyService leaveCityPolicyService;

    /**
     * 按账号 ID 取当前人城市（字典 {@code ehr.job_city} 的 value，如「西安」）。
     * 员工不存在或未配置城市返回 null，由调用方按空城市 fail-closed 兜底。
     */
    @GetMapping("/employees/{accountId}/city")
    public R<String> city(@PathVariable("accountId") Long accountId) {
        if (accountId == null) {
            return R.ok(null);
        }
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, accountId)
                .eq(Employee::getDeleted, 0)
                .last("limit 1"));
        return R.ok(leaveCityPolicyService.cityOf(emp));
    }
}
