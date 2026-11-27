package com.laimengchao.sso.contract.module.contract.support;

import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.SignUrlRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 合同模块附件统一口径：采购计划、合同、验收、付款计划共用一个存储策略 contract_attachment，
 * 以 ownerType 区分业务对象；业务表只存 file_id，预览 URL 由统一文件服务签发。
 */
@Slf4j
public final class ContractAttachments {

    /** file_storage_policy 必须有启用的 contract_attachment 策略，否则上传报 8102 */
    public static final String FILE_BIZ_TYPE = "contract_attachment";
    public static final String OWNER_PURCHASE_PLAN = "CONTRACT_PURCHASE_PLAN";
    public static final String OWNER_CONTRACT = "CONTRACT_HEADER";
    public static final String OWNER_ACCEPTANCE = "CONTRACT_ACCEPTANCE";
    public static final String OWNER_PAYMENT_PLAN = "CONTRACT_PAYMENT_PLAN";
    /** 预览签名有效期，与财务报销附件保持一致 */
    public static final long SIGN_EXPIRE_SECONDS = 900L;

    private ContractAttachments() {
    }

    /** 请求中的 file_id 列表落库：去重、去空，顺序即前端展示顺序 */
    public static String join(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return String.join(",", ids.stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList());
    }

    /** 库里的逗号串还原为 file_id 列表 */
    public static List<String> split(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim)
                .filter(StringUtils::hasText).distinct().toList();
    }

    /** 逐条签发只读预览 URL；签名失败只记日志，不影响单据主流程 */
    public static List<String> signedUrls(List<String> ids, FileApi fileApi) {
        List<String> urls = new ArrayList<>();
        if (ids == null || ids.isEmpty() || fileApi == null) {
            return urls;
        }
        SignUrlRequest req = new SignUrlRequest();
        req.setPurpose("VIEW");
        req.setExpiresSeconds(SIGN_EXPIRE_SECONDS);
        for (String fileId : ids) {
            try {
                var resp = fileApi.sign(fileId, req);
                if (resp != null && resp.ok() && resp.getData() != null && resp.getData().getUrl() != null) {
                    urls.add(resp.getData().getUrl());
                }
            } catch (Exception ex) {
                log.warn("合同附件签名失败：fileId={} {}", fileId, ex.getMessage());
            }
        }
        return urls;
    }
}
