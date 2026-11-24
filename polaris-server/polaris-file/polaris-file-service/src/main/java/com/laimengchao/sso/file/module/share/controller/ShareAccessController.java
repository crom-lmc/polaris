package com.laimengchao.sso.file.module.share.controller;

import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.service.FileService;
import com.laimengchao.sso.file.module.share.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 分享链接公开访问（无需登录）。凭分享码 + 可选提取码换取文件下载。
 * 安全性由分享码随机性（12 位）+ 提取码哈希 + 有效期/次数上限保证。
 */
@RestController
@RequestMapping("/api/file/share")
@RequiredArgsConstructor
public class ShareAccessController {

    private final ShareService shareService;
    private final FileService fileService;

    @GetMapping("/access")
    public ResponseEntity<byte[]> access(@RequestParam("code") String code,
                                         @RequestParam(value = "password", required = false) String password) {
        FileObject file = shareService.access(code, password);
        String name = URLEncoder.encode(file.getOriginalName() == null ? "file" : file.getOriginalName(),
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + name)
                .header("Content-Type", file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                .body(fileService.content(String.valueOf(file.getId())));
    }
}
