package com.laimengchao.sso.file.module.file.controller;

import com.laimengchao.sso.file.module.file.service.FileAliasService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 旧存储路径兼容读取端点（存量迁移专用）。
 * <p>
 * 网关把 CMS 旧静态直链 /api/cms/media/file/**、论坛正文插图 /api/forum/media/file/**
 * 等前缀路由到本服务，这里用原样请求 URI 查 file_alias 映射，命中则从 file-service 读回文件，
 * 使历史正文内嵌的旧图片链接与论坛编辑器插图都能长期匿名访问。无映射时返回 404，由调用方感知。
 * <p>
 * 公开读取（图片/媒体本来就是公开内容），不做用户鉴权；恶意路径因查不到映射而 404。
 */
@RestController
@RequiredArgsConstructor
public class LegacyFileController {

    private final FileAliasService aliasService;

    @GetMapping({
            "/api/file/legacy/**",
            "/api/cms/media/file/**",
            "/api/forum/media/file/**",
            "/uploads/forum/**",
            "/uploads-cms/**"
    })
    public ResponseEntity<byte[]> read(HttpServletRequest req) {
        String path = req.getRequestURI();
        byte[] bytes = aliasService.resolve(path);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = aliasService.resolveContentType(path);
        MediaType type;
        try {
            type = contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
