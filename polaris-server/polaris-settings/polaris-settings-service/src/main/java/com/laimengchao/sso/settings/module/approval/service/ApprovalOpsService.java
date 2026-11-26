package com.laimengchao.sso.settings.module.approval.service;

import com.laimengchao.sso.settings.module.approval.dto.ApprovalActionRequest;
import com.laimengchao.sso.settings.module.approval.dto.BatchApprovalRequest;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalApproverStatsVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalBacklogVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalStatsVO;
import com.laimengchao.sso.settings.module.approval.vo.BatchApprovalResultVO;
import java.time.LocalDate;
import java.util.List;

public interface ApprovalOpsService {

    /**
     * 催办：只对进行中的单生效，次数累加并写 appr_action(action=URGE)，
     * 同时给当前所有待办人发一条站内消息。
     */
    ApprInstance urge(Long instanceId, ApprovalActionRequest req);

    /**
     * 扫描当前步骤停留超过 sla_hours 的在途单，给待办人发提醒。
     * 同一实例每天最多提醒一次（按 appr_action 里当天的 SLA_REMIND 流水去重）。
     * 返回本次提醒的实例数，供定时任务日志使用。
     */
    int scanSla();

    /** 场景 / 组织维度的审批时效统计 */
    ApprovalStatsVO stats(String appCode, String bizType, Long orgId, LocalDate from, LocalDate to);

    /** 审批人维度统计：按 APPROVE 任务的处理时长聚合 */
    List<ApprovalApproverStatsVO> approverStats(String appCode, String bizType, Long orgId, LocalDate from, LocalDate to);

    /** 积压列表：在途单按当前节点等待时长倒序 */
    List<ApprovalBacklogVO> backlog(String appCode, String bizType, Long orgId, int limit);

    /**
     * 批量通过/驳回。逐条校验（任务存在、属于操作人、实例进行中），
     * 单条失败记入 failed；已成功的单已各自提交，不回滚。
     */
    BatchApprovalResultVO batch(BatchApprovalRequest req);

    /**
     * 导出 CSV（带 BOM，Excel 直接打开不乱码）。
     * type=pending 只导当前操作人的待办；type=instances 按筛选条件导审批流水。
     * 只导出审批元数据，不含 form_data 等业务明文；导出行为写操作日志留痕。
     */
    String export(String type, Long accountId, String appCode, String bizType, Long orgId, LocalDate from, LocalDate to, Long operatorId);

    /** 实例快照里的当前步骤；快照损坏或旧单缺字段时返回 null */
    ApprovalSnapshot.Step currentStep(ApprInstance inst);
}
