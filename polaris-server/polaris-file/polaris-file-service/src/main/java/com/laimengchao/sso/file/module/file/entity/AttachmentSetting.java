package com.laimengchao.sso.file.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 附件全局设置：key-value 集中存预览/缩略图/秒传/签名/审计/回收站等开关与阈值。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_attachment_setting")
public class AttachmentSetting {

    /** 字符串业务主键，必须声明 @TableId 否则 selectById/updateById/deleteById 无法绑定 statement */
    @TableId(type = IdType.INPUT)
    private String settingKey;

    private String settingValue;

    private LocalDateTime updatedAt;
}
