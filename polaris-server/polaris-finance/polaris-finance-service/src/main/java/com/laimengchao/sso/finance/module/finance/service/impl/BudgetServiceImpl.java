package com.laimengchao.sso.finance.module.finance.service.impl;
import com.laimengchao.sso.finance.module.finance.service.BudgetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.common.EmbedPointService;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinBudget;
import com.laimengchao.sso.finance.module.finance.entity.FinVoucher;
import com.laimengchao.sso.finance.module.finance.mapper.FinBudgetMapper;
import com.laimengchao.sso.finance.module.finance.mapper.FinVoucherMapper;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 预算额度：本地 base + 凭证派生 used，合同承诺字段由合同域跨服写入。 */
@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

    private final FinBudgetMapper budgetMapper;
    private final FinVoucherMapper voucherMapper;
    private final EmbedPointService embedPointService;
    private final DataScopeService dataScopeService;

    /** 列表：used/current 由已过账凭证按部门与年度派生，执行率按埋点预警 */
    public R.Page<FinanceVOs.BudgetVO> page(String dept, Integer year, int page, int size) {
        LambdaQueryWrapper<FinBudget> w = query(dept, year);
        w.orderByDesc(FinBudget::getYear).orderByAsc(FinBudget::getDept);
        var p = budgetMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(page, 1), Math.max(size, 1)), w);
        Map<String, BigDecimal> usedByDept = currentUsed(year);
        int alert = embedPointService.alertRatio();
        List<FinanceVOs.BudgetVO> rows = new ArrayList<>();
        for (FinBudget b : p.getRecords()) {
            rows.add(toVO(b, usedByDept.getOrDefault(key(b.getDept(), b.getYear()), BigDecimal.ZERO), alert));
        }
        return R.Page.of(rows, p.getTotal());
    }

    public List<FinanceVOs.BudgetVO> listAll(Integer year) {
        LambdaQueryWrapper<FinBudget> w = query(null, year);
        w.orderByDesc(FinBudget::getYear).orderByAsc(FinBudget::getDept);
        Map<String, BigDecimal> usedByDept = currentUsed(year);
        int alert = embedPointService.alertRatio();
        List<FinanceVOs.BudgetVO> out = new ArrayList<>();
        for (FinBudget b : budgetMapper.selectList(w)) {
            out.add(toVO(b, usedByDept.getOrDefault(key(b.getDept(), b.getYear()), BigDecimal.ZERO), alert));
        }
        return out;
    }

    private LambdaQueryWrapper<FinBudget> query(String dept, Integer year) {
        LambdaQueryWrapper<FinBudget> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinBudget::getOrgCode);
        w.eq(year != null, FinBudget::getYear, year)
                .eq(StringUtils.hasText(dept), FinBudget::getDept, (dept == null ? null : dept.trim()));
        return w;
    }

    /** 本期已用：已过账、非应收来源的凭证，按 部门+年度 汇总（同样受数据范围约束） */
    private Map<String, BigDecimal> currentUsed(Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        LambdaQueryWrapper<FinVoucher> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinVoucher::getOrgCode);
        w.eq(FinVoucher::getStatus, VoucherServiceImpl.POSTED)
                .ne(FinVoucher::getSourceType, "AR")
                .ge(FinVoucher::getDate, LocalDate.of(y, 1, 1))
                .le(FinVoucher::getDate, LocalDate.of(y, 12, 31))
                .select(FinVoucher::getDept, FinVoucher::getAmount);
        Map<String, BigDecimal> out = new HashMap<>();
        for (FinVoucher v : voucherMapper.selectList(w)) {
            if (v.getAmount() == null || v.getDept() == null) {
                continue;
            }
            out.merge(key(v.getDept(), y), v.getAmount(), BigDecimal::add);
        }
        return out;
    }

    public FinanceVOs.BudgetVO detail(Long id) {
        FinBudget b = require(id);
        dataScopeService.assertCanAccessOrg(b.getOrgCode());
        BigDecimal current = currentUsed(b.getYear())
                .getOrDefault(key(b.getDept(), b.getYear()), BigDecimal.ZERO);
        return toVO(b, current, embedPointService.alertRatio());
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.BudgetVO create(FinanceRequests.BudgetSave req) {
        validate(req);
        FinBudget b = new FinBudget();
        apply(b, req);
        b.setOrgCode(dataScopeService.orgCodeOf(req.getOrgCode()));
        budgetMapper.insert(b);
        return detail(b.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.BudgetVO update(Long id, FinanceRequests.BudgetSave req) {
        FinBudget b = require(id);
        dataScopeService.assertCanAccessOrg(b.getOrgCode());
        validate(req);
        apply(b, req);
        budgetMapper.updateById(b);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinBudget b = require(id);
        dataScopeService.assertCanAccessOrg(b.getOrgCode());
        budgetMapper.deleteById(id);
    }

    /** 合同域查询可用额度：无预算返回 0（读接口 fail-closed） */
    public BigDecimal available(String dept, Integer year) {
        if (!StringUtils.hasText(dept)) {
            return BigDecimal.ZERO;
        }
        int y = year == null ? LocalDate.now().getYear() : year;
        FinBudget b = budgetMapper.selectOne(new LambdaQueryWrapper<FinBudget>()
                .eq(FinBudget::getDept, dept.trim())
                .eq(FinBudget::getYear, y)
                .last("limit 1"));
        if (b == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal used = currentUsed(y).getOrDefault(key(b.getDept(), y), BigDecimal.ZERO);
        BigDecimal committed = nvl(b.getContractCommitted());
        BigDecimal left = nvl(b.getAmount()).subtract(used).subtract(committed);
        return left.max(BigDecimal.ZERO);
    }

    /** 合同承诺占用：累加 contract_committed，超额抛业务错误 */
    @Transactional(rollbackFor = Exception.class)
    public void occupy(String dept, Integer year, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(FinanceErrorCode.BUDGET_AMOUNT_INVALID);
        }
        if (available(dept, year).compareTo(amount) < 0) {
            throw new BizException(FinanceErrorCode.BUDGET_INSUFFICIENT);
        }
        FinBudget b = requireFor(dept, year);
        budgetMapper.update(null, new LambdaUpdateWrapper<FinBudget>()
                .eq(FinBudget::getId, b.getId())
                .set(FinBudget::getContractCommitted, nvl(b.getContractCommitted()).add(amount)));
    }

    /** 合同承诺释放：扣减 contract_committed，最低 0 */
    @Transactional(rollbackFor = Exception.class)
    public void release(String dept, Integer year, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(FinanceErrorCode.BUDGET_AMOUNT_INVALID);
        }
        FinBudget b = requireFor(dept, year);
        BigDecimal next = nvl(b.getContractCommitted()).subtract(amount).max(BigDecimal.ZERO);
        budgetMapper.update(null, new LambdaUpdateWrapper<FinBudget>()
                .eq(FinBudget::getId, b.getId())
                .set(FinBudget::getContractCommitted, next));
    }

    /** 合同域写入已付/在途（只更新这两个字段，不碰 base 与占用） */
    @Transactional(rollbackFor = Exception.class)
    public void syncContractStats(String dept, Integer year, BigDecimal paid, BigDecimal inFlight) {
        FinBudget b = requireFor(dept, year);
        budgetMapper.update(null, new LambdaUpdateWrapper<FinBudget>()
                .eq(FinBudget::getId, b.getId())
                .set(FinBudget::getContractPaid, nvl(paid))
                .set(FinBudget::getContractInFlight, nvl(inFlight)));
    }

    private FinBudget requireFor(String dept, Integer year) {
        if (!StringUtils.hasText(dept)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "部门不能为空");
        }
        int y = year == null ? LocalDate.now().getYear() : year;
        FinBudget b = budgetMapper.selectOne(new LambdaQueryWrapper<FinBudget>()
                .eq(FinBudget::getDept, dept.trim())
                .eq(FinBudget::getYear, y)
                .last("limit 1"));
        if (b == null) {
            throw new BizException(FinanceErrorCode.BUDGET_NOT_FOUND);
        }
        return b;
    }

    private void validate(FinanceRequests.BudgetSave req) {
        if (req == null || !StringUtils.hasText(req.getDept()) || req.getYear() == null
                || req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "部门、年度与预算额度不合法");
        }
    }

    private void apply(FinBudget b, FinanceRequests.BudgetSave req) {
        b.setDept(req.getDept().trim());
        b.setYear(req.getYear());
        b.setAmount(req.getAmount());
        b.setHistory(nvl(req.getHistory()));
        b.setContractCommitted(nvl(req.getContractCommitted()));
        b.setContractPaid(nvl(req.getContractPaid()));
        b.setContractInFlight(nvl(req.getContractInFlight()));
    }

    private FinBudget require(Long id) {
        FinBudget b = id == null ? null : budgetMapper.selectById(id);
        if (b == null) {
            throw new BizException(FinanceErrorCode.BUDGET_NOT_FOUND);
        }
        return b;
    }

    private FinanceVOs.BudgetVO toVO(FinBudget b, BigDecimal current, int alert) {
        FinanceVOs.BudgetVO vo = new FinanceVOs.BudgetVO();
        vo.setId(b.getId());
        vo.setDept(b.getDept());
        vo.setYear(b.getYear());
        vo.setAmount(b.getAmount());
        vo.setHistory(nvl(b.getHistory()));
        vo.setCurrent(current);
        BigDecimal used = nvl(b.getHistory()).add(current);
        vo.setUsed(used);
        BigDecimal base = nvl(b.getAmount());
        BigDecimal left = base.subtract(used).subtract(nvl(b.getContractCommitted())).max(BigDecimal.ZERO);
        vo.setAvailable(left);
        int rate = base.compareTo(BigDecimal.ZERO) == 0 ? 0
                : used.multiply(BigDecimal.valueOf(100)).divide(base, 0, RoundingMode.HALF_UP).intValue();
        vo.setRate(Math.min(rate, 999));
        vo.setAlertFlag(rate >= alert ? "RED" : (rate >= Math.max(0, alert - 20) ? "YELLOW" : ""));
        vo.setContractCommitted(nvl(b.getContractCommitted()));
        vo.setContractPaid(nvl(b.getContractPaid()));
        vo.setContractInFlight(nvl(b.getContractInFlight()));
        vo.setOrgCode(b.getOrgCode());
        vo.setCreatedAt(b.getCreatedAt());
        return vo;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String key(String dept, Integer year) {
        return dept + "|" + year;
    }
}
