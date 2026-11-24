package com.laimengchao.sso.file.module.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 文件分享链接元数据。 */
@Data
@TableName("file_share")
public class FileShare {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String shareCode;

    /** 提取码哈希（非明文） */
    private String passwordHash;

    private LocalDateTime expiresAt;

    private Integer maxViews;

    private Integer viewCount;

    private Boolean enabled;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    private LocalDateTime createdAt;

    /** 分享 URL（非持久化） */
    private String url;

    /** 文件原始名（非持久化，列表展示） */
    private String fileName;
}
