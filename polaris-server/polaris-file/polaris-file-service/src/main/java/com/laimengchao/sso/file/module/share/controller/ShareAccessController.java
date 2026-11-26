package com.laimengchao.sso.file.module.share.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.service.FileService;
import com.laimengchao.sso.file.module.share.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 分享链接公开访问（无需登录）。凭分享码 + 可选提取码换取文件下载。
 * 安全性由分享码随机性（12 位）+ 提取码哈希 + 有效期/次数上限保证。
 *
 * <p>浏览器直接打开 /access 时跳到前端访问页（输入提取码后再由页面以 XHR 取文件），
 * 避免带提取码的分享只显示一段 8119 JSON。旧链接因此也能继续用。
 */
@RestController
@RequestMapping("/api/file/share")
@RequiredArgsConstructor
public class ShareAccessController {

    private final ShareService shareService;
    private final FileService fileService;

    /** 访问页展示用的元信息，不暴露提取码哈希。 */
    @GetMapping("/info")
    public ResponseEntity<?> info(@RequestParam("code") String code) {
        try {
            return ResponseEntity.ok(R.ok(shareService.info(code)));
        } catch (BizException e) {
            return bizFail(e);
        }
    }

    @GetMapping("/access")
    public ResponseEntity<?> access(@RequestParam("code") String code,
                                    @RequestParam(value = "password", required = false) String password,
                                    @RequestHeader(value = "Accept", required = false) String accept) {
        // 浏览器导航（text/html）统一跳访问页；页面内 XHR 带 Accept:*/* 才会真正下载
        if (accept != null && accept.contains("text/html")) {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, shareService.sharePageUrl(code))
                    .build();
        }
        FileObject file;
        try {
            file = shareService.access(code, password);
        } catch (BizException e) {
            // 提取码错误/过期/次数用尽是预期拒绝，直接返回业务响应，避免冒泡成容器 ERROR 日志
            return bizFail(e);
        }
        String name = URLEncoder.encode(file.getOriginalName() == null ? "file" : file.getOriginalName(),
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + name)
                .header("Content-Type", file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                .body(fileService.content(String.valueOf(file.getId())));
    }

    /** 分享类业务拒绝按原业务码/HTTP 状态返回，保留前端的错误分支，同时不落 ERROR 堆栈。 */
    private ResponseEntity<?> bizFail(BizException e) {
        int status = e.getErrorCode() == null || e.getErrorCode().httpStatus() <= 0
                ? 200 : e.getErrorCode().httpStatus();
        return ResponseEntity.status(status).body(R.fail(e.getCode(), e.getMessage()));
    }
}
