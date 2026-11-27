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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 物料三级分类：分类独立表，编码 01/0101/010101，服务端按父级续位。 */
@Service
@RequiredArgsConstructor
public class MaterialCategoryServiceImpl implements MaterialCategoryService {

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
        return buildTree(flat);
    }

    @Override
    public List<ContractVOs.MaterialCategoryVO> tree() {
        return list();
    }

    @Override
    public List<ContractVOs.MaterialCategoryVO> children(Long parentId) {
        LambdaQueryWrapper<ContractMaterialCategory> qw = new LambdaQueryWrapper<ContractMaterialCategory>()
                .orderByAsc(ContractMaterialCategory::getCode);
        if (parentId == null) {
            qw.isNull(ContractMaterialCategory::getParentId);
        } else {
            qw.eq(ContractMaterialCategory::getParentId, parentId);
        }
        dataScopeService.applyOrgScope(qw, ContractMaterialCategory::getOrgCode);
        return mapper.selectList(qw).stream().map(this::toVO).toList();
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
        return toVO(require(id));
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
