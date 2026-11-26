package com.laimengchao.sso.ehr.module.recruit.service;

import com.laimengchao.sso.ehr.module.recruit.dto.CandidateCommRequest;
import com.laimengchao.sso.ehr.module.recruit.entity.CandidateComm;
import com.laimengchao.sso.ehr.module.recruit.vo.CandidateCommVO;
import java.util.List;
import java.util.Map;

public interface RecruitCommService {

    List<CandidateCommVO> list(Long candidateId);

    CandidateCommVO add(Long candidateId, CandidateCommRequest req);

    /** 批量取每位候选人最近一条沟通记录，供看板卡片展示 */
    Map<Long, CandidateComm> lastCommMap(List<Long> candidateIds);
}
