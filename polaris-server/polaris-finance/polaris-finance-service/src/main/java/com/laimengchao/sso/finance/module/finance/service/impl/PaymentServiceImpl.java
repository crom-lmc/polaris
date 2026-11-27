package com.laimengchao.sso.finance.module.finance.service.impl;

import com.laimengchao.sso.finance.common.impl.BizNoServiceImpl;
import com.laimengchao.sso.finance.module.finance.service.PaymentService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.common.BizNoService;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinPayment;
import com.laimengchao.sso.finance.module.finance.mapper.FinPaymentMapper;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.DataScopeService;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.SignUrlRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 付款单：由合同付款申请跨服生成，记录合同号、金额与状态。 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";
    private static final long SIGN_EXPIRE_SECONDS = 900L;

    private final FinPaymentMapper paymentMapper;
    private final BizNoService bizNoService;
    private final DataScopeService dataScopeService;
    private final FileApi fileApi;

    public R.Page<FinanceVOs.PaymentVO> page(String contractNo, String status, String keyword,
                                              int page, int size) {
        LambdaQueryWrapper<FinPayment> w = query(contractNo, status, keyword);
        w.orderByDesc(FinPayment::getDate).orderByDesc(FinPayment::getId);
        var p = paymentMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(page, 1), Math.max(size, 1)), w);
        List<FinanceVOs.PaymentVO> rows = new ArrayList<>();
        for (FinPayment v : p.getRecords()) {
            rows.add(toVO(v));
        }
        return R.Page.of(rows, p.getTotal());
    }

    private LambdaQueryWrapper<FinPayment> query(String contractNo, String status, String keyword) {
        LambdaQueryWrapper<FinPayment> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinPayment::getOrgCode);
        w.eq(StringUtils.hasText(contractNo), FinPayment::getContractNo, (contractNo == null ? null : contractNo.trim()))
                .eq(StringUtils.hasText(status), FinPayment::getStatus, (status == null ? null : status.trim()));
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.like(FinPayment::getNo, k)
                    .or().like(FinPayment::getContractNo, k)
                    .or().like(FinPayment::getParty, k));
        }
        return w;
    }

    public FinanceVOs.PaymentVO detail(Long id) {
        FinPayment p = require(id);
        dataScopeService.assertCanAccessOrg(p.getOrgCode());
        return toVO(p);
    }

    /** 手工/跨服创建：合同付款申请生成付款单 */
    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.PaymentVO create(FinanceRequests.PaymentSave req) {
        validate(req);
        FinPayment p = new FinPayment();
        p.setNo(bizNoService.next(BizNoServiceImpl.TABLE_PAYMENT));
        p.setContractNo(req.getContractNo().trim());
        p.setParty(req.getParty() == null || req.getParty().isBlank() ? req.getContractNo().trim() : req.getParty().trim());
        p.setStage(req.getStage() == null ? "" : req.getStage().trim());
        p.setAmount(req.getAmount());
        p.setSummary(req.getSummary() == null ? "" : req.getSummary().trim());
        p.setDueDate(req.getDueDate());
        p.setSourceDocNo(req.getSourceDocNo() == null ? null : req.getSourceDocNo().trim());
        p.setSettleMethod(req.getSettleMethod() == null ? "" : req.getSettleMethod().trim());
        p.setFileIds(normalizeFileIds(req.getFileIds()));
        p.setDate(req.getDate() == null ? LocalDate.now() : req.getDate());
        p.setStatus(PENDING);
        p.setOrgCode(dataScopeService.orgCodeOf(req.getOrgCode()));
        paymentMapper.insert(p);
        return toVO(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void pay(Long id) {
        pay(id, null);
    }

    /** 支付并记录过账凭证号（内部回写或管理端按凭证过账后调用） */
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long id, String voucherNo) {
        FinPayment p = require(id);
        dataScopeService.assertCanAccessOrg(p.getOrgCode());
        if (!PENDING.equals(p.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅待付款的付款单可支付");
        }
        p.setStatus(PAID);
        if (voucherNo != null && !voucherNo.isBlank()) {
            p.setVoucherNo(voucherNo.trim());
        }
        paymentMapper.updateById(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        FinPayment p = require(id);
        dataScopeService.assertCanAccessOrg(p.getOrgCode());
        if (!PENDING.equals(p.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅待付款的付款单可取消");
        }
        p.setStatus(CANCELLED);
        paymentMapper.updateById(p);
    }

    private void validate(FinanceRequests.PaymentSave req) {
        if (req == null || !StringUtils.hasText(req.getContractNo())
                || req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "合同号与付款金额不合法");
        }
    }

    private FinPayment require(Long id) {
        FinPayment p = id == null ? null : paymentMapper.selectById(id);
        if (p == null) {
            throw new BizException(FinanceErrorCode.PAYMENT_NOT_FOUND);
        }
        return p;
    }

    private FinanceVOs.PaymentVO toVO(FinPayment p) {
        FinanceVOs.PaymentVO vo = new FinanceVOs.PaymentVO();
        vo.setId(p.getId());
        vo.setNo(p.getNo());
        vo.setContractNo(p.getContractNo());
        vo.setParty(p.getParty());
        vo.setStage(p.getStage());
        vo.setAmount(p.getAmount());
        vo.setSummary(p.getSummary());
        vo.setDueDate(p.getDueDate());
        vo.setSourceDocNo(p.getSourceDocNo());
        vo.setVoucherNo(p.getVoucherNo());
        vo.setSettleMethod(p.getSettleMethod());
        vo.setStatus(p.getStatus());
        vo.setDate(p.getDate());
        vo.setOrgCode(p.getOrgCode());
        java.util.List<String> fileIds = StringUtils.hasText(p.getFileIds())
                ? java.util.Arrays.stream(p.getFileIds().split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).distinct().toList()
                : java.util.List.<String>of();
        vo.setFileIds(fileIds);
        java.util.List<String> urls = new java.util.ArrayList<>();
        SignUrlRequest signReq = new SignUrlRequest();
        signReq.setPurpose("VIEW");
        signReq.setExpiresSeconds(SIGN_EXPIRE_SECONDS);
        for (String fileId : fileIds) {
            try {
                var resp = fileApi.sign(fileId, signReq);
                if (resp != null && resp.ok() && resp.getData() != null && resp.getData().getUrl() != null) {
                    urls.add(resp.getData().getUrl());
                }
            } catch (Exception ignored) {
                // 签名失败不影响付款单主流程
            }
        }
        vo.setAttachmentUrls(urls);
        vo.setCreatedAt(p.getCreatedAt());
        return vo;
    }

    private String normalizeFileIds(java.util.List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return String.join(",", ids.stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList());
    }
}
