package com.laimengchao.sso.file.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 附件水印配置：按业务类型（biz_type）配置模板/样式/生效范围。 */
@Data
@TableName("file_watermark_config")
public class WatermarkConfig {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 业务类型，对应 file_storage_policy.biz_type */
    private String bizType;

    /** 是否启用 */
    private Boolean enabled;

    /** 水印文本模板，支持 {username}/{date}/{userId}/{ip}/{filename}/{app} 变量 */
    private String content;

    /** 字号 px */
    private Integer fontSize;

    /** 颜色（十六进制） */
    private String color;

    /** 透明度百分比 5-100 */
    private Integer opacity;

    /** 旋转角度 -90~0 */
    private Integer rotation;

    /** TILE 平铺 / CENTER 居中 */
    private String layout;

    /** 平铺间距 px */
    private Integer gap;

    /** 作用文件类型，逗号分隔 IMAGE/SVG/PDF/DOCX/XLSX/PPTX */
    private String fileTypes;

    /** 生效环节，逗号分隔 VIEW/SHARE/DOWNLOAD */
    private String scenes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
