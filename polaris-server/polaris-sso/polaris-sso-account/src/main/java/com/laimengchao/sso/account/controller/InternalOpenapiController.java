package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.dto.request.ApplicationRequest;
import com.laimengchao.sso.account.dto.request.CreatePermissionRequest;
import com.laimengchao.sso.account.dto.request.InternalAuthzGrantRequest;
import com.laimengchao.sso.account.dto.request.InternalClientAppRequest;
import com.laimengchao.sso.account.dto.response.ApplicationVO;
import com.laimengchao.sso.account.dto.response.ClientAppVO;
import com.laimengchao.sso.account.dto.request.InternalAuthzRevokeRequest;
import com.laimengchao.sso.account.dto.request.InternalSignatureVerifyRequest;
import com.laimengchao.sso.account.service.ApplicationService;
import com.laimengchao.sso.account.service.PermissionService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 接口开放平台专用的服务间接口。**不经网关**，由 X-Internal-Token 拦截器把关。
 * <p>
 * 的存在意义是让第三方应用的「凭证 + 权限」单一来源留在统一登录中心：
 * <ul>
 *   <li>{@link #appPermissions(String)}：拿某应用在其 app_code 域下注册的开放 API 权限码，
 *       供开放平台签发短期令牌时写入 perms；</li>
 *   <li>{@link #verifySignature(InternalSignatureVerifyRequest)}：用库里的 app_secret 重算 HMAC，
 *       <b>密钥全程不出账号域</b>——开放平台只转发签名材料，拿不到 secret。</li>
 * </ul>
 * 第三方应用的自助维护（新增/编辑/启停/密钥/删除 + 接口授权）已前移到开放平台
 * 「对接应用」页，本控制器为它提供底层接口；凭证与权限的单一来源仍是账号中心。
 */
@RestController
@RequestMapping("/internal/openapi")
@RequiredArgsConstructor
public class InternalOpenapiController {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ApplicationService applicationService;
    private final PermissionService permissionService;

    /** 应用在其 app_code 域下注册的开放 API 权限码（inbound:/outbound: 前缀），供令牌签发写入 perms */
    @GetMapping("/app-permissions")
    public R<List<String>> appPermissions(@RequestParam String appCode) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        SsoApplication app = applicationService.requireEnabled(appCode);
        List<String> codes = applicationService.apiPermissionCodes(appCode);
        return R.ok(codes);
    }

    /**
     * 给第三方应用授予开放接口权限码。仅接受启用中的 OPENAPI_CLIENT 应用，幂等写入
     * （已授权的接口跳过），返回本次新建数量。
     */
    @PostMapping("/authz/grant")
    public R<Map<String, Object>> grantAuthz(@RequestBody InternalAuthzGrantRequest req) {
        if (req == null || req.getAppCode() == null || req.getAppCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        SsoApplication app = applicationService.requireEnabled(req.getAppCode());
        if (!"OPENAPI_CLIENT".equals(app.getAppType())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅第三方对接应用（OPENAPI_CLIENT）可配置接口授权");
        }
        List<CreatePermissionRequest> items = new ArrayList<>();
        if (req.getItems() != null) {
            for (InternalAuthzGrantRequest.PermItem i : req.getItems()) {
                if (i == null || i.getCode() == null || i.getCode().isBlank()) {
                    continue;
                }
                CreatePermissionRequest c = new CreatePermissionRequest();
                c.setAppCode(req.getAppCode());
                c.setCode(i.getCode());
                c.setName(i.getName());
                items.add(c);
            }
        }
        int created = permissionService.grantApiPermissions(req.getAppCode(), items);
        return R.ok(Map.of("created", created));
    }

    /** 撤销第三方应用的开放接口权限码，返回删除数量（0 表示本来就没授权） */
    @PostMapping("/authz/revoke")
    public R<Map<String, Object>> revokeAuthz(@RequestBody InternalAuthzRevokeRequest req) {
        if (req == null || req.getAppCode() == null || req.getAppCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        SsoApplication app = applicationService.requireEnabled(req.getAppCode());
        if (!"OPENAPI_CLIENT".equals(app.getAppType())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅第三方对接应用（OPENAPI_CLIENT）可配置接口授权");
        }
        int deleted = permissionService.revokeApiPermission(req.getAppCode(), req.getCode());
        return R.ok(Map.of("deleted", deleted));
    }

    // ---------------- 第三方对接应用自助维护（开放平台专用） ----------------

    private static final String APP_TYPE_OPENAPI_CLIENT = "OPENAPI_CLIENT";

    /** 第三方对接应用清单（含停用），供开放平台「对接应用」页自助维护 */
    @GetMapping("/apps")
    public R<List<ClientAppVO>> listClientApps() {
        List<ClientAppVO> apps = applicationService.list(null, null).stream()
                .filter(a -> APP_TYPE_OPENAPI_CLIENT.equals(a.getAppType()))
                .map(this::toClientAppVO)
                .toList();
        return R.ok(apps);
    }

    /** 新增第三方对接应用，appType 强制 OPENAPI_CLIENT，返回明文密钥（仅此一次） */
    @PostMapping("/apps")
    public R<ClientAppVO> createClientApp(@RequestBody InternalClientAppRequest req) {
        ApplicationVO created = applicationService.create(toApplicationRequest(req));
        ClientAppVO vo = toClientAppVO(created);
        vo.setAppSecret(created.getAppSecret());
        return R.ok(vo);
    }

    /** 编辑第三方对接应用（编码不可改，类型不可改） */
    @PostMapping("/apps/update")
    public R<ClientAppVO> updateClientApp(@RequestBody InternalClientAppRequest req) {
        SsoApplication app = requireClientApp(req == null ? null : req.getCode());
        ApplicationVO updated = applicationService.update(app.getId(), toApplicationRequest(req));
        return R.ok(toClientAppVO(updated));
    }

    /** 启停第三方对接应用 */
    @PostMapping("/apps/toggle")
    public R<Void> toggleClientApp(@RequestParam String appCode, @RequestParam Integer status) {
        SsoApplication app = requireClientApp(appCode);
        InternalClientAppRequest req = new InternalClientAppRequest();
        req.setStatus(status);
        applicationService.update(app.getId(), toApplicationRequest(req));
        return R.ok(null);
    }

    /** 重置第三方对接应用密钥，返回新的明文密钥 */
    @PostMapping("/apps/reset-secret")
    public R<String> resetClientAppSecret(@RequestParam String appCode) {
        SsoApplication app = requireClientApp(appCode);
        return R.ok(applicationService.resetSecret(app.getId()));
    }

    /** 删除第三方对接应用（含清理账号授权与权限码） */
    @PostMapping("/apps/delete")
    public R<Void> deleteClientApp(@RequestParam String appCode) {
        SsoApplication app = requireClientApp(appCode);
        applicationService.delete(app.getId());
        return R.ok(null);
    }

    /** 按 code 取启用/停用均可的应用，且必须是第三方对接类型；否则拒绝（防止误操作内部应用） */
    private SsoApplication requireClientApp(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        SsoApplication app = applicationService.requireByCode(appCode);
        if (!APP_TYPE_OPENAPI_CLIENT.equals(app.getAppType())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅可维护第三方对接应用（OPENAPI_CLIENT）");
        }
        return app;
    }

    private ApplicationRequest toApplicationRequest(InternalClientAppRequest req) {
        ApplicationRequest r = new ApplicationRequest();
        if (req != null) {
            r.setCode(req.getCode());
            r.setName(req.getName());
            r.setBaseUrl(req.getBaseUrl());
            r.setRedirectUri(req.getRedirectUri());
            r.setRemark(req.getRemark());
            r.setStatus(req.getStatus());
            r.setSort(req.getSort());
        }
        // 类型由服务端强制，杜绝调用方误建内部应用
        r.setAppType(APP_TYPE_OPENAPI_CLIENT);
        return r;
    }

    private ClientAppVO toClientAppVO(ApplicationVO a) {
        ClientAppVO vo = new ClientAppVO();
        vo.setId(a.getId());
        vo.setCode(a.getCode());
        vo.setName(a.getName());
        vo.setAppType(a.getAppType());
        vo.setBaseUrl(a.getBaseUrl());
        vo.setRedirectUri(a.getRedirectUri());
        vo.setRemark(a.getRemark());
        vo.setStatus(a.getStatus());
        vo.setSort(a.getSort());
        return vo;
    }

    /** 用库里的 app_secret 重算 HMAC-SHA256 并定长比对；应用停用或材料不全一律 false */
    @PostMapping("/verify-signature")
    public R<Boolean> verifySignature(@RequestBody InternalSignatureVerifyRequest req) {
        if (req == null || req.getAppCode() == null || req.getAppCode().isBlank()
                || req.getContent() == null || req.getSignature() == null
                || req.getTimestamp() == null || req.getNonce() == null) {
            return R.ok(false);
        }
        SsoApplication app;
        try {
            app = applicationService.requireEnabled(req.getAppCode());
        } catch (BizException e) {
            return R.ok(false);
        }
        if (app.getAppSecret() == null || app.getAppSecret().isBlank()) {
            return R.ok(false);
        }
        try {
            String canonical = req.getTimestamp() + "\n" + req.getNonce() + "\n" + req.getContent();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(app.getAppSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(req.getSignature());
            return R.ok(MessageDigest.isEqual(expected, actual));
        } catch (Exception e) {
            // 签名材料非法（如 signature 非 Hex）不作为服务端错误，统一判失败
            return R.ok(false);
        }
    }
}
