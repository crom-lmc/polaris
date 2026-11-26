package com.laimengchao.sso.forum.module.member.service;


import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.member.dto.MemberProfileUpdateRequest;
import com.laimengchao.sso.forum.module.member.vo.MemberProfileVO;
import com.laimengchao.sso.forum.module.member.vo.PointRankingVO;
import com.laimengchao.sso.forum.module.member.vo.PublicMemberVO;
import java.util.List;


public interface MemberService {

    PublicMemberVO publicMember(long accountId);

    List<ThreadVO> memberThreads(long accountId, String kind);

    List<PointRankingVO> pointRanking(Integer limit);

    /** 等级排行：按累计经验降序，复用积分排行的 VO 与等级换算 */
    List<PointRankingVO> levelRanking(Integer limit);

    MemberProfileVO profile();

    MemberProfileVO updateProfile(MemberProfileUpdateRequest body);
}
