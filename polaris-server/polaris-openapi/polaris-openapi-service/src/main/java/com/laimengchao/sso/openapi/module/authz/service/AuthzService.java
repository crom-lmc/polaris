package com.laimengchao.sso.openapi.module.authz.service;

import com.laimengchao.sso.openapi.module.authz.vo.AuthzVo;
import java.util.List;

public interface AuthzService {

    /** 授权列表：一行授权对应一个接口，启停/删除只作用于该接口。 */
    List<AuthzVo> list();

    /** 批量授权：api_code → required_perm 后幂等落本地库，返回新建条数。 */
    int grant(String appCode, List<String> apiCodes);

    /** 撤销授权：只删该应用对该接口的授权，不影响其他接口。 */
    int revoke(String appCode, String apiCode);

    /** 启停授权：只作用于该接口。1=启用 2=停用，停用后下次换票不再带该接口编码。 */
    int toggle(String appCode, String apiCode, Integer status);
}
