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
 * 报销凭证上传代理：前端只把文件 base64 发到本接口，业务表只存 file_id，
 * 文件本体与预览签名全部交给统一文件服务（bizType=finance_expense_attachment）。
 */
@RestController
@RequestMapping("/api/finance/expense/files")
@RequiredArgsConstructor
public class ExpenseFileController {

    private final FileApi fileApi;

    @PostMapping("/upload")
    @Permit(PermissionCodes.FINANCE_EXPENSE_CREATE)
    public R<Map<String, Object>> upload(@RequestBody UploadBody body) {
        if (body == null || !org.springframework.util.StringUtils.hasText(body.getContent())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件内容不能为空");
        }
        FileUploadRequest req = new FileUploadRequest();
        req.setBizType(ExpenseServiceImpl.FILE_BIZ_TYPE);
        req.setVisibility("PRIVATE");
        req.setOriginalName(body.getOriginalName() == null ? "expense" : body.getOriginalName());
        req.setContentType(body.getContentType() == null ? "application/octet-stream" : body.getContentType());
        req.setContent(body.getContent());
        req.setOwnerType(ExpenseServiceImpl.FILE_OWNER_TYPE);
        req.setOwnerId(body.getExpenseId() == null ? "" : body.getExpenseId());
        req.setUploaderId(AccountContext.getAccountId() == null ? 0L : AccountContext.getAccountId());
        var uploaded = fileApi.upload(req);
        if (uploaded == null || !uploaded.ok() || uploaded.getData() == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "报销凭证上传失败");
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
        private String expenseId;
        private String originalName;
        private String contentType;
        private String content;
    }
}
