package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.dto.request.ApplicationRequest;
import com.laimengchao.sso.account.dto.response.ApplicationVO;

import java.util.List;

public interface ApplicationService {

    List<ApplicationVO> list(String keyword, Integer status);

    List<ApplicationVO> listEnabled();

    ApplicationVO detail(Long id);

    /** 创建：服务端生成 appSecret，返回体带一次明文 */
    ApplicationVO create(ApplicationRequest req);

    ApplicationVO update(Long id, ApplicationRequest req);

    void delete(Long id);

    /** 重置密钥，返回新的明文（旧密钥立即失效） */
    String resetSecret(Long id);

    /** 取启用状态的应用；不存在或已停用抛业务异常（ticket 签发前校验用） */
    SsoApplication requireEnabled(String code);

    /** 按编码取应用（含停用），不存在抛业务异常（管理端维护用） */
    SsoApplication requireByCode(String code);

    /** 校验应用密钥，供 /api/auth/ticket/exchange 鉴权 */
    boolean verifySecret(String code, String secret);

    /**
     * 应用在其 app_code 域下注册的开放 API 权限码（inbound:/outbound: 前缀）。
     * <p>
     * 供接口开放平台签发短期令牌时写入 perms——权限单一来源仍是统一登录中心的
     * sso_permission，开放平台不持有副本。
     */
    List<String> apiPermissionCodes(String appCode);
}
