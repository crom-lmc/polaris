package com.laimengchao.sso.openapi.module.authz.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.app.entity.ApiClientAppPermission;
import com.laimengchao.sso.openapi.module.app.service.ClientAppService;
import com.laimengchao.sso.openapi.module.app.vo.ClientAppVo;
import com.laimengchao.sso.openapi.module.authz.vo.AuthzVo;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.service.EndpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 应用授权管理：维护「第三方应用 × 开放接口」的授权关系。
 * <p>
 * 授权数据落在开放平台本地库 api_client_app_permission，
 * 令牌签发时读取写入 JWT perms，不经过角色绑定，因此这里写入即刻生效（下次换票）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthzService {

    private final ClientAppService clientAppService;
    private final EndpointService endpointService;

    /** 授权列表：一行授权对应一个接口，启停/删除只作用于该接口。 */
    public List<AuthzVo> list() {
        List<ClientAppVo> apps = clientAppService.list();
        if (apps.isEmpty()) {
            return List.of();
        }
        Map<String, ApiOpenEndpoint> endpointByCode = endpointService.list().stream()
                .filter(e -> e.getApiCode() != null)
                .collect(Collectors.toMap(ApiOpenEndpoint::getApiCode, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));

        List<AuthzVo> out = new ArrayList<>();
        for (ClientAppVo app : apps) {
            if (app.getCode() == null || app.getCode().isBlank()) {
                continue;
            }
            List<ApiClientAppPermission> perms = clientAppService.permissionList(app.getCode());
            for (ApiClientAppPermission perm : perms) {
                ApiOpenEndpoint ep = endpointByCode.get(perm.getApiCode());
                if (ep == null) {
                    continue; // 接口已被删除，不展示
                }
                out.add(toVo(app, ep, perm.getStatus()));
            }
        }
        return out;
    }

    private AuthzVo toVo(ClientAppVo app, ApiOpenEndpoint ep, Integer authStatus) {
        AuthzVo vo = new AuthzVo();
        vo.setAppCode(app.getCode());
        vo.setAppName(app.getName());
        vo.setApiCode(ep.getApiCode());
        vo.setApiName(ep.getApiName());
        vo.setDirection(ep.getDirection());
        boolean inbound = ep.getDirection() == null || ep.getDirection() == 1;
        vo.setDirectionLabel(inbound ? "对外提供（出）" : "调用第三方（进）");
        vo.setOperationType(ep.getOperationType());
        vo.setMethod(ep.getMethod());
        vo.setPath(ep.getPath());
        vo.setRequiredPerm(ep.getRequiredPerm());
        vo.setEndpointStatus(ep.getStatus());
        // 兼容历史数据 status 为 null 时按启用处理
        vo.setStatus(authStatus == null ? 1 : authStatus);
        return vo;
    }

    /** 批量授权：api_code → required_perm 后幂等落本地库，返回新建条数。 */
    public int grant(String appCode, List<String> apiCodes) {
        requireThirdPartyApp(appCode);
        if (apiCodes == null || apiCodes.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要授权的接口");
        }
        Map<String, ApiOpenEndpoint> endpointByCode = endpointService.list().stream()
                .filter(e -> e.getApiCode() != null)
                .collect(Collectors.toMap(ApiOpenEndpoint::getApiCode, Function.identity(), (a, b) -> a));

        List<ApiClientAppPermission> items = new ArrayList<>();
        for (String apiCode : apiCodes) {
            ApiOpenEndpoint ep = endpointByCode.get(apiCode);
            if (ep == null) {
                throw new BizException(OpenapiErrorCode.API_NOT_FOUND, "接口不存在：" + apiCode);
            }
            if (ep.getRequiredPerm() == null || ep.getRequiredPerm().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "接口「" + ep.getApiName() + "」未配置所需权限码，无法授权");
            }
            ApiClientAppPermission item = new ApiClientAppPermission();
            item.setApiCode(ep.getApiCode());
            item.setPermCode(ep.getRequiredPerm());
            item.setPermName(ep.getApiName());
            items.add(item);
        }
        int n = clientAppService.grant(appCode, items);
        log.info("应用授权成功：appCode={} 新建={} 提交={}", appCode, n, items.size());
        return n;
    }

    /** 撤销授权：只删该应用对该接口的授权，不影响其他接口。 */
    public int revoke(String appCode, String apiCode) {
        requireThirdPartyApp(appCode);
        int n = clientAppService.revoke(appCode, apiCode);
        log.info("撤销应用授权：appCode={} apiCode={} 删除={}", appCode, apiCode, n);
        return n;
    }

    /** 启停授权：只作用于该接口。1=启用 2=停用，停用后下次换票不再带该接口编码。 */
    public int toggle(String appCode, String apiCode, Integer status) {
        requireThirdPartyApp(appCode);
        return clientAppService.togglePermission(appCode, apiCode, status);
    }

    /** 校验 appCode 是存在的第三方对接应用 */
    private void requireThirdPartyApp(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        clientAppService.requireByCode(appCode);
    }
}
