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
import com.laimengchao.sso.ehr.client.api.EhrOrgApi;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.contract.security.DataScopeService;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final EhrOrgApi ehrOrgApi;

    /**
     * 发起采购计划的默认申请人/需求部门。服务端按账号 ID 在 EHR 员工与组织树中反查，
     * 避免前端用 /api/auth/me 的雪花 ID 做本地匹配时丢精度导致部门静默为空。
     */
    @Override
    public ContractVOs.PurchasePlanProfileVO myProfile() {
        ContractVOs.PurchasePlanProfileVO vo = new ContractVOs.PurchasePlanProfileVO();
        Long accountId = AccountContext.getAccountId();
        if (accountId == null) {
            log.warn("采购计划默认部门解析失败：当前账号上下文为空");
            return vo;
        }
        String account = String.valueOf(accountId);
        Map<String, Object> emp = activeEmployees().stream()
                .filter(e -> account.equals(text(e.get("id"))))
                .findFirst().orElse(null);
        if (emp == null) {
            log.warn("采购计划默认部门解析失败：EHR 未找到账号 {} 对应的在职员工", account);
            return vo;
        }
        vo.setApplicant(text(emp.get("name")));
        String orgId = text(emp.get("orgId"));
        if (!StringUtils.hasText(orgId)) {
            log.warn("采购计划默认部门解析失败：账号 {} 对应员工未维护所属组织", account);
            return vo;
        }
        Map<String, Object> org = orgTree().stream()
                .filter(n -> orgId.equals(text(n.get("id"))))
                .findFirst().orElse(null);
        if (org == null) {
            log.warn("采购计划默认部门解析失败：EHR 组织 {} 不存在", orgId);
            return vo;
        }
        vo.setDept(text(org.get("name")));
        vo.setOrgCode(text(org.get("code")));
        return vo;
    }

    private List<Map<String, Object>> activeEmployees() {
        try {
            var resp = ehrOrgApi.activeEmployees();
            return resp != null && resp.ok() && resp.getData() != null ? resp.getData() : List.of();
        } catch (Exception e) {
            log.warn("采购计划默认申请人读取 EHR 在职员工失败：{}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> orgTree() {
        try {
            var resp = ehrOrgApi.tree();
            return resp != null && resp.ok() && resp.getData() != null ? resp.getData() : List.of();
        } catch (Exception e) {
            log.warn("采购计划默认部门读取 EHR 组织树失败：{}", e.getMessage());
            return List.of();
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

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
        // 申请人/需求部门以服务端 EHR 反查为准（审批按部门名称取负责人），前端传值仅在 EHR 不可用时兜底
        ContractVOs.PurchasePlanProfileVO profile = myProfile();
        ContractPurchasePlan entity = new ContractPurchasePlan();
        entity.setNo(bizNoService.next(BizNoServiceImpl.TABLE_PURCHASE_PLAN));
        entity.setTitle(req.getTitle().trim());
        entity.setDept(StringUtils.hasText(profile.getDept()) ? profile.getDept() : nvl(req.getDept(), ""));
        entity.setApplicant(StringUtils.hasText(profile.getApplicant()) ? profile.getApplicant() : nvl(req.getApplicant(), ""));
        entity.setDate(req.getDate() == null ? java.time.LocalDate.now() : req.getDate());
        entity.setNeedDate(req.getNeedDate());
        entity.setStatus("DRAFT");
        entity.setOrgCode(orgCodeForSave(profile, req.getOrgCode()));
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
        entity.setDept(nvl(req.getDept(), entity.getDept()));
        entity.setApplicant(nvl(req.getApplicant(), entity.getApplicant()));
        entity.setDate(req.getDate() == null ? entity.getDate() : req.getDate());
        entity.setNeedDate(req.getNeedDate());
        // 归属组织：已有值保持不变（编辑人不等于创建人时不漂移）；历史空值才按当前登录人部门补齐
        if (!StringUtils.hasText(entity.getOrgCode())) {
            entity.setOrgCode(orgCodeForSave(myProfile(), req.getOrgCode()));
        }
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
        if (req.getNote() != null && req.getNote().length() > 200) {
            throw new BizException(ErrorCode.PARAM_ERROR, "备注最多 200 字");
        }
        String orgCode = nvl(req.getOrgCode(), plan.getOrgCode());
        // 计划数据兜底：主信息与附件以用户在转合同抽屉填写为准，未填则沿用采购计划
        String fileIds = req.getFileIds() == null || req.getFileIds().isEmpty()
                ? plan.getFileIds() : ContractAttachments.join(req.getFileIds());
        ContractHeader header = contractService.createForPlan(
                ContractServiceImpl.PURCHASE, nvl(req.getName(), plan.getTitle()), req.getPartnerCode().trim(),
                req.getProjectNo() == null ? null : req.getProjectNo().trim(),
                nvl(req.getDept(), plan.getDept()), nvl(req.getOwner(), plan.getApplicant()),
                nvl(req.getPaymentTerms(), ""), nvl(req.getSubject(), ""),
                orgCode, fileIds, plan.getNo(),
                req.getSignedDate(), req.getEffectiveDate(), req.getEndDate(), nvl(req.getNote(), ""));
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

    /**
     * 明细跟随主表归属：调用方（detail / convert / toVO）均已通过 require(id) 或列表 applyOrgScope 校验主表数据权限，
     * 子表不再单独套 applyOrgScope，避免历史空 org_code 明细被过滤掉（详情明细、明细数、计划金额全变 0）。
     */
    private List<ContractPurchasePlanItem> itemsOf(String planNo) {
        LambdaQueryWrapper<ContractPurchasePlanItem> qw = new LambdaQueryWrapper<ContractPurchasePlanItem>()
                .eq(ContractPurchasePlanItem::getPlanNo, planNo)
                .orderByAsc(ContractPurchasePlanItem::getId);
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

    /**
     * 采购计划归属组织落库口径：当前登录人所在部门编码（服务端 EHR 反查）优先，
     * 其次前端传值，最后退回账号数据范围根；必须落非空，否则列表会被 applyOrgScope 过滤成空集。
     */
    private String orgCodeForSave(ContractVOs.PurchasePlanProfileVO profile, String requested) {
        if (StringUtils.hasText(profile.getOrgCode())) {
            return profile.getOrgCode();
        }
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return nvl(dataScopeService.currentScopeCode(), "");
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
