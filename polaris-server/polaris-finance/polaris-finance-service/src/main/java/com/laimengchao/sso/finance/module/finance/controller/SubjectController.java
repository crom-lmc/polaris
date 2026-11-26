package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.SubjectService;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 会计科目主数据管理。 */
@RestController
@RequestMapping("/api/finance/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    @Permit(PermissionCodes.FINANCE_ACCOUNT_SUBJECT_LIST)
    public R<R.Page<FinanceVOs.SubjectVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(subjectService.page(keyword, status, level, category, page, size));
    }

    @GetMapping("/options")
    @Permit(PermissionCodes.FINANCE_ACCOUNT_SUBJECT_LIST)
    public R<List<FinanceVOs.SubjectVO>> options() {
        return R.ok(subjectService.enabledOptions());
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_ACCOUNT_SUBJECT_LIST)
    public R<FinanceVOs.SubjectVO> detail(@PathVariable("id") Long id) {
        return R.ok(subjectService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.FINANCE_ACCOUNT_SUBJECT_EDIT)
    public R<FinanceVOs.SubjectVO> create(@RequestBody FinanceRequests.SubjectSave req) {
        return R.ok(subjectService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_ACCOUNT_SUBJECT_EDIT)
    public R<FinanceVOs.SubjectVO> update(@PathVariable("id") Long id,
                                          @RequestBody FinanceRequests.SubjectSave req) {
        return R.ok(subjectService.update(id, req));
    }

    /** 启停：不传 status 时按当前状态翻转 */
    @PostMapping("/{id}/toggle")
    @Permit(PermissionCodes.FINANCE_ACCOUNT_SUBJECT_EDIT)
    public R<Void> toggle(@PathVariable("id") Long id,
                          @RequestParam(value = "status", required = false) String status) {
        subjectService.toggle(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_ACCOUNT_SUBJECT_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        subjectService.delete(id);
        return R.ok();
    }
}
