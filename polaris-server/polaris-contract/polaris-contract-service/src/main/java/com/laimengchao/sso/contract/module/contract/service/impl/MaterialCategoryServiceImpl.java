package com.laimengchao.sso.contract.module.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.common.EmbedPointService;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterialCategory;
import com.laimengchao.sso.contract.module.contract.mapper.ContractMaterialCategoryMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractMaterialMapper;
import com.laimengchao.sso.contract.module.contract.service.MaterialCategoryService;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 物料三级分类：分类独立表，编码 01/0101/010101，服务端按父级续位。 */
@Service
@RequiredArgsConstructor
public class MaterialCategoryServiceImpl implements MaterialCategoryService {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    private static final Long ROOT_PARENT_KEY = -1L;

    private final ContractMaterialCategoryMapper mapper;
    private final ContractMaterialMapper materialMapper;
    private final EmbedPointService embedPointService;
    private final DataScopeService dataScopeService;

    @Override
    public List<ContractVOs.MaterialCategoryVO> list() {
        LambdaQueryWrapper<ContractMaterialCategory> qw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .orderByAsc(ContractMaterialCategory::getCode);
        dataScopeService.applyOrgScope(qw, ContractMaterialCategory::getOrgCode);
        List<ContractVOs.MaterialCategoryVO> flat = mapper.selectList(qw).stream().map(this::toVO).toList();
        decorate(flat);
        return buildTree(flat);
    }

    @Override
    public List<ContractVOs.MaterialCategoryVO> tree() {
        return list();
    }

    @Override
    public List<ContractVOs.MaterialCategoryVO> children(Long parentId) {
        if (parentId == null) {
            LambdaQueryWrapper<ContractMaterialCategory> qw = new LambdaQueryWrapper<ContractMaterialCategory>()
                    .isNull(ContractMaterialCategory::getParentId)
                    .orderByAsc(ContractMaterialCategory::getCode);
            dataScopeService.applyOrgScope(qw, ContractMaterialCategory::getOrgCode);
            List<ContractVOs.MaterialCategoryVO> roots = mapper.selectList(qw).stream().map(this::toVO).toList();
            decorate(roots);
            return roots;
        }
        require(parentId);
        // 选中任意层级分类：列表展示其下全部子孙分类（含二级、三级），按编码排序
        Map<Long, List<ContractMaterialCategory>> byParent = scopedCategories().stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? ROOT_PARENT_KEY : c.getParentId()));
        Set<Long> visited = new HashSet<>();
        visited.add(parentId);
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(parentId);
        List<ContractMaterialCategory> descendants = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            for (ContractMaterialCategory child : byParent.getOrDefault(current, List.of())) {
                if (visited.add(child.getId())) {
                    descendants.add(child);
                    queue.add(child.getId());
                }
            }
        }
        List<ContractVOs.MaterialCategoryVO> vos = descendants.stream()
                .sorted(Comparator.comparing(c -> c.getCode() == null ? "" : c.getCode()))
                .map(this::toVO).toList();
        decorate(vos);
        return vos;
    }

    /** 选料弹窗树：只取一层，停用分类及其子孙不返回 */
    @Override
    public List<ContractVOs.MaterialCategoryVO> directChildren(Long parentId) {
        List<ContractMaterialCategory> all = scopedCategories();
        Map<Long, ContractMaterialCategory> byId = all.stream().collect(Collectors.toMap(
                ContractMaterialCategory::getId, Function.identity(), (a, b) -> a));
        Map<Long, Boolean> memo = new HashMap<>();
        List<ContractMaterialCategory> enabledAll = all.stream()
                .filter(c -> enabled(c, byId, memo, new HashSet<>())).toList();
        Map<Long, Long> childCounts = enabledAll.stream().collect(Collectors.groupingBy(
                c -> c.getParentId() == null ? ROOT_PARENT_KEY : c.getParentId(), Collectors.counting()));
        LambdaQueryWrapper<ContractMaterialCategory> qw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .orderByAsc(ContractMaterialCategory::getCode);
        if (parentId == null) {
            qw.isNull(ContractMaterialCategory::getParentId);
        } else {
            require(parentId);
            qw.eq(ContractMaterialCategory::getParentId, parentId);
        }
        dataScopeService.applyOrgScope(qw, ContractMaterialCategory::getOrgCode);
        List<ContractVOs.MaterialCategoryVO> vos = mapper.selectList(qw).stream()
                .filter(c -> enabled(c, byId, memo, new HashSet<>()))
                .map(this::toVO).toList();
        for (ContractVOs.MaterialCategoryVO vo : vos) {
            vo.setChildCount(childCounts.getOrDefault(vo.getId(), 0L));
        }
        return vos;
    }

    /** 自身及所有祖先均启用才可选；父级不在数据范围内时按根节点处理 */
    private boolean enabled(ContractMaterialCategory category,
                            Map<Long, ContractMaterialCategory> byId,
                            Map<Long, Boolean> memo, Set<Long> chain) {
        Boolean cached = memo.get(category.getId());
        if (cached != null) {
            return cached;
        }
        if (!chain.add(category.getId())) {
            return false;
        }
        boolean result = !STATUS_DISABLED.equals(category.getStatus());
        if (result && category.getParentId() != null) {
            ContractMaterialCategory parent = byId.get(category.getParentId());
            if (parent != null) {
                result = enabled(parent, byId, memo, chain);
            }
        }
        chain.remove(category.getId());
        memo.put(category.getId(), result);
        return result;
    }

    /**
     * 补齐直接子分类数、直接物料数与可删除标记，供列表禁用删除按钮；
     * 服务端 delete() 仍做二次校验，前端禁用不能替代服务端规则。
     */
    private void decorate(List<ContractVOs.MaterialCategoryVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        Map<Long, Long> childCounts = scopedCategories().stream()
                .collect(Collectors.groupingBy(
                        c -> c.getParentId() == null ? ROOT_PARENT_KEY : c.getParentId(), Collectors.counting()));
        LambdaQueryWrapper<ContractMaterial> mqw = new LambdaQueryWrapper<ContractMaterial>()
                .select(ContractMaterial::getCategoryId);
        dataScopeService.applyOrgScope(mqw, ContractMaterial::getOrgCode);
        Map<Long, Long> materialCounts = materialMapper.selectList(mqw).stream()
                .collect(Collectors.groupingBy(
                        m -> m.getCategoryId() == null ? ROOT_PARENT_KEY : m.getCategoryId(), Collectors.counting()));
        for (ContractVOs.MaterialCategoryVO vo : vos) {
            long children = childCounts.getOrDefault(vo.getId(), 0L);
            long materials = materialCounts.getOrDefault(vo.getId(), 0L);
            vo.setChildCount(children);
            vo.setMaterialCount(materials);
            vo.setCanDelete(children == 0 && materials == 0);
        }
    }

    /** 带数据范围的平铺分类，按编码排序 */
    private List<ContractMaterialCategory> scopedCategories() {
        LambdaQueryWrapper<ContractMaterialCategory> qw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .orderByAsc(ContractMaterialCategory::getCode);
        dataScopeService.applyOrgScope(qw, ContractMaterialCategory::getOrgCode);
        return mapper.selectList(qw);
    }

    /** 把平铺节点按 parentId 拼成树，根节点为 parentId 为空的节点 */
    private List<ContractVOs.MaterialCategoryVO> buildTree(List<ContractVOs.MaterialCategoryVO> flat) {
        Map<Long, ContractVOs.MaterialCategoryVO> byId = flat.stream()
                .collect(Collectors.toMap(ContractVOs.MaterialCategoryVO::getId, Function.identity(), (a, b) -> a));
        List<ContractVOs.MaterialCategoryVO> roots = new ArrayList<>();
        for (ContractVOs.MaterialCategoryVO vo : flat) {
            ContractVOs.MaterialCategoryVO parent = vo.getParentId() == null ? null : byId.get(vo.getParentId());
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

    @Override
    public ContractVOs.MaterialCategoryVO detail(Long id) {
        ContractVOs.MaterialCategoryVO vo = toVO(require(id));
        decorate(List.of(vo));
        return vo;
    }

    @Override
    public ContractMaterialCategory require(Long id) {
        ContractMaterialCategory entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    @Override
    public ContractMaterialCategory requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        LambdaQueryWrapper<ContractMaterialCategory> qw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .eq(ContractMaterialCategory::getCode, code.trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractMaterialCategory::getOrgCode);
        ContractMaterialCategory entity = mapper.selectOne(qw);
        if (entity == null) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.MaterialCategoryVO save(ContractRequests.MaterialCategorySave req) {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类名称不能为空");
        }
        ContractMaterialCategory parent = req.getParentId() == null ? null : require(req.getParentId());
        if (parent != null && STATUS_DISABLED.equals(parent.getStatus())) {
            throw new BizException(ContractErrorCode.MATERIAL_STATUS_INVALID, "上级分类已停用，不能新增子分类");
        }
        int width = categoryWidth();
        int categoryLen = 3 * width;
        if (parent != null && (parent.getCode() == null || parent.getCode().length() >= categoryLen)) {
            throw new BizException(ContractErrorCode.MATERIAL_CODE_INVALID);
        }
        String code = nextCode(parent, width);
        if (code.isEmpty()) {
            throw new BizException(ContractErrorCode.MATERIAL_CODE_INVALID);
        }
        ContractMaterialCategory entity = new ContractMaterialCategory();
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setCode(code);
        entity.setName(req.getName().trim());
        entity.setStatus(STATUS_ENABLED);
        entity.setOrgCode(nvl(req.getOrgCode(), parent == null ? "" : parent.getOrgCode()));
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.MaterialCategorySave req) {
        ContractMaterialCategory entity = require(id);
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类名称不能为空");
        }
        entity.setName(req.getName().trim());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        String target = status == null ? "" : status.trim();
        if (!STATUS_ENABLED.equals(target) && !STATUS_DISABLED.equals(target)) {
            throw new BizException(ContractErrorCode.MATERIAL_STATUS_INVALID);
        }
        ContractMaterialCategory entity = require(id);
        entity.setStatus(target);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        LambdaQueryWrapper<ContractMaterialCategory> childQw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .eq(ContractMaterialCategory::getParentId, id);
        dataScopeService.applyOrgScope(childQw, ContractMaterialCategory::getOrgCode);
        Long children = mapper.selectCount(childQw);
        if (children != null && children > 0) {
            throw new BizException(ContractErrorCode.MATERIAL_HAS_CHILDREN);
        }
        LambdaQueryWrapper<ContractMaterial> materialQw = new LambdaQueryWrapper<ContractMaterial>()
                .eq(ContractMaterial::getCategoryId, id);
        dataScopeService.applyOrgScope(materialQw, ContractMaterial::getOrgCode);
        Long materials = materialMapper.selectCount(materialQw);
        if (materials != null && materials > 0) {
            throw new BizException(ContractErrorCode.MATERIAL_IN_USE);
        }
        mapper.deleteById(id);
    }

    @Override
    public ContractVOs.MaterialCategoryVO toVO(ContractMaterialCategory entity) {
        ContractVOs.MaterialCategoryVO vo = new ContractVOs.MaterialCategoryVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setStatus(entity.getStatus() == null ? STATUS_ENABLED : entity.getStatus());
        vo.setOrgCode(entity.getOrgCode());
        return vo;
    }

    /** 分类宽度：默认 2 位，埋点兼容旧口径（3 级 × 2 位） */
    private int categoryWidth() {
        int[] pattern = embedPointService.materialCodePattern();
        if (pattern != null && pattern.length >= 3 && pattern[0] == 3 && pattern[1] > 0
                && pattern[0] * pattern[1] == 6 && pattern[2] == 6) {
            return pattern[1];
        }
        return 2;
    }

    /** 按父级续位：一级取同级最大值 +1，子级 = 父编码 + 本级序号 */
    private String nextCode(ContractMaterialCategory parent, int width) {
        LambdaQueryWrapper<ContractMaterialCategory> qw = new LambdaQueryWrapper<ContractMaterialCategory>();
        if (parent == null) {
            qw.isNull(ContractMaterialCategory::getParentId);
        } else {
            qw.eq(ContractMaterialCategory::getParentId, parent.getId());
        }
        dataScopeService.applyOrgScope(qw, ContractMaterialCategory::getOrgCode);
        List<ContractMaterialCategory> siblings = mapper.selectList(qw);
        int offset = parent == null ? 0 : parent.getCode().length();
        int max = siblings.stream()
                .map(e -> parse(e.getCode(), offset))
                .max(Comparator.naturalOrder()).orElse(0);
        String prefix = parent == null ? "" : parent.getCode();
        return prefix + String.format("%0" + width + "d", max + 1);
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
