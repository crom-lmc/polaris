package com.laimengchao.sso.contract.module.contract.service.impl;

import com.laimengchao.sso.contract.common.impl.BizNoServiceImpl;
import com.laimengchao.sso.contract.module.contract.service.ProjectService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.common.BizNoService;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentPlan;
import com.laimengchao.sso.contract.module.contract.entity.ContractProject;
import com.laimengchao.sso.contract.module.contract.mapper.ContractHeaderMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractItemMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPaymentPlanMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractProjectMapper;
import com.laimengchao.sso.contract.module.contract.support.ContractApprovalInfoSupport;
import com.laimengchao.sso.contract.module.contract.support.ContractApprovalLauncher;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 项目档案：单号走 settings，状态机 DRAFT→PENDING→(ACTIVE|DRAFT)，ACTIVE↔PAUSED，结项/取消拒绝。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAUSED = "PAUSED";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";
    public static final String BIZ_PROJECT = "contract.project";

    private final ContractProjectMapper mapper;
    private final ContractHeaderMapper headerMapper;
    private final ContractItemMapper itemMapper;
    private final ContractPaymentPlanMapper planMapper;
    private final BizNoService bizNoService;
    private final DataScopeService dataScopeService;
    private final ContractApprovalLauncher approvalLauncher;
    private final ContractApprovalInfoSupport approvalInfoSupport;

    @Value("${contract.internal-base-url:http://127.0.0.1:9015}")
    private String internalBaseUrl;

    public PageResult<ContractVOs.ProjectVO> page(String code, String name, String type, String status,
                                                   int page, int size) {
        LambdaQueryWrapper<ContractProject> qw = new LambdaQueryWrapper<ContractProject>()
                .eq(code != null && !code.isBlank(), ContractProject::getCode, (code == null ? null : code.trim()))
                .eq(type != null && !type.isBlank(), ContractProject::getType, (type == null ? null : type.trim()))
                .eq(status != null && !status.isBlank(), ContractProject::getStatus, (status == null ? null : status.trim()))
                .like(name != null && !name.isBlank(), ContractProject::getName, name == null ? "" : name.trim())
                .orderByDesc(ContractProject::getId);
        dataScopeService.applyOrgScope(qw, ContractProject::getOrgCode);
        Page<ContractProject> p = mapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), qw);
        return PageResult.of(p.getRecords().stream().map(this::toVO).peek(this::decorate).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ContractVOs.ProjectVO detail(Long id) {
        ContractVOs.ProjectVO vo = toVO(require(id));
        decorate(vo);
        return vo;
    }

    public List<ContractVOs.ProjectOptionVO> options() {
        LambdaQueryWrapper<ContractProject> qw = new LambdaQueryWrapper<ContractProject>()
                .eq(ContractProject::getStatus, ACTIVE)
                .orderByAsc(ContractProject::getCode);
        dataScopeService.applyOrgScope(qw, ContractProject::getOrgCode);
        return mapper.selectList(qw).stream().map(e -> {
            ContractVOs.ProjectOptionVO vo = new ContractVOs.ProjectOptionVO();
            vo.setCode(e.getCode());
            vo.setName(e.getName());
            vo.setType(e.getType());
            vo.setOrgCode(e.getOrgCode());
            vo.setStatus(e.getStatus());
            vo.setBudgetAmount(e.getBudgetAmount());
            return vo;
        }).toList();
    }

    /** 供合同侧与 internal 接口调用：项目是否允许新关联单据 */
    public ContractVOs.ProjectUsableVO validateUsable(String code) {
        ContractVOs.ProjectUsableVO vo = new ContractVOs.ProjectUsableVO();
        vo.setCode(code);
        LambdaQueryWrapper<ContractProject> qw = new LambdaQueryWrapper<ContractProject>()
                .eq(ContractProject::getCode, code == null ? "" : code.trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractProject::getOrgCode);
        ContractProject project = mapper.selectOne(qw);
        if (project == null) {
            vo.setUsable(false);
            vo.setReason("项目不存在或不在数据范围内");
            return vo;
        }
        if (!ACTIVE.equals(project.getStatus())) {
            vo.setUsable(false);
            vo.setReason("项目状态为 " + project.getStatus() + "，不能关联新单据");
            return vo;
        }
        vo.setUsable(true);
        return vo;
    }

    /** 合同侧强校验：不可用直接抛业务错误 */
    public ContractProject requireUsable(String code) {
        ContractVOs.ProjectUsableVO result = validateUsable(code);
        if (!result.isUsable()) {
            throw new BizException(ContractErrorCode.PROJECT_NOT_USABLE, result.getReason());
        }
        LambdaQueryWrapper<ContractProject> qw = new LambdaQueryWrapper<ContractProject>()
                .eq(ContractProject::getCode, code.trim()).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractProject::getOrgCode);
        return mapper.selectOne(qw);
    }

    public ContractProject require(Long id) {
        ContractProject entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.PROJECT_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.ProjectVO save(ContractRequests.ProjectSave req) {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "项目名称不能为空");
        }
        String orgCode = req.getOrgCode() == null || req.getOrgCode().isBlank()
                ? (req.getTeamCode() == null ? "" : req.getTeamCode().trim())
                : req.getOrgCode().trim();
        ContractProject entity = new ContractProject();
        entity.setCode(bizNoService.next(BizNoServiceImpl.TABLE_PROJECT));
        apply(entity, req, orgCode);
        // 状态是流程结果：新建固定 DRAFT，不接受请求体覆盖（提交审批后由回调推进 ACTIVE）
        entity.setStatus(DRAFT);
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.ProjectSave req) {
        ContractProject entity = require(id);
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "项目名称不能为空");
        }
        if (!DRAFT.equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID, "项目已进入执行，不允许编辑");
        }
        apply(entity, req, entity.getOrgCode());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        if (status == null || status.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态不能为空");
        }
        String target = status.trim();
        if (CLOSED.equals(target) || CANCELLED.equals(target)) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID, "结项/取消不可作为流转目标");
        }
        ContractProject entity = require(id);
        String current = entity.getStatus();
        if (CLOSED.equals(current) || CANCELLED.equals(current)) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID);
        }
        // 审批中状态只能由审批中心回调推进，禁止手工改状态绕过审批
        if (PENDING.equals(current) && approvalInfoSupport.hasRunning(String.valueOf(id), BIZ_PROJECT)) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID, "项目审批中，不能手工改状态");
        }
        // DRAFT→ACTIVE 必须走提交审批，由终审回调推进；这里只保留 ACTIVE↔PAUSED
        boolean allowed = (ACTIVE.equals(current) && PAUSED.equals(target))
                || (PAUSED.equals(current) && ACTIVE.equals(target))
                || current.equals(target);
        if (!allowed) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID);
        }
        entity.setStatus(target);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractProject entity = require(id);
        if (!DRAFT.equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID, "项目已进入执行，不允许删除");
        }
        LambdaQueryWrapper<ContractHeader> usedQw = new LambdaQueryWrapper<ContractHeader>()
                .eq(ContractHeader::getProjectNo, entity.getCode());
        dataScopeService.applyOrgScope(usedQw, ContractHeader::getOrgCode);
        Long used = headerMapper.selectCount(usedQw);
        if (used != null && used > 0) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID, "项目已被合同引用，禁止删除");
        }
        mapper.deleteById(id);
    }

    @Override
    public void submit(Long id) {
        ContractProject entity = require(id);
        if (!DRAFT.equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID, "仅立项中的项目可提交审批");
        }
        // 审批人规则按 dept 取上级领导，归属组织为空会导致解析不到审批人（BLOCK）
        if (entity.getOrgCode() == null || entity.getOrgCode().isBlank()) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID, "项目缺归属组织，无法提交审批");
        }
        String before = entity.getStatus();
        entity.setStatus(PENDING);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        // 先落库再发起审批：终审可能同步回调，避免回调读到未提交行；失败退回原状态，避免卡 PENDING 不可重试
        try {
            approvalLauncher.launch(() -> buildApprovalStart(entity));
        } catch (RuntimeException e) {
            mapper.update(null, new LambdaUpdateWrapper<ContractProject>()
                    .eq(ContractProject::getId, entity.getId())
                    .eq(ContractProject::getStatus, PENDING)
                    .set(ContractProject::getStatus, before));
            entity.setStatus(before);
            throw e;
        }
    }

    /** 构造项目审批发起请求：上级领导一级审批，驳回回 DRAFT 可修改再提交 */
    private ApprovalStartDTO buildApprovalStart(ContractProject entity) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_PROJECT);
        dto.setBizId(String.valueOf(entity.getId()));
        dto.setBizNo(entity.getCode());
        // applicant_id 在 appr_instance 非空无默认值；不传会让 settings 侧 INSERT 失败返回 500
        Long applicantId = AccountContext.getAccountId();
        if (applicantId == null) {
            throw new BizException(ContractErrorCode.APPROVAL_START_FAILED, "无法获取当前登录人，请重新登录后再发起审批");
        }
        dto.setApplicantId(applicantId);
        dto.setApplicantName(AccountContext.getUsername());
        dto.setTitle("项目档案 " + entity.getCode() + " " + entity.getName());
        dto.setSubjectId(String.valueOf(entity.getId()));
        dto.setBizIdKind("DOC");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("projectId", String.valueOf(entity.getId()));
        form.put("code", entity.getCode());
        form.put("name", entity.getName());
        form.put("dept", entity.getOrgCode());       // 审批规则 FIELD:dept:ORG + levelOffset=1 取上级领导
        form.put("owner", entity.getOwner());
        form.put("budgetAmount", entity.getBudgetAmount());
        dto.setFormData(form);
        dto.setCallbackUrl(internalBaseUrl + "/internal/contract/approval-callback");
        return dto;
    }

    /**
     * 审批中心终审回调：通过 PENDING→ACTIVE（执行中），驳回/撤回/终止 PENDING→DRAFT（可修改再提交）。
     * internal 调用无登录上下文，不套 dataScope；仅 PENDING 更新，重复投递返回 false。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleApprovalResult(String bizId, Boolean approved, Integer result) {
        Long id;
        try { id = Long.valueOf(bizId); }
        catch (NumberFormatException e) { return false; }
        ContractProject entity = mapper.selectById(id);
        if (entity == null || !PENDING.equals(entity.getStatus())) {
            log.info("项目审批回调忽略：id={} status={}", id, entity == null ? "-" : entity.getStatus());
            return false;
        }
        boolean pass = Boolean.TRUE.equals(approved) || Integer.valueOf(1).equals(result);
        return mapper.update(null, new LambdaUpdateWrapper<ContractProject>()
                .eq(ContractProject::getId, id)
                .eq(ContractProject::getStatus, PENDING)
                .set(ContractProject::getStatus, pass ? ACTIVE : DRAFT)
                .set(ContractProject::getUpdatedAt, LocalDateTime.now())) > 0;
    }

    @Override
    public ContractVOs.ApprovalInfoVO approvalInfo(Long id) {
        require(id); // 复用数据权限校验
        com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO found =
                approvalInfoSupport.latest(String.valueOf(id), BIZ_PROJECT);
        return approvalInfoSupport.toVO(found);
    }

    private void decorate(ContractVOs.ProjectVO vo) {
        LambdaQueryWrapper<ContractHeader> hw = new LambdaQueryWrapper<ContractHeader>()
                .eq(ContractHeader::getProjectNo, vo.getCode());
        dataScopeService.applyOrgScope(hw, ContractHeader::getOrgCode);
        List<ContractHeader> contracts = headerMapper.selectList(hw);
        vo.setContractCount(contracts.size());
        Set<String> nos = contracts.stream().map(ContractHeader::getNo).collect(Collectors.toSet());
        if (nos.isEmpty()) {
            vo.setContractAmount(BigDecimal.ZERO);
            vo.setUsedAmount(BigDecimal.ZERO);
            return;
        }
        LambdaQueryWrapper<ContractItem> iw = new LambdaQueryWrapper<ContractItem>()
                .in(ContractItem::getContractNo, nos);
        dataScopeService.applyOrgScope(iw, ContractItem::getOrgCode);
        BigDecimal contractAmount = itemMapper.selectList(iw).stream()
                .map(e -> e.getAmount() == null ? BigDecimal.ZERO : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setContractAmount(contractAmount);
        LambdaQueryWrapper<ContractPaymentPlan> pw = new LambdaQueryWrapper<ContractPaymentPlan>()
                .in(ContractPaymentPlan::getContractNo, nos)
                .in(ContractPaymentPlan::getStatus, List.of("APPROVED", "PAID", "POSTED"));
        dataScopeService.applyOrgScope(pw, ContractPaymentPlan::getOrgCode);
        BigDecimal used = planMapper.selectList(pw).stream()
                .map(e -> e.getAmount() == null ? BigDecimal.ZERO : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setUsedAmount(used);
    }

    private void apply(ContractProject e, ContractRequests.ProjectSave req, String orgCode) {
        e.setName(req.getName().trim());
        e.setType(nvl(req.getType(), "INTERNAL"));
        e.setOrgCode(orgCode);
        e.setTeamCode(nvl(req.getTeamCode(), ""));
        e.setCostCenterCode(nvl(req.getCostCenterCode(), ""));
        e.setOwner(nvl(req.getOwner(), ""));
        e.setBudgetAmount(req.getBudgetAmount() == null ? BigDecimal.ZERO : req.getBudgetAmount());
        e.setStartDate(req.getStartDate());
        e.setEndDate(req.getEndDate());
        e.setRemark(nvl(req.getRemark(), ""));
    }

    private ContractVOs.ProjectVO toVO(ContractProject e) {
        ContractVOs.ProjectVO vo = new ContractVOs.ProjectVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setType(e.getType());
        vo.setOrgCode(e.getOrgCode());
        vo.setTeamCode(e.getTeamCode());
        vo.setCostCenterCode(e.getCostCenterCode());
        vo.setOwner(e.getOwner());
        vo.setBudgetAmount(e.getBudgetAmount());
        vo.setStartDate(e.getStartDate() == null ? null : e.getStartDate().toString());
        vo.setEndDate(e.getEndDate() == null ? null : e.getEndDate().toString());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        return vo;
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
