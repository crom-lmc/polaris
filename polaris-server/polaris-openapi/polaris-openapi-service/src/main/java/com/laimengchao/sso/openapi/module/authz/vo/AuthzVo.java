package com.laimengchao.sso.openapi.module.authz.vo;

import lombok.Data;

/** 应用授权列表项：一条「第三方应用 × 开放接口」授权关系。 */
@Data
public class AuthzVo {

    private String appCode;
    private String appName;
    private String apiCode;
    private String apiName;
    private Integer direction;
    private String directionLabel;
    private String operationType;
    private String method;
    private String path;
    private String requiredPerm;
    /** 接口目录本身的状态：1=启用 2=停用 */
    private Integer endpointStatus;
    /** 本条授权的状态：1=启用 2=停用（停用不进入令牌 perms） */
    private Integer status;
}
