package com.laimengchao.sso.contract.module.contract.service.impl;

import com.laimengchao.sso.contract.common.impl.BizNoServiceImpl;
import com.laimengchao.sso.contract.module.contract.service.PurchasePlanService;
import com.laimengchao.sso.contract.module.contract.service.ContractItemService;
import com.laimengchao.sso.contract.module.contract.service.ContractService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.common.BizNoService;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.entity.ContractPurchasePlan;
import com.laimengchao.sso.contract.module.contract.entity.ContractPurchasePlanItem;
import com.laimengchao.sso.contract.module.contract.mapper.ContractMaterialMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPurchasePlanItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPurchasePlanMapper;
import com.laimengchao.sso.contract.module.contract.support.ContractAttachments;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 采购计划：审批通过后转采购合同，明细带入合同清单。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasePlanServiceImpl implements PurchasePlanService {

    private static final String APPROVED = "APPROVED";
    private static final String CONVERTED = "CONVERTED";

    private final ContractPurchasePlanMapper mapper;
    private final ContractPurchasePlanItemMapper itemMapper;
    private final ContractMaterialMapper materialMapper;
    private final BizNoService bizNoService;
    private final ContractService contractService;
    private final ContractItemService itemService;
    private final DataScopeService dataScopeService;
    private final FileApi fileApi;

    public PageResult<ContractVOs.PurchasePlanVO> page(String no, String status, String keyword,
                                                        int page, int size) {
        String kw = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<ContractPurchasePlan> qw = new LambdaQueryWrapper<ContractPurchasePlan>()
                .eq(no != null && !no.isBlank(), ContractPurchasePlan::getNo, (no == null ? null : no.trim()))
                .eq(status != null && !status.isBlank(), ContractPurchasePlan::getStatus, (status == null ? null : status.trim()))
                .and(!kw.isEmpty(), w -> w.like(ContractPurchasePlan::getTitle, kw)
                        .or().like(ContractPurchasePlan::getNo, kw)
                        .or().like(ContractPurchasePlan::getApplicant, kw))
                .orderByDesc(ContractPurchasePlan::getId);
        dataScopeService.applyOrgScope(qw, ContractPurchasePlan::getOrgCode);
        Page<ContractPurchasePlan> p = mapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), qw);
        return PageResult.of(p.getRecords().stream().map(this::toVO).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ContractVOs.PurchasePlanVO detail(Long id) {
        return toVO(require(id));
    }

    public ContractPurchasePlan require(Long id) {
        ContractPurchasePlan entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.PLAN_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.PurchasePlanVO save(ContractRequests.PurchasePlanSave req) {
        if (req == null || req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采购计划标题不能为空");
        }
        ContractPurchasePlan entity = new ContractPurchasePlan();
        entity.setNo(bizNoService.next(BizNoServiceImpl.TABLE_PURCHASE_PLAN));
        entity.setTitle(req.getTitle().trim());
        entity.setDept(nvl(req.getDept(), ""));
        entity.setApplicant(nvl(req.getApplicant(), ""));
        entity.setDate(req.getDate() == null ? java.time.LocalDate.now() : req.getDate());
        entity.setNeedDate(req.getNeedDate());
        entity.setStatus("DRAFT");
        entity.setOrgCode(nvl(req.getOrgCode(), ""));
        entity.setFileIds(ContractAttachments.join(req.getFileIds()));
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        if (req.getItems() != null) {
            for (ContractRequests.PurchasePlanItemSave itemReq : req.getItems()) {
                insertItem(entity, itemReq);
            }
        }
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.PurchasePlanSave req) {
        ContractPurchasePlan entity = require(id);
        if (CONVERTED.equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PLAN_STATUS_INVALID, "已转合同的采购计划禁止修改");
        }
        if (req == null || req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采购计划标题不能为空");
        }
        entity.setTitle(req.getTitle().trim());
        entity.setDept(nvl(req.getDept(), ""));
        entity.setApplicant(nvl(req.getApplicant(), ""));
        entity.setDate(req.getDate() == null ? entity.getDate() : req.getDate());
        entity.setNeedDate(req.getNeedDate());
        entity.setOrgCode(nvl(req.getOrgCode(), entity.getOrgCode()));
        if (req.getFileIds() != null) {
            entity.setFileIds(ContractAttachments.join(req.getFileIds()));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        if (req.getItems() != null) {
            itemMapper.delete(new LambdaQueryWrapper<ContractPurchasePlanItem>()
                    .eq(ContractPurchasePlanItem::getPlanNo, entity.getNo()));
            for (ContractRequests.PurchasePlanItemSave itemReq : req.getItems()) {
                insertItem(entity, itemReq);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ContractPurchasePlan entity = require(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PLAN_STATUS_INVALID);
        }
        entity.setStatus("PENDING");
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    /** 审批通过：DRAFT / PENDING → APPROVED，仅 APPROVED 允许转合同 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        ContractPurchasePlan entity = require(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"PENDING".equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PLAN_STATUS_INVALID);
        }
        entity.setStatus(APPROVED);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) {
        ContractPurchasePlan entity = require(id);
        if (!"PENDING".equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PLAN_STATUS_INVALID);
        }
        entity.setStatus("REJECTED");
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.ContractVO convert(Long id, ContractRequests.PlanConvertRequest req) {
        ContractPurchasePlan plan = require(id);
        if (!APPROVED.equals(plan.getStatus())) {
            throw new BizException(ContractErrorCode.PLAN_STATUS_INVALID, "仅已批准采购计划可转合同");
        }
        if (req == null || req.getPartnerCode() == null || req.getPartnerCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "转换时必须指定往来单位");
        }
        List<ContractPurchasePlanItem> planItems = itemsOf(plan.getNo());
        if (planItems.isEmpty()) {
            throw new BizException(ContractErrorCode.PLAN_STATUS_INVALID, "采购计划没有明细，不能转合同");
        }
        String orgCode = nvl(req.getOrgCode(), plan.getOrgCode());
        ContractHeader header = contractService.createForPlan(
                ContractServiceImpl.PURCHASE, plan.getTitle(), req.getPartnerCode().trim(),
                req.getProjectNo() == null ? null : req.getProjectNo().trim(),
                nvl(req.getDept(), plan.getDept()), nvl(req.getOwner(), plan.getApplicant()),
                nvl(req.getPaymentTerms(), ""), nvl(req.getSubject(), ""),
                orgCode, plan.getFileIds(), plan.getNo());
        for (ContractPurchasePlanItem source : planItems) {
            ContractRequests.ItemSave itemReq = new ContractRequests.ItemSave();
            itemReq.setMaterialCode(source.getMaterialCode());
            itemReq.setMaterialName(source.getMaterialName());
            itemReq.setSpec(source.getSpec());
            itemReq.setUnit(source.getUnit());
            itemReq.setQty(source.getQty());
            itemReq.setPrice(source.getPrice());
            itemReq.setAmount(source.getAmount());
            itemReq.setRemark("由采购计划 " + plan.getNo() + " 转入");
            itemService.save(header.getNo(), itemReq);
        }
        plan.setStatus(CONVERTED);
        plan.setContractNo(header.getNo());
        plan.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(plan);
        log.info("采购计划转合同：planNo={} contractNo={}", plan.getNo(), header.getNo());
        return contractService.detail(header.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractPurchasePlan entity = require(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PLAN_STATUS_INVALID, "仅草稿采购计划可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<ContractPurchasePlanItem>()
                .eq(ContractPurchasePlanItem::getPlanNo, entity.getNo()));
        mapper.deleteById(id);
    }

    private void insertItem(ContractPurchasePlan plan, ContractRequests.PurchasePlanItemSave req) {
        if (req == null || req.getMaterialCode() == null || req.getMaterialCode().isBlank()
                || req.getQty() == null || req.getPrice() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采购计划明细必须填写物料、数量与单价");
        }
        String code = req.getMaterialCode().trim();
        LambdaQueryWrapper<ContractMaterial> materialQw = new LambdaQueryWrapper<ContractMaterial>()
                .eq(ContractMaterial::getCode, code).last(" LIMIT 1");
        dataScopeService.applyOrgScope(materialQw, ContractMaterial::getOrgCode);
        ContractMaterial material = materialMapper.selectOne(materialQw);
        if (material == null) {
            throw new BizException(ContractErrorCode.MATERIAL_NOT_FOUND);
        }
        ContractPurchasePlanItem entity = new ContractPurchasePlanItem();
        entity.setPlanNo(plan.getNo());
        entity.setMaterialCode(code);
        entity.setMaterialName(material.getName());
        entity.setSpec(nvl(req.getSpec(), material.getSpec()));
        entity.setUnit(nvl(req.getUnit(), material.getUnit()));
        entity.setQty(req.getQty());
        entity.setPrice(req.getPrice());
        entity.setAmount(req.getQty().multiply(req.getPrice()));
        entity.setOrgCode(plan.getOrgCode());
        entity.setCreatedAt(LocalDateTime.now());
        itemMapper.insert(entity);
    }

    private List<ContractPurchasePlanItem> itemsOf(String planNo) {
        LambdaQueryWrapper<ContractPurchasePlanItem> qw = new LambdaQueryWrapper<ContractPurchasePlanItem>()
                .eq(ContractPurchasePlanItem::getPlanNo, planNo)
                .orderByAsc(ContractPurchasePlanItem::getId);
        dataScopeService.applyOrgScope(qw, ContractPurchasePlanItem::getOrgCode);
        return itemMapper.selectList(qw);
    }

    private ContractVOs.PurchasePlanVO toVO(ContractPurchasePlan e) {
        ContractVOs.PurchasePlanVO vo = new ContractVOs.PurchasePlanVO();
        vo.setId(e.getId());
        vo.setNo(e.getNo());
        vo.setTitle(e.getTitle());
        vo.setDept(e.getDept());
        vo.setApplicant(e.getApplicant());
        vo.setDate(e.getDate() == null ? null : e.getDate().toString());
        vo.setNeedDate(e.getNeedDate() == null ? null : e.getNeedDate().toString());
        vo.setStatus(e.getStatus());
        vo.setContractNo(e.getContractNo());
        vo.setOrgCode(e.getOrgCode());
        List<ContractPurchasePlanItem> items = itemsOf(e.getNo());
        BigDecimal amount = items.stream().map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setAmount(amount);
        vo.setItemCount(items.size());
        vo.setItems(new ArrayList<>(items.stream().map(this::toItemVO).toList()));
        List<String> fileIds = ContractAttachments.split(e.getFileIds());
        vo.setFileIds(fileIds);
        vo.setAttachmentUrls(ContractAttachments.signedUrls(fileIds, fileApi));
        return vo;
    }

    private ContractVOs.PurchasePlanItemVO toItemVO(ContractPurchasePlanItem e) {
        ContractVOs.PurchasePlanItemVO vo = new ContractVOs.PurchasePlanItemVO();
        vo.setId(e.getId());
        vo.setPlanNo(e.getPlanNo());
        vo.setMaterialCode(e.getMaterialCode());
        vo.setMaterialName(e.getMaterialName());
        vo.setSpec(e.getSpec());
        vo.setUnit(e.getUnit());
        vo.setQty(e.getQty());
        vo.setPrice(e.getPrice());
        vo.setAmount(e.getAmount());
        return vo;
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
