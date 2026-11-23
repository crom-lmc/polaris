package com.laimengchao.sso.openapi.module.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.app.entity.ApiClientApp;
import com.laimengchao.sso.openapi.module.app.entity.ApiClientAppPermission;
import com.laimengchao.sso.openapi.module.app.mapper.ApiClientAppMapper;
import com.laimengchao.sso.openapi.module.app.mapper.ApiClientAppPermissionMapper;
import com.laimengchao.sso.openapi.module.app.vo.ClientAppSaveRequest;
import com.laimengchao.sso.openapi.module.app.vo.ClientAppVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 第三方对接应用主数据与授权服务（开放平台本地库唯一来源）。
 * <p>
 * 管理端 CRUD、openapi-auth 换票验凭证/取授权、令牌 perms 来源全部收敛到这里。
 * 应用密钥同时存 SHA-256 摘要（校验用）与明文（管理端可查看）；换票校验只用摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientAppService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 2;
    private static final int SECRET_BYTES = 32;

    private final ApiClientAppMapper appMapper;
    private final ApiClientAppPermissionMapper permMapper;

    /** 对接应用清单（含停用），按 sort/id 排序。 */
    public List<ClientAppVo> list() {
        return appMapper.selectList(new LambdaQueryWrapper<ApiClientApp>()
                        .orderByAsc(ApiClientApp::getSort, ApiClientApp::getId))
                .stream().map(this::toVo).toList();
    }

    /** 全部启用应用（内部下拉用）。 */
    public List<ClientAppVo> listEnabled() {
        return appMapper.selectList(new LambdaQueryWrapper<ApiClientApp>()
                        .eq(ApiClientApp::getStatus, STATUS_ENABLED)
                        .orderByAsc(ApiClientApp::getSort, ApiClientApp::getId))
                .stream().map(this::toVo).toList();
    }

    /** 新增应用，返回 VO（appSecret 为明文，仅此一次）。 */
    @Transactional(rollbackFor = Exception.class)
    public ClientAppVo create(ClientAppSaveRequest req) {
        String code = req.getCode() == null ? "" : req.getCode().trim();
        if (code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "应用编码不能为空");
        }
        if (appMapper.selectByAppCode(code) != null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "应用编码已存在：" + code);
        }
        String rawSecret = generateSecret();
        ApiClientApp app = new ApiClientApp();
        app.setAppCode(code);
        app.setAppName(req.getName());
        app.setAppSecret(sha256(rawSecret));
        app.setAppSecretPlain(rawSecret);
        app.setBaseUrl(req.getBaseUrl());
        app.setRedirectUri(req.getRedirectUri());
        app.setRemark(req.getRemark());
        app.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        app.setSort(req.getSort() == null ? 0 : req.getSort());
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.insert(app);
        ClientAppVo vo = toVo(app);
        vo.setAppSecret(rawSecret);
        log.info("新增第三方应用 {} id={}", code, app.getId());
        return vo;
    }

    /** 编辑应用（编码不可改）。 */
    @Transactional(rollbackFor = Exception.class)
    public ClientAppVo update(ClientAppSaveRequest req) {
        ApiClientApp app = requireByCode(req.getCode());
        app.setAppName(req.getName());
        app.setBaseUrl(req.getBaseUrl());
        app.setRedirectUri(req.getRedirectUri());
        app.setRemark(req.getRemark());
        if (req.getStatus() != null) app.setStatus(req.getStatus());
        if (req.getSort() != null) app.setSort(req.getSort());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return toVo(app);
    }

    /** 启停应用。 */
    @Transactional(rollbackFor = Exception.class)
    public void toggle(String appCode, Integer status) {
        ApiClientApp app = requireByCode(appCode);
        app.setStatus(status);
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
    }

    /** 重置密钥，返回新明文（仅此一次）。 */
    @Transactional(rollbackFor = Exception.class)
    public String resetSecret(String appCode) {
        ApiClientApp app = requireByCode(appCode);
        String rawSecret = generateSecret();
        app.setAppSecret(sha256(rawSecret));
        app.setAppSecretPlain(rawSecret);
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        log.info("重置第三方应用密钥 {}", appCode);
        return rawSecret;
    }

    /** 查看密钥明文：存量应用未记录明文时返回空串，前端提示先重置。 */
    public String viewSecret(String appCode) {
        ApiClientApp app = requireByCode(appCode);
        String plain = app.getAppSecretPlain();
        return plain == null ? "" : plain;
    }

    /** 删除应用（逻辑删除）：仍有接口授权时拒绝，需先到应用授权页逐条撤销。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String appCode) {
        ApiClientApp app = requireByCode(appCode);
        Long authCount = permMapper.selectCount(new LambdaQueryWrapper<ApiClientAppPermission>()
                .eq(ApiClientAppPermission::getAppCode, appCode));
        if (authCount != null && authCount > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR,
                    "该应用仍有 " + authCount + " 个接口授权，请先撤销全部授权后再删除");
        }
        appMapper.deleteById(app.getId());
        permMapper.delete(new LambdaQueryWrapper<ApiClientAppPermission>()
                .eq(ApiClientAppPermission::getAppCode, appCode));
        log.info("删除第三方应用 {} 及其授权", appCode);
    }

    /** 校验应用凭证：应用存在、启用、密钥摘要匹配。 */
    public ApiClientApp verifyCredentials(String appCode, String appSecret) {
        ApiClientApp app = appMapper.selectByAppCode(appCode);
        if (app == null || app.getStatus() == null || app.getStatus() != STATUS_ENABLED) {
            throw new BizException(OpenapiErrorCode.PERM_DENIED, "应用不存在或已停用");
        }
        if (appSecret == null || !sha256(appSecret).equals(app.getAppSecret())) {
            throw new BizException(OpenapiErrorCode.PERM_DENIED, "应用密钥错误");
        }
        return app;
    }

    /** 取应用授权的接口编码（令牌 perms 唯一来源，一行一个接口）。 */
    public List<String> permissions(String appCode) {
        return permMapper.selectApiCodes(appCode);
    }

    /** 授权列表（供 AuthzService）。 */
    public List<ApiClientAppPermission> permissionList(String appCode) {
        return permMapper.selectByAppCode(appCode);
    }

    /** 幂等授权：按 appCode + apiCode 去重，返回新增条数。 */
    @Transactional(rollbackFor = Exception.class)
    public int grant(String appCode, List<ApiClientAppPermission> items) {
        requireByCode(appCode);
        int created = 0;
        for (ApiClientAppPermission item : items) {
            Long exists = permMapper.selectCount(new LambdaQueryWrapper<ApiClientAppPermission>()
                    .eq(ApiClientAppPermission::getAppCode, appCode)
                    .eq(ApiClientAppPermission::getApiCode, item.getApiCode()));
            if (exists != null && exists > 0) continue;
            ApiClientAppPermission row = new ApiClientAppPermission();
            row.setAppCode(appCode);
            row.setApiCode(item.getApiCode());
            row.setPermCode(item.getPermCode());
            row.setPermName(item.getPermName());
            row.setStatus(STATUS_ENABLED);
            row.setCreatedAt(LocalDateTime.now());
            permMapper.insert(row);
            created++;
        }
        return created;
    }

    /** 撤销授权（物理删除，只删这一个接口），返回删除条数。 */
    @Transactional(rollbackFor = Exception.class)
    public int revoke(String appCode, String apiCode) {
        return permMapper.delete(new LambdaQueryWrapper<ApiClientAppPermission>()
                .eq(ApiClientAppPermission::getAppCode, appCode)
                .eq(ApiClientAppPermission::getApiCode, apiCode));
    }

    /** 启停授权（只作用于这一个接口）：1=启用 2=停用。停用后该接口编码不再进入令牌 perms。 */
    @Transactional(rollbackFor = Exception.class)
    public int togglePermission(String appCode, String apiCode, Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "授权状态只能是 1(启用) 或 2(停用)");
        }
        ApiClientAppPermission row = permMapper.selectOne(new LambdaQueryWrapper<ApiClientAppPermission>()
                .eq(ApiClientAppPermission::getAppCode, appCode)
                .eq(ApiClientAppPermission::getApiCode, apiCode));
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "授权记录不存在");
        }
        row.setStatus(status);
        int n = permMapper.updateById(row);
        log.info("启停应用授权：appCode={} apiCode={} status={}", appCode, apiCode, status);
        return n;
    }

    public ApiClientApp requireByCode(String appCode) {
        ApiClientApp app = appMapper.selectByAppCode(appCode);
        if (app == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "第三方应用不存在：" + appCode);
        }
        return app;
    }

    private ClientAppVo toVo(ApiClientApp a) {
        ClientAppVo vo = new ClientAppVo();
        vo.setId(a.getId());
        vo.setCode(a.getAppCode());
        vo.setName(a.getAppName());
        vo.setBaseUrl(a.getBaseUrl());
        vo.setRedirectUri(a.getRedirectUri());
        vo.setRemark(a.getRemark());
        vo.setStatus(a.getStatus());
        vo.setSort(a.getSort());
        return vo;
    }

    private static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
