package com.laimengchao.sso.contract.module.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.common.EmbedPointService;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterialCategory;
import com.laimengchao.sso.contract.module.contract.entity.ContractPurchasePlanItem;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractMaterialCategoryMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractMaterialMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPurchasePlanItemMapper;
import com.laimengchao.sso.contract.module.contract.service.MaterialService;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 物料：独立表，category_id 指向三级分类；编码 = 三级分类码 + 6 位流水。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    public static final String CATEGORY = "CATEGORY";
    public static final String MATERIAL = "MATERIAL";

    private static final Long ROOT_PARENT_KEY = -1L;

    private final ContractMaterialMapper mapper;
    private final ContractMaterialCategoryMapper categoryMapper;
    private final ContractItemMapper itemMapper;
    private final ContractPurchasePlanItemMapper planItemMapper;
    private final EmbedPointService embedPointService;
    private final DataScopeService dataScopeService;

    @Override
    public List<ContractVOs.MaterialVO> list() {
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>()
                .orderByAsc(ContractMaterial::getCode);
        dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
        return mapper.selectList(qw).stream().map(this::toVO).toList();
    }

    @Override
    public List<ContractVOs.MaterialVO> byCategory(Long categoryId) {
        if (categoryId == null) {
            LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>()
                    .isNull(ContractMaterial::getCategoryId)
                    .orderByAsc(ContractMaterial::getCode);
            dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
            return mapper.selectList(qw).stream().map(this::toVO).toList();
        }
        ContractMaterialCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(category.getOrgCode());
        // 分类码为层级前缀（01/0101/010101），物料码=三级分类码+6位流水。
        // 选中任意级分类，按其编码前缀 like 即可命中该分类及其全部子孙分类下的物料。
        String prefix = category.getCode();
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>()
                .likeRight(ContractMaterial::getCode, prefix)
                .orderByAsc(ContractMaterial::getCode);
        dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
        return mapper.selectList(qw).stream().map(this::toVO).toList();
    }

    /** 分类自身 + 全部子孙分类 ID，按编码顺序去重 */
    private Set<Long> withDescendantCategoryIds(Long categoryId) {
        LambdaQueryWrapper<ContractMaterialCategory> cqw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .orderByAsc(ContractMaterialCategory::getCode);
        dataScopeService.applyOrgScope(cqw, ContractMaterialCategory::getOrgCode);
        Map<Long, List<ContractMaterialCategory>> byParent = categoryMapper.selectList(cqw).stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? ROOT_PARENT_KEY : c.getParentId()));
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(categoryId);
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            for (ContractMaterialCategory child : byParent.getOrDefault(current, List.of())) {
                if (ids.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }
        return ids;
    }

    /**
     * 分类 + 物料拼合树：仅供合同清单/采购计划选料下拉使用。
     * 停用分类及其子孙、其下物料都不进入下拉；物料节点 parentId 指向所属三级分类，kind=MATERIAL。
     */
    @Override
    public List<ContractVOs.MaterialVO> tree() {
        List<ContractVOs.MaterialVO> flat = new ArrayList<>();
        Set<Long> enabledIds = enabledCategoryIds();
        LambdaQueryWrapper<ContractMaterialCategory> cqw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .orderByAsc(ContractMaterialCategory::getCode);
        dataScopeService.applyOrgScope(cqw, ContractMaterialCategory::getOrgCode);
        for (ContractMaterialCategory c : categoryMapper.selectList(cqw)) {
            if (!enabledIds.contains(c.getId())) {
                continue;
            }
            ContractVOs.MaterialVO vo = new ContractVOs.MaterialVO();
            vo.setId(c.getId());
            vo.setParentId(c.getParentId());
            vo.setCode(c.getCode());
            vo.setName(c.getName());
            vo.setKind(CATEGORY);
            vo.setOrgCode(c.getOrgCode());
            flat.add(vo);
        }
        for (ContractVOs.MaterialVO m : list()) {
            if (!enabledIds.contains(m.getCategoryId())) {
                continue;
            }
            m.setKind(MATERIAL);
            m.setParentId(m.getCategoryId());
            flat.add(m);
        }
        Map<Long, ContractVOs.MaterialVO> byId = flat.stream()
                .collect(Collectors.toMap(ContractVOs.MaterialVO::getId, Function.identity(), (a, b) -> a));
        List<ContractVOs.MaterialVO> roots = new ArrayList<>();
        for (ContractVOs.MaterialVO vo : flat) {
            ContractVOs.MaterialVO parent = vo.getParentId() == null ? null : byId.get(vo.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>(List.of(vo)));
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }

    /**
     * 选料弹窗用分页查询：分类维度沿用「选中分类含子孙」口径，停用分类下物料不返回；
     * 关键字同时匹配编码、名称、规格，避免一次性把全量物料拉到前端。
     */
    @Override
    public PageResult<ContractVOs.MaterialVO> search(String keyword, Long categoryId, int page, int size) {
        Set<Long> enabled = enabledCategoryIds();
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>();
        if (categoryId == null) {
            Collection<Long> scope = enabled.isEmpty() ? List.of(-1L) : enabled;
            qw.in(ContractMaterial::getCategoryId, scope);
        } else {
            ContractMaterialCategory category = categoryMapper.selectById(categoryId);
            if (category == null) {
                throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
            }
            dataScopeService.assertCanAccessOrg(category.getOrgCode());
            Set<Long> ids = withDescendantCategoryIds(categoryId);
            ids.retainAll(enabled);
            Collection<Long> scope = ids.isEmpty() ? List.of(-1L) : ids;
            qw.in(ContractMaterial::getCategoryId, scope);
        }
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            String like = "%" + kw + "%";
            qw.and(w -> w.like(ContractMaterial::getName, like)
                    .or().like(ContractMaterial::getCode, like)
                    .or().like(ContractMaterial::getSpec, like));
        }
        qw.orderByAsc(ContractMaterial::getCode);
        Page<ContractMaterial> p = mapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), qw);
        LambdaQueryWrapper<ContractMaterialCategory> cqw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .select(ContractMaterialCategory::getId, ContractMaterialCategory::getName);
        dataScopeService.applyOrgScope(cqw, ContractMaterialCategory::getOrgCode);
        Map<Long, String> categoryNames = categoryMapper.selectList(cqw).stream().collect(Collectors.toMap(
                ContractMaterialCategory::getId, ContractMaterialCategory::getName, (a, b) -> a));
        List<ContractVOs.MaterialVO> vos = p.getRecords().stream().map(e -> {
            ContractVOs.MaterialVO vo = toVO(e);
            vo.setCategoryName(categoryNames.get(e.getCategoryId()));
            return vo;
        }).toList();
        return PageResult.of(vos, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 选料下拉可用分类：自身及所有祖先均为启用；父级不在数据范围内时按根节点处理 */
    private Set<Long> enabledCategoryIds() {
        LambdaQueryWrapper<ContractMaterialCategory> cqw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .orderByAsc(ContractMaterialCategory::getCode);
        dataScopeService.applyOrgScope(cqw, ContractMaterialCategory::getOrgCode);
        List<ContractMaterialCategory> all = categoryMapper.selectList(cqw);
        Map<Long, ContractMaterialCategory> byId = all.stream().collect(Collectors.toMap(
                ContractMaterialCategory::getId, Function.identity(), (a, b) -> a));
        Map<Long, Boolean> memo = new HashMap<>();
        Set<Long> enabled = new LinkedHashSet<>();
        for (ContractMaterialCategory c : all) {
            if (effectiveEnabled(c, byId, memo, new HashSet<>())) {
                enabled.add(c.getId());
            }
        }
        return enabled;
    }

    private boolean effectiveEnabled(ContractMaterialCategory category,
                                     Map<Long, ContractMaterialCategory> byId,
                                     Map<Long, Boolean> memo, Set<Long> chain) {
        Boolean cached = memo.get(category.getId());
        if (cached != null) {
            return cached;
        }
        if (!chain.add(category.getId())) {
            return false;
        }
        boolean result = !MaterialCategoryServiceImpl.STATUS_DISABLED.equals(category.getStatus());
        if (result && category.getParentId() != null) {
            ContractMaterialCategory parent = byId.get(category.getParentId());
            if (parent != null) {
                result = effectiveEnabled(parent, byId, memo, chain);
            }
        }
        chain.remove(category.getId());
        memo.put(category.getId(), result);
        return result;
    }

    @Override
    public ContractVOs.MaterialVO detail(Long id) {
        return toVO(require(id));
    }

    @Override
    public ContractMaterial require(Long id) {
        ContractMaterial entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    @Override
    public ContractMaterial requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>()
                .eq(ContractMaterial::getCode, code.trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
        ContractMaterial entity = mapper.selectOne(qw);
        if (entity == null) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.MaterialVO save(ContractRequests.MaterialSave req) {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "物料名称不能为空");
        }
        Long categoryId = req.getCategoryId() != null ? req.getCategoryId() : req.getParentId();
        if (categoryId == null) {
            throw new BizException(ContractErrorCode.MATERIAL_CODE_INVALID, "请选择三级分类");
        }
        ContractMaterialCategory category = categoryMapper.selectById(categoryId);
        if (category == null || category.getCode() == null || category.getCode().length() != categoryLength()) {
            throw new BizException(ContractErrorCode.MATERIAL_CODE_INVALID, "只能挂在三级分类下");
        }
        dataScopeService.assertCanAccessOrg(category.getOrgCode());
        if (MaterialCategoryServiceImpl.STATUS_DISABLED.equals(category.getStatus())) {
            throw new BizException(ContractErrorCode.MATERIAL_STATUS_INVALID, "所属分类已停用，不能新增物料");
        }
        String code = nextCode(categoryId, category.getCode());
        if (code.isEmpty()) {
            throw new BizException(ContractErrorCode.MATERIAL_CODE_INVALID);
        }
        ContractMaterial entity = new ContractMaterial();
        entity.setCategoryId(categoryId);
        entity.setCode(code);
        entity.setName(req.getName().trim());
        entity.setSpec(nvl(req.getSpec(), ""));
        entity.setUnit(nvl(req.getUnit(), ""));
        entity.setReferencePrice(req.getReferencePrice());
        entity.setOrgCode(nvl(req.getOrgCode(), category.getOrgCode()));
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.MaterialSave req) {
        ContractMaterial entity = require(id);
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "物料名称不能为空");
        }
        entity.setName(req.getName().trim());
        entity.setSpec(nvl(req.getSpec(), ""));
        entity.setUnit(nvl(req.getUnit(), ""));
        entity.setReferencePrice(req.getReferencePrice());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractMaterial entity = require(id);
        LambdaQueryWrapper<ContractItem> itemQw = new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getMaterialCode, entity.getCode());
        dataScopeService.applyOrgScope(itemQw, ContractItem::getOrgCode);
        LambdaQueryWrapper<ContractPurchasePlanItem> planQw = new LambdaQueryWrapper<ContractPurchasePlanItem>()
                .eq(ContractPurchasePlanItem::getMaterialCode, entity.getCode());
        dataScopeService.applyOrgScope(planQw, ContractPurchasePlanItem::getOrgCode);
        Long usedInItems = itemMapper.selectCount(itemQw);
        Long usedInPlans = planItemMapper.selectCount(planQw);
        if ((usedInItems != null && usedInItems > 0) || (usedInPlans != null && usedInPlans > 0)) {
            throw new BizException(ContractErrorCode.MATERIAL_IN_USE);
        }
        mapper.deleteById(id);
    }

    @Override
    public ContractVOs.MaterialVO toVO(ContractMaterial entity) {
        ContractVOs.MaterialVO vo = new ContractVOs.MaterialVO();
        vo.setId(entity.getId());
        vo.setCategoryId(entity.getCategoryId());
        vo.setParentId(entity.getCategoryId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setSpec(entity.getSpec());
        vo.setUnit(entity.getUnit());
        vo.setReferencePrice(entity.getReferencePrice());
        vo.setOrgCode(entity.getOrgCode());
        return vo;
    }

    /** 分类码总长：默认 3 级 × 2 位 = 6；埋点仅兼容同口径 */
    private int categoryLength() {
        int[] pattern = embedPointService.materialCodePattern();
        if (pattern != null && pattern.length >= 3 && pattern[0] == 3 && pattern[1] > 0
                && pattern[0] * pattern[1] == 6 && pattern[2] == 6) {
            return pattern[0] * pattern[1];
        }
        return 6;
    }

    /** 物料流水：三级分类码 + 同级最大流水 +1（6 位） */
    private String nextCode(Long categoryId, String categoryCode) {
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>()
                .eq(ContractMaterial::getCategoryId, categoryId);
        dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
        List<ContractMaterial> siblings = mapper.selectList(qw);
        int offset = categoryCode.length();
        int max = siblings.stream()
                .map(e -> parse(e.getCode(), offset))
                .max(Comparator.naturalOrder()).orElse(0);
        return categoryCode + String.format("%06d", max + 1);
    }

    private int parse(String code, int offset) {
        if (code == null || code.length() <= offset) {
            return 0;
        }
        try {
            return Integer.parseInt(code.substring(offset));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
