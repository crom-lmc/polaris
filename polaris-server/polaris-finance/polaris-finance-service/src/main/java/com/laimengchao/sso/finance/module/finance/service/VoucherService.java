package com.laimengchao.sso.finance.module.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.common.BizNoService;
import com.laimengchao.sso.finance.common.EmbedPointService;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinVoucher;
import com.laimengchao.sso.finance.module.finance.mapper.FinVoucherMapper;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 会计凭证：手工新增、由业务单过账生成、按来源追溯。 */
@Service
@RequiredArgsConstructor
public class VoucherService {

    /** 已过账凭证才算生效 */
    public static final String POSTED = "POSTED";
    public static final String DRAFT = "DRAFT";

    private final FinVoucherMapper voucherMapper;
    private final BizNoService bizNoService;
    private final DataScopeService dataScopeService;
    private final EmbedPointService embedPointService;

    /** 列表：来源追溯 + 数据范围 */
    public R.Page<FinanceVOs.VoucherVO> page(String sourceType, String sourceNo, String status,
                                              String keyword, LocalDate from, LocalDate to,
                                              int page, int size) {
        LambdaQueryWrapper<FinVoucher> w = query(sourceType, sourceNo, status, keyword, from, to);
        w.orderByDesc(FinVoucher::getDate).orderByDesc(FinVoucher::getId);
        var p = voucherMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(page, 1), Math.max(size, 1)), w);
        List<FinanceVOs.VoucherVO> rows = new ArrayList<>();
        for (FinVoucher v : p.getRecords()) {
            rows.add(toVO(v));
        }
        return R.Page.of(rows, p.getTotal());
    }

    private LambdaQueryWrapper<FinVoucher> query(String sourceType, String sourceNo, String status,
                                                 String keyword, LocalDate from, LocalDate to) {
        LambdaQueryWrapper<FinVoucher> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinVoucher::getOrgCode);
        w.eq(sourceType != null && !sourceType.isBlank(), FinVoucher::getSourceType, trim(sourceType))
                .eq(sourceNo != null && !sourceNo.isBlank(), FinVoucher::getSourceNo, trim(sourceNo))
                .eq(status != null && !status.isBlank(), FinVoucher::getStatus, trim(status))
                .ge(from != null, FinVoucher::getDate, from)
                .le(to != null, FinVoucher::getDate, to);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            w.and(x -> x.like(FinVoucher::getNo, k)
                    .or().like(FinVoucher::getSummary, k)
                    .or().like(FinVoucher::getSourceNo, k)
                    .or().like(FinVoucher::getContractNo, k));
        }
        return w;
    }

    /** 详情：先列表范围过滤口径，再单条二次校验 */
    public FinanceVOs.VoucherVO detail(Long id) {
        FinVoucher v = require(id);
        dataScopeService.assertCanAccessOrg(v.getOrgCode());
        return toVO(v);
    }

    /** 手工新增：直接生成一张已过账凭证 */
    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.VoucherVO createManual(FinanceRequests.VoucherSave req) {
        requireAmount(req.getAmount());
        LocalDate date = req.getDate() == null ? LocalDate.now() : req.getDate();
        FinVoucher v = build(BizNoService.TABLE_VOUCHER,
                req.getSourceType() == null ? "MANUAL" : req.getSourceType().trim(),
                req.getSourceNo(), req.getSummary(), req.getSubject(), req.getAmount(), date,
                req.getCreator(), req.getDept(), req.getContractNo(), req.getOrgCode());
        v.setStatus(POSTED);
        voucherMapper.insert(v);
        return toVO(v);
    }

    /** 过账草稿凭证 */
    @Transactional(rollbackFor = Exception.class)
    public void post(Long id) {
        FinVoucher v = require(id);
        dataScopeService.assertCanAccessOrg(v.getOrgCode());
        if (POSTED.equals(v.getStatus())) {
            throw new BizException(FinanceErrorCode.ALREADY_POSTED);
        }
        voucherMapper.update(null, new LambdaUpdateWrapper<FinVoucher>()
                .eq(FinVoucher::getId, id)
                .set(FinVoucher::getStatus, POSTED));
    }

    /**
     * 由业务单据生成凭证并过账；同来源+来源单号幂等，避免重复过账。
     * 供报销、应收应付及 internal 回写统一调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinVoucher createAndPost(String sourceType, String sourceNo, String summary, String subject,
                                    BigDecimal amount, LocalDate date, String creator, String dept,
                                    String contractNo, String orgCode) {
        if (sourceNo != null && !sourceNo.isBlank()) {
            FinVoucher exists = voucherMapper.selectOne(new LambdaQueryWrapper<FinVoucher>()
                    .eq(FinVoucher::getSourceType, sourceType)
                    .eq(FinVoucher::getSourceNo, sourceNo.trim())
                    .last("limit 1"));
            if (exists != null) {
                return exists;
            }
        }
        FinVoucher v = build(BizNoService.TABLE_VOUCHER, sourceType, sourceNo, summary, subject,
                amount, date == null ? LocalDate.now() : date, creator, dept, contractNo, orgCode);
        v.setStatus(POSTED);
        voucherMapper.insert(v);
        return v;
    }

    /** 跨服回写入口：按业务单幂等生成凭证 */
    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.VoucherVO postFromBiz(FinanceRequests.VoucherPostRequest req) {
        requireAmount(req.getAmount());
        FinVoucher v = createAndPost(req.getSourceType(), req.getSourceNo(), req.getSummary(),
                req.getSubject(), req.getAmount(), req.getDate(), req.getCreator(), req.getDept(),
                req.getContractNo(), req.getOrgCode());
        return toVO(v);
    }

    /** 按业务单号查凭证（来源追溯，内部接口用） */
    public List<FinanceVOs.VoucherVO> bySourceNo(String sourceNo) {
        if (sourceNo == null || sourceNo.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<FinVoucher> w = new LambdaQueryWrapper<FinVoucher>()
                .eq(FinVoucher::getSourceNo, sourceNo.trim())
                .orderByDesc(FinVoucher::getDate);
        List<FinanceVOs.VoucherVO> out = new ArrayList<>();
        for (FinVoucher v : voucherMapper.selectList(w)) {
            out.add(toVO(v));
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinVoucher v = require(id);
        dataScopeService.assertCanAccessOrg(v.getOrgCode());
        voucherMapper.deleteById(id);
    }

    private FinVoucher build(String table, String sourceType, String sourceNo, String summary,
                             String subject, BigDecimal amount, LocalDate date, String creator,
                             String dept, String contractNo, String orgCode) {
        requireOpenPeriod(date);
        FinVoucher v = new FinVoucher();
        v.setNo(bizNoService.next(table));
        v.setSourceType(sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType.trim());
        v.setSourceNo(sourceNo == null ? "" : sourceNo.trim());
        v.setSummary(summary == null ? "" : summary.trim());
        v.setSubject(subject == null ? "" : subject.trim());
        v.setAmount(amount);
        v.setDate(date);
        v.setCreator(creator == null ? "" : creator.trim());
        v.setDept(dept == null ? "" : dept.trim());
        v.setContractNo(contractNo == null || contractNo.isBlank() ? null : contractNo.trim());
        v.setOrgCode(dataScopeService.orgCodeOf(orgCode));
        return v;
    }

    /**
     * 期间校验：埋点 finance.voucher.period-check 开启时，凭证日期所属会计期间
     * 必须在「次月关账日」之前仍开放。例如关账日=5，2026-10 的凭证必须在 2026-11-05 前录入；
     * 当前已过该截止日则拒绝。埋点读取失败时工具类按默认 true/5 兜底并记 WARN。
     */
    private void requireOpenPeriod(LocalDate date) {
        if (date == null || !embedPointService.periodCheck()) {
            return;
        }
        LocalDate deadline = date.withDayOfMonth(1).plusMonths(1)
                .withDayOfMonth(embedPointService.closeDay());
        if (LocalDate.now().isAfter(deadline)) {
            throw new BizException(FinanceErrorCode.VOUCHER_PERIOD_CLOSED,
                    "凭证日期 " + date + " 所属期间已于 " + deadline + " 关账");
        }
    }

    private void requireAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(FinanceErrorCode.WRITEOFF_AMOUNT_INVALID, "凭证金额必须大于零");
        }
    }

    private FinVoucher require(Long id) {
        FinVoucher v = id == null ? null : voucherMapper.selectById(id);
        if (v == null) {
            throw new BizException(FinanceErrorCode.VOUCHER_NOT_FOUND);
        }
        return v;
    }

    private FinanceVOs.VoucherVO toVO(FinVoucher v) {
        FinanceVOs.VoucherVO vo = new FinanceVOs.VoucherVO();
        vo.setId(v.getId());
        vo.setNo(v.getNo());
        vo.setSourceType(v.getSourceType());
        vo.setSourceNo(v.getSourceNo());
        vo.setSummary(v.getSummary());
        vo.setSubject(v.getSubject());
        // 薪资类凭证金额按 finance:salary:view 独立脱敏，与数据范围互不替代
        boolean salary = v.getSubject() != null && v.getSubject().contains("薪");
        vo.setAmount(salary && !dataScopeService.canRevealSalary() ? null : v.getAmount());
        vo.setStatus(v.getStatus());
        vo.setDate(v.getDate());
        vo.setCreator(v.getCreator());
        vo.setDept(v.getDept());
        vo.setContractNo(v.getContractNo());
        vo.setOrgCode(v.getOrgCode());
        vo.setCreatedAt(v.getCreatedAt());
        return vo;
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
