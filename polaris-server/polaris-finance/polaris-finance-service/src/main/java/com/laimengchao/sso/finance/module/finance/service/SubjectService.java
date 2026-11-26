package com.laimengchao.sso.finance.module.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.common.FinanceErrorCode;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinBudgetSubject;
import com.laimengchao.sso.finance.module.finance.entity.FinSubject;
import com.laimengchao.sso.finance.module.finance.entity.FinVoucher;
import com.laimengchao.sso.finance.module.finance.mapper.FinBudgetSubjectMapper;
import com.laimengchao.sso.finance.module.finance.mapper.FinSubjectMapper;
import com.laimengchao.sso.finance.module.finance.mapper.FinVoucherMapper;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 会计科目主数据：新增 / 编辑 / 启停 / 删除（被引用时禁止删除）。 */
@Service
@RequiredArgsConstructor
public class SubjectService {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private final FinSubjectMapper subjectMapper;
    private final FinVoucherMapper voucherMapper;
    private final FinBudgetSubjectMapper budgetSubjectMapper;
    private final DataScopeService dataScopeService;

    /** 主数据列表：数据范围过滤；status/level/category 可筛选 */
    public R.Page<FinanceVOs.SubjectVO> page(String keyword, String status, Integer level,
                                             String category, int page, int size) {
        LambdaQueryWrapper<FinSubject> w = query(keyword, status, level, category);
        w.orderByAsc(FinSubject::getCode);
        var p = subjectMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(page, 1), Math.max(size, 1)), w);
        List<FinanceVOs.SubjectVO> rows = new ArrayList<>();
        for (FinSubject s : p.getRecords()) {
            rows.add(toVO(s));
        }
        return R.Page.of(rows, p.getTotal());
    }

    /** 下拉：仅启用科目，仍按数据范围过滤 */
    public List<FinanceVOs.SubjectVO> enabledOptions() {
        LambdaQueryWrapper<FinSubject> w = query(null, ENABLED, null, null);
        w.orderByAsc(FinSubject::getCode);
        List<FinanceVOs.SubjectVO> out = new ArrayList<>();
        for (FinSubject s : subjectMapper.selectList(w)) {
            out.add(toVO(s));
        }
        return out;
    }

    private LambdaQueryWrapper<FinSubject> query(String keyword, String status, Integer level, String category) {
        LambdaQueryWrapper<FinSubject> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FinSubject::getOrgCode);
        w.eq(status != null && !status.isBlank(), FinSubject::getStatus, status.trim())
                .eq(level != null, FinSubject::getLevel, level)
                .eq(category != null && !category.isBlank(), FinSubject::getCategory, category.trim());
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            w.and(x -> x.like(FinSubject::getCode, k).or().like(FinSubject::getName, k));
        }
        return w;
    }

    public FinanceVOs.SubjectVO detail(Long id) {
        FinSubject s = require(id);
        dataScopeService.assertCanAccessOrg(s.getOrgCode());
        return toVO(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.SubjectVO create(FinanceRequests.SubjectSave req) {
        requireFields(req);
        if (subjectMapper.selectCount(new LambdaQueryWrapper<FinSubject>()
                .eq(FinSubject::getCode, req.getCode().trim())) > 0) {
            throw new BizException(FinanceErrorCode.SUBJECT_CODE_DUPLICATE);
        }
        FinSubject s = new FinSubject();
        apply(s, req);
        s.setOrgCode(dataScopeService.orgCodeOf(req.getOrgCode()));
        subjectMapper.insert(s);
        return toVO(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceVOs.SubjectVO update(Long id, FinanceRequests.SubjectSave req) {
        FinSubject s = require(id);
        dataScopeService.assertCanAccessOrg(s.getOrgCode());
        requireFields(req);
        FinSubject dup = subjectMapper.selectOne(new LambdaQueryWrapper<FinSubject>()
                .eq(FinSubject::getCode, req.getCode().trim())
                .ne(FinSubject::getId, id)
                .last("limit 1"));
        if (dup != null) {
            throw new BizException(FinanceErrorCode.SUBJECT_CODE_DUPLICATE);
        }
        apply(s, req);
        subjectMapper.updateById(s);
        return toVO(s);
    }

    /** 启停：ENABLED ↔ DISABLED */
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, String status) {
        FinSubject s = require(id);
        dataScopeService.assertCanAccessOrg(s.getOrgCode());
        String target = status == null || status.isBlank()
                ? (ENABLED.equals(s.getStatus()) ? DISABLED : ENABLED) : status.trim();
        if (!ENABLED.equals(target) && !DISABLED.equals(target)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态只能是 ENABLED / DISABLED");
        }
        s.setStatus(target);
        subjectMapper.updateById(s);
    }

    /** 删除：被凭证（subject 存编码或名称）或预算科目引用时禁止 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinSubject s = require(id);
        dataScopeService.assertCanAccessOrg(s.getOrgCode());
        Long vouchers = voucherMapper.selectCount(new LambdaQueryWrapper<FinVoucher>()
                .and(w -> w.eq(FinVoucher::getSubject, s.getCode()).or().eq(FinVoucher::getSubject, s.getName())));
        Long budgets = budgetSubjectMapper.selectCount(new LambdaQueryWrapper<FinBudgetSubject>()
                .eq(FinBudgetSubject::getAccountSubjectCode, s.getCode()));
        if ((vouchers != null && vouchers > 0) || (budgets != null && budgets > 0)) {
            throw new BizException(FinanceErrorCode.SUBJECT_IN_USE);
        }
        subjectMapper.deleteById(id);
    }

    private void apply(FinSubject s, FinanceRequests.SubjectSave req) {
        s.setCode(req.getCode().trim());
        s.setName(req.getName().trim());
        s.setLevel(req.getLevel() == null ? 1 : req.getLevel());
        s.setBalanceDirection(req.getBalanceDirection() == null || req.getBalanceDirection().isBlank()
                ? "DEBIT" : req.getBalanceDirection().trim());
        s.setCategory(req.getCategory() == null ? "" : req.getCategory().trim());
        s.setStatus(req.getStatus() == null || req.getStatus().isBlank()
                ? ENABLED : req.getStatus().trim());
    }

    private void requireFields(FinanceRequests.SubjectSave req) {
        if (req == null || req.getCode() == null || req.getCode().isBlank()
                || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "科目编码与名称不能为空");
        }
    }

    private FinSubject require(Long id) {
        FinSubject s = id == null ? null : subjectMapper.selectById(id);
        if (s == null) {
            throw new BizException(FinanceErrorCode.SUBJECT_NOT_FOUND);
        }
        return s;
    }

    private FinanceVOs.SubjectVO toVO(FinSubject s) {
        FinanceVOs.SubjectVO vo = new FinanceVOs.SubjectVO();
        vo.setId(s.getId());
        vo.setCode(s.getCode());
        vo.setName(s.getName());
        vo.setLevel(s.getLevel());
        vo.setBalanceDirection(s.getBalanceDirection());
        vo.setCategory(s.getCategory());
        vo.setStatus(s.getStatus());
        vo.setOrgCode(s.getOrgCode());
        vo.setCreatedAt(s.getCreatedAt());
        return vo;
    }
}
