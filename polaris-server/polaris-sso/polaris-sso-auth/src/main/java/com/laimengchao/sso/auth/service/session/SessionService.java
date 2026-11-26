package com.laimengchao.sso.auth.service.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface SessionService {

    /** 登录成功后建立中心会话，并把 sessionId 写进 HttpOnly cookie */
    String create(Long accountId, HttpServletResponse response);

    /** 当前 cookie 对应的账户 ID；无会话返回 null */
    Long currentAccountId(HttpServletRequest request);

    /** 当前中心会话 ID（即使服务端已失效也返回 cookie 里的值，供登出时精确作废） */
    String currentSid(HttpServletRequest request);

    /** 会话有效期（秒），用于计算吊销条目的保留时长 */
    long ttlSeconds();

    /**
     * 按 sid 直接删除服务端会话。
     * <p>用于 cookie 不在当前域名、只能凭 sid 作废的跨应用登出场景，
     * 以及清理「已进吊销名单但记录还在」的僵尸会话。
     */
    void removeBySid(String sid);

    /**
     * 单点登出：删服务端会话 + 让浏览器立刻丢掉 cookie。
     *
     * @return 被销毁的 sid（cookie 不存在时为 null）。调用方还要拿它去作废
     *         同样绑在这个会话上的其他应用令牌，否则只删了自己这一份。
     */
    String destroy(HttpServletRequest request, HttpServletResponse response);

    /**
     * 管理端强制下线：作废该账号所有中心会话（含各应用绑定的令牌）。
     * 逐个 sid 调用 TokenService.logout，使网关的吊销名单同步生效。
     *
     * @return 被作废的会话数
     */
    int forceLogout(Long accountId);
}
