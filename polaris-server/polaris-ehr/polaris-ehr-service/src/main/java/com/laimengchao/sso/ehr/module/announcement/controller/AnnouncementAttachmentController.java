package com.laimengchao.sso.ehr.module.announcement.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.module.announcement.service.AnnouncementAttachmentService;
import com.laimengchao.sso.ehr.module.announcement.service.AnnouncementService;
import com.laimengchao.sso.ehr.module.announcement.vo.AnnouncementAttachmentVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 内部通知附件：上传走公告管理权，下载只要求登录 + 公告可见范围。
 */
@RestController
@RequestMapping("/api/ehr/announcements/attachments")
@RequiredArgsConstructor
public class AnnouncementAttachmentController {

    private final AnnouncementAttachmentService attachmentService;
    private final AnnouncementService announcementService;

    @PostMapping("/upload")
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<AnnouncementAttachmentVO> upload(@RequestParam("file") MultipartFile file) {
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        return R.ok(attachmentService.upload(file, user.getAccountId()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        if (!announcementService.canAccessAttachment(id, user.getAccountId())) {
            // 用 NOT_FOUND 而不是 FORBIDDEN：不向无权用户暴露附件是否存在
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        AnnouncementAttachmentService.AttachmentFile file = attachmentService.content(id);
        String name = file.getFileName() == null || file.getFileName().isBlank() ? "attachment" : file.getFileName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(file.getMime() == null || file.getMime().isBlank()
                        ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.getMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encoded
                        + "\"; filename*=UTF-8''" + encoded)
                .body(file.getBytes());
    }
}
