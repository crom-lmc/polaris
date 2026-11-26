package com.laimengchao.sso.finance.module.finance.support;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.dto.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 财务审批发起器（沿用 EHR ApprovalLauncher 的三段式范式，精简为当前域所需能力）。
 *
 * <p>settings 的 start() 在无审批人/条件全假时会在同一次调用里把实例推到终态并同步回调，
 * 因此业务行必须先用独立事务提交，再远程发起；本类不负责本地事务边界，由调用方保证
 * 「先落库提交 → 再 launch」。在途判断统一查审批中心 running，不在业务表存单值实例号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceApprovalLauncher {

    private static final int STATUS_RUNNING = 0;

    private final ApprovalApi approvalApi;

    /** 发起审批；返回实例（状态可能已是终态）。失败先查 latest 兜底，避免「有单没审批」。 */
    public ApprovalInstanceDTO launch(Supplier<ApprovalStartDTO> startSupplier) {
        ApprovalStartDTO dto = startSupplier.get();
        try {
            R<ApprovalInstanceDTO> resp = approvalApi.start(dto);
            if (resp == null || !resp.ok() || resp.getData() == null || resp.getData().getId() == null) {
                throw new BizException(FinanceErrorCode.APPROVAL_START_FAILED, messageOf(resp));
            }
            ApprovalInstanceDTO inst = resp.getData();
            if (!Integer.valueOf(STATUS_RUNNING).equals(inst.getStatus())) {
                log.info("财务审批实例发起即终态：bizType={} bizId={} instance={} status={}",
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
            log.error("发起财务审批失败 bizType={} bizId={}", dto.getBizType(), dto.getBizId(), e);
            ApprovalInstanceDTO recovered = recover(dto);
            if (recovered != null) {
                return recovered;
            }
            throw new BizException(FinanceErrorCode.APPROVAL_START_FAILED,
                    "发起审批失败（审批中心不可用），请稍后重试");
        }
    }

    /** 是否有在途单；审批中心不可用时 fail-closed，避免绕过在途限制重复发起。 */
    public boolean hasRunning(String subjectId, String bizType) {
        if (subjectId == null || subjectId.isBlank()) {
            return false;
        }
        try {
            R<Boolean> resp = approvalApi.running(subjectId, bizType);
            return resp != null && resp.ok() && Boolean.TRUE.equals(resp.getData());
        } catch (Exception e) {
            log.warn("查询财务在途审批失败 subjectId={} bizType={}：{}", subjectId, bizType, e.getMessage());
            throw new BizException(FinanceErrorCode.APPROVAL_START_FAILED, "审批中心不可用，请稍后重试");
        }
    }

    /** start 响应丢失时查 latest 兜底：确认已建在途单就不视为失败。 */
    private ApprovalInstanceDTO recover(ApprovalStartDTO dto) {
        try {
            R<ApprovalInstanceDTO> latest = approvalApi.latest(dto.getBizType(), dto.getBizId());
            if (latest != null && latest.ok() && latest.getData() != null
                    && latest.getData().getId() != null
                    && Integer.valueOf(STATUS_RUNNING).equals(latest.getData().getStatus())) {
                log.warn("发起财务审批响应异常但实例已创建：bizType={} bizId={} instance={}",
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
