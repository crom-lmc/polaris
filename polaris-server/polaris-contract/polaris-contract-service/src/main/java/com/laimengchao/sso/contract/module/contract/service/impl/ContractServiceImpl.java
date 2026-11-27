package com.laimengchao.sso.contract.module.contract.service.impl;

import com.laimengchao.sso.contract.common.impl.BizNoServiceImpl;
import com.laimengchao.sso.contract.module.contract.service.ContractService;
import com.laimengchao.sso.contract.module.contract.service.ProjectService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.common.BizNoService;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.common.EmbedPointService;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractChangeLog;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentPlan;
import com.laimengchao.sso.contract.module.contract.entity.ContractPartner;
import com.laimengchao.sso.contract.module.contract.entity.ContractProject;
import com.laimengchao.sso.contract.module.contract.mapper.ContractChangeLogMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractHeaderMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPaymentPlanMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPartnerMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractProjectMapper;
import com.laimengchao.sso.contract.module.contract.support.ContractApprovalLauncher;
import com.laimengchao.sso.contract.module.contract.support.ContractAttachments;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.contract.security.DataScopeService;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 合同台账：采购 / 销售共用表，金额从清单派生，统计从付款计划 + 单据状态派生。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    public static final String PURCHASE = "PURCHASE";
    public static final String SALES = "SALES";

    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "PENDING", "EFFECTIVE", "EXECUTING", "COMPLETED", "TERMINATED");

    /** 手工按钮允许的状态迁移；审批回调直接走 handleApprovalResult，不经过这里。 */
    private static final Map<String, Set<String>> MANUAL_TRANSITIONS = Map.of(
            "DRAFT", Set.of("PENDING"),
            "PENDING", Set.of("EFFECTIVE", "DRAFT"),
            "EFFECTIVE", Set.of("EXECUTING", "TERMINATED"),
            "EXECUTING", Set.of("COMPLETED", "TERMINATED"),
            "COMPLETED", Set.of(),
            "TERMINATED", Set.of());
    private static final Set<String> PAID_STATUS = Set.of("PAID", "POSTED");
    private static final Set<String> IN_FLIGHT_STATUS = Set.of("PENDING", "APPROVED");

    private final ContractHeaderMapper mapper;
    private final ContractChangeLogMapper changeLogMapper;
    private final ContractItemMapper itemMapper;
    private final ContractPaymentPlanMapper planMapper;
    private final ContractPartnerMapper partnerMapper;
    private final ContractProjectMapper projectMapper;
    private final BizNoService bizNoService;
    private final ProjectService projectService;
    private final EmbedPointService embedPointService;
    private final ContractApprovalLauncher approvalLauncher;
    private final DataScopeService dataScopeService;
    private final FileApi fileApi;

    /** 采购合同审批 bizType；超升级线用 senior 模板多一级审批（同 EHR 招聘双 bizType 模式） */
    public static final String BIZ_CONTRACT_PURCHASE = "contract.purchase";
    public static final String BIZ_CONTRACT_PURCHASE_SENIOR = "contract.purchase.senior";

    @Value("${contract.internal-base-url:http://127.0.0.1:9015}")
    private String internalBaseUrl;

    /** 列表与 internal 列表共用同一套 wrapper 构造 */
    public PageResult<ContractVOs.ContractVO> page(String type, String status, String keyword,
                                                    String projectNo, String partnerCode, int page, int size) {
        Page<ContractHeader> p = mapper.selectPage(
                new Page<>(Math.max(page, 1), Math.max(size, 1)),
                query(type, status, keyword, projectNo, partnerCode));
        List<ContractVOs.ContractVO> vos = decorate(p.getRecords());
        return PageResult.of(vos, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** internal 只读：不分页，返回同一份 VO（字段与 contract-client 对齐） */
    public List<ContractVOs.ContractVO> list(String type, String status, String projectNo) {
        LambdaQueryWrapper<ContractHeader> qw = query(type, status, null, projectNo, null);
        qw.orderByDesc(ContractHeader::getId);
        return decorate(mapper.selectList(qw));
    }

    /** 财务凭证选择合同用：internal Feign 不带 X-Account-Id，改用显式账号解析范围 */
    @Override
    public List<ContractVOs.ContractVO> optionsForAccount(Long accountId) {
        String scopeCode = accountId == null ? null : dataScopeService.scopeCodeOf(accountId);
        LambdaQueryWrapper<ContractHeader> qw = new LambdaQueryWrapper<ContractHeader>()
                .orderByDesc(ContractHeader::getId);
        dataScopeService.applyOrgScope(qw, ContractHeader::getOrgCode, scopeCode);
        // 下拉只要编号与名称，纯字段映射即可；decorate 会按当前请求上下文查子表，internal 调用无意义
        return mapper.selectList(qw).stream().map(this::toVO).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<ContractHeader> query(String type, String status, String keyword,
                                                      String projectNo, String partnerCode) {
        String kw = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<ContractHeader> qw = new LambdaQueryWrapper<ContractHeader>()
                .eq(type != null && !type.isBlank(), ContractHeader::getType, (type == null ? null : type.trim()))
                .eq(status != null && !status.isBlank(), ContractHeader::getStatus, (status == null ? null : status.trim()))
                .eq(projectNo != null && !projectNo.isBlank(), ContractHeader::getProjectNo, (projectNo == null ? null : projectNo.trim()))
                .eq(partnerCode != null && !partnerCode.isBlank(), ContractHeader::getPartnerCode, (partnerCode == null ? null : partnerCode.trim()))
                .and(!kw.isEmpty(), w -> w.like(ContractHeader::getNo, kw)
                        .or().like(ContractHeader::getName, kw)
                        .or().like(ContractHeader::getOwner, kw)
                        .or().like(ContractHeader::getPartnerCode, kw))
                .orderByDesc(ContractHeader::getId);
        dataScopeService.applyOrgScope(qw, ContractHeader::getOrgCode);
        return qw;
    }

    public ContractVOs.ContractVO detail(Long id) {
        ContractHeader header = require(id);
        return decorate(List.of(header)).get(0);
    }

    public ContractVOs.ContractVO getByNo(String no) {
        if (no == null || no.isBlank()) {
            throw new BizException(ContractErrorCode.CONTRACT_NOT_FOUND);
        }
        LambdaQueryWrapper<ContractHeader> qw = new LambdaQueryWrapper<ContractHeader>()
                .eq(ContractHeader::getNo, no.trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractHeader::getOrgCode);
        ContractHeader header = mapper.selectOne(qw);
        if (header == null) {
            throw new BizException(ContractErrorCode.CONTRACT_NOT_FOUND);
        }
        return decorate(List.of(header)).get(0);
    }

    public ContractHeader require(Long id) {
        ContractHeader entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.CONTRACT_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    public ContractHeader requireByNo(String no) {
        if (no == null || no.isBlank()) {
            throw new BizException(ContractErrorCode.CONTRACT_NOT_FOUND);
        }
        LambdaQueryWrapper<ContractHeader> qw = new LambdaQueryWrapper<ContractHeader>()
                .eq(ContractHeader::getNo, no.trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractHeader::getOrgCode);
        ContractHeader entity = mapper.selectOne(qw);
        if (entity == null) {
            throw new BizException(ContractErrorCode.CONTRACT_NOT_FOUND);
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.ContractVO save(ContractRequests.ContractSave req) {
        // 合同主信息除备注外全部必填（与设计稿一致）；前端选择负责人后自动带出归属部门
        if (req == null || !hasText(req.getName()) || !hasText(req.getPartnerCode())
                || !hasText(req.getProjectNo()) || !hasText(req.getOwner())
                || !hasText(req.getDept()) || req.getSignedDate() == null
                || req.getEffectiveDate() == null || req.getEndDate() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "合同名称、往来单位、所属项目、归属部门、负责人、签订/生效/到期日期均为必填");
        }
        String type = nvl(req.getType(), PURCHASE);
        if (!PURCHASE.equals(type) && !SALES.equals(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "合同类型只能为 PURCHASE 或 SALES");
        }
        if (req.getNote() != null && req.getNote().length() > 200) {
            throw new BizException(ErrorCode.PARAM_ERROR, "备注最多 200 字");
        }
        LambdaQueryWrapper<ContractPartner> partnerQw = new LambdaQueryWrapper<ContractPartner>()
                .eq(ContractPartner::getCode, req.getPartnerCode().trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(partnerQw, ContractPartner::getOrgCode);
        ContractPartner partner = partnerMapper.selectOne(partnerQw);
        if (partner == null) {
            throw new BizException(ContractErrorCode.PARTNER_NOT_FOUND);
        }
        // 采购合同只能选供应商/两者，销售合同只能选客户/两者
        String expectRole = PURCHASE.equals(type) ? "SUPPLIER" : "CUSTOMER";
        String partnerRole = nvl(partner.getRole(), "");
        if (!expectRole.equals(partnerRole) && !"BOTH".equals(partnerRole)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    (PURCHASE.equals(type) ? "采购合同" : "销售合同") + "的往来单位角色不匹配");
        }
        String orgCode = nvl(req.getOrgCode(), partner.getOrgCode());
        String projectNo = req.getProjectNo().trim();
        ContractProject project = projectService.requireUsable(projectNo);
        orgCode = project.getOrgCode();
        dataScopeService.assertCanAccessOrg(orgCode);
        ContractHeader entity = new ContractHeader();
        entity.setNo(bizNoService.next(PURCHASE.equals(type)
                ? BizNoServiceImpl.TABLE_CONTRACT_PURCHASE : BizNoServiceImpl.TABLE_CONTRACT_SALES));
        apply(entity, req, type, projectNo, orgCode);
        // 新建合同只能是草稿；状态后续由审批/执行动作推进，不接受请求体传入
        entity.setStatus("DRAFT");
        entity.setFileIds(ContractAttachments.join(req.getFileIds()));
        entity.setContractSource("DIRECT");
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return decorate(List.of(entity)).get(0);
    }

    /** 采购计划转合同复用：直接落库并返回实体，清单由调用方写入 */
    @Transactional(rollbackFor = Exception.class)
    public ContractHeader createForPlan(String type, String name, String partnerCode, String projectNo,
                                        String dept, String owner, String paymentTerms, String subject,
                                        String orgCode, String fileIds, String planNo) {
        ContractHeader entity = new ContractHeader();
        entity.setNo(bizNoService.next(PURCHASE.equals(type)
                ? BizNoServiceImpl.TABLE_CONTRACT_PURCHASE : BizNoServiceImpl.TABLE_CONTRACT_SALES));
        entity.setType(type);
        entity.setName(name);
        entity.setPartnerCode(partnerCode);
        entity.setProjectNo(projectNo);
        entity.setDept(dept);
        entity.setOwner(owner);
        entity.setSignedDate(LocalDateTime.now().toLocalDate());
        entity.setPaymentTerms(paymentTerms);
        entity.setSubject(subject);
        entity.setContractSource("FROM_PLAN");
        entity.setPlanNo(planNo);
        entity.setOrgCode(orgCode);
        entity.setFileIds(fileIds == null ? "" : fileIds);
        entity.setStatus("DRAFT");
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.ContractSave req) {
        ContractHeader entity = require(id);
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "合同名称不能为空");
        }
        String before = changeSnapshot(entity);
        String projectNo = req.getProjectNo() == null || req.getProjectNo().isBlank()
                ? null : req.getProjectNo().trim();
        if (projectNo != null && !projectNo.equals(entity.getProjectNo())) {
            projectService.requireUsable(projectNo);
        }
        apply(entity, req, nvl(req.getType(), entity.getType()), projectNo, nvl(req.getOrgCode(), entity.getOrgCode()));
        String after = changeSnapshot(entity);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        // 关键字段有实质变化才留痕并计入变更次数（草稿阶段修改不算合同变更）
        if (!before.equals(after) && !"DRAFT".equals(entity.getStatus())) {
            recordChange(entity, before, after, nvl(req.getRemark(), ""));
        }
    }

    /** 变更留痕：次数上限由埋点 contract.change.max-times 控制（<0 不限）；单号走 settings 规则 */
    private void recordChange(ContractHeader entity, String before, String after, String reason) {
        int maxTimes = embedPointService.changeMaxTimes();
        Long used = changeLogMapper.selectCount(new LambdaQueryWrapper<ContractChangeLog>()
                .eq(ContractChangeLog::getContractNo, entity.getNo()));
        if (maxTimes >= 0 && used != null && used >= maxTimes) {
            throw new BizException(ContractErrorCode.CHANGE_TIMES_EXCEEDED,
                    "合同 " + entity.getNo() + " 已变更 " + used + " 次，达到上限 " + maxTimes + " 次");
        }
        ContractChangeLog log = new ContractChangeLog();
        log.setNo(bizNoService.next(BizNoServiceImpl.TABLE_CONTRACT_CHANGE));
        log.setContractNo(entity.getNo());
        log.setChangeType("OTHER");
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setReason(reason);
        log.setOperator(currentUser());
        log.setOrgCode(entity.getOrgCode());
        log.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(log);
    }

    /** 关键业务字段快照，用于判断是否发生实质变更及前后对比留痕 */
    private String changeSnapshot(ContractHeader e) {
        return String.join("|",
                nvl(e.getName(), ""), nvl(e.getPartnerCode(), ""), nvl(e.getProjectNo(), ""),
                nvl(e.getDept(), ""), nvl(e.getOwner(), ""),
                String.valueOf(e.getSignedDate()), String.valueOf(e.getEffectiveDate()),
                String.valueOf(e.getEndDate()), nvl(e.getPaymentTerms(), ""), nvl(e.getSubject(), ""));
    }

    private String currentUser() {
        try {
            var info = com.laimengchao.sso.utils.AccountContext.get();
            return info == null ? "" : (info.getUsername() == null ? "" : info.getUsername());
        } catch (Exception e) {
            return "";
        }
    }

    /** 合同变更记录查询（按合同号，数据范围收口） */
    public List<ContractVOs.ChangeLogVO> changeLogs(Long contractId) {
        ContractHeader header = require(contractId);
        LambdaQueryWrapper<ContractChangeLog> qw = new LambdaQueryWrapper<ContractChangeLog>()
                .eq(ContractChangeLog::getContractNo, header.getNo())
                .orderByDesc(ContractChangeLog::getId);
        dataScopeService.applyOrgScope(qw, ContractChangeLog::getOrgCode);
        return changeLogMapper.selectList(qw).stream().map(l -> {
            ContractVOs.ChangeLogVO vo = new ContractVOs.ChangeLogVO();
            vo.setId(l.getId());
            vo.setNo(l.getNo());
            vo.setChangeType(l.getChangeType());
            vo.setBeforeValue(l.getBeforeValue());
            vo.setAfterValue(l.getAfterValue());
            vo.setReason(l.getReason());
            vo.setOperator(l.getOperator());
            vo.setCreatedAt(l.getCreatedAt() == null ? null : l.getCreatedAt().toString());
            return vo;
        }).toList();
    }

    /**
     * 状态流转（按钮驱动，目标必须命中状态机白名单）。采购合同提交时清单金额达到埋点
     * contract.purchase.approval-amount 才进审批中心等回调；未达线直接 EFFECTIVE。
     */
    public void changeStatus(Long id, String status) {
        String target = status == null ? null : status.trim();
        if (target == null || !STATUSES.contains(target)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID);
        }
        ContractHeader entity = require(id);
        assertManualTransition(entity, target);
        // 销售合同提交审批前做信用额度校验（埋点开启才生效，credit_limit=0 表示不限制）
        if ("PENDING".equals(target) && SALES.equals(entity.getType())) {
            requireCreditOk(entity);
        }
        boolean needApproval = "PENDING".equals(target) && PURCHASE.equals(entity.getType())
                && contractAmount(entity.getNo()).compareTo(
                        BigDecimal.valueOf(embedPointService.purchaseApprovalAmount())) >= 0;
        // 采购合同金额未达审批线时无需审批中心，提交即生效；达线才停在 PENDING 等回调
        String finalStatus = "PENDING".equals(target) && PURCHASE.equals(entity.getType()) && !needApproval
                ? "EFFECTIVE" : target;
        entity.setStatus(finalStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);

        if (needApproval) {
            approvalLauncher.launch(() -> buildApprovalStart(entity));
        }
    }

    /** 手工状态流转必须命中当前状态允许的目标；采购合同审批中只能由审批回调推进。 */
    private void assertManualTransition(ContractHeader entity, String target) {
        String current = entity.getStatus();
        Set<String> allowed = MANUAL_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID,
                    "合同状态不支持从 " + current + " 改为 " + target);
        }
        if (PURCHASE.equals(entity.getType()) && "PENDING".equals(current)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID,
                    "采购合同审批中状态由审批中心回调更新，不能手工修改");
        }
    }

    /**
     * 销售合同信用额度校验：埋点开启且客户授信额度 > 0 时，
     * 校验「已占用额度 + 本合同清单金额」不超过授信额度。
     * credit_limit=0 表示不限制（保护存量客户）；埋点关闭完全不查库。
     */
    private void requireCreditOk(ContractHeader entity) {
        if (!embedPointService.salesCreditCheck()) {
            return;
        }
        if (entity.getPartnerCode() == null || entity.getPartnerCode().isBlank()) {
            return;
        }
        ContractPartner partner = partnerMapper.selectOne(new LambdaQueryWrapper<ContractPartner>()
                .eq(ContractPartner::getCode, entity.getPartnerCode().trim()).last(" LIMIT 1"));
        if (partner == null || partner.getCreditLimit() == null
                || partner.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal used = partner.getCreditUsed() == null ? BigDecimal.ZERO : partner.getCreditUsed();
        BigDecimal amount = contractAmount(entity.getNo());
        if (used.add(amount).compareTo(partner.getCreditLimit()) > 0) {
            log.warn("销售合同信用校验拦截：contract={} partner={} used={} amount={} limit={}",
                    entity.getNo(), partner.getCode(), used, amount, partner.getCreditLimit());
            throw new BizException(ContractErrorCode.CREDIT_LIMIT_EXCEEDED,
                    "客户 " + partner.getName() + " 授信 " + partner.getCreditLimit()
                            + " 元，已占用 " + used + " 元，本合同 " + amount + " 元");
        }
    }

    /** 合同清单金额合计（与 decorate 同口径） */
    private BigDecimal contractAmount(String contractNo) {
        java.util.List<ContractItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ContractItem>().eq(ContractItem::getContractNo, contractNo));
        return items == null ? BigDecimal.ZERO : items.stream()
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 构造采购合同审批发起请求 */
    private ApprovalStartDTO buildApprovalStart(ContractHeader entity) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        boolean senior = contractAmount(entity.getNo()).compareTo(
                BigDecimal.valueOf(embedPointService.purchaseApprovalAmount())) > 0;
        dto.setBizType(senior ? BIZ_CONTRACT_PURCHASE_SENIOR : BIZ_CONTRACT_PURCHASE);
        dto.setBizId(String.valueOf(entity.getId()));
        dto.setBizNo(entity.getNo());
        dto.setTitle("采购合同 " + entity.getNo() + " " + entity.getName());
        dto.setSubjectId(String.valueOf(entity.getId()));
        dto.setBizIdKind("DOC");
        java.util.Map<String, Object> form = new java.util.LinkedHashMap<>();
        form.put("contractId", String.valueOf(entity.getId()));
        form.put("no", entity.getNo());
        form.put("name", entity.getName());
        form.put("amount", contractAmount(entity.getNo()));
        form.put("dept", entity.getDept());
        form.put("partnerCode", entity.getPartnerCode());
        dto.setFormData(form);
        dto.setCallbackUrl(internalBaseUrl + "/internal/contract/approval-callback");
        return dto;
    }

    /**
     * 审批中心终审回调：通过 → EFFECTIVE，驳回/撤回/终止 → DRAFT（可重新编辑提交）。
     * 幂等：仅 PENDING 才更新，重复投递返回 false。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handleApprovalResult(String bizId, Boolean approved, Integer result) {
        Long id;
        try {
            id = Long.valueOf(bizId);
        } catch (NumberFormatException e) {
            log.warn("合同审批回调 bizId 非数字：{}", bizId);
            return false;
        }
        ContractHeader entity = mapper.selectById(id);
        if (entity == null || !"PENDING".equals(entity.getStatus())) {
            log.info("合同审批回调忽略：id={} status={}", id, entity == null ? "deleted" : entity.getStatus());
            return false;
        }
        boolean pass = Boolean.TRUE.equals(approved) || Integer.valueOf(1).equals(result);
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ContractHeader>()
                .eq(ContractHeader::getId, id).eq(ContractHeader::getStatus, "PENDING")
                .set(ContractHeader::getStatus, pass ? "EFFECTIVE" : "DRAFT"));
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractHeader entity = require(id);
        Long items = itemMapper.selectCount(new LambdaQueryWrapper<ContractItem>()
                .eq(ContractItem::getContractNo, entity.getNo()));
        Long plans = planMapper.selectCount(new LambdaQueryWrapper<ContractPaymentPlan>()
                .eq(ContractPaymentPlan::getContractNo, entity.getNo()));
        if ((items != null && items > 0) || (plans != null && plans > 0)) {
            throw new BizException(ContractErrorCode.CONTRACT_STATUS_INVALID, "合同已有清单或付款计划，禁止删除");
        }
        mapper.deleteById(id);
    }

    public ContractVOs.ContractStatsVO stats(String type) {
        LambdaQueryWrapper<ContractHeader> qw = new LambdaQueryWrapper<ContractHeader>()
                .eq(type != null && !type.isBlank(), ContractHeader::getType, type == null ? null : type.trim());
        dataScopeService.applyOrgScope(qw, ContractHeader::getOrgCode);
        List<ContractHeader> headers = mapper.selectList(qw);
        ContractVOs.ContractStatsVO vo = new ContractVOs.ContractStatsVO();
        vo.setTotal(headers.size());
        if (headers.isEmpty()) {
            vo.setAmount(BigDecimal.ZERO);
            vo.setPaidAmount(BigDecimal.ZERO);
            vo.setInFlightAmount(BigDecimal.ZERO);
            vo.setPendingAmount(BigDecimal.ZERO);
            return vo;
        }
        List<ContractVOs.ContractVO> decorated = decorate(headers);
        vo.setAmount(decorated.stream().map(ContractVOs.ContractVO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setPaidAmount(decorated.stream().map(ContractVOs.ContractVO::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setInFlightAmount(decorated.stream().map(ContractVOs.ContractVO::getInFlightAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setPendingAmount(decorated.stream().map(ContractVOs.ContractVO::getPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return vo;
    }

    /** 批量派生金额与统计；子表查询同样带数据范围 */
    public List<ContractVOs.ContractVO> decorate(List<ContractHeader> headers) {
        if (headers.isEmpty()) {
            return List.of();
        }
        Set<String> nos = headers.stream().map(ContractHeader::getNo).collect(Collectors.toSet());
        Map<String, BigDecimal> amountByNo = new LinkedHashMap<>();
        Map<String, Integer> countByNo = new LinkedHashMap<>();
        LambdaQueryWrapper<ContractItem> iw = new LambdaQueryWrapper<ContractItem>()
                .in(ContractItem::getContractNo, nos);
        dataScopeService.applyOrgScope(iw, ContractItem::getOrgCode);
        for (ContractItem item : itemMapper.selectList(iw)) {
            BigDecimal amount = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
            amountByNo.merge(item.getContractNo(), amount, BigDecimal::add);
            countByNo.merge(item.getContractNo(), 1, Integer::sum);
        }
        Map<String, BigDecimal> paidByNo = new LinkedHashMap<>();
        Map<String, BigDecimal> inFlightByNo = new LinkedHashMap<>();
        Map<String, Integer> planCountByNo = new LinkedHashMap<>();
        LambdaQueryWrapper<ContractPaymentPlan> pw = new LambdaQueryWrapper<ContractPaymentPlan>()
                .in(ContractPaymentPlan::getContractNo, nos);
        dataScopeService.applyOrgScope(pw, ContractPaymentPlan::getOrgCode);
        for (ContractPaymentPlan plan : planMapper.selectList(pw)) {
            BigDecimal amount = plan.getAmount() == null ? BigDecimal.ZERO : plan.getAmount();
            planCountByNo.merge(plan.getContractNo(), 1, Integer::sum);
            if (PAID_STATUS.contains(plan.getStatus())) {
                paidByNo.merge(plan.getContractNo(), amount, BigDecimal::add);
            } else if (IN_FLIGHT_STATUS.contains(plan.getStatus())) {
                inFlightByNo.merge(plan.getContractNo(), amount, BigDecimal::add);
            }
        }
        Set<String> partnerCodes = headers.stream().map(ContractHeader::getPartnerCode)
                .filter(c -> c != null && !c.isBlank()).collect(Collectors.toSet());
        Map<String, String> partnerNames = new LinkedHashMap<>();
        if (!partnerCodes.isEmpty()) {
            LambdaQueryWrapper<ContractPartner> qw = new LambdaQueryWrapper<ContractPartner>()
                    .in(ContractPartner::getCode, partnerCodes);
            dataScopeService.applyOrgScope(qw, ContractPartner::getOrgCode);
            for (ContractPartner p : partnerMapper.selectList(qw)) {
                partnerNames.put(p.getCode(), p.getName());
            }
        }
        Map<String, Integer> changeCountByNo = new LinkedHashMap<>();
        LambdaQueryWrapper<ContractChangeLog> cw = new LambdaQueryWrapper<ContractChangeLog>()
                .in(ContractChangeLog::getContractNo, nos)
                .select(ContractChangeLog::getContractNo);
        dataScopeService.applyOrgScope(cw, ContractChangeLog::getOrgCode);
        for (ContractChangeLog c : changeLogMapper.selectList(cw)) {
            changeCountByNo.merge(c.getContractNo(), 1, Integer::sum);
        }
        Set<String> projectNos = headers.stream().map(ContractHeader::getProjectNo)
                .filter(c -> c != null && !c.isBlank()).collect(Collectors.toSet());
        Map<String, String> projectNames = new LinkedHashMap<>();
        if (!projectNos.isEmpty()) {
            LambdaQueryWrapper<ContractProject> qw = new LambdaQueryWrapper<ContractProject>()
                    .in(ContractProject::getCode, projectNos);
            dataScopeService.applyOrgScope(qw, ContractProject::getOrgCode);
            for (ContractProject p : projectMapper.selectList(qw)) {
                projectNames.put(p.getCode(), p.getName());
            }
        }
        return headers.stream().map(h -> {
            ContractVOs.ContractVO vo = toVO(h);
            BigDecimal amount = amountByNo.getOrDefault(h.getNo(), BigDecimal.ZERO);
            BigDecimal paid = paidByNo.getOrDefault(h.getNo(), BigDecimal.ZERO);
            BigDecimal inFlight = inFlightByNo.getOrDefault(h.getNo(), BigDecimal.ZERO);
            BigDecimal pending = amount.subtract(paid).subtract(inFlight).max(BigDecimal.ZERO);
            vo.setAmount(amount);
            vo.setPaidAmount(paid);
            vo.setInFlightAmount(inFlight);
            vo.setPendingAmount(pending);
            vo.setProgress(amount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : paid.multiply(BigDecimal.valueOf(100)).divide(amount, 2, RoundingMode.HALF_UP));
            vo.setItemCount(countByNo.getOrDefault(h.getNo(), 0));
            vo.setPlanCount(planCountByNo.getOrDefault(h.getNo(), 0));
            vo.setChangeCount(changeCountByNo.getOrDefault(h.getNo(), 0));
            vo.setPartnerName(partnerNames.get(h.getPartnerCode()));
            vo.setProjectName(projectNames.get(h.getProjectNo()));
            return vo;
        }).toList();
    }

    public ContractVOs.ContractVO toVO(ContractHeader e) {
        ContractVOs.ContractVO vo = new ContractVOs.ContractVO();
        vo.setId(e.getId());
        vo.setNo(e.getNo());
        vo.setType(e.getType());
        vo.setName(e.getName());
        vo.setPartnerCode(e.getPartnerCode());
        vo.setProjectNo(e.getProjectNo());
        vo.setDept(e.getDept());
        vo.setOwner(e.getOwner());
        vo.setSignedDate(e.getSignedDate() == null ? null : e.getSignedDate().toString());
        vo.setEffectiveDate(e.getEffectiveDate() == null ? null : e.getEffectiveDate().toString());
        vo.setEndDate(e.getEndDate() == null ? null : e.getEndDate().toString());
        vo.setStatus(e.getStatus());
        vo.setPaymentTerms(e.getPaymentTerms());
        vo.setSubject(e.getSubject());
        vo.setNote(e.getNote());
        vo.setContractSource(e.getContractSource());
        vo.setPlanNo(e.getPlanNo());
        vo.setOrgCode(e.getOrgCode());
        List<String> fileIds = ContractAttachments.split(e.getFileIds());
        vo.setFileIds(fileIds);
        vo.setAttachmentUrls(ContractAttachments.signedUrls(fileIds, fileApi));
        return vo;
    }

    private void apply(ContractHeader e, ContractRequests.ContractSave req, String type,
                       String projectNo, String orgCode) {
        e.setType(type);
        e.setName(req.getName().trim());
        e.setPartnerCode(nvl(req.getPartnerCode(), ""));
        e.setProjectNo(projectNo);
        e.setDept(nvl(req.getDept(), ""));
        e.setOwner(nvl(req.getOwner(), ""));
        e.setSignedDate(req.getSignedDate());
        e.setEffectiveDate(req.getEffectiveDate());
        e.setEndDate(req.getEndDate());
        // 状态只能由 changeStatus 按状态机推进，保存/局部更新不得覆盖
        e.setPaymentTerms(nvl(req.getPaymentTerms(), ""));
        e.setSubject(nvl(req.getSubject(), ""));
        // 备注：局部更新（如只改所属项目）未传时保留原值；新建时落空串
        if (req.getNote() != null) {
            e.setNote(req.getNote());
        } else if (e.getNote() == null) {
            e.setNote("");
        }
        e.setOrgCode(orgCode);
        // 局部更新（如只改所属项目）不传 fileIds 时必须保留原附件
        if (req.getFileIds() != null) {
            e.setFileIds(ContractAttachments.join(req.getFileIds()));
        }
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
