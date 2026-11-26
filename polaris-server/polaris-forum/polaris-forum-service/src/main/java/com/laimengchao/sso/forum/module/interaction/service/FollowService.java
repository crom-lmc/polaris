package com.laimengchao.sso.forum.module.interaction.service;


import com.laimengchao.sso.forum.module.interaction.vo.FollowRelationVO;
import com.laimengchao.sso.forum.module.interaction.vo.FollowToggleVO;
import java.util.List;





public interface FollowService {

    FollowToggleVO follow(long accountId);

    FollowToggleVO unfollow(long accountId);

    List<FollowRelationVO> relations(long accountId, String type);

    List<FollowRelationVO> following(long accountId);

    List<FollowRelationVO> followers(long accountId);

    int followingCount(long accountId);

    int followerCount(long accountId);

    boolean isFollowing(long accountId, long followedAccountId);
}
