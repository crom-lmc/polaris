package com.laimengchao.sso.file.module.file.controller;

import com.laimengchao.sso.audit.client.annotation.AuditLog;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.file.common.FileErrorCode;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.entity.StoragePolicy;
import com.laimengchao.sso.file.module.file.entity.WatermarkConfig;
import com.laimengchao.sso.file.module.file.service.AttachmentSettingService;
import com.laimengchao.sso.file.module.file.service.FileService;
import com.laimengchao.sso.file.module.file.service.MultipartUploadService;
import com.laimengchao.sso.file.module.file.service.ThumbnailService;
import com.laimengchao.sso.file.module.file.service.WatermarkService;
import jakarta.servlet.http.HttpServletRequest;
import com.laimengchao.sso.file.module.file.vo.MultipartInitRequest;
import com.laimengchao.sso.file.module.file.vo.SignRequest;
import com.laimengchao.sso.file.module.file.vo.UploadRequest;
import com.laimengchao.sso.file.security.Permit;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 文件服务管理端（SSO 网关身份头 + @Permit）。 */
@RestController
@RequestMapping("/api/file/admin")
@RequiredArgsConstructor
public class FileAdminController {

    private final FileService fileService;
    private final MultipartUploadService multipartService;
    private final WatermarkService watermarks;
    private final AttachmentSettingService settings;

    @GetMapping("/overview")
    @Permit(PermissionCodes.FILE_OVERVIEW_VIEW)
    public R<Map<String, Object>> overview() {
        return R.ok(fileService.overview());
    }

    /** 上传统计（抽屉自定义时间范围），返回区间内每一天的文件数与字节数。 */
    @GetMapping("/overview/trend")
    @Permit(PermissionCodes.FILE_OVERVIEW_VIEW)
    public R<List<Map<String, Object>>> uploadTrend(
            @RequestParam(value = "startDate") String startDate,
            @RequestParam(value = "endDate") String endDate) {
        return R.ok(fileService.uploadTrend(startDate, endDate));
    }

    @GetMapping("/files")
    @Permit(PermissionCodes.FILE_LIST)
    public R<?> files(@RequestParam(value = "page", defaultValue = "1") int page,
                      @RequestParam(value = "size", defaultValue = "20") int size,
                      @RequestParam(value = "bizType", required = false) String bizType,
                      @RequestParam(value = "visibility", required = false) String visibility,
                      @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(fileService.page(page, size, bizType, visibility, keyword));
    }

    /** 回收站：已软删文件分页。 */
    @GetMapping("/recycle")
    @Permit(PermissionCodes.FILE_LIST)
    public R<?> recycle(@RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "bizType", required = false) String bizType,
                        @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(fileService.recycleBin(page, size, bizType, keyword));
    }

    @PostMapping("/files/upload")
    @Permit(PermissionCodes.FILE_UPLOAD)
    @AuditLog(action = "FILE_UPLOAD", targetType = "FILE")
    public R<FileObject> upload(@RequestBody UploadRequest request) {
        if (request.getUploaderId() == null) {
            request.setUploaderId(AccountContext.getAccountId());
        }
        byte[] bytes = decode(request.getContent());
        return R.ok(fileService.upload(request.getBizType(), request.getVisibility(),
                request.getOriginalName(), request.getContentType(), bytes,
                request.getOwnerType(), request.getOwnerId(), request.getUploaderId()));
    }

    /**
     * 预览信息：返回文件该如何内联展示。
     * mode: IMAGE / PDF / TEXT / UNSUPPORTED；url 为内联预览签名链接（TEXT 需前端文本渲染）。
     */
    @GetMapping("/files/{id}/preview")
    @Permit(PermissionCodes.FILE_DOWNLOAD)
    public R<Map<String, Object>> preview(@PathVariable("id") String id, HttpServletRequest request) {
        FileObject obj = fileService.meta(id);
        if (obj == null) {
            throw new com.laimengchao.sso.common.BizException(FileErrorCode.FILE_NOT_FOUND);
        }
        String ct = (obj.getContentType() == null ? "" : obj.getContentType()).toLowerCase();
        String mode;
        if (ThumbnailService.isRasterImage(ct) || ct.contains("svg")) {
            mode = "IMAGE";
        } else if (ct.contains("pdf")) {
            mode = "PDF";
        } else if (ct.startsWith("text/") || ct.contains("json") || ct.contains("csv")
                || ct.contains("javascript") || ct.contains("xml")) {
            mode = "TEXT";
        } else {
            mode = "UNSUPPORTED";
        }
        // 在线预览总开关 / 大小上限（附件设置）。关闭或超限时不给可内联预览的 URL。
        boolean previewEnabled = settings.bool(AttachmentSettingService.PREVIEW_ENABLED, true);
        int maxPreviewMb = settings.integer(AttachmentSettingService.PREVIEW_MAX_SIZE_MB, 50);
        long sizeMb = obj.getSize() == null ? 0 : obj.getSize() / (1024 * 1024);
        String previewReason = null;
        if (!previewEnabled || sizeMb > maxPreviewMb) {
            mode = "UNSUPPORTED";
            previewReason = previewEnabled ? "文件超过在线预览大小上限" : "在线预览已关闭";
        }
        Map<String, Object> info = new java.util.HashMap<>();
        if (previewReason != null) {
            info.put("reason", previewReason);
        }
        info.put("mode", mode);
        info.put("name", obj.getOriginalName());
        info.put("contentType", obj.getContentType());
        info.put("url", obj.getUrl());
        info.put("thumbUrl", obj.getThumbUrl());
        // PDF 前端叠加：返回该业务的水印参数（图片已在字节流叠加，这里只给 PDF/前端用）
        String fileType = "PDF".equals(mode) ? "PDF" : ("IMAGE".equals(mode) ? "IMAGE" : null);
        String clientIp = request.getHeader("X-Forwarded-For") != null
                ? request.getHeader("X-Forwarded-For").split(",")[0].trim() : request.getRemoteAddr();
        WatermarkConfig cfg = watermarks.config(obj.getBizType());
        if (fileType != null && watermarks.applies(cfg, "VIEW", fileType)) {
            Map<String, Object> wm = new java.util.HashMap<>();
            wm.put("text", watermarks.renderedText(cfg, obj, clientIp));
            wm.put("fontSize", cfg.getFontSize() == null ? 26 : cfg.getFontSize());
            wm.put("color", cfg.getColor() == null ? "#8B5CF6" : cfg.getColor());
            wm.put("opacity", cfg.getOpacity() == null ? 30 : cfg.getOpacity());
            wm.put("rotation", cfg.getRotation() == null ? -24 : cfg.getRotation());
            wm.put("layout", cfg.getLayout() == null ? "TILE" : cfg.getLayout());
            wm.put("gap", cfg.getGap() == null ? 46 : cfg.getGap());
            info.put("watermark", wm);
        }
        return R.ok(info);
    }

    // ---------------- 分片上传（大文件断点续传） ----------------

    @PostMapping("/multipart/init")
    @Permit(PermissionCodes.FILE_UPLOAD)
    public R<Map<String, Object>> multipartInit(@RequestBody MultipartInitRequest request) {
        return R.ok(multipartService.init(request));
    }

    @PostMapping("/multipart/{uploadId}/part/{index}")
    @Permit(PermissionCodes.FILE_UPLOAD)
    public R<Void> multipartPart(@PathVariable("uploadId") String uploadId,
                                 @PathVariable("index") int index,
                                 @RequestBody java.util.Map<String, String> body) {
        multipartService.part(uploadId, index, body.get("content"));
        return R.ok();
    }

    @GetMapping("/multipart/{uploadId}/status")
    @Permit(PermissionCodes.FILE_UPLOAD)
    public R<Map<String, Object>> multipartStatus(@PathVariable("uploadId") String uploadId) {
        return R.ok(multipartService.status(uploadId));
    }

    @PostMapping("/multipart/{uploadId}/complete")
    @Permit(PermissionCodes.FILE_UPLOAD)
    @AuditLog(action = "FILE_MULTIPART_COMPLETE", targetType = "FILE")
    public R<FileObject> multipartComplete(@PathVariable("uploadId") String uploadId) {
        Long uploaderId = AccountContext.getAccountId();
        return R.ok(multipartService.complete(uploadId, uploaderId));
    }

    @PostMapping("/files/{id}/sign")
    @Permit(PermissionCodes.FILE_DOWNLOAD)
    public R<Map<String, Object>> sign(@PathVariable("id") String id, @RequestBody SignRequest request) {
        String[] signed = fileService.sign(id, request.getPurpose(), request.getExpiresSeconds());
        return R.ok(Map.of("url", signed[0], "expiresAt", Long.parseLong(signed[1])));
    }

    @DeleteMapping("/files/{id}")
    @Permit(PermissionCodes.FILE_DELETE)
    @AuditLog(action = "FILE_DELETE", targetType = "FILE")
    public R<Void> delete(@PathVariable("id") String id) {
        fileService.delete(id);
        return R.ok();
    }

    /** 回收站恢复。 */
    @PostMapping("/files/{id}/restore")
    @Permit(PermissionCodes.FILE_DELETE)
    @AuditLog(action = "FILE_RESTORE", targetType = "FILE")
    public R<Void> restore(@PathVariable("id") String id) {
        fileService.restore(id);
        return R.ok();
    }

    /** 回收站彻底删除（物理删字节，秒传共享时保留字节）。 */
    @DeleteMapping("/files/{id}/purge")
    @Permit(PermissionCodes.FILE_DELETE)
    @AuditLog(action = "FILE_PURGE", targetType = "FILE")
    public R<Void> purge(@PathVariable("id") String id) {
        fileService.purge(id);
        return R.ok();
    }

    @GetMapping("/policies")
    @Permit(PermissionCodes.FILE_POLICY_LIST)
    public R<List<StoragePolicy>> policies() {
        return R.ok(fileService.policies());
    }

    @PutMapping("/policies")
    @Permit(PermissionCodes.FILE_POLICY_EDIT)
    @AuditLog(action = "FILE_POLICY_SAVE", targetType = "FILE_POLICY")
    public R<Void> savePolicy(@RequestBody StoragePolicy policy) {
        fileService.savePolicy(policy);
        return R.ok();
    }

    private byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(base64);
    }
}
