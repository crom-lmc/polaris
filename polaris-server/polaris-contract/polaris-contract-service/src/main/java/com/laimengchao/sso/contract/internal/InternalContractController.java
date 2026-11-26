package com.laimengchao.sso.contract.internal;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.contract.module.contract.entity.ContractAcceptance;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.entity.ContractPartner;
import com.laimengchao.sso.contract.module.contract.mapper.ContractAcceptanceMapper;
import com.laimengchao.sso.contract.module.contract.service.ContractItemService;
import com.laimengchao.sso.contract.module.contract.service.ContractService;
import com.laimengchao.sso.contract.module.contract.service.MaterialService;
import com.laimengchao.sso.contract.module.contract.service.PartnerService;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同域跨服务只读接口：供财务域经 contract-client 引用。
 * 无 @Permit，准入由 common 的 InternalTokenInterceptor 处理；数据范围按 X-Account-Id 解析。
 */
@RestController
@RequestMapping("/internal/contract")
@RequiredArgsConstructor
public class InternalContractController {

    private final ContractService contractService;
    private final ContractItemService contractItemService;
    private final PartnerService partnerService;
    private final MaterialService materialService;
    private final ContractAcceptanceMapper acceptanceMapper;
    private final DataScopeService dataScopeService;

    @GetMapping("/contracts")
    public R<List<ContractVOs.ContractVO>> list(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "projectNo", required = false) String projectNo) {
        return R.ok(contractService.list(type, status, projectNo));
    }

    /**
     * 财务凭证「关联合同号」下拉。internal Feign 无登录上下文，显式传账号由合同域解析数据范围；
     * 不用 /contracts/options 以免与 /contracts/{no} 混淆。
     */
    @GetMapping("/contract-options")
    public R<List<ContractVOs.ContractVO>> options(@RequestParam(value = "accountId", required = false) Long accountId) {
        return R.ok(contractService.optionsForAccount(accountId));
    }

    @GetMapping("/contracts/{no}")
    public R<ContractVOs.ContractVO> getByNo(@PathVariable("no") String no) {
        return R.ok(contractService.getByNo(no));
    }

    @GetMapping("/contracts/{no}/items")
    public R<List<ContractVOs.ItemVO>> items(@PathVariable("no") String no) {
        return R.ok(contractItemService.list(no));
    }

    @GetMapping("/partners")
    public R<List<ContractVOs.PartnerVO>> partners(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status) {
        return R.ok(partnerService.list(role, status));
    }

    @GetMapping("/partners/{code}")
    public R<ContractVOs.PartnerVO> partner(@PathVariable("code") String code) {
        ContractPartner partner = partnerService.requireByCode(code);
        return R.ok(partnerService.toVO(partner,
                dataScopeService.hasPermission(PermissionCodes.CONTRACT_PARTNER_BANK_VIEW)));
    }

    @GetMapping("/materials/tree")
    public R<List<ContractVOs.MaterialVO>> materialTree() {
        return R.ok(materialService.tree());
    }

    @GetMapping("/materials/{code}")
    public R<ContractVOs.MaterialVO> material(@PathVariable("code") String code) {
        ContractMaterial material = materialService.requireByCode(code);
        return R.ok(materialService.detail(material.getId()));
    }

    @GetMapping("/acceptances/{no}")
    public R<ContractVOs.AcceptanceVO> acceptance(@PathVariable("no") String no) {
        LambdaQueryWrapper<ContractAcceptance> qw = new LambdaQueryWrapper<ContractAcceptance>()
                .eq(ContractAcceptance::getNo, no);
        dataScopeService.applyOrgScope(qw, ContractAcceptance::getOrgCode);
        ContractAcceptance entity = acceptanceMapper.selectOne(qw.last(" LIMIT 1"));
        if (entity == null) {
            return R.ok();
        }
        ContractVOs.AcceptanceVO vo = new ContractVOs.AcceptanceVO();
        vo.setId(entity.getId());
        vo.setNo(entity.getNo());
        vo.setContractNo(entity.getContractNo());
        vo.setItemId(entity.getItemId());
        vo.setMaterialCode(entity.getMaterialCode());
        vo.setQty(entity.getQty());
        vo.setDate(entity.getDate() == null ? null : entity.getDate().toString());
        vo.setResult(entity.getResult());
        vo.setStatus(entity.getStatus());
        vo.setOrgCode(entity.getOrgCode());
        return R.ok(vo);
    }

    @PostMapping("/acceptances/{no}/posted")
    public R<Void> posted(@PathVariable("no") String no) {
        contractItemService.markPosted(no);
        return R.ok();
    }
}
