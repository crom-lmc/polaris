package com.laimengchao.sso.file.module.file.controller;

import com.laimengchao.sso.audit.client.annotation.AuditLog;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.file.common.FileErrorCode;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.service.FileAliasService;
import com.laimengchao.sso.file.module.file.service.FileService;
import com.laimengchao.sso.file.module.file.vo.AliasRegisterRequest;
import com.laimengchao.sso.file.module.file.vo.SignRequest;
import com.laimengchao.sso.file.module.file.vo.UploadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 服务间文件接口（/internal/files/**），由 common 的 InternalTokenInterceptor 做 fail-closed 准入。
 * 与 file-client 的 FileApi 声明一一对应。
 */
@RestController
@RequestMapping("/internal/files")
@RequiredArgsConstructor
public class InternalFileController {

    private final FileService fileService;
    private final FileAliasService aliasService;

    @PostMapping("/upload")
    @AuditLog(action = "FILE_INTERNAL_UPLOAD", targetType = "FILE")
    public R<FileObject> upload(@RequestBody UploadRequest request) {
        byte[] bytes = decode(request.getContent());
        FileObject obj = fileService.upload(request.getBizType(), request.getVisibility(),
                request.getOriginalName(), request.getContentType(), bytes,
                request.getOwnerType(), request.getOwnerId(), request.getUploaderId());
        return R.ok(obj);
    }

    @GetMapping("/{id}")
    public R<FileObject> meta(@PathVariable("id") String id) {
        return R.ok(fileService.meta(id));
    }

    @GetMapping("/metas")
    public R<List<FileObject>> metas(@RequestParam("ids") String ids) {
        return R.ok(fileService.metas(ids));
    }

    @PostMapping("/{id}/sign")
    public R<Map<String, Object>> sign(@PathVariable("id") String id, @RequestBody SignRequest request) {
        String[] signed = fileService.sign(id, request.getPurpose(), request.getExpiresSeconds());
        return R.ok(Map.of("url", signed[0], "expiresAt", Long.parseLong(signed[1])));
    }

    @GetMapping("/{id}/content")
    public R<String> content(@PathVariable("id") String id) {
        byte[] bytes = fileService.content(id);
        return R.ok(Base64.getEncoder().encodeToString(bytes));
    }

    @DeleteMapping("/{id}")
    @AuditLog(action = "FILE_INTERNAL_DELETE", targetType = "FILE")
    public R<Void> delete(@PathVariable("id") String id) {
        fileService.delete(id);
        return R.ok();
    }

    /** 注册旧路径别名（存量迁移脚本调用，幂等）。 */
    @PostMapping("/alias/register")
    public R<Void> registerAlias(@RequestBody AliasRegisterRequest request) {
        aliasService.register(request.getOldPath(), request.getFileId(), request.getSourceApp());
        return R.ok();
    }

    private byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new com.laimengchao.sso.common.BizException(FileErrorCode.BAD_BASE64);
        }
    }
}
