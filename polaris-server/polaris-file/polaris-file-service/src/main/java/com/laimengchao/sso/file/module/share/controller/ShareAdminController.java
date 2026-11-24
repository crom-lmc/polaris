package com.laimengchao.sso.file.module.share.controller;

import com.laimengchao.sso.audit.client.annotation.AuditLog;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.file.module.share.entity.FileShare;
import com.laimengchao.sso.file.module.share.service.ShareService;
import com.laimengchao.sso.file.module.share.vo.ShareCreateRequest;
import com.laimengchao.sso.file.security.Permit;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 分享链接管理端（登录 + 权限）。 */
@RestController
@RequestMapping("/api/file/admin/shares")
@RequiredArgsConstructor
public class ShareAdminController {

    private final ShareService shareService;

    @GetMapping
    @Permit(PermissionCodes.FILE_DOWNLOAD)
    public R<List<FileShare>> list() {
        return R.ok(shareService.list(AccountContext.getAccountId()));
    }

    @PostMapping
    @Permit(PermissionCodes.FILE_DOWNLOAD)
    @AuditLog(action = "FILE_SHARE_CREATE", targetType = "FILE_SHARE")
    public R<FileShare> create(@RequestBody ShareCreateRequest request) {
        return R.ok(shareService.create(request, AccountContext.getAccountId()));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.FILE_DOWNLOAD)
    @AuditLog(action = "FILE_SHARE_REVOKE", targetType = "FILE_SHARE")
    public R<Void> revoke(@PathVariable("id") String id) {
        shareService.revoke(Long.valueOf(id), AccountContext.getAccountId());
        return R.ok();
    }
}
