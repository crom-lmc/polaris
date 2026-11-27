package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.module.contract.support.ContractAttachments;
import com.laimengchao.sso.contract.security.Permit;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.FileUploadRequest;
import com.laimengchao.sso.file.client.dto.SignUrlRequest;
import com.laimengchao.sso.utils.AccountContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 合同附件上传代理：前端只把文件 base64 发到本接口，业务表只存 file_id，
 * 文件本体与预览签名全部交给统一文件服务（bizType=contract_attachment）。
 * 三类单据各有独立按钮权限，挂在所属菜单下，不与新增/编辑权限复用。
 */
@RestController
@RequestMapping("/api/contract/files")
@RequiredArgsConstructor
public class ContractFileController {

    private final FileApi fileApi;

    /** 采购计划附件：沿用采购计划编辑权限 */
    @PostMapping("/purchase-plan/upload")
    @Permit(PermissionCodes.CONTRACT_PLAN_ATTACHMENT_UPLOAD)
    public R<Map<String, Object>> uploadPlan(@RequestBody UploadBody body) {
        return upload(body, ContractAttachments.OWNER_PURCHASE_PLAN);
    }

    /** 合同附件：沿用合同台账编辑权限 */
    @PostMapping("/contract/upload")
    @Permit(PermissionCodes.CONTRACT_LEDGER_ATTACHMENT_UPLOAD)
    public R<Map<String, Object>> uploadContract(@RequestBody UploadBody body) {
        return upload(body, ContractAttachments.OWNER_CONTRACT);
    }

    /** 验收凭证：沿用合同清单编辑权限 */
    @PostMapping("/acceptance/upload")
    @Permit(PermissionCodes.CONTRACT_ITEM_ATTACHMENT_UPLOAD)
    public R<Map<String, Object>> uploadAcceptance(@RequestBody UploadBody body) {
        return upload(body, ContractAttachments.OWNER_ACCEPTANCE);
    }

    /** 付款计划合同与验收凭证：沿用付款编辑权限 */
    @PostMapping("/payment-plan/upload")
    @Permit(PermissionCodes.CONTRACT_PAYMENT_ATTACHMENT_UPLOAD)
    public R<Map<String, Object>> uploadPaymentPlan(@RequestBody UploadBody body) {
        return upload(body, ContractAttachments.OWNER_PAYMENT_PLAN);
    }

    private R<Map<String, Object>> upload(UploadBody body, String ownerType) {
        if (body == null || !StringUtils.hasText(body.getContent())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件内容不能为空");
        }
        FileUploadRequest req = new FileUploadRequest();
        req.setBizType(ContractAttachments.FILE_BIZ_TYPE);
        req.setVisibility("PRIVATE");
        req.setOriginalName(body.getOriginalName() == null ? "attachment" : body.getOriginalName());
        req.setContentType(body.getContentType() == null ? "application/octet-stream" : body.getContentType());
        req.setContent(body.getContent());
        req.setOwnerType(ownerType);
        req.setOwnerId(body.getBizId() == null ? "" : body.getBizId());
        req.setUploaderId(AccountContext.getAccountId() == null ? 0L : AccountContext.getAccountId());
        var uploaded = fileApi.upload(req);
        if (uploaded == null || !uploaded.ok() || uploaded.getData() == null) {
            throw new BizException(ContractErrorCode.ATTACHMENT_UPLOAD_FAILED);
        }
        String fileId = String.valueOf(uploaded.getData().getId());
        SignUrlRequest signReq = new SignUrlRequest();
        signReq.setPurpose("VIEW");
        signReq.setExpiresSeconds(ContractAttachments.SIGN_EXPIRE_SECONDS);
        String url = "";
        try {
            var signed = fileApi.sign(fileId, signReq);
            if (signed != null && signed.ok() && signed.getData() != null) {
                url = signed.getData().getUrl();
            }
        } catch (Exception ignored) {
            // 签名失败不影响 file_id 落库，列表/详情会重新签名
        }
        return R.ok(Map.of("fileId", fileId, "url", url == null ? "" : url));
    }

    @Data
    public static class UploadBody {
        /** 业务单据 ID；新建阶段尚未落库时允许为空 */
        private String bizId;
        private String originalName;
        private String contentType;
        private String content;
    }
}
