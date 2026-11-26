package com.laimengchao.sso.contract.module.contract.support;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.dto.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 合同域审批发起器（沿用 EHR ApprovalLauncher 三段式范式，精简为当前域所需能力）。
 * 业务行须由调用方先用独立事务落库提交，再调 launch，避免同步回调读到未提交行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractApprovalLauncher {

    private static final int STATUS_RUNNING = 0;

    private final ApprovalApi approvalApi;

    public ApprovalInstanceDTO launch(Supplier<ApprovalStartDTO> startSupplier) {
        ApprovalStartDTO dto = startSupplier.get();
        try {
            R<ApprovalInstanceDTO> resp = approvalApi.start(dto);
            if (resp == null || !resp.ok() || resp.getData() == null || resp.getData().getId() == null) {
                throw new BizException(ContractErrorCode.APPROVAL_START_FAILED, messageOf(resp));
            }
            ApprovalInstanceDTO inst = resp.getData();
            if (!Integer.valueOf(STATUS_RUNNING).equals(inst.getStatus())) {
                log.info("合同审批实例发起即终态：bizType={} bizId={} instance={} status={}",
                        dto.getBizType(), dto.getBizId(), inst.getId(), inst.getStatus());
            }
            return inst;
        } catch (BizException e) {
            ApprovalInstanceDTO recovered = recover(dto);
            if (recovered != null) {
                return recovered;
            }
            throw e;
        } catch (Exception e) {
            log.error("发起合同审批失败 bizType={} bizId={}", dto.getBizType(), dto.getBizId(), e);
            ApprovalInstanceDTO recovered = recover(dto);
            if (recovered != null) {
                return recovered;
            }
            throw new BizException(ContractErrorCode.APPROVAL_START_FAILED,
                    "发起审批失败（审批中心不可用），请稍后重试");
        }
    }

    private ApprovalInstanceDTO recover(ApprovalStartDTO dto) {
        try {
            R<ApprovalInstanceDTO> latest = approvalApi.latest(dto.getBizType(), dto.getBizId());
            if (latest != null && latest.ok() && latest.getData() != null
                    && latest.getData().getId() != null
                    && Integer.valueOf(STATUS_RUNNING).equals(latest.getData().getStatus())) {
                log.warn("发起合同审批响应异常但实例已创建：bizType={} bizId={} instance={}",
                        dto.getBizType(), dto.getBizId(), latest.getData().getId());
                return latest.getData();
            }
        } catch (Exception e) {
            log.warn("发起失败后查询 latest 也失败：{}", e.getMessage());
        }
        return null;
    }

    private static String messageOf(R<?> resp) {
        String msg = resp == null || resp.getMessage() == null ? "" : resp.getMessage();
        return msg.isBlank() ? "发起审批失败，请稍后重试" : msg;
    }
}
