package com.laimengchao.sso.file.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 文件元数据。文件字节落本地磁盘，本表只存元数据与相对存储路径。 */
@Data
@TableName("file_object")
public class FileObject {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 业务类型，决定存储策略（ehr_attachment / cms_media / forum_post 等） */
    private String bizType;

    /** PUBLIC / PRIVATE */
    private String visibility;

    private String originalName;

    private String contentType;

    private Long size;

    private String sha256;

    /** 相对存储根目录下的路径（按日期分目录），不含根目录前缀 */
    private String storagePath;

    private String ownerType;

    private String ownerId;

    /** 上传账号 ID；内部调用无主体时填 0 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploaderId;

    /** NORMAL / DELETED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    /** 访问 URL（非持久化，查询时按可见性动态签发） */
    @TableField(exist = false)
    private String url;
}
