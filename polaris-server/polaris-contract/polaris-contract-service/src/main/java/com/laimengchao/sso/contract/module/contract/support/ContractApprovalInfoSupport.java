package com.laimengchao.sso.contract.module.contract.support;

import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalTaskDTO;
import com.laimengchao.sso.settings.client.dto.R;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 合同域审批信息只读查询（Support，不拆接口）。
 * 复用 settings-client 的 ApprovalApi.latest 按 bizType+bizId 反查；同一单走了多套模板时取雪花 ID 最大的一张。
 * 雪花 ID 全部按 String 下发，避免前端精度丢失。settings 侧零改动。
 */
@Component
@RequiredArgsConstructor
public class ContractApprovalInfoSupport {

    private final ApprovalApi approvalApi;

    /** 查最新审批实例；多模板时取 id 最大者，未发起返回 null。 */
    public ApprovalInstanceDTO latest(String bizId, String... bizTypes) {
        ApprovalInstanceDTO found = null;
        for (String bizType : bizTypes) {
            R<ApprovalInstanceDTO> resp = approvalApi.latest(bizType, bizId);
            ApprovalInstanceDTO inst = resp != null && resp.ok() ? resp.getData() : null;
            if (inst != null && inst.getId() != null
                    && (found == null || inst.getId() > found.getId())) {
                found = inst;
            }
        }
        return found;
    }

    /** 是否存在在途审批实例（status=0）：用于阻止手工审批绕过审批中心。 */
    public boolean hasRunning(String bizId, String... bizTypes) {
        ApprovalInstanceDTO found = latest(bizId, bizTypes);
        return found != null && found.getId() != null && Integer.valueOf(0).equals(found.getStatus());
    }

    /** 审批实例 → 只读 VO（雪花 ID String 化）。 */
    public ContractVOs.ApprovalInfoVO toVO(ApprovalInstanceDTO found) {
        if (found == null) {
            return null;
        }
        ContractVOs.ApprovalInfoVO vo = new ContractVOs.ApprovalInfoVO();
        vo.setId(String.valueOf(found.getId()));
        vo.setBizType(found.getBizType());
        vo.setBizNo(found.getBizNo());
        vo.setTitle(found.getTitle());
        vo.setStatus(found.getStatus());
        vo.setStatusName(found.getStatusName());
        vo.setApplicantName(found.getApplicantName());
        vo.setCreatedAt(fmt(found.getCreatedAt()));
        vo.setFinishedAt(fmt(found.getFinishedAt()));
        java.util.List<ContractVOs.ApprovalInfoVO.ApprovalTaskVO> tasks = new java.util.ArrayList<>();
        if (found.getTasks() != null) {
            for (ApprovalTaskDTO t : found.getTasks()) {
                ContractVOs.ApprovalInfoVO.ApprovalTaskVO tv = new ContractVOs.ApprovalInfoVO.ApprovalTaskVO();
                tv.setId(String.valueOf(t.getId()));
                tv.setStepSeq(t.getStepSeq());
                tv.setStepName(t.getStepName());
                tv.setNodeName(t.getNodeName());
                tv.setApproverName(t.getApproverName());
                tv.setStatus(t.getStatus());
                tv.setStatusName(t.getStatusName());
                tv.setOpinion(t.getOpinion());
                tv.setActedAt(fmt(t.getActedAt()));
                tasks.add(tv);
            }
        }
        vo.setTasks(tasks);
        return vo;
    }

    private static String fmt(java.time.LocalDateTime t) {
        return t == null ? null : t.toString();
    }
}
