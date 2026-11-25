package com.laimengchao.sso.file.module.share.controller;

import com.laimengchao.sso.audit.client.annotation.AuditLog;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.file.module.share.entity.FileShare;
import com.laimengchao.sso.file.module.share.service.ShareService;
import com.laimengchao.sso.file.module.share.vo.ShareCreateRequest;
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

/**
 * 分享链接的公共能力：任何登录账号都可用，不挂管理端权限码。
 *
 * <p>与上传/下载一样属于平台基础文件能力，业务页面（EHR 档案附件、CMS 媒体库等）
 * 拿到 fileId 后即可直接调用。仅能管理自己创建的分享，创建人取网关注入的可信身份头。
 * 管理端 /api/file/admin/shares 保留原权限口径，供 file-web 使用。
 */
@RestController
@RequestMapping("/api/file/my")
@RequiredArgsConstructor
public class MyShareController {

    private final ShareService shareService;

    @PostMapping("/shares")
    @AuditLog(action = "FILE_SHARE_CREATE", targetType = "FILE_SHARE")
    public R<FileShare> create(@RequestBody ShareCreateRequest request) {
        return R.ok(shareService.create(request, AccountContext.getAccountId()));
    }

    @GetMapping("/shares")
    public R<List<FileShare>> list() {
        return R.ok(shareService.list(AccountContext.getAccountId()));
    }

    @DeleteMapping("/shares/{id}")
    @AuditLog(action = "FILE_SHARE_REVOKE", targetType = "FILE_SHARE")
    public R<Void> revoke(@PathVariable("id") String id) {
        shareService.revoke(Long.valueOf(id), AccountContext.getAccountId());
        return R.ok();
    }

}
