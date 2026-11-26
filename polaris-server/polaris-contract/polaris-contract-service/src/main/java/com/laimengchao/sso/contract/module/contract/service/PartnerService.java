package com.laimengchao.sso.contract.module.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.entity.ContractPartner;
import com.laimengchao.sso.contract.module.contract.mapper.ContractHeaderMapper;
import com.laimengchao.sso.contract.module.contract.mapper.ContractPartnerMapper;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import com.laimengchao.sso.contract.security.DataScopeService;
import com.laimengchao.sso.utils.SensitiveMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 往来单位：一张主数据表承载供应商 / 客户，银行账号按权限码脱敏。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final ContractPartnerMapper mapper;
    private final ContractHeaderMapper headerMapper;
    private final DataScopeService dataScopeService;

    public PageResult<ContractVOs.PartnerVO> page(String code, String name, String role, String status,
                                                   String keyword, int page, int size) {
        LambdaQueryWrapper<ContractPartner> qw = new LambdaQueryWrapper<ContractPartner>()
                .eq(code != null && !code.isBlank(), ContractPartner::getCode, trim(code))
                .eq(role != null && !role.isBlank(), ContractPartner::getRole, trim(role))
                .eq(status != null && !status.isBlank(), ContractPartner::getStatus, trim(status))
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(ContractPartner::getName, trim(keyword))
                        .or().like(ContractPartner::getCode, trim(keyword))
                        .or().like(ContractPartner::getContact, trim(keyword)))
                .orderByDesc(ContractPartner::getId);
        if (name != null && !name.isBlank()) {
            qw.like(ContractPartner::getName, trim(name));
        }
        dataScopeService.applyOrgScope(qw, ContractPartner::getOrgCode);
        Page<ContractPartner> p = mapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), qw);
        boolean bankVisible = dataScopeService.hasPermission(PermissionCodes.CONTRACT_PARTNER_BANK_VIEW);
        return PageResult.of(p.getRecords().stream().map(e -> toVO(e, bankVisible)).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 下拉 / 跨服务只读：返回全局可见的合作中单位，但同样按数据范围收口 */
    public List<ContractVOs.PartnerVO> list(String role, String status) {
        LambdaQueryWrapper<ContractPartner> qw = new LambdaQueryWrapper<ContractPartner>()
                .eq(role != null && !role.isBlank(), ContractPartner::getRole, trim(role))
                .eq(status != null && !status.isBlank(), ContractPartner::getStatus, trim(status))
                .orderByAsc(ContractPartner::getCode);
        dataScopeService.applyOrgScope(qw, ContractPartner::getOrgCode);
        return mapper.selectList(qw).stream().map(e -> toVO(e, false)).toList();
    }

    public ContractVOs.PartnerVO detail(Long id) {
        return toVO(require(id), dataScopeService.hasPermission(PermissionCodes.CONTRACT_PARTNER_BANK_VIEW));
    }

    public ContractPartner require(Long id) {
        ContractPartner entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContractErrorCode.PARTNER_NOT_FOUND);
        }
        dataScopeService.assertCanAccessOrg(entity.getOrgCode());
        return entity;
    }

    public ContractPartner requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ContractErrorCode.PARTNER_NOT_FOUND);
        }
        LambdaQueryWrapper<ContractPartner> qw = new LambdaQueryWrapper<ContractPartner>()
                .eq(ContractPartner::getCode, trim(code)).last(" LIMIT 1");
        dataScopeService.applyOrgScope(qw, ContractPartner::getOrgCode);
        ContractPartner entity = mapper.selectOne(qw);
        if (entity == null) {
            throw new BizException(ContractErrorCode.PARTNER_NOT_FOUND);
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVOs.PartnerVO save(ContractRequests.PartnerSave req) {
        validate(req);
        LambdaQueryWrapper<ContractPartner> dup = new LambdaQueryWrapper<ContractPartner>()
                .eq(ContractPartner::getCode, trim(req.getCode()));
        if (mapper.selectCount(dup) > 0) {
            throw new BizException(ContractErrorCode.PARTNER_CODE_DUPLICATE);
        }
        ContractPartner entity = new ContractPartner();
        apply(entity, req);
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return toVO(entity, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractRequests.PartnerSave req) {
        ContractPartner entity = require(id);
        validate(req);
        if (!entity.getCode().equals(trim(req.getCode()))) {
            LambdaQueryWrapper<ContractPartner> dup = new LambdaQueryWrapper<ContractPartner>()
                    .eq(ContractPartner::getCode, trim(req.getCode()));
            if (mapper.selectCount(dup) > 0) {
                throw new BizException(ContractErrorCode.PARTNER_CODE_DUPLICATE);
            }
        }
        apply(entity, req);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, String status) {
        ContractPartner entity = require(id);
        if (status == null || status.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态不能为空");
        }
        entity.setStatus(trim(status));
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractPartner entity = require(id);
        LambdaQueryWrapper<ContractHeader> usedQw = new LambdaQueryWrapper<ContractHeader>()
                .eq(ContractHeader::getPartnerCode, entity.getCode());
        dataScopeService.applyOrgScope(usedQw, ContractHeader::getOrgCode);
        Long used = headerMapper.selectCount(usedQw);
        if (used != null && used > 0) {
            throw new BizException(ContractErrorCode.PARTNER_IN_USE);
        }
        mapper.deleteById(id);
    }

    private void validate(ContractRequests.PartnerSave req) {
        if (req == null || req.getCode() == null || req.getCode().isBlank()
                || req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "往来单位编码与名称不能为空");
        }
    }

    private void apply(ContractPartner e, ContractRequests.PartnerSave req) {
        e.setCode(trim(req.getCode()));
        e.setName(trim(req.getName()));
        e.setSubjectType(nvl(req.getSubjectType(), "COMPANY"));
        e.setRole(nvl(req.getRole(), "SUPPLIER"));
        e.setType(nvl(req.getType(), ""));
        e.setCreditCode(nvl(req.getCreditCode(), ""));
        e.setLegalPerson(nvl(req.getLegalPerson(), ""));
        e.setRegisteredCapital(nvl(req.getRegisteredCapital(), ""));
        e.setEstablishDate(req.getEstablishDate());
        e.setTaxpayerType(nvl(req.getTaxpayerType(), ""));
        e.setAddress(nvl(req.getAddress(), ""));
        e.setRemark(nvl(req.getRemark(), ""));
        e.setContact(nvl(req.getContact(), ""));
        e.setPhone(nvl(req.getPhone(), ""));
        e.setTerms(nvl(req.getTerms(), ""));
        e.setRating(nvl(req.getRating(), "B"));
        e.setCreditLimit(req.getCreditLimit() == null ? BigDecimal.ZERO : req.getCreditLimit());
        e.setCreditUsed(req.getCreditUsed() == null ? BigDecimal.ZERO : req.getCreditUsed());
        e.setStatus(nvl(req.getStatus(), "PENDING"));
        e.setBank(nvl(req.getBank(), ""));
        e.setAccountName(nvl(req.getAccountName(), ""));
        e.setBankAccount(nvl(req.getBankAccount(), ""));
        e.setOrgCode(nvl(req.getOrgCode(), e.getOrgCode() == null ? "" : e.getOrgCode()));
    }

    /** 银行账号与个人证件号仅在有明文权限时展示，否则脱敏 */
    public ContractVOs.PartnerVO toVO(ContractPartner e, boolean bankVisible) {
        ContractVOs.PartnerVO vo = new ContractVOs.PartnerVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setSubjectType(e.getSubjectType());
        vo.setRole(e.getRole());
        vo.setType(e.getType());
        vo.setCreditCode(bankVisible ? e.getCreditCode() : sensitiveCode(e));
        vo.setTaxNo(vo.getCreditCode());
        vo.setLegalPerson(e.getLegalPerson());
        vo.setRegisteredCapital(e.getRegisteredCapital());
        vo.setEstablishDate(e.getEstablishDate() == null ? null : e.getEstablishDate().toString());
        vo.setTaxpayerType(e.getTaxpayerType());
        vo.setAddress(e.getAddress());
        vo.setRemark(e.getRemark());
        vo.setContact(e.getContact());
        vo.setPhone(e.getPhone());
        vo.setTerms(e.getTerms());
        vo.setRating(e.getRating());
        vo.setCreditLimit(e.getCreditLimit());
        vo.setCreditUsed(e.getCreditUsed());
        vo.setStatus(e.getStatus());
        vo.setBank(e.getBank());
        vo.setAccountName(e.getAccountName());
        vo.setBankAccount(bankVisible ? e.getBankAccount() : SensitiveMasker.maskBankAccount(e.getBankAccount()));
        vo.setBankMasked(!bankVisible);
        vo.setOrgCode(e.getOrgCode());
        return vo;
    }

    private String sensitiveCode(ContractPartner e) {
        return "PERSON".equals(e.getSubjectType())
                ? SensitiveMasker.maskIdCard(e.getCreditCode()) : e.getCreditCode();
    }

    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
