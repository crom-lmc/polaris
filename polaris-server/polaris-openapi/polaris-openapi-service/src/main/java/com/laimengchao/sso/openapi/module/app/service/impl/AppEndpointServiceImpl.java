package com.laimengchao.sso.openapi.module.app.service.impl;

import com.laimengchao.sso.openapi.module.app.service.AppEndpointService;
import com.laimengchao.sso.openapi.module.app.service.ClientAppService;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.openapi.module.app.entity.ApiClientApp;
import com.laimengchao.sso.openapi.module.app.vo.AppEndpointVo;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.service.EndpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 第三方对接应用可调用接口清单：按应用域下的开放 API 权限码匹配 endpoint 目录。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppEndpointServiceImpl implements AppEndpointService {

    private static final String PLATFORM_NAME = "统一开放平台";

    private final ClientAppService clientAppService;
    private final EndpointService endpointService;

    /** 取指定第三方应用被授权调用的接口清单（含方向文案）。 */
    public List<AppEndpointVo> listByApp(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        ApiClientApp app = clientAppService.requireByCode(appCode);
        Set<String> authorizedApiCodes = new HashSet<>(clientAppService.permissions(appCode));
        final String appName = app.getAppName() == null || app.getAppName().isBlank()
                ? appCode : app.getAppName();

        return endpointService.list().stream()
                .filter(ep -> ep.getApiCode() != null && authorizedApiCodes.contains(ep.getApiCode()))
                .map(ep -> toVo(ep, appName))
                .toList();
    }

    private AppEndpointVo toVo(ApiOpenEndpoint ep, String appName) {
        AppEndpointVo vo = new AppEndpointVo();
        vo.setApiCode(ep.getApiCode());
        vo.setApiName(ep.getApiName());
        vo.setDirection(ep.getDirection());
        boolean inbound = ep.getDirection() == null || ep.getDirection() == 1;
        // 业务语义按用户定义：对外提供=出（底层 INBOUND），调用第三方=进（底层 OUTBOUND）
        vo.setDirectionLabel(inbound ? "对外提供" : "调用第三方");
        vo.setDirectionFlow(inbound
                ? appName + " → " + PLATFORM_NAME
                : PLATFORM_NAME + " → " + (ep.getAppCode() == null ? "第三方系统" : ep.getAppCode()));
        vo.setOperationType(ep.getOperationType());
        vo.setMethod(ep.getMethod());
        vo.setPath(ep.getPath());
        vo.setAppCode(ep.getAppCode());
        vo.setRequiredPerm(ep.getRequiredPerm());
        vo.setStatus(ep.getStatus());
        return vo;
    }
}
