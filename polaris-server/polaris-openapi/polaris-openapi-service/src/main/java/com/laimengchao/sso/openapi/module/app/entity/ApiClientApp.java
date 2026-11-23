package com.laimengchao.sso.openapi.module.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 第三方对接应用主数据。
 * <p>
 * 应用编码（appCode）与密钥由开放平台自助维护，是第三方接入的唯一身份凭证。
 * 令牌签发时本表 id 作为 sub（数字主体），app_code 作为 client_app_code claim。
 * appSecret 存 SHA-256 摘要用于换票校验，appSecretPlain 存明文供管理端查看。
 */
@Data
@TableName("api_client_app")
public class ApiClientApp {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 第三方应用编码，对外暴露，如 ufida / kingdee */
    private String appCode;
    private String appName;
    /** 应用密钥摘要（SHA-256），校验用 */
    private String appSecret;
    /** 应用密钥明文，管理端可随时查看（存库便于运维交接） */
    private String appSecretPlain;
    private String baseUrl;
    private String redirectUri;
    /** 备注 */
    private String remark;
    /** 1启用 2停用 */
    private Integer status;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
