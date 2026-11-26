package com.laimengchao.sso.forum.module.experience.service;


import com.laimengchao.sso.forum.module.experience.dto.ExperienceRuleRequest;
import com.laimengchao.sso.forum.module.experience.vo.ExperienceRuleVO;
import java.util.List;


public interface ExperienceService {

    /** 供发帖/回复调用：按规则给成员增加经验，规则停用或未配置则忽略。bizId 仅用于日志语义 */
    void grant(long accountId, String ruleKey, String bizId);

    List<ExperienceRuleVO> adminList();

    /** 公开的经验获取途径（启用的规则），供前台展示 */
    List<ExperienceRuleVO> publicRules();

    ExperienceRuleVO create(ExperienceRuleRequest body);

    ExperienceRuleVO update(long id, ExperienceRuleRequest body);

    void delete(long id);
}
