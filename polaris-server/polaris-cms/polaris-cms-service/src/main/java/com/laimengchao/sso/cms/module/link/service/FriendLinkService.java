package com.laimengchao.sso.cms.module.link.service;

import com.laimengchao.sso.cms.module.link.entity.FriendLink;
import java.util.List;

public interface FriendLinkService {

    /** 管理端：全部（含停用） */
    List<FriendLink> listAll();

    /** 门户：仅启用 */
    List<FriendLink> listEnabled();

    FriendLink save(FriendLink link);

    void delete(Long id);
}
