package com.laimengchao.sso.contract.module.contract.service.impl;

import com.laimengchao.sso.contract.module.contract.service.MaterialService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.common.EmbedPointService;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.entity.ContractPurchasePlanItem;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractMaterialMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPurchasePlanItemMapper;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 物料三级树：分类 01/0101/010101，物料 = 三级分类码 + 6 位流水；编码在 Service 续位。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private static final String CATEGORY = "CATEGORY";
    private static final String MATERIAL = "MATERIAL";

    private final ContractMaterialMapper mapper;
    private final ContractItemMapper itemMapper;
    private final ContractPurchasePlanItemMapper planItemMapper;
    private final EmbedPointService embedPointService;
    private final DataScopeService dataScopeService;

    /** 平铺列表：内部接口按 materialCode 取用 */
    public List<ContractVOs.MaterialVO> list() {
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>()
                .orderByAsc(ContractMaterial::getCode);
        dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
        return mapper.selectList(qw).stream().map(this::toVO).toList();
    }

    /** 树形列表：管理端物料库 */
    public List<ContractVOs.MaterialVO> tree() {
        List<ContractVOs.MaterialVO> flat = list();
        Map<Long, ContractVOs.MaterialVO> byId = new LinkedHashMap<>();
        for (ContractVOs.MaterialVO vo : flat) {
            byId.put(vo.getId(), vo);
        }
        List<ContractVOs.MaterialVO> roots = new ArrayList<>();
        for (ContractVOs.MaterialVO vo : flat) {
            ContractVOs.MaterialVO parent = vo.getParentId() == null ? null : byId.get(vo.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }

    public ContractVOs.MaterialVO detail(Long id) {
        return toVO(require(id));
    }

    public ContractMaterial require(Long id) {
        ContractMaterial entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    public ContractMaterial requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>()
                .eq(ContractMaterial::getCode, code.trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
        ContractMaterial entity = mapper.selectOne(qw);
        if (entity == null || !MATERIAL.equals(entity.getKind())) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.MaterialVO save(ContractRequests.MaterialSave req) {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "物料名称不能为空");
        }
        String kind = req.getKind() == null || req.getKind().isBlank() ? CATEGORY : req.getKind().trim();
        if (!CATEGORY.equals(kind) && !MATERIAL.equals(kind)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点类型只能为 CATEGORY 或 MATERIAL");
        }
        ContractMaterial parent = req.getParentId() == null ? null : require(req.getParentId());
        if (parent != null && !CATEGORY.equals(parent.getKind())) {
            throw new BizException(ContractErrorCode.MATERIAL_CODE_INVALID);
        }
        String code = nextCode(parent, kind);
        if (code.isEmpty()) {
            throw new BizException(ContractErrorCode.MATERIAL_CODE_INVALID);
        }
        ContractMaterial entity = new ContractMaterial();
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setCode(code);
        entity.setName(req.getName().trim());
        entity.setKind(kind);
        entity.setSpec(nvl(req.getSpec(), ""));
        entity.setUnit(nvl(req.getUnit(), ""));
        entity.setReferencePrice(req.getReferencePrice());
        entity.setOrgCode(nvl(req.getOrgCode(), parent == null ? "" : parent.getOrgCode()));
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.MaterialSave req) {
        ContractMaterial entity = require(id);
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "物料名称不能为空");
        }
        // 编码与父级保持不变，避免合同清单 / 采购计划中的引用断裂
        entity.setName(req.getName().trim());
        entity.setSpec(nvl(req.getSpec(), ""));
        entity.setUnit(nvl(req.getUnit(), ""));
        entity.setReferencePrice(req.getReferencePrice());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractMaterial entity = require(id);
        LambdaQueryWrapper<ContractMaterial> childrenQw = new LambdaQueryWrapper<ContractMaterial>()
                .eq(ContractMaterial::getParentId, id);
        dataScopeService.applyOrgScope(childrenQw, ContractMaterial::getOrgCode);
        Long children = mapper.selectCount(childrenQw);
        if (children != null && children > 0) {
            throw new BizException(ContractErrorCode.MATERIAL_HAS_CHILDREN);
        }
        if (MATERIAL.equals(entity.getKind())) {
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
        }
        mapper.deleteById(id);
    }

    /** 按父节点续位生成编码：分类逐级补位、末级分类码 + 流水。位数取埋点 contract.material.code-pattern */
    private String nextCode(ContractMaterial parent, String kind) {
        // 默认 3 级 × 2 位分类码 + 6 位流水。埋点参数只有在「分类码总长=存量 6 位、流水=6 位、
        // 层级=3」时才采用——否则改变位数会与存量编码冲突，记 WARN 后回退默认，保护历史数据。
        int width = 2;
        int serial = 6;
        int[] pattern = embedPointService.materialCodePattern();
        if (pattern != null) {
            int levels = pattern[0];
            int categoryLen = levels > 0 ? levels * pattern[1] : -1;
            if (levels == 3 && categoryLen == 6 && pattern[1] > 0 && pattern[2] == 6) {
                width = pattern[1];
                serial = pattern[2];
            } else {
                log.warn("物料编码埋点 contract.material.code-pattern={} 与存量编码结构（3级×2位+6位流水）不兼容，已回退默认；如需变更请先做编码迁移",
                        java.util.Arrays.toString(pattern));
            }
        }
        int categoryLen = 3 * width;
        LambdaQueryWrapper<ContractMaterial> qw = new LambdaQueryWrapper<ContractMaterial>();
        if (parent == null) {
            qw.isNull(ContractMaterial::getParentId).eq(ContractMaterial::getKind, CATEGORY);
        } else {
            qw.eq(ContractMaterial::getParentId, parent.getId()).eq(ContractMaterial::getKind, kind);
        }
        dataScopeService.applyOrgScope(qw, ContractMaterial::getOrgCode);
        List<ContractMaterial> siblings = mapper.selectList(qw);
        if (CATEGORY.equals(kind)) {
            if (parent == null) {
                int max = siblings.stream().map(e -> parse(e.getCode(), 0)).max(Comparator.naturalOrder()).orElse(0);
                return pad(max + 1, width);
            }
            if (parent.getCode() == null || parent.getCode().length() >= categoryLen) {
                return "";
            }
            int max = siblings.stream().map(e -> parse(e.getCode(), parent.getCode().length()))
                    .max(Comparator.naturalOrder()).orElse(0);
            return parent.getCode() + pad(max + 1, width);
        }
        if (parent == null || parent.getCode() == null || parent.getCode().length() != categoryLen) {
            return "";
        }
        int max = siblings.stream().map(e -> parse(e.getCode(), categoryLen)).max(Comparator.naturalOrder()).orElse(0);
        return parent.getCode() + pad(max + 1, serial);
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

    private String pad(int value, int width) {
        return String.format("%0" + width + "d", value);
    }

    public ContractVOs.MaterialVO toVO(ContractMaterial e) {
        ContractVOs.MaterialVO vo = new ContractVOs.MaterialVO();
        vo.setId(e.getId());
        vo.setParentId(e.getParentId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setKind(e.getKind());
        vo.setSpec(e.getSpec());
        vo.setUnit(e.getUnit());
        vo.setReferencePrice(e.getReferencePrice());
        vo.setOrgCode(e.getOrgCode());
        return vo;
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
