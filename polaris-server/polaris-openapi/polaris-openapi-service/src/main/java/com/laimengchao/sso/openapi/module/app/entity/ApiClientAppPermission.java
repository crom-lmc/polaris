package com.laimengchao.sso.openapi.module.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 第三方应用被授权调用的开放接口权限码。
 * <p>
 * 对应 api_open_endpoint.required_perm，是令牌 perms 的唯一来源。
 * 授权即刻生效：下次换票时读取本表写入 JWT perms。
 */
@Data
@TableName("api_client_app_permission")
public class ApiClientAppPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String appCode;
    /** 开放接口编码，如 inbound.master.items；授权粒度为「一行一个接口」 */
    private String apiCode;
    /** 权限码，如 inbound:master:list */
    private String permCode;
    private String permName;
    /** 授权状态：1=启用（进入令牌 perms） 2=停用（不进入 perms） */
    private Integer status;
    private LocalDateTime createdAt;
}
