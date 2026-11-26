package com.laimengchao.sso.openapi.module.app.service;

import com.laimengchao.sso.openapi.module.app.entity.ApiClientApp;
import com.laimengchao.sso.openapi.module.app.entity.ApiClientAppPermission;
import com.laimengchao.sso.openapi.module.app.vo.ClientAppSaveRequest;
import com.laimengchao.sso.openapi.module.app.vo.ClientAppVo;
import java.util.List;

public interface ClientAppService {

    /** 对接应用清单（含停用），按 sort/id 排序。 */
    List<ClientAppVo> list();

    /** 全部启用应用（内部下拉用）。 */
    List<ClientAppVo> listEnabled();

    /** 新增应用，返回 VO（appSecret 为明文，仅此一次）。 */
    ClientAppVo create(ClientAppSaveRequest req);

    /** 编辑应用（编码不可改）。 */
    ClientAppVo update(ClientAppSaveRequest req);

    /** 启停应用。 */
    void toggle(String appCode, Integer status);

    /** 重置密钥，返回新明文（仅此一次）。 */
    String resetSecret(String appCode);

    /** 查看密钥明文：存量应用未记录明文时返回空串，前端提示先重置。 */
    String viewSecret(String appCode);

    /** 删除应用（逻辑删除）：仍有接口授权时拒绝，需先到应用授权页逐条撤销。 */
    void delete(String appCode);

    /** 校验应用凭证：应用存在、启用、密钥摘要匹配。 */
    ApiClientApp verifyCredentials(String appCode, String appSecret);

    /** 取应用授权的接口编码（令牌 perms 唯一来源，一行一个接口）。 */
    List<String> permissions(String appCode);

    /** 授权列表（供 AuthzService）。 */
    List<ApiClientAppPermission> permissionList(String appCode);

    /** 幂等授权：按 appCode + apiCode 去重，返回新增条数。 */
    int grant(String appCode, List<ApiClientAppPermission> items);

    /** 撤销授权（物理删除，只删这一个接口），返回删除条数。 */
    int revoke(String appCode, String apiCode);

    /** 启停授权（只作用于这一个接口）：1=启用 2=停用。停用后该接口编码不再进入令牌 perms。 */
    int togglePermission(String appCode, String apiCode, Integer status);

    ApiClientApp requireByCode(String appCode);
}
