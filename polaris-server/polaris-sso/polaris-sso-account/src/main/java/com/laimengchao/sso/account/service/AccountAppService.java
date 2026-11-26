package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.dto.response.ApplicationVO;

import java.util.List;

public interface AccountAppService {

    /** 账号被授权的应用 ID 列表（含已停用的，便于管理端回显勾选状态） */
    List<Long> appIds(Long accountId);

    /** 账号可用的应用详情（仅启用） */
    List<ApplicationVO> appsOf(Long accountId);

    /** 覆盖式授权：先清空再写入 */
    void grant(Long accountId, List<Long> appIds);

}
