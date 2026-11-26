package com.laimengchao.sso.settings.module.approval.service;

import com.laimengchao.sso.settings.module.approval.dto.ApprovalActionRequest;
import com.laimengchao.sso.settings.module.approval.entity.ApprDelegate;
import java.util.List;

public interface ApprovalMoveService {

    void transfer(Long taskId, ApprovalActionRequest req);

    void addSign(Long taskId, ApprovalActionRequest req);

    void cc(Long instanceId, List<Long> accountIds, ApprovalActionRequest req);

    List<ApprDelegate> delegates(Long ownerId);


    /**
     * 业务侧登记委托（如部门负责人请假期间代审）。
     * <p>
     * 按 ownerId + remark 幂等：审批回调可能重复投递，重复时只更新时间段与被委托人，
     * 不再插一条新的委托记录。
     */
    ApprDelegate upsertDelegate(ApprDelegate d);

}
