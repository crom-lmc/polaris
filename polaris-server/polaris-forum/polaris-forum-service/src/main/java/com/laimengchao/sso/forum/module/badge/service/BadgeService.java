package com.laimengchao.sso.forum.module.badge.service;


import com.laimengchao.sso.forum.module.badge.dto.BadgeRequest;
import com.laimengchao.sso.forum.module.badge.vo.BadgeAdminVO;
import com.laimengchao.sso.forum.module.badge.vo.BadgeVO;
import com.laimengchao.sso.forum.module.badge.vo.MyBadgeVO;
import java.util.List;


public interface BadgeService {

    List<BadgeVO> publicBadges();

    List<MyBadgeVO> myBadges();

    List<BadgeAdminVO> adminList(String status);

    BadgeVO create(BadgeRequest body);

    BadgeVO update(long id, BadgeRequest body);

    BadgeAdminVO publish(long id);

    BadgeVO offline(long id);

    void evaluate(long accountId, String... ruleTypes);
}
