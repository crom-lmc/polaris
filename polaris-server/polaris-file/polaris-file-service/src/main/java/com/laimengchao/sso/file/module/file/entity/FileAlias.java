package com.laimengchao.sso.file.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 旧存储路径 → 新文件 ID 的兼容映射。
 * <p>
 * CMS 等正文里内嵌的旧静态直链（如 /api/cms/media/file/2026/01/01/xxx.png）
 * 迁移后文件路径会变，靠本表把旧路径指回 file-service 里的新文件，
 * 保证历史正文里的图片不裂。forum/EHR 按数据库 ID 寻址，不走本表。
 */
@Data
@TableName("file_alias")
public class FileAlias {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 旧路径（去掉 host，含前导 /），唯一 */
    private String oldPath;

    /** 新文件 ID（file_object.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    /** 来源应用：cms / forum / ehr */
    private String sourceApp;

    private LocalDateTime createdAt;
}
