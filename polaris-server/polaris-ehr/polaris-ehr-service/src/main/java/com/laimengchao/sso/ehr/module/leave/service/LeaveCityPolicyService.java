package com.laimengchao.sso.ehr.module.leave.service;

import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public interface LeaveCityPolicyService {

    public record CityPolicy(String city, String type, BigDecimal days, String dayMode) {
    }

    boolean supports(String type);

    Set<String> types();

    /** 员工所属二级单位的城市；找不到返回 null */
    String cityOf(Employee emp);

    /** 城市可用的三类政策；城市未配置或埋点缺失时返回空 map */
    Map<String, CityPolicy> policies(String city);

    /** 员工某类假期的城市政策；未配置返回 null（由调用方给出可读错误） */
    CityPolicy policy(Employee emp, String type);
}
