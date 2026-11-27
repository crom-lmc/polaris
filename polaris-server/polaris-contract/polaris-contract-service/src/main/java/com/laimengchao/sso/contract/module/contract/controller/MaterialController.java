package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.MaterialCategoryService;
import com.laimengchao.sso.contract.module.contract.service.MaterialService;
import com.laimengchao.sso.contract.security.Permit;
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

/** 物料库：分类树/分类列表维护分类；物料维护：按三级分类维护物料。均独立成表。 */
@RestController
@RequestMapping("/api/contract")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialCategoryService categoryService;
    private final MaterialService materialService;

    /* ============ 分类（物料库）：树里只有分类，不挂物料 ============ */

    @GetMapping("/material-categories/tree")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_LIST)
    public R<?> categoryTree() {
        return R.ok(categoryService.tree());
    }

    @GetMapping("/material-categories")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_LIST)
    public R<?> categoryChildren(@RequestParam(value = "parentId", required = false) Long parentId) {
        return R.ok(categoryService.children(parentId));
    }

    @GetMapping("/material-categories/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_LIST)
    public R<?> categoryDetail(@PathVariable("id") Long id) {
        return R.ok(categoryService.detail(id));
    }

    @PostMapping("/material-categories")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_CATEGORY_ADD)
    public R<?> saveCategory(@RequestBody ContractRequests.MaterialCategorySave req) {
        return R.ok(categoryService.save(req));
    }

    @PutMapping("/material-categories/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_CATEGORY_EDIT)
    public R<Void> updateCategory(@PathVariable("id") Long id,
                                  @RequestBody ContractRequests.MaterialCategorySave req) {
        categoryService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/material-categories/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_CATEGORY_EDIT)
    public R<Void> deleteCategory(@PathVariable("id") Long id) {
        categoryService.delete(id);
        return R.ok();
    }

    /* ============ 物料（物料维护）：选中三级分类后在列表维护 ============ */

    /** 分类 + 物料拼合树：仅用于合同清单/采购计划选料下拉，不用于物料库/维护页 */
    @GetMapping("/materials/tree")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_LIST)
    public R<?> materialTree() {
        return R.ok(materialService.tree());
    }

    @GetMapping("/materials")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_MAINTAIN_LIST)
    public R<?> materials(@RequestParam(value = "categoryId", required = false) Long categoryId) {
        return R.ok(categoryId == null ? materialService.list() : materialService.byCategory(categoryId));
    }

    @GetMapping("/materials/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_MAINTAIN_LIST)
    public R<?> detail(@PathVariable("id") Long id) {
        return R.ok(materialService.detail(id));
    }

    @PostMapping("/materials")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_ADD)
    public R<?> save(@RequestBody ContractRequests.MaterialSave req) {
        return R.ok(materialService.save(req));
    }

    @PutMapping("/materials/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_EDIT)
    public R<Void> update(@PathVariable("id") Long id, @RequestBody ContractRequests.MaterialSave req) {
        materialService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/materials/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        materialService.delete(id);
        return R.ok();
    }
}
