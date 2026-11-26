package com.laimengchao.sso.contract.module.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 项目档案：单号走 settings，状态机 DRAFT→ACTIVE→PAUSED→ACTIVE，结项/取消拒绝。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAUSED = "PAUSED";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";

    private final ContractProjectMapper mapper;
    private final ContractHeaderMapper headerMapper;
    private final ContractItemMapper itemMapper;
    private final ContractPaymentPlanMapper planMapper;
    private final BizNoService bizNoService;
    private final DataScopeService dataScopeService;

    public PageResult<ContractVOs.ProjectVO> page(String code, String name, String type, String status,
                                                   int page, int size) {
        LambdaQueryWrapper<ContractProject> qw = new LambdaQueryWrapper<ContractProject>()
                .eq(code != null && !code.isBlank(), ContractProject::getCode, code.trim())
                .eq(type != null && !type.isBlank(), ContractProject::getType, type.trim())
                .eq(status != null && !status.isBlank(), ContractProject::getStatus, status.trim())
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
        entity.setCode(bizNoService.next(BizNoService.TABLE_PROJECT));
        apply(entity, req, orgCode);
        entity.setStatus(nvl(req.getStatus(), DRAFT));
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
        if (CLOSED.equals(entity.getStatus()) || CANCELLED.equals(entity.getStatus())) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID);
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
        boolean allowed = (DRAFT.equals(current) && ACTIVE.equals(target))
                || (ACTIVE.equals(current) && PAUSED.equals(target))
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
        LambdaQueryWrapper<ContractHeader> usedQw = new LambdaQueryWrapper<ContractHeader>()
                .eq(ContractHeader::getProjectNo, entity.getCode());
        dataScopeService.applyOrgScope(usedQw, ContractHeader::getOrgCode);
        Long used = headerMapper.selectCount(usedQw);
        if (used != null && used > 0) {
            throw new BizException(ContractErrorCode.PROJECT_STATUS_INVALID, "项目已被合同引用，禁止删除");
        }
        mapper.deleteById(id);
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
