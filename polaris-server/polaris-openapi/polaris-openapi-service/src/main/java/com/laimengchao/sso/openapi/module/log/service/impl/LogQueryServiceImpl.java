package com.laimengchao.sso.openapi.module.log.service.impl;

import com.laimengchao.sso.openapi.module.log.service.LogQueryService;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.AppInfo;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.openapi.module.channel.entity.ApiOutboundChannel;
import com.laimengchao.sso.openapi.module.channel.mapper.ApiOutboundChannelMapper;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.mapper.ApiOpenEndpointMapper;
import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import com.laimengchao.sso.openapi.module.log.mapper.ApiCallLogMapper;
import com.laimengchao.sso.openapi.module.log.vo.LogQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 调用日志查询（管理端）：分页 + 条件过滤。报文在写入时已脱敏，这里原样返回，不再二次处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogQueryServiceImpl implements LogQueryService {

    private final ApiCallLogMapper mapper;
    private final ApiOpenEndpointMapper endpointMapper;
    private final ApiOutboundChannelMapper channelMapper;
    private final AccountApi accountApi;

    /** 分页查询。 */
    public IPage<ApiCallLog> page(LogQuery q) {
        int page = Math.max(1, q.getPage());
        int size = Math.min(200, Math.max(1, q.getSize()));
        Page<ApiCallLog> p = new Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCallLog> w = wrapper(q);
        w.orderByDesc(ApiCallLog::getCreatedAt);
        IPage<ApiCallLog> result = mapper.selectPage(p, w);
        fillNames(result.getRecords());
        return result;
    }

    /** 导出：不分页，最多 5000 条，防止一次性拉爆内存。 */
    public List<ApiCallLog> export(LogQuery q) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCallLog> w = wrapper(q);
        w.last("ORDER BY created_at DESC LIMIT 5000");
        List<ApiCallLog> rows = mapper.selectList(w);
        fillNames(rows);
        return rows;
    }

    /** 详情。 */
    public ApiCallLog detail(Long id) {
        ApiCallLog row = mapper.selectById(id);
        if (row != null) {
            fillNames(Collections.singletonList(row));
        }
        return row;
    }

    /**
     * 批量补齐展示名：apiCode→接口名、appCode→第三方应用名、channel→出站渠道名。
     * 映射表一次加载后用 Map 命中，避免逐条查询；任一来源不可用只留空，不影响日志主流程。
     */
    private void fillNames(List<ApiCallLog> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, String> apiNames;
        try {
            apiNames = endpointMapper.selectList(null).stream()
                    .filter(e -> e.getApiCode() != null)
                    .collect(Collectors.toMap(ApiOpenEndpoint::getApiCode, ApiOpenEndpoint::getApiName,
                            (a, b) -> a, java.util.LinkedHashMap::new));
        } catch (Exception e) {
            log.warn("补齐日志接口名称失败，回退显示编码", e);
            apiNames = Collections.emptyMap();
        }
        Map<String, String> appNames;
        try {
            R<List<AppInfo>> resp = accountApi.listEnabledApps();
            List<AppInfo> apps = (resp == null || !resp.ok() || resp.getData() == null)
                    ? Collections.emptyList() : resp.getData();
            appNames = apps.stream()
                    .filter(a -> a.getCode() != null)
                    .collect(Collectors.toMap(AppInfo::getCode, AppInfo::getName, (a, b) -> a,
                            java.util.LinkedHashMap::new));
        } catch (Exception e) {
            log.warn("补齐日志应用名称失败，回退显示编码", e);
            appNames = Collections.emptyMap();
        }
        Map<String, String> channelNames;
        try {
            channelNames = channelMapper.selectList(null).stream()
                    .filter(c -> c.getChannelCode() != null)
                    .collect(Collectors.toMap(ApiOutboundChannel::getChannelCode, ApiOutboundChannel::getChannelName,
                            (a, b) -> a, java.util.LinkedHashMap::new));
        } catch (Exception e) {
            log.warn("补齐日志渠道名称失败，回退显示编码", e);
            channelNames = Collections.emptyMap();
        }

        for (ApiCallLog row : rows) {
            row.setApiName(apiNames.get(row.getApiCode()));
            row.setAppName(appNames.get(row.getAppCode()));
            row.setChannelName(channelNames.get(row.getChannel()));
        }
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCallLog> wrapper(LogQuery q) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCallLog> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (q.getDirection() != null && !q.getDirection().isBlank()) {
            w.eq(ApiCallLog::getDirection, q.getDirection().trim());
        }
        if (q.getAppCode() != null && !q.getAppCode().isBlank()) {
            w.eq(ApiCallLog::getAppCode, q.getAppCode().trim());
        }
        if (q.getApiCode() != null && !q.getApiCode().isBlank()) {
            w.eq(ApiCallLog::getApiCode, q.getApiCode().trim());
        }
        if (q.getChannel() != null && !q.getChannel().isBlank()) {
            w.eq(ApiCallLog::getChannel, q.getChannel().trim());
        }
        if (q.getFrom() != null && !q.getFrom().isBlank()) {
            w.ge(ApiCallLog::getCreatedAt, parse(q.getFrom()));
        }
        if (q.getTo() != null && !q.getTo().isBlank()) {
            w.le(ApiCallLog::getCreatedAt, parse(q.getTo()));
        }
        if (Boolean.TRUE.equals(q.getOnlyFailed())) {
            w.apply("(http_status >= 400 OR (biz_code IS NOT NULL AND biz_code <> '0'))");
        }
        return w;
    }

    private LocalDateTime parse(String s) {
        return LocalDateTime.parse(s.trim().replace(' ', 'T'),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }
}
