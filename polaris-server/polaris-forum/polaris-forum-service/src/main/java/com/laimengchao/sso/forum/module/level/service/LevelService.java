package com.laimengchao.sso.forum.module.level.service;


import com.laimengchao.sso.forum.module.level.dto.LevelProgress;
import com.laimengchao.sso.forum.module.level.dto.LevelRequest;
import com.laimengchao.sso.forum.module.level.vo.LevelRouteVO;
import com.laimengchao.sso.forum.module.level.vo.LevelVO;
import java.util.List;


public interface LevelService {

    /**
     * 允许配置的最高等级序号，供礼品门槛等外部校验复用。
     * 从配置中心埋点读取（60s 缓存）；埋点缺失/JSON 非法/配置中心不可用时兜底 20。
     */
    int maxLevel();

    /** 当前用户的晋升路线：未登录时只返回等级列表，进度按 0 经验计算 */
    LevelRouteVO currentRoute();

    List<LevelVO> adminList();

    /** 启用的等级定义（按等级序号），供等级徽章展示 */
    List<LevelVO> definitions();

    /** 公开晋升路线：全部启用等级 + 按累计经验计算的当前进度 */
    LevelRouteVO route(long experience);

    /** 按当前等级配置计算某累计经验对应的等级与升级进度 */
    LevelProgress progress(long experience);

    /**
     * 批量按累计经验计算等级：只查一次等级配置，避免排行榜逐行调用 {@link #progress(long)}
     * 造成 N+1（每行一次 listEnabled）。纯读+计算，天然幂等。
     */
    List<LevelProgress> progressBatch(List<Long> experiences);

    /** 指定账号当前等级序号，供礼品兑换门槛等场景校验（无会员记录按 1 级） */
    int currentLevel(long accountId);

    LevelVO create(LevelRequest body);

    LevelVO update(long id, LevelRequest body);

    void delete(long id);
}
