package com.laimengchao.sso.settings.module.approval.service;

import com.laimengchao.sso.settings.module.approval.entity.ApprAction;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprTask;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalInstanceVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalTaskVO;
import java.util.List;

public interface ApprovalQueryService {

    /** 实例详情：步骤条 + 分支任务 + 表单快照 */
    ApprovalInstanceVO detail(Long instanceId, Long viewerId);

    /** 详情和渲染上下文共用同一参与人口径：申请人、审批/抄送人、委托人均可查看。 */
    void requireParticipant(ApprInstance inst, Long viewerId);

    /**
     * 五视图。
     *
     * @param scope pending 待我审批（含代收） / approved 已审批 / mine 我发起 /
     *              cc 抄送我 / delegated 我委托出去的
     */
    List<ApprovalInstanceVO> list(Long accountId, String scope);

    List<ApprAction> actions(Long instanceId);

    ApprovalInstanceVO toVO(ApprInstance inst, Long viewerId);

    ApprovalTaskVO toTaskVO(ApprTask t);
}
