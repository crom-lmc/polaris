package com.laimengchao.sso.finance.module.finance.service.impl;

import com.laimengchao.sso.finance.common.impl.BizNoServiceImpl;
import com.laimengchao.sso.finance.module.finance.service.ArApService;
import com.laimengchao.sso.finance.module.finance.service.VoucherService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.contract.client.api.AcceptanceApi;
import com.laimengchao.sso.contract.client.dto.AcceptanceDTO;
import com.laimengchao.sso.finance.common.BizNoService;
import com.laimengchao.sso.finance.common.EmbedPointService;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinArAp;
import com.laimengchao.sso.finance.module.finance.mapper.FinArApMapper;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 应收应付单：过账 / 核销（FIFO 或 MANUAL）/ 销售验收推应收。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArApServiceImpl implements ArApService {

    public static final String AR = "AR";
    public static final String AP = "AP";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String POSTED = "POSTED";
    public static final String PAID = "PAID";
    public static final String REJECTED = "REJECTED";

    private final FinArApMapper arApMapper;
    private final BizNoService bizNoService;
    private final EmbedPointService embedPointService;
    private final VoucherService voucherService;
    private final DataScopeService dataScopeService;
    private final AcceptanceApi acceptanceApi;

    public R.Page<FinanceVOs.ArApVO> page(String type, String status, String keyword, String contractNo,
                                           LocalDate from, LocalDate to, int page, int size) {
        LambdaQueryWrapper<FinArAp> w = query(type, status, keyword, contractNo, from, to);
        w.orderByDesc(FinArAp::getDate).orderByDesc(FinArAp::getId);
        var p = arApMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(page, 1), Math.max(size, 1)), w);
        List<FinanceVOs.ArApVO> rows = new ArrayList<>();
        for (FinArAp a : p.getRecords()) {
            rows.add(toVO(a));
        }
        return R.Page.of(rows, p.getTotal());
    }

    private LambdaQueryWrapper<FinArAp> query(String type, String status, String keyword,
                                               String contractNo, LocalDate from, LocalDate to) {
        LambdaQueryWrapper<FinArAp> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinArAp::getOrgCode);
        w.eq(StringUtils.hasText(type), FinArAp::getType, (type == null ? null : type.trim()))
                .eq(StringUtils.hasText(status), FinArAp::getStatus, (status == null ? null : status.trim()))
                .eq(StringUtils.hasText(contractNo), FinArAp::getContractNo, (contractNo == null ? null : contractNo.trim()))
                .ge(from != null, FinArAp::getDate, from)
                .le(to != null, FinArAp::getDate, to);
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.like(FinArAp::getNo, k)
                    .or().like(FinArAp::getParty, k)
                    .or().like(FinArAp::getSummary, k)
                    .or().like(FinArAp::getContractNo, k));
        }
        return w;
    }

    public FinanceVOs.ArApVO detail(Long id) {
        FinArAp a = require(id);
        dataScopeService.assertCanAccessOrg(a.getOrgCode());
        return toVO(a);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.ArApVO create(FinanceRequests.ArApSave req) {
        validate(req);
        FinArAp a = build(req.getType(), req.getParty(), req.getAmount(), req.getContractNo(),
                req.getSubject(), req.getSummary(), req.getDate(), req.getDept(), req.getOrgCode());
        a.setInvoiceNo(StringUtils.hasText(req.getInvoiceNo()) ? req.getInvoiceNo().trim() : "");
        a.setInvoiceType(StringUtils.hasText(req.getInvoiceType()) ? req.getInvoiceType().trim() : "");
        a.setCreditDays(req.getCreditDays());
        // 到期日：显式传入优先；否则按发生日期 + 账期天数派生
        LocalDate due = req.getDueDate();
        if (due == null && req.getCreditDays() != null && a.getDate() != null) {
            due = a.getDate().plusDays(req.getCreditDays());
        }
        a.setDueDate(due);
        a.setStatus(APPROVED);
        arApMapper.insert(a);
        return toVO(a);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        FinArAp a = require(id);
        dataScopeService.assertCanAccessOrg(a.getOrgCode());
        if (!PENDING.equals(a.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅待审批的应收应付单可审批");
        }
        arApMapper.update(null, new LambdaUpdateWrapper<FinArAp>()
                .eq(FinArAp::getId, id)
                .set(FinArAp::getStatus, APPROVED));
    }

    @Transactional(rollbackFor = Exception.class)
    public void post(Long id) {
        FinArAp a = require(id);
        dataScopeService.assertCanAccessOrg(a.getOrgCode());
        if (!APPROVED.equals(a.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅已通过的应收应付单可过账");
        }
        postInternal(a);
    }

    private void postInternal(FinArAp a) {
        var voucher = voucherService.createAndPost(a.getType(), a.getNo(), a.getSummary(), a.getSubject(),
                a.getAmount(), a.getDate(), a.getCreator(), a.getDept(), a.getContractNo(), a.getOrgCode());
        arApMapper.update(null, new LambdaUpdateWrapper<FinArAp>()
                .eq(FinArAp::getId, a.getId())
                .set(FinArAp::getVoucherNo, voucher.getNo())
                .set(FinArAp::getStatus, POSTED));
    }

    /**
     * 核销：FIFO 按日期从早到晚冲抵同单位未核销单据；MANUAL 指定目标单号。
     * 全部核销完的单据状态置 PAID，不再计入往来余额与账龄。
     */
    @Transactional(rollbackFor = Exception.class)
    public void writeOff(Long id, FinanceRequests.WriteOffRequest req) {
        FinArAp source = require(id);
        dataScopeService.assertCanAccessOrg(source.getOrgCode());
        if (req == null || req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(FinanceErrorCode.WRITEOFF_AMOUNT_INVALID);
        }
        if (!APPROVED.equals(source.getStatus()) && !POSTED.equals(source.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "仅已通过或已入账的单据可核销");
        }
        BigDecimal left = balanceOf(source);
        if (req.getAmount().compareTo(left) > 0) {
            throw new BizException(FinanceErrorCode.WRITEOFF_AMOUNT_INVALID);
        }

        List<FinArAp> targets;
        if ("MANUAL".equals(embedPointService.writeoffMode())) {
            if (!StringUtils.hasText(req.getTargetNo())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "MANUAL 核销模式必须指定目标单号");
            }
            FinArAp target = arApMapper.selectOne(new LambdaQueryWrapper<FinArAp>()
                    .eq(FinArAp::getNo, req.getTargetNo().trim())
                    .ne(FinArAp::getId, source.getId())
                    .last("limit 1"));
            if (target == null || !source.getType().equals(target.getType())
                    || !source.getParty().equals(target.getParty())) {
                throw new BizException(FinanceErrorCode.ACCEPTANCE_INVALID, "目标单号与核销单据不匹配");
            }
            targets = List.of(target);
        } else {
            targets = arApMapper.selectList(new LambdaQueryWrapper<FinArAp>()
                    .eq(FinArAp::getType, source.getType())
                    .eq(FinArAp::getParty, source.getParty())
                    .in(FinArAp::getStatus, APPROVED, POSTED)
                    .ne(FinArAp::getId, source.getId())
                    .orderByAsc(FinArAp::getDate)
                    .orderByAsc(FinArAp::getId));
        }

        BigDecimal remain = req.getAmount();
        for (FinArAp t : targets) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = balanceOf(t);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal used = remain.min(available);
            t.setWrittenOffAmount(nvl(t.getWrittenOffAmount()).add(used));
            if (t.getWrittenOffAmount().compareTo(nvl(t.getAmount())) >= 0) {
                t.setStatus(PAID);
            }
            arApMapper.updateById(t);
            remain = remain.subtract(used);
        }
        if (remain.compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(FinanceErrorCode.WRITEOFF_AMOUNT_INVALID, "可核销余额不足");
        }
        // 源单据本身也按核销金额推进状态
        FinArAp refresh = require(id);
        refresh.setWrittenOffAmount(nvl(refresh.getWrittenOffAmount()).add(req.getAmount()));
        if (refresh.getWrittenOffAmount().compareTo(nvl(refresh.getAmount())) >= 0) {
            refresh.setStatus(PAID);
        }
        arApMapper.updateById(refresh);
    }

    /** 跨服核销入口：按单号定位后复用同一套核销规则 */
    @Transactional(rollbackFor = Exception.class)
    public void writeOffByNo(String no, FinanceRequests.WriteOffRequest req) {
        if (!StringUtils.hasText(no)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "核销单号不能为空");
        }
        FinArAp a = arApMapper.selectOne(new LambdaQueryWrapper<FinArAp>()
                .eq(FinArAp::getNo, no.trim())
                .last("limit 1"));
        if (a == null) {
            throw new BizException(FinanceErrorCode.AR_AP_NOT_FOUND);
        }
        writeOff(a.getId(), req);
    }

    /**
     * 销售合同验收推应收：先读合同域验收单（读失败按默认值兜底），
     * 验收不合格直接拒绝；请求参数优先，缺失部分用验收单补齐。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.ArApVO createFromAcceptance(FinanceRequests.AcceptanceArRequest req) {
        if (req == null || !StringUtils.hasText(req.getAcceptanceNo())) {
            throw new BizException(FinanceErrorCode.ACCEPTANCE_INVALID);
        }
        AcceptanceDTO acceptance = null;
        try {
            var resp = acceptanceApi.getByNo(req.getAcceptanceNo().trim());
            acceptance = resp == null || !resp.ok() ? null : resp.getData();
        } catch (Exception e) {
            log.warn("查询合同验收单失败，按入参生成应收：no={}，原因={}", req.getAcceptanceNo(), e.getMessage());
        }
        if (acceptance != null && acceptance.getResult() != null && acceptance.getResult().contains("不合格")
                && !acceptance.getResult().contains("部分合格")) {
            throw new BizException(FinanceErrorCode.ACCEPTANCE_INVALID, "验收不合格，不能生成应收");
        }
        String contractNo = StringUtils.hasText(req.getContractNo())
                ? req.getContractNo() : (acceptance == null ? null : acceptance.getContractNo());
        BigDecimal amount = req.getAmount();
        if (!StringUtils.hasText(contractNo) || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(FinanceErrorCode.ACCEPTANCE_INVALID, "缺少合同号或应收金额");
        }
        String summary = StringUtils.hasText(req.getSummary())
                ? req.getSummary() : (contractNo + " 验收款");
        String party = StringUtils.hasText(req.getParty()) ? req.getParty() : contractNo;
        LocalDate date = req.getDate() == null ? LocalDate.now() : req.getDate();
        FinArAp a = build(AR, party, amount, contractNo, "主营业务收入", summary, date,
                req.getDept(), req.getOrgCode());
        a.setStatus(PENDING);
        arApMapper.insert(a);
        return toVO(a);
    }

    /** 应收账龄：分档来自埋点 finance.aging.buckets，未核销（APPROVED/POSTED）才计入 */
    public List<FinanceVOs.AgingBucketVO> aging() {
        LambdaQueryWrapper<FinArAp> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinArAp::getOrgCode);
        w.eq(FinArAp::getType, AR)
                .in(FinArAp::getStatus, APPROVED, POSTED)
                .select(FinArAp::getDate, FinArAp::getAmount);
        List<FinArAp> rows = arApMapper.selectList(w);
        List<Integer> buckets = embedPointService.agingBuckets();
        List<FinanceVOs.AgingBucketVO> out = new ArrayList<>();
        int min = 0;
        for (int max : buckets) {
            final int lo = min;
            final int hi = max;
            BigDecimal value = BigDecimal.ZERO;
            for (FinArAp a : rows) {
                if (a.getAmount() == null || a.getDate() == null) {
                    continue;
                }
                long age = ChronoUnit.DAYS.between(a.getDate(), LocalDate.now());
                if (age >= lo && age <= hi) {
                    value = value.add(a.getAmount());
                }
            }
            FinanceVOs.AgingBucketVO vo = new FinanceVOs.AgingBucketVO();
            vo.setLabel(lo + "–" + hi + " 天");
            vo.setMinDays(lo);
            vo.setMaxDays(hi);
            vo.setValue(value);
            out.add(vo);
            min = max + 1;
        }
        BigDecimal total = out.stream().map(FinanceVOs.AgingBucketVO::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (FinanceVOs.AgingBucketVO vo : out) {
            vo.setPercent(total.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : vo.getValue().multiply(BigDecimal.valueOf(100))
                    .divide(total, 0, java.math.RoundingMode.HALF_UP).intValue());
        }
        return out;
    }

    /** 合同域按合同号查应付单（系统间调用，不经用户数据范围） */
    public List<FinanceVOs.ArApVO> payablesByContract(String contractNo) {
        if (!StringUtils.hasText(contractNo)) {
            return List.of();
        }
        List<FinArAp> rows = arApMapper.selectList(new LambdaQueryWrapper<FinArAp>()
                .eq(FinArAp::getType, AP)
                .eq(FinArAp::getContractNo, (contractNo == null ? null : contractNo.trim()))
                .orderByDesc(FinArAp::getDate));
        List<FinanceVOs.ArApVO> out = new ArrayList<>();
        for (FinArAp a : rows) {
            out.add(toVO(a));
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinArAp a = require(id);
        dataScopeService.assertCanAccessOrg(a.getOrgCode());
        if (POSTED.equals(a.getStatus()) || PAID.equals(a.getStatus())) {
            throw new BizException(FinanceErrorCode.STATUS_INVALID, "已入账或已核销的应收应付单不可删除");
        }
        arApMapper.deleteById(id);
    }

    private void validate(FinanceRequests.ArApSave req) {
        if (req == null || !StringUtils.hasText(req.getParty()) || !StringUtils.hasText(req.getType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "往来单位与类型不能为空");
        }
        if (!AR.equals(req.getType()) && !AP.equals(req.getType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "类型只能是 AR 或 AP");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "金额必须大于零");
        }
    }

    private FinArAp build(String type, String party, BigDecimal amount, String contractNo,
                          String subject, String summary, LocalDate date, String dept, String orgCode) {
        FinArAp a = new FinArAp();
        a.setNo(AP.equals(type) ? bizNoService.nextPayable() : bizNoService.next(BizNoServiceImpl.TABLE_AR_AP));
        a.setType(type);
        a.setParty(party.trim());
        a.setAmount(amount);
        a.setContractNo(StringUtils.hasText(contractNo) ? contractNo.trim() : null);
        a.setSubject(StringUtils.hasText(subject) ? subject.trim() : "");
        a.setSummary(StringUtils.hasText(summary) ? summary.trim() : "");
        a.setDate(date == null ? LocalDate.now() : date);
        a.setDept(StringUtils.hasText(dept) ? dept.trim() : "");
        a.setCreator(orgCode == null ? "" : "system");
        a.setWrittenOffAmount(BigDecimal.ZERO);
        a.setOrgCode(dataScopeService.orgCodeOf(orgCode));
        return a;
    }

    private FinArAp require(Long id) {
        FinArAp a = id == null ? null : arApMapper.selectById(id);
        if (a == null) {
            throw new BizException(FinanceErrorCode.AR_AP_NOT_FOUND);
        }
        return a;
    }

    private BigDecimal balanceOf(FinArAp a) {
        BigDecimal total = nvl(a.getAmount());
        BigDecimal done = nvl(a.getWrittenOffAmount());
        return done.compareTo(total) >= 0 ? BigDecimal.ZERO : total.subtract(done);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private FinanceVOs.ArApVO toVO(FinArAp a) {
        FinanceVOs.ArApVO vo = new FinanceVOs.ArApVO();
        vo.setId(a.getId());
        vo.setNo(a.getNo());
        vo.setParty(a.getParty());
        vo.setType(a.getType());
        // 薪资类往来金额按 finance:salary:view 独立脱敏
        boolean salary = a.getSubject() != null && a.getSubject().contains("薪");
        boolean reveal = !salary || dataScopeService.canRevealSalary();
        vo.setAmount(reveal ? a.getAmount() : null);
        vo.setBalance(reveal ? balanceOf(a) : null);
        vo.setAgeDays(a.getDate() == null ? 0
                : (int) ChronoUnit.DAYS.between(a.getDate(), LocalDate.now()));
        vo.setContractNo(a.getContractNo());
        vo.setVoucherNo(a.getVoucherNo());
        vo.setStatus(a.getStatus());
        vo.setDate(a.getDate());
        vo.setSubject(a.getSubject());
        vo.setSummary(a.getSummary());
        vo.setPaymentStage(a.getPaymentStage());
        vo.setInvoiceNo(a.getInvoiceNo());
        vo.setInvoiceType(a.getInvoiceType());
        vo.setCreditDays(a.getCreditDays());
        vo.setDueDate(a.getDueDate());
        vo.setDept(a.getDept());
        vo.setOrgCode(a.getOrgCode());
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }
}
