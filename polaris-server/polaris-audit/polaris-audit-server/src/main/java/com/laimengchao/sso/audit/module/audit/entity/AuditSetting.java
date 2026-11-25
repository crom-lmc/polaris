package com.laimengchao.sso.audit.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计全局设置：K-V 集中存采集/忽略/留存/脱敏/导出策略。
 * 字符串业务主键，必须声明 @TableId 否则 selectById/updateById/deleteById 无法绑定 statement。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("audit_setting")
public class AuditSetting {

    @TableId(value = "setting_key", type = IdType.INPUT)
    private String settingKey;

    private String settingValue;

    private LocalDateTime updatedAt;
}
