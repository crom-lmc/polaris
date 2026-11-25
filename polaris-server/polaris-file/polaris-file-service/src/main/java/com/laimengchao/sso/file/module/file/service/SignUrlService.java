package com.laimengchao.sso.file.module.file.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.file.common.FileErrorCode;
import com.laimengchao.sso.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 临时访问 URL 的 HMAC 签名。签名串 = id:purpose:expiresAt，防止篡改文件 ID 与有效期。
 * 公开文件与私有文件共用同一套签名，区别只在控制器是否额外要求登录态。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignUrlService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final FileProperties props;
    private final AttachmentSettingService settings;

    /** 生成签名 URL 与过期时间（epoch 秒）。 */
    public String[] sign(String fileId, String purpose, Long expiresSeconds) {
        // 未显式指定时取“附件设置”里的私有签名有效期，缺省回落配置文件
        long defaultTtl = settings.integer(AttachmentSettingService.SIGN_TTL_SECONDS,
                (int) props.getSignTtlSeconds());
        long ttl = expiresSeconds == null || expiresSeconds <= 0
                ? defaultTtl
                : Math.min(expiresSeconds, props.getSignTtlMaxSeconds());
        long expiresAt = System.currentTimeMillis() / 1000 + ttl;
        String signature = hmac(fileId + ":" + purpose + ":" + expiresAt);
        String base = props.getPublicBaseUrl() == null || props.getPublicBaseUrl().isBlank()
                ? "" : props.getPublicBaseUrl();
        String path = "/api/file/" + ("DOWNLOAD".equals(purpose) ? "secure/download" : "public/view");
        // HMAC 结果已是 URL-safe base64（无 + / =），可直接作为查询参数，无需二次编码
        String url = base + path + "?id=" + fileId + "&expires=" + expiresAt + "&purpose=" + purpose
                + "&sign=" + signature;
        return new String[]{url, String.valueOf(expiresAt)};
    }

    /** 校验签名；过期或签名不符抛 403。 */
    public void verify(String fileId, String purpose, long expiresAt, String signature) {
        if (expiresAt < System.currentTimeMillis() / 1000) {
            // 过期访问属可预期拒绝，不打印签名原文
            log.warn("文件访问链接已过期：fileId={} purpose={} expiresAt={}", fileId, purpose, expiresAt);
            throw new BizException(FileErrorCode.SIGN_INVALID, "访问链接已过期");
        }
        String expected = hmac(fileId + ":" + purpose + ":" + expiresAt);
        if (signature == null || !constantEquals(expected, signature)) {
            // 签名不符可能是篡改/越权，留痕 fileId 与 purpose（不记录签名串本身）
            log.warn("文件访问签名校验失败：fileId={} purpose={}", fileId, purpose);
            throw new BizException(FileErrorCode.SIGN_INVALID);
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(props.getSignSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            // 签名密钥/算法异常导致全站文件访问不可用，属致命失败（不打印密钥）
            log.error("计算文件访问签名失败：purpose 校验中断，请检查签名密钥配置", e);
            throw new IllegalStateException("计算文件访问签名失败", e);
        }
    }

    private boolean constantEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
