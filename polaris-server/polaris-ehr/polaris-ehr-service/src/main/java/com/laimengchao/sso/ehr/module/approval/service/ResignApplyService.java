package com.laimengchao.sso.ehr.module.approval.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.approval.dto.HandoverConfirmRequest;
import com.laimengchao.sso.ehr.module.approval.dto.ResignApplyRequest;
import com.laimengchao.sso.ehr.module.approval.vo.ResignApplyVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;

import java.util.List;

public interface ResignApplyService {

    /**
     * 员工自助提交离职申请：被办理人就是当前登录人自己的档案。
     * <p>刻意不加 {@code @Transactional}：本地写入由 ApprovalLauncher.inTx 独立提交，
     * 远程发起必须发生在本地事务之外，否则 settings 的同步回调读不到未提交的业务行。
     */
    ResignApplyVO submitSelf(ResignApplyRequest req);

    /** HR 在人员列表上提交减员：被办理人是选中的那个人（同样不加事务，见 submitSelf） */
    ResignApplyVO submitReduce(Long employeeId, ResignApplyRequest req);

    /**
     * 交接人确认交接项。全部确认后申请单 handoverStatus 才置 1，
     * 并<b>在这一刻发起审批中心实例</b> —— 交接没做完就不进审批流。
     * <p>
     * 只认 {@code handover_account_id} 本人，不提供超管代确认的口子。
     */
    ResignApplyVO confirmHandover(Long id, HandoverConfirmRequest req);

    /**
     * 接收审批中心回传的离职/减员终态。必须幂等：回调失败会退避重试。
     * <p>
     * 通过 → 档案置离职 + 停用账号、单据 status=1；驳回/撤回/终止 → 还原档案状态、单据 status=2/3。
     * 两种情况都清掉申请单与员工档案上的在途标记。
     */
    void handleResignApprovalResult(ApprovalCallbackRequest req);

    /**
     * 撤销申请：先把审批中心的在途单撤回，再改本地状态。
     * <p>
     * <b>刻意不加 @Transactional</b>：settings 处理撤回时会同步回调本服务，回调线程要
     * UPDATE 同一行；若本方法持有写锁等 settings 响应，两边会互相等待直到锁超时。
     */
    ResignApplyVO cancel(Long id);

    /** 单据详情：审批弹窗要展开交接清单，单独取一次比列表里塞全量更省 */
    ResignApplyVO detail(Long id);

    /** 待我确认交接的单子（交接人视角）；includeFinished=true 时连已确认完的一起返回 */
    List<ResignApplyVO> myHandovers(boolean includeFinished);

    /** scope=mine 我提交的（含 HR 为我提交的减员）；scope=all 全部（需审批权限）；status 可过滤 */
    R.Page<ResignApplyVO> page(String scope, Integer status, int page, int size);
}
