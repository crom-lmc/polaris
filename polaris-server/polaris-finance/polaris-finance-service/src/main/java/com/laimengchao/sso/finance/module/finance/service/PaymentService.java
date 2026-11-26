package com.laimengchao.sso.finance.module.finance.service;

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
public class PaymentService {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";

    private final FinPaymentMapper paymentMapper;
    private final BizNoService bizNoService;
    private final DataScopeService dataScopeService;

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
        w.eq(StringUtils.hasText(contractNo), FinPayment::getContractNo, contractNo.trim())
                .eq(StringUtils.hasText(status), FinPayment::getStatus, status.trim());
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
        p.setNo(bizNoService.next(BizNoService.TABLE_PAYMENT));
        p.setContractNo(req.getContractNo().trim());
        p.setParty(req.getParty() == null || req.getParty().isBlank() ? req.getContractNo().trim() : req.getParty().trim());
        p.setAmount(req.getAmount());
        p.setDate(req.getDate() == null ? LocalDate.now() : req.getDate());
        p.setStatus(PENDING);
        p.setOrgCode(dataScopeService.orgCodeOf(req.getOrgCode()));
        paymentMapper.insert(p);
        return toVO(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void pay(Long id) {
        FinPayment p = require(id);
        dataScopeService.assertCanAccessOrg(p.getOrgCode());
        if (!PENDING.equals(p.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅待付款的付款单可支付");
        }
        p.setStatus(PAID);
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
        vo.setAmount(p.getAmount());
        vo.setStatus(p.getStatus());
        vo.setDate(p.getDate());
        vo.setOrgCode(p.getOrgCode());
        vo.setCreatedAt(p.getCreatedAt());
        return vo;
    }
}
