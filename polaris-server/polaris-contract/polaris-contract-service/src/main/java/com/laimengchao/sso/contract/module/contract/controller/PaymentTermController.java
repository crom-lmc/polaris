package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.PaymentTermService;
import com.laimengchao.sso.contract.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 合同付款节点：草稿阶段配置，比例合计 100%、必含验收款；复用合同列表/编辑权限码。 */
@RestController
@RequestMapping("/api/contract/contracts/{contractNo}/payment-terms")
@RequiredArgsConstructor
public class PaymentTermController {

    private final PaymentTermService termService;

    @GetMapping
    @Permit(PermissionCodes.CONTRACT_LEDGER_LIST)
    public R<?> list(@PathVariable("contractNo") String contractNo) {
        return R.ok(termService.list(contractNo));
    }

    /** 一次性替换该合同全部付款节点（仅草稿） */
    @PostMapping
    @Permit(PermissionCodes.CONTRACT_LEDGER_EDIT)
    public R<?> save(@PathVariable("contractNo") String contractNo,
                     @RequestBody ContractRequests.PaymentTermSaveBatch req) {
        return R.ok(termService.save(contractNo, req));
    }
}
