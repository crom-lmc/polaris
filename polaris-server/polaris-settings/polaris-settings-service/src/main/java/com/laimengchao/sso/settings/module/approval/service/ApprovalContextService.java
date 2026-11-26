package com.laimengchao.sso.settings.module.approval.service;

import com.laimengchao.sso.settings.module.approval.vo.ApprovalContextVO;

public interface ApprovalContextService {

    /**
     * @param viewerId 当前查看人，用来回填「我的待办」——前端据此决定通过/驳回按钮是否可用
     */
    ApprovalContextVO context(Long instanceId, Long viewerId, String view);
}
