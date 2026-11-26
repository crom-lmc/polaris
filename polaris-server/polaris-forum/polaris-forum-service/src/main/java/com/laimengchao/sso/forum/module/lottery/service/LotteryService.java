package com.laimengchao.sso.forum.module.lottery.service;


import com.laimengchao.sso.forum.module.lottery.dto.LotteryConfigRequest;
import com.laimengchao.sso.forum.module.lottery.dto.PrizeRequest;
import com.laimengchao.sso.forum.module.lottery.vo.LotteryConfigVO;
import com.laimengchao.sso.forum.module.lottery.vo.LotteryPrizeVO;
import com.laimengchao.sso.forum.module.reward.vo.PrizeVO;
import java.util.List;


public interface LotteryService {

    /** 当前抽奖形态，缺省按方形九宫格。 */
    String mode();

    int lotteryCost();

    int dailyLimit();

    /** 公开抽奖盘的奖项（仅启用，按 sort_no 顺序，过滤无库存礼品）。 */
    List<PrizeVO> publicPrizes();

    /**
     * 按权重随机抽取奖项，返回其在启用奖项有序列表中的下标（供前台定位格子/扇区）。
     * 没有启用奖项时不抛异常，由调用方给出业务错误。
     */
    int drawIndex();

    /** 取可抽奖项中指定下标的奖项（与 drawIndex 使用同一有序列表）。 */
    PrizeVO prizeAt(int index);

    LotteryConfigVO adminConfig();

    LotteryConfigVO updateConfig(LotteryConfigRequest body);

    /**
     * 把启用奖项权重按现有比例缩放，使合计恰好为 100（百分比制）。
     * 最大余数法保证整数且总和精确为 100；不改变相对概率，仅归一化数值。
     */
    void normalizeWeights();

    LotteryPrizeVO createPrize(PrizeRequest body);

    LotteryPrizeVO updatePrize(long id, PrizeRequest body);

    void deletePrize(long id);
}
