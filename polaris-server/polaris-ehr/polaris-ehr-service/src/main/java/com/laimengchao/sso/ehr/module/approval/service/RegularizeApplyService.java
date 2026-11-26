package com.laimengchao.sso.ehr.module.approval.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.approval.dto.RegularizeApplyRequest;
import com.laimengchao.sso.ehr.module.approval.entity.RegularizeApply;
import com.laimengchao.sso.ehr.module.approval.vo.RegularizeApplyVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;

public interface RegularizeApplyService {

    /**
     * 提交转正申请。刻意不加 {@code @Transactional}：
     * 本地写入由 {@code approvalLauncher.inTx} 独立提交，远程发起必须在其之外。
     *
     * @param employeeId 被转正的员工档案ID
     * @param req        申请人填写的内容（可为空，老前端只点一个按钮也能提交）
     */
    RegularizeApplyVO submit(Long employeeId, RegularizeApplyRequest req);

    /**
     * 接收审批中心回传的转正终态。<b>必须幂等</b>：回调失败会退避重试。
     * 通过 → 档案转正式在职（40）+ regularizeStatus=3；驳回/撤回/终止 → 留在试用期、regularizeStatus=-1。
     * <p>
     * 兼容两种 bizId：
     * <ul>
     *   <li><b>申请单 ID</b>（新逻辑）：按 {@link RegularizeApply} 定位。</li>
     *   <li><b>员工 ID</b>（老逻辑）：改造前的转正实例 bizId 就是 employeeId，
     *       这里按 employeeId 回落处理，保证存量在途单不会卡住。</li>
     * </ul>
     */
    void handleRegularizeApprovalResult(ApprovalCallbackRequest req);

    /**
     * 撤销转正申请：先撤回审批中心的在途单，再改本地状态。
     * <p>刻意不加 {@code @Transactional}：撤回时 settings 会同步回调本服务，
     * 回调线程要 UPDATE 同一行，持有写锁等响应会互等超时。
     */
    RegularizeApplyVO cancel(Long id);

    RegularizeApplyVO detail(Long id);

    /** scope=mine 我提交的；scope=all 全部（需人员编辑权限） */
    R.Page<RegularizeApplyVO> page(String scope, Integer status, int page, int size);
}
