package com.laimengchao.sso.contract.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.contract.module.contract.entity.ContractProject;
import com.laimengchao.sso.contract.module.contract.mapper.ContractProjectMapper;
import com.laimengchao.sso.contract.module.contract.service.ProjectService;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 项目档案跨服务只读接口：项目选项与可用性校验，无 @Permit，由内部令牌拦截器把关。 */
@RestController
@RequestMapping("/internal/contract/projects")
@RequiredArgsConstructor
public class InternalProjectController {

    private final ProjectService projectService;
    private final ContractProjectMapper projectMapper;
    private final DataScopeService dataScopeService;

    @GetMapping("/options")
    public R<List<ContractVOs.ProjectOptionVO>> options() {
        return R.ok(projectService.options());
    }

    @GetMapping("/{code}/usable")
    public R<Boolean> usable(@PathVariable("code") String code) {
        return R.ok(projectService.validateUsable(code).isUsable());
    }

    @GetMapping("/{code}")
    public R<ContractVOs.ProjectVO> getByCode(@PathVariable("code") String code) {
        LambdaQueryWrapper<ContractProject> qw = new LambdaQueryWrapper<ContractProject>()
                .eq(ContractProject::getCode, code);
        dataScopeService.applyOrgScope(qw, ContractProject::getOrgCode);
        ContractProject project = projectMapper.selectOne(qw.last(" LIMIT 1"));
        return R.ok(project == null ? null : projectService.detail(project.getId()));
    }
}
