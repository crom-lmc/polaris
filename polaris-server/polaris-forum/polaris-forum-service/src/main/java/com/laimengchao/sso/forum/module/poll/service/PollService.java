package com.laimengchao.sso.forum.module.poll.service;


import com.laimengchao.sso.forum.module.poll.dto.PollConfigDTO;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigRequest;
import com.laimengchao.sso.forum.module.poll.dto.PollVoteRequest;
import com.laimengchao.sso.forum.module.poll.vo.PollVO;


public interface PollService {

    PollConfigDTO normalizeDraftConfig(PollConfigRequest source);

    PollConfigDTO validateForPublish(String kind, PollConfigRequest raw);

    void createPoll(long threadId, PollConfigDTO config);

    PollVO detail(long threadId, Long accountId);

    long vote(long threadId, PollVoteRequest body);
}
