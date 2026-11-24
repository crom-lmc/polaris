package com.laimengchao.sso.file.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 按业务类型配置的存储策略：大小上限、允许的扩展名、默认可见性。 */
@Data
@TableName("file_storage_policy")
public class StoragePolicy {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String bizType;

    private String bizName;

    /** 单文件大小上限（MB） */
    private Long maxSizeMb;

    /** 允许的扩展名，逗号分隔，为空表示不限制 */
    private String allowedExt;

    /** 该业务默认可见性 PUBLIC / PRIVATE */
    private String defaultVisibility;

    /** 是否强制私有（如 EHR 附件，忽略调用方传入的 PUBLIC） */
    private Boolean forcePrivate;

    /** 业务总配额（GB），0 / 空表示不限 */
    private Long quotaGb;

    /** 文件生命周期（天），超过后自动移入回收站，0 / 空表示永久 */
    private Integer ttlDays;

    private Boolean enabled;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
