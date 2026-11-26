package com.laimengchao.sso.auth.service.ticket;

import com.laimengchao.sso.auth.dto.AppBrief;
import com.laimengchao.sso.auth.dto.TicketResponse;
import java.util.List;

public interface TicketService {

    /**
     * 签发票据。
     *
     * @param myApps 当前账号可访问的应用清单——不在清单里直接 403，这是「账号能访问哪些应用」的落点
     */
    TicketResponse issue(Long accountId, String appCode, String redirect, List<AppBrief> myApps);

    /**
     * 签发票据。
     *
     * @param myApps 当前账号可访问的应用清单——不在清单里直接 403，这是「账号能访问哪些应用」的落点
     * @param sid    发起换票时所在的中心会话，会被写进票据，最终落到换出的令牌上，
     *               这样应用侧拿到的令牌与 SSO 会话同生共死
     */
    TicketResponse issue(Long accountId, String appCode, String redirect, List<AppBrief> myApps, String sid);

    /** 消费票据：取走即删，重复使用拿不到（返回 null） */
    TicketStore.TicketPayload take(String ticket);
}
