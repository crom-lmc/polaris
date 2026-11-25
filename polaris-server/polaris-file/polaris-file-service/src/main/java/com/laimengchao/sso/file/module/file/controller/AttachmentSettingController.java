package com.laimengchao.sso.file.module.file.controller;

import com.laimengchao.sso.audit.client.annotation.AuditLog;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.file.module.file.service.AttachmentSettingService;
import com.laimengchao.sso.file.module.file.vo.AttachmentSettingsSaveRequest;
import com.laimengchao.sso.file.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 附件全局设置管理端。 */
@RestController
@RequestMapping("/api/file/admin/settings")
@RequiredArgsConstructor
public class AttachmentSettingController {

    private final AttachmentSettingService service;

    @GetMapping
    @Permit(PermissionCodes.FILE_WATERMARK_VIEW)
    public R<Map<String, String>> get() {
        return R.ok(service.all());
    }

    @PutMapping
    @Permit(PermissionCodes.FILE_WATERMARK_EDIT)
    @AuditLog(action = "FILE_SETTING_SAVE", targetType = "FILE_SETTING")
    public R<Void> save(@RequestBody AttachmentSettingsSaveRequest req) {
        service.save(req == null ? null : req.getSettings());
        return R.ok();
    }
}
