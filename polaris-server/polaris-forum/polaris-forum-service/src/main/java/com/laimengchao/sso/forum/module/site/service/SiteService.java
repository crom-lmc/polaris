package com.laimengchao.sso.forum.module.site.service;


import com.laimengchao.sso.forum.module.site.dto.FriendLinkRequest;
import com.laimengchao.sso.forum.module.site.dto.SiteConfigRequest;
import com.laimengchao.sso.forum.module.site.entity.SiteConfig;
import com.laimengchao.sso.forum.module.site.vo.FriendLinkVO;
import com.laimengchao.sso.forum.module.site.vo.PublicSiteConfigVO;
import java.util.List;


public interface SiteService {

    PublicSiteConfigVO publicConfig();

    List<FriendLinkVO> publicLinks();

    SiteConfig adminConfig();

    SiteConfig updateConfig(SiteConfigRequest body);

    List<FriendLinkVO> adminLinks();

    FriendLinkVO createLink(FriendLinkRequest body);

    FriendLinkVO updateLink(long id, FriendLinkRequest body);
}
