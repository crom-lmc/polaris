package com.laimengchao.sso.file.module.file.controller;

import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.service.FileService;
import com.laimengchao.sso.file.module.file.service.SignUrlService;
import com.laimengchao.sso.file.module.file.service.ThumbnailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件读取通道。鉴权不靠 Spring Security 的全局规则（这里 permitAll），
 * 而靠短时效签名：签名由端内签发，签发动作本身在管理端（登录+权限）或服务间（内部令牌）已完成。
 * <ul>
 *   <li>/api/file/public/view → 内联预览，PUBLIC/PRIVATE 均可，必须持短时效签名；</li>
 *   <li>/api/file/secure/download → 附件下载，PUBLIC/PRIVATE 均可，必须持短时效签名。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileDownloadController {

    private final FileService fileService;
    private final SignUrlService signer;
    private final ThumbnailService thumbnails;

    @GetMapping("/public/view")
    public ResponseEntity<byte[]> view(@RequestParam("id") String id,
                                       @RequestParam("expires") long expires,
                                       @RequestParam("purpose") String purpose,
                                       @RequestParam("sign") String sign,
                                       @RequestParam(value = "w", required = false) Integer width) {
        // purpose 必须是 VIEW：防止拿 DOWNLOAD 签名访问内联预览端点
        signer.verify(id, "VIEW", expires, sign);
        FileObject obj = fileService.meta(id);
        if (obj == null) {
            return ResponseEntity.notFound().build();
        }
        // 私有文件允许内联预览：签名由管理端权限校验后签发，本身即授权令牌；
        // 列表/上传返回的 url 也走此端点，若这里拒绝 PRIVATE 会导致管理端“查看”一律 404。
        byte[] bytes = fileService.content(id);
        if (width != null && ThumbnailService.isRasterImage(obj.getContentType())) {
            int w = Math.max(32, Math.min(width, 1200)); // 限幅，避免超大图耗尽内存
            bytes = thumbnails.scale(bytes, w);
        }
        return inline(bytes, obj);
    }

    @GetMapping("/secure/download")
    public ResponseEntity<byte[]> download(@RequestParam("id") String id,
                                           @RequestParam("expires") long expires,
                                           @RequestParam("purpose") String purpose,
                                           @RequestParam("sign") String sign) {
        signer.verify(id, "DOWNLOAD", expires, sign);
        FileObject obj = fileService.meta(id);
        if (obj == null) {
            return ResponseEntity.notFound().build();
        }
        return attachment(fileService.content(id), obj);
    }

    private ResponseEntity<byte[]> inline(byte[] bytes, FileObject obj) {
        MediaType type = mediaType(obj.getContentType());
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .body(bytes);
    }

    private ResponseEntity<byte[]> attachment(byte[] bytes, FileObject obj) {
        String name = URLEncoder.encode(obj.getOriginalName() == null ? "file" : obj.getOriginalName(),
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name)
                .body(bytes);
    }

    private MediaType mediaType(String contentType) {
        try {
            return contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
