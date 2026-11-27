package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.FileUploadRequest;
import com.laimengchao.sso.file.client.dto.SignUrlRequest;
import com.laimengchao.sso.finance.module.finance.service.impl.ExpenseServiceImpl;
import com.laimengchao.sso.finance.security.Permit;
import com.laimengchao.sso.utils.AccountContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 财务附件上传代理（凭证/付款单）：前端只把文件 base64 发到本接口，业务表只存 file_id，
 * 文件本体与预览签名全部交给统一文件服务（bizType=finance_expense_attachment）。
 * 沿用各单据自身的编辑权限，不新增也不复用其他模块权限码。
 */
@RestController
@RequestMapping("/api/finance/files")
@RequiredArgsConstructor
public class FinanceFileController {

    private final FileApi fileApi;

    /** 凭证附件：沿用凭证编辑权限 */
    @PostMapping("/voucher/upload")
    @Permit(PermissionCodes.FINANCE_VOUCHER_EDIT)
    public R<Map<String, Object>> uploadVoucher(@RequestBody UploadBody body) {
        return upload(body, "FIN_VOUCHER");
    }

    /** 付款单合同与验收凭证：沿用应收应付写权限 */
    @PostMapping("/payment/upload")
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<Map<String, Object>> uploadPayment(@RequestBody UploadBody body) {
        return upload(body, "FIN_PAYMENT");
    }

    private R<Map<String, Object>> upload(UploadBody body, String ownerType) {
        if (body == null || !org.springframework.util.StringUtils.hasText(body.getContent())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件内容不能为空");
        }
        FileUploadRequest req = new FileUploadRequest();
        req.setBizType(ExpenseServiceImpl.FILE_BIZ_TYPE);
        req.setVisibility("PRIVATE");
        req.setOriginalName(body.getOriginalName() == null ? "attachment" : body.getOriginalName());
        req.setContentType(body.getContentType() == null ? "application/octet-stream" : body.getContentType());
        req.setContent(body.getContent());
        req.setOwnerType(ownerType);
        req.setOwnerId(body.getBizId() == null ? "" : body.getBizId());
        req.setUploaderId(AccountContext.getAccountId() == null ? 0L : AccountContext.getAccountId());
        var uploaded = fileApi.upload(req);
        if (uploaded == null || !uploaded.ok() || uploaded.getData() == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "附件上传失败");
        }
        String fileId = String.valueOf(uploaded.getData().getId());
        SignUrlRequest signReq = new SignUrlRequest();
        signReq.setPurpose("VIEW");
        signReq.setExpiresSeconds(900L);
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
