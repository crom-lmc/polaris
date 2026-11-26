package com.laimengchao.sso.forum.module.reward.service;


import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.reward.vo.FeatureFlagsVO;
import com.laimengchao.sso.forum.module.reward.vo.GiftExchangeVO;
import com.laimengchao.sso.forum.module.reward.vo.LotteryDrawVO;
import com.laimengchao.sso.forum.module.reward.vo.PublicRewardsVO;
import com.laimengchao.sso.forum.module.reward.vo.RewardSummaryVO;
import java.util.List;





public interface RewardService {

    PublicRewardsVO publicRewards();

    /** 供前台/后台判断入口、按钮是否置灰；开关来自配置中心埋点。 */
    FeatureFlagsVO featureFlags();

    /** 抽奖开关：埋点缺失/解析失败/配置中心不可用时按开启处理，避免误伤线上功能。 */
    boolean lotteryEnabled();

    /** 兑换开关：埋点缺失/解析失败/配置中心不可用时按开启处理。 */
    boolean exchangeEnabled();

    RewardSummaryVO summary();

    RewardSummaryVO checkin();

    LotteryDrawVO draw();

    List<ForumExchange> exchanges();

    GiftExchangeVO exchange(long giftId);
}
