package com.laimengchao.sso.ehr.module.org.service;

import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.org.dto.OrgNodeRequest;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.vo.LeaderChangeVO;
import com.laimengchao.sso.ehr.module.org.vo.OrgNodeVO;
import java.util.List;

public interface OrgService {

    /** 完整组织树（根节点列表，子节点挂在 children 上） */
    List<OrgNodeVO> tree(String keyword);

    /** 扁平列表（按树序展开），前端级联下拉用 */
    List<OrgNodeVO> flatten(String keyword);

    OrgNodeVO detail(Long id);

    /**
     * 新增组织。编码<b>不再由前端指定</b>，按层级规则自动生成（见 {@link #generateOrgCode}），
     * 请求里即便带了 code 也会被忽略——避免手填编码与自动编码两套口径打架。
     */
    OrgNodeVO create(OrgNodeRequest req);

    /**
     * 编辑节点：名称/简称/负责人/排序/办公信息/备注。
     * 「上级」和「编码」是一次性决策，编辑接口不接变更——挪节点要重算整棵子树的
     * path/depth，编码是外部系统认的键，两者改动成本都远高于收益。
     */
    OrgNodeVO update(Long id, OrgNodeRequest req);

    /**
     * 设置 / 更换负责人。负责人是「建部门之后的事」：新增节点时不填，
     * 建完再从员工档案里挑人，避免建部门时档案还没录全。
     * <p>
     * <b>每一次「设置 / 更换 / 清除」都留一行记录</b>（{@code ehr_leader_change}）：
     * 记原负责人、新负责人、变更原因、发起人和结果，组织详情里能直接看变更履历。
     * <p>
     * 指定新负责人<b>不再直接生效</b>：先落一条待审批记录行并<b>独立提交</b>，再发起
     * 「部门设置负责人审批」（{@code ehr.dept_leader}）；审批单的 bizId 就是这条记录行的 id，
     * 终审回调按它回填「已生效 / 已驳回」并把新负责人写回组织节点。
     * 传 0 / null（清除负责人）仍直接生效——那是纠错动作，同样留痕，状态直接标记为已生效。
     *
     * @param employeeId 员工档案 ID；null 或 0 表示清除负责人
     * @param reason     变更原因；指定新负责人时必填
     */
    OrgNodeVO setLeader(Long id, Long employeeId, String reason);

    /**
     * 某部门的负责人变更记录（新→旧）。给组织详情的时间线用，按提交时间倒序。
     */
    List<LeaderChangeVO> leaderChanges(Long orgId);

    /**
     * 负责人变更审批终审回调。<b>必须幂等</b>：回调失败会退避重试，同一结果可能投递多次。
     * <p>
     * 新流程的 bizId = {@code ehr_leader_change.id}（记录行）；历史在途单的 bizId 还是组织ID，
     * 查不到记录行时走 {@link #handleLegacyLeaderApproval} 兼容分支，老单不会被卡住。
     * <p>
     * 通过 → 记录行置「已生效」+ 生效时间，并把新负责人写回组织节点；
     * 驳回 / 撤回 / 终止 → 记录行置终态，负责人保持原样，同时清掉组织上的「审批中」标记。
     */
    void handleLeaderApprovalResult(ApprovalCallbackRequest req);

    /**
     * 启用 / 禁用节点。禁用比删除温和，但仍然拒绝「有下级」或「有人」的节点：
     * 否则子节点挂在一个停用的父节点下，审批链路会静默断掉。
     *
     * @param status 1 启用、2 禁用
     */
    OrgNodeVO setStatus(Long id, Integer status);

    /** 删除节点：有子节点或有人，一律拒绝——组织树错删比错建难恢复得多 */
    void delete(Long id);

    OrgNode require(Long id);
}
