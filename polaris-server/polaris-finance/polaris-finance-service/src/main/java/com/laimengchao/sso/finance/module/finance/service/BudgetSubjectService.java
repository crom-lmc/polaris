package com.laimengchao.sso.finance.module.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinBudgetSubject;
import com.laimengchao.sso.finance.module.finance.entity.FinVoucher;
import com.laimengchao.sso.finance.module.finance.mapper.FinBudgetSubjectMapper;
import com.laimengchao.sso.finance.module.finance.mapper.FinVoucherMapper;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 预算科目主数据：新增 / 编辑 / 启停 / 删除（已被凭证引用时禁止删除）。 */
@Service
@RequiredArgsConstructor
public class BudgetSubjectService {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private final FinBudgetSubjectMapper budgetSubjectMapper;
    private final FinVoucherMapper voucherMapper;
    private final DataScopeService dataScopeService;

    public R.Page<FinanceVOs.BudgetSubjectVO> page(String keyword, Integer year, String status,
                                                    int page, int size) {
        LambdaQueryWrapper<FinBudgetSubject> w = query(keyword, year, status);
        w.orderByDesc(FinBudgetSubject::getYear).orderByAsc(FinBudgetSubject::getCode);
        var p = budgetSubjectMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(page, 1), Math.max(size, 1)), w);
        List<FinanceVOs.BudgetSubjectVO> rows = new ArrayList<>();
        for (FinBudgetSubject s : p.getRecords()) {
            rows.add(toVO(s));
        }
        return R.Page.of(rows, p.getTotal());
    }

    public List<FinanceVOs.BudgetSubjectVO> enabledOptions(Integer year) {
        LambdaQueryWrapper<FinBudgetSubject> w = query(null, year, ENABLED);
        w.orderByAsc(FinBudgetSubject::getCode);
        List<FinanceVOs.BudgetSubjectVO> out = new ArrayList<>();
        for (FinBudgetSubject s : budgetSubjectMapper.selectList(w)) {
            out.add(toVO(s));
        }
        return out;
    }

    private LambdaQueryWrapper<FinBudgetSubject> query(String keyword, Integer year, String status) {
        LambdaQueryWrapper<FinBudgetSubject> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinBudgetSubject::getOrgCode);
        w.eq(year != null, FinBudgetSubject::getYear, year)
                .eq(status != null && !status.isBlank(), FinBudgetSubject::getStatus, status.trim());
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            w.and(x -> x.like(FinBudgetSubject::getCode, k).or().like(FinBudgetSubject::getName, k));
        }
        return w;
    }

    public FinanceVOs.BudgetSubjectVO detail(Long id) {
        FinBudgetSubject s = require(id);
        dataScopeService.assertCanAccessOrg(s.getOrgCode());
        return toVO(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.BudgetSubjectVO create(FinanceRequests.BudgetSubjectSave req) {
        requireFields(req);
        if (budgetSubjectMapper.selectCount(new LambdaQueryWrapper<FinBudgetSubject>()
                .eq(FinBudgetSubject::getCode, req.getCode().trim())
                .eq(FinBudgetSubject::getYear, req.getYear())) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该年度预算科目编码已存在");
        }
        FinBudgetSubject s = new FinBudgetSubject();
        apply(s, req);
        s.setOrgCode(dataScopeService.orgCodeOf(req.getOrgCode()));
        budgetSubjectMapper.insert(s);
        return toVO(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.BudgetSubjectVO update(Long id, FinanceRequests.BudgetSubjectSave req) {
        FinBudgetSubject s = require(id);
        dataScopeService.assertCanAccessOrg(s.getOrgCode());
        requireFields(req);
        apply(s, req);
        budgetSubjectMapper.updateById(s);
        return toVO(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, String status) {
        FinBudgetSubject s = require(id);
        dataScopeService.assertCanAccessOrg(s.getOrgCode());
        String target = status == null || status.isBlank()
                ? (ENABLED.equals(s.getStatus()) ? DISABLED : ENABLED) : status.trim();
        if (!ENABLED.equals(target) && !DISABLED.equals(target)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态只能是 ENABLED / DISABLED");
        }
        s.setStatus(target);
        budgetSubjectMapper.updateById(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinBudgetSubject s = require(id);
        dataScopeService.assertCanAccessOrg(s.getOrgCode());
        Long used = voucherMapper.selectCount(new LambdaQueryWrapper<FinVoucher>()
                .and(w -> w.eq(FinVoucher::getSubject, s.getCode()).or().eq(FinVoucher::getSubject, s.getName())));
        if (used != null && used > 0) {
            throw new BizException(FinanceErrorCode.BUDGET_SUBJECT_IN_USE);
        }
        budgetSubjectMapper.deleteById(id);
    }

    private void apply(FinBudgetSubject s, FinanceRequests.BudgetSubjectSave req) {
        s.setCode(req.getCode().trim());
        s.setName(req.getName().trim());
        s.setYear(req.getYear() == null ? java.time.LocalDate.now().getYear() : req.getYear());
        s.setAnnualAmount(req.getAnnualAmount() == null ? java.math.BigDecimal.ZERO : req.getAnnualAmount());
        s.setAccountSubjectCode(req.getAccountSubjectCode() == null ? null : req.getAccountSubjectCode().trim());
        s.setStatus(req.getStatus() == null || req.getStatus().isBlank()
                ? ENABLED : req.getStatus().trim());
    }

    private void requireFields(FinanceRequests.BudgetSubjectSave req) {
        if (req == null || req.getCode() == null || req.getCode().isBlank()
                || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "预算科目编码与名称不能为空");
        }
    }

    private FinBudgetSubject require(Long id) {
        FinBudgetSubject s = id == null ? null : budgetSubjectMapper.selectById(id);
        if (s == null) {
            throw new BizException(FinanceErrorCode.BUDGET_SUBJECT_NOT_FOUND);
        }
        return s;
    }

    private FinanceVOs.BudgetSubjectVO toVO(FinBudgetSubject s) {
        FinanceVOs.BudgetSubjectVO vo = new FinanceVOs.BudgetSubjectVO();
        vo.setId(s.getId());
        vo.setCode(s.getCode());
        vo.setName(s.getName());
        vo.setYear(s.getYear());
        vo.setAnnualAmount(s.getAnnualAmount());
        vo.setAccountSubjectCode(s.getAccountSubjectCode());
        vo.setStatus(s.getStatus());
        vo.setOrgCode(s.getOrgCode());
        vo.setCreatedAt(s.getCreatedAt());
        return vo;
    }
}
