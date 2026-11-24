package com.laimengchao.sso.file.client.dto;

import lombok.Data;

/**
 * 服务间上传请求。文件内容以 base64 内联传输，避免引入 feign-form 额外依赖；
 * 大文件的浏览器直传走 file-service 的客户端签名接口，不经本 Feign 契约。
 */
@Data
public class FileUploadRequest {

    /** 业务类型，决定存储策略（如 ehr_attachment、cms_media、forum_post） */
    private String bizType;

    /** PUBLIC / PRIVATE；私有类型由服务端策略可强制覆盖为 PRIVATE */
    private String visibility;

    private String originalName;

    private String contentType;

    /** base64 文件内容 */
    private String content;

    /** 归属对象类型与 ID（如 EMPLOYEE / 123） */
    private String ownerType;

    private String ownerId;

    /** 上传账号 ID，无登录主体时由调用方填 0 */
    private Long uploaderId;
}
