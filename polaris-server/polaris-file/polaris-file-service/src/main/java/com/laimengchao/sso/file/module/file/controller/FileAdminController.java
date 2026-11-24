package com.laimengchao.sso.file.module.file.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.entity.StoragePolicy;
import com.laimengchao.sso.file.module.file.service.FileService;
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

    @PostMapping("/files/upload")
    @Permit(PermissionCodes.FILE_UPLOAD)
    public R<FileObject> upload(@RequestBody UploadRequest request) {
        if (request.getUploaderId() == null) {
            request.setUploaderId(AccountContext.getAccountId());
        }
        byte[] bytes = decode(request.getContent());
        return R.ok(fileService.upload(request.getBizType(), request.getVisibility(),
                request.getOriginalName(), request.getContentType(), bytes,
                request.getOwnerType(), request.getOwnerId(), request.getUploaderId()));
    }

    @PostMapping("/files/{id}/sign")
    @Permit(PermissionCodes.FILE_DOWNLOAD)
    public R<Map<String, Object>> sign(@PathVariable("id") String id, @RequestBody SignRequest request) {
        String[] signed = fileService.sign(id, request.getPurpose(), request.getExpiresSeconds());
        return R.ok(Map.of("url", signed[0], "expiresAt", Long.parseLong(signed[1])));
    }

    @DeleteMapping("/files/{id}")
    @Permit(PermissionCodes.FILE_DELETE)
    public R<Void> delete(@PathVariable("id") String id) {
        fileService.delete(id);
        return R.ok();
    }

    @GetMapping("/policies")
    @Permit(PermissionCodes.FILE_POLICY_LIST)
    public R<List<StoragePolicy>> policies() {
        return R.ok(fileService.policies());
    }

    @PutMapping("/policies")
    @Permit(PermissionCodes.FILE_POLICY_EDIT)
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
