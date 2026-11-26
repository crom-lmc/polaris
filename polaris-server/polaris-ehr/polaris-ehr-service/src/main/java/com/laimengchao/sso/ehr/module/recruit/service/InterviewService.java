package com.laimengchao.sso.ehr.module.recruit.service;

import com.laimengchao.sso.ehr.module.recruit.dto.InterviewFeedbackRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.InterviewRequest;
import com.laimengchao.sso.ehr.module.recruit.vo.InterviewTodoVO;
import com.laimengchao.sso.ehr.module.recruit.vo.InterviewVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface InterviewService {

    /** 候选人面试摘要（列表卡片展示） */
    public static class Summary {
        public Integer passedRound;
        public int pendingCount;
        public LocalDateTime nextAt;
    }

    /** 候选人全部面试记录，按轮次、时间升序，前端按 roundNo 分组展示 */
    List<InterviewVO> listByCandidate(Long candidateId);

    /** 约面试：轮次只能自增（当前最大轮次 + 1） */
    List<InterviewVO> schedule(Long candidateId, InterviewRequest req);

    /** 面试官「我的面试」：待反馈在前按时间升序，已反馈的保留在列表中供查看结果 */
    List<InterviewTodoVO> myTodos();

    /** 面试官提交反馈：必须给出本轮结论；不通过即淘汰，通过则等本轮全部通过后由 HR 决定发 Offer 或下一轮 */
    void submitFeedback(Long interviewId, InterviewFeedbackRequest req);

    /** HR 取消一场面试（同轮其它面试官不受影响） */
    void cancel(Long interviewId, String remark);

    /** 已全部面试官通过的最大轮次；无通过记录返回 null */
    Integer passedRound(Long candidateId);

    /** 是否存在未完成（已约未反馈、未判定）的面试 */
    boolean hasPending(Long candidateId);

    /** 批量摘要：按候选人统计待反馈数与最近安排时间，避免列表 N+1 */
    Map<Long, Summary> summaries(List<Long> candidateIds);
}
