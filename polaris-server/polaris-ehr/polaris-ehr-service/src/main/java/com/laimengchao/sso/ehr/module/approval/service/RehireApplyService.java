package com.laimengchao.sso.ehr.module.approval.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.approval.dto.RehireApplyRequest;
import com.laimengchao.sso.ehr.module.approval.vo.RehireApplyVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;

public interface RehireApplyService {

    /**
     * 提交复职申请。刻意不加 {@code @Transactional}：
     * 本地写入由 {@code approvalLauncher.inTx} 独立提交，远程发起必须在其之外。
     */
    RehireApplyVO submit(Long employeeId, RehireApplyRequest req);

    /**
     * 接收审批中心回传的复职终态。<b>必须幂等</b>：回调失败会退避重试。
     * 通过 → 档案复职（清离职信息、按需启用账号）；驳回/撤回/终止 → 只清在途标记。
     */
    void handleRehireApprovalResult(ApprovalCallbackRequest req);

    /**
     * 撤销复职申请：先撤回审批中心的在途单，再改本地状态。
     * <p>刻意不加 {@code @Transactional}：撤回时 settings 会同步回调本服务，
     * 回调线程要 UPDATE 同一行，持有写锁等响应会互等超时。
     */
    RehireApplyVO cancel(Long id);

    RehireApplyVO detail(Long id);

    /** scope=mine 我提交的；scope=all 全部（需离职/复职管理权限） */
    R.Page<RehireApplyVO> page(String scope, Integer status, int page, int size);
}
