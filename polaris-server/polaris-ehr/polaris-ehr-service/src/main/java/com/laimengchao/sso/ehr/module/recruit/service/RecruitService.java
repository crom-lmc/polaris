package com.laimengchao.sso.ehr.module.recruit.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.CandidateRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.OfferRequest;
import com.laimengchao.sso.ehr.module.recruit.vo.CandidateVO;
import com.laimengchao.sso.ehr.module.recruit.vo.OfferVO;
import org.springframework.core.io.Resource;

public interface RecruitService {

    public record ResumeDownload(Resource resource, String fileName) {
    }

    R.Page<CandidateVO> pageCandidates(String stage, String keyword, int page, int size);

    CandidateVO createCandidate(CandidateRequest req);

    CandidateVO updateCandidate(Long id, CandidateRequest req);

    ResumeDownload downloadResume(Long id);

    /** 淘汰：有审批中/已通过的 Offer 时不允许 */
    void eliminate(Long id, String remark);

    /** 恢复：已淘汰 → 面试中 */
    void restore(Long id);

    R.Page<OfferVO> pageOffers(String status, String keyword, int page, int size);

    /**
     * 发起录用审批：本地先落 APPROVING 并提交，再远程发起（三段式避免同步回调读到未提交数据）。
     * 远程发起失败且审批中心确认无在途单时，回滚本地 Offer 并还原候选人阶段。
     */
    OfferVO createOffer(OfferRequest req);

    /** 撤销审批中的 Offer：先撤审批中心的在途单，再回滚本地状态 */
    void cancelOffer(Long id);

    /**
     * HR 线下与候选人确认后点「确认 Offer」：APPROVED → CONFIRMED。
     * 确认只代表双方达成一致，真正建员工档案要再点「办理入职」，两步分离便于回退与统计。
     */
    OfferVO confirmOffer(Long id);

    /** 候选人放弃 Offer：APPROVED/CONFIRMED → DECLINED，候选人回退到面试中 */
    OfferVO declineOffer(Long id, String reason);

    /**
     * 办理入职：CONFIRMED → 生成/复用待入职档案（status=10），回写 employee_id 并复制简历附件。
     * 幂等：已生成过档案直接返回，重复点击不会重复建档（source_offer_id 唯一兜底）。
     */
    OfferVO onboard(Long id);

    /**
     * 录用审批终审回调。幂等：同一结果可能重复投递，只有 APPROVING → 终态的那一次生效。
     */
    void handleOfferApprovalResult(ApprovalCallbackRequest req);
}
