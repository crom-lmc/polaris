package com.laimengchao.sso.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件服务配置。存储根目录、签名密钥与公开域名均通过环境变量/Nacos 下发，
 * 生产禁止使用默认值。
 */
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /** 本地存储根目录 */
    private String storageRoot = "./data/files";

    /** 签名 URL 的 HMAC 密钥（生产必须覆盖） */
    private String signSecret = "file-local-dev-secret-change-me";

    /** 签名 URL 默认有效期（秒） */
    private long signTtlSeconds = 900L;

    /** 签名 URL 有效期上限（秒） */
    private long signTtlMaxSeconds = 86400L;

    /** 对外访问域名（拼接公开 URL 用，如 https://polaris-file.laimengchao.site） */
    private String publicBaseUrl = "";

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getSignSecret() {
        return signSecret;
    }

    public void setSignSecret(String signSecret) {
        this.signSecret = signSecret;
    }

    public long getSignTtlSeconds() {
        return signTtlSeconds;
    }

    public void setSignTtlSeconds(long signTtlSeconds) {
        this.signTtlSeconds = signTtlSeconds;
    }

    public long getSignTtlMaxSeconds() {
        return signTtlMaxSeconds;
    }

    public void setSignTtlMaxSeconds(long signTtlMaxSeconds) {
        this.signTtlMaxSeconds = signTtlMaxSeconds;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
