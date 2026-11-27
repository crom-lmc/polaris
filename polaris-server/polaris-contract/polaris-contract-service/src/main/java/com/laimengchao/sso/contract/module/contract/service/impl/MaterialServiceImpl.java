package com.laimengchao.sso.contract.module.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 物料：独立表，category_id 指向三级分类；编码 = 三级分类码 + 6 位流水。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    public static final String CATEGORY = "CATEGORY";
    public static final String MATERIAL = "MATERIAL";

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
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>()
                .orderByAsc(ContractMaterial::getCode);
        if (categoryId == null) {
            qw.isNull(ContractMaterial::getCategoryId);
        } else {
            qw.eq(ContractMaterial::getCategoryId, categoryId);
        }
        dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
        return mapper.selectList(qw).stream().map(this::toVO).toList();
    }

    /**
     * 分类 + 物料拼合树：仅供 internal 接口兼容，管理端已改分类树 + 物料列表。
     * 物料节点 parentId 指向所属三级分类，kind=MATERIAL。
     */
    @Override
    public List<ContractVOs.MaterialVO> tree() {
        List<ContractVOs.MaterialVO> flat = new ArrayList<>();
        LambdaQueryWrapper<ContractMaterialCategory> cqw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .orderByAsc(ContractMaterialCategory::getCode);
        dataScopeService.applyOrgScope(cqw, ContractMaterialCategory::getOrgCode);
        for (ContractMaterialCategory c : categoryMapper.selectList(cqw)) {
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
