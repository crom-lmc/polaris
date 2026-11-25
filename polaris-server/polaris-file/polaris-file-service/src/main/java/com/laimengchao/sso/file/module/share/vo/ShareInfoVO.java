package com.laimengchao.sso.file.module.share.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享链接的公开元信息，供匿名访问页展示。
 * 只暴露必要字段：不返回提取码哈希、文件 ID、创建人等敏感信息。
 */
@Data
public class ShareInfoVO {

    /** 文件原始名 */
    private String fileName;

    /** 是否需要提取码（只返回布尔值，绝不暴露哈希） */
    private boolean needPassword;

    /** 过期时间，null 表示长期有效 */
    private LocalDateTime expiresAt;

    /** 查看次数上限，0 表示不限 */
    private Integer maxViews;

    /** 已被查看次数 */
    private Integer viewCount;
}
