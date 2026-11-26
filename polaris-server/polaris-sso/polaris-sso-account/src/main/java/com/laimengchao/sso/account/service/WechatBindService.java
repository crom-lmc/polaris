package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.domain.entity.SsoWechatBind;
import java.util.List;

public interface WechatBindService {

    /** 按 openid 取绑定；未绑定返回 null（auth 据此引导用户先绑定）。 */
    SsoWechatBind findByOpenid(String openid, String appType);

    /** 按 openid 反查绑定账户ID；未绑定返回 null。 */
    Long findAccountIdByOpenid(String openid, String appType);

    /** 当前登录账户绑定一个微信 identity（幂等：同一 openid 重复绑定覆盖）。 */
    void bind(Long accountId, String openid, String unionid, String appType, String nickname, String avatarUrl);

    List<SsoWechatBind> list(Long accountId);

    void unbind(Long id, Long accountId);

}
