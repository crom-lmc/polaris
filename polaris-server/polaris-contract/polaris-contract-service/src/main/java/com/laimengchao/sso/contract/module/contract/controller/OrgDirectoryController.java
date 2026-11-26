package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.client.api.EhrOrgApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 组织 / 人员选择数据源：真相源在 EHR，经内部令牌代理给合同前端。
 * <p>
 * 不放开 EHR 的 /api/ehr/orgs/flatten（@Permit(org:view) 且 EHR 切面强制 appCode=ehr，
 * contract 令牌会被 403）；改走 EHR /internal/orgs/**，由本服务用内部令牌 Feign 转发。
 * 接口只要求登录态（/api/contract/** authenticated），不额外挂权限码——组织与人员是填表基础候选。
 * <p>
 * 组织 id/parentId、人员 id 均为字符串化雪花 ID，前端可安全承载。
 */
@Slf4j
@RestController
@RequestMapping("/api/contract/orgs")
@RequiredArgsConstructor
public class OrgDirectoryController {

    private final EhrOrgApi ehrOrgApi;

    /** 全量组织节点扁平列表，前端自行组树；失败返回空列表，不拖垮表单 */
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        try {
            com.laimengchao.sso.ehr.client.dto.R<List<Map<String, Object>>> resp = ehrOrgApi.tree();
            if (resp != null && resp.ok() && resp.getData() != null) {
                return R.ok(resp.getData());
            }
            log.warn("合同读取 EHR 组织树失败：{}", resp == null ? "无响应" : resp.getMessage());
        } catch (Exception e) {
            log.warn("合同读取 EHR 组织树异常：{}", e.getMessage());
        }
        return R.ok(Collections.emptyList());
    }

    /** 全部已开户、未离职员工（姓名/工号/账号ID），人员下拉候选 */
    @GetMapping("/employees/active")
    public R<List<Map<String, Object>>> activeEmployees() {
        try {
            com.laimengchao.sso.ehr.client.dto.R<List<Map<String, Object>>> resp = ehrOrgApi.activeEmployees();
            if (resp != null && resp.ok() && resp.getData() != null) {
                return R.ok(resp.getData());
            }
            log.warn("合同读取 EHR 在职员工失败：{}", resp == null ? "无响应" : resp.getMessage());
        } catch (Exception e) {
            log.warn("合同读取 EHR 在职员工异常：{}", e.getMessage());
        }
        return R.ok(Collections.emptyList());
    }

    /** 某组织已开户、未离职的直属成员，按部门联动选人用 */
    @GetMapping("/{id}/employees")
    public R<List<Map<String, Object>>> employees(@PathVariable("id") Long orgId) {
        try {
            com.laimengchao.sso.ehr.client.dto.R<List<Map<String, Object>>> resp = ehrOrgApi.employees(orgId);
            if (resp != null && resp.ok() && resp.getData() != null) {
                return R.ok(resp.getData());
            }
            log.warn("合同读取 EHR 组织 {} 成员失败：{}", orgId, resp == null ? "无响应" : resp.getMessage());
        } catch (Exception e) {
            log.warn("合同读取 EHR 组织 {} 成员异常：{}", orgId, e.getMessage());
        }
        return R.ok(Collections.emptyList());
    }
}
