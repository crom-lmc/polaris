package com.laimengchao.sso.meeting.module.meeting.service.impl;

import com.laimengchao.sso.meeting.module.meeting.service.MeetingSupportService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.client.api.EhrEmployeeApi;
import com.laimengchao.sso.ehr.client.api.EhrOrgApi;
import com.laimengchao.sso.ehr.client.dto.R;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.settings.client.dto.DictItemDTO;
import com.laimengchao.sso.settings.client.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 平台能力读取门面：城市、字典、埋点上限、员工搜索。配置类数据一律不落本服务本地表。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingSupportServiceImpl implements MeetingSupportService {

    public static final String EMBED_MAX_ADVANCE_DAYS = "meeting.booking.max_advance_days";
    public static final String EMBED_NO_SHOW_GRACE_MINUTES = "meeting.booking.no_show_grace_minutes";
    public static final String EMBED_MAX_BOOKING_MINUTES = "meeting.booking.max_duration_minutes";
    public static final String EMBED_NO_SHOW_RESTRICTION = "meeting.booking.no_show_restriction";
    public static final String DICT_CITY = "ehr.job_city";
    public static final String DICT_ROOM_TYPE = "meeting.room_type";
    public static final String DICT_ROOM_CAPACITY = "meeting.room_capacity";
    public static final String DICT_ROOM_STATUS = "meeting.room_status";
    public static final String DICT_LOCATION_STATUS = "meeting.location_status";
    public static final String DICT_DEVICE = "meeting.device";
    public static final String DICT_SEAT_LAYOUT = "meeting.seat_layout";
    public static final String DICT_BOOKING_STATUS = "meeting.booking_status";

    private static final int DEFAULT_MAX_DAYS = 7;

    private static final List<String> DICT_TYPES = List.of(
            DICT_CITY, DICT_ROOM_TYPE, DICT_ROOM_CAPACITY, DICT_ROOM_STATUS,
            DICT_LOCATION_STATUS, DICT_DEVICE, DICT_SEAT_LAYOUT, DICT_BOOKING_STATUS);

    private final SettingsClient settingsClient;
    private final EhrEmployeeApi ehrEmployeeApi;
    private final EhrOrgApi ehrOrgApi;
    private final ObjectMapper objectMapper;

    /** 当前账号；未登录直接拒绝（fail-closed，绝不返回全量） */
    public Long requireAccountId() {
        Long id = AccountContext.getAccountId();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    /** 当前人城市：EHR 组织节点的二级单位 city；取不到返回空串（调用方按 fail-closed 处理） */
    public String currentCity() {
        Long accountId = AccountContext.getAccountId();
        if (accountId == null) {
            return "";
        }
        try {
            R<String> resp = ehrEmployeeApi.city(accountId);
            String city = resp != null && resp.ok() ? resp.getData() : null;
            return city == null ? "" : city.trim();
        } catch (Exception e) {
            log.warn("读取当前人城市失败：accountId={} {}", accountId, e.getMessage());
            return "";
        }
    }

    /** 可提前预约天数：埋点缺失/非法兜底 7；0 表示只能约当天 */
    public int maxAdvanceDays() {
        try {
            EmbedPointDTO point = settingsClient.embedPoint(EMBED_MAX_ADVANCE_DAYS);
            if (point == null || point.getValue() == null || point.getValue().isBlank()) {
                log.error("埋点 {} 未配置，按默认 {} 天处理", EMBED_MAX_ADVANCE_DAYS, DEFAULT_MAX_DAYS);
                return DEFAULT_MAX_DAYS;
            }
            JsonNode node = objectMapper.readTree(point.getValue());
            JsonNode max = node.get("maxDays");
            if (max == null || !max.canConvertToInt() || max.asInt() < 0) {
                log.error("埋点 {} 的 maxDays 非法：{}", EMBED_MAX_ADVANCE_DAYS, point.getValue());
                return DEFAULT_MAX_DAYS;
            }
            return max.asInt();
        } catch (Exception e) {
            log.error("读取埋点 {} 失败：{}", EMBED_MAX_ADVANCE_DAYS, e.getMessage());
            return DEFAULT_MAX_DAYS;
        }
    }

    /** 未签到自动释放宽限期：埋点缺失/非法兜底 15 分钟，0 表示会议开始即释放 */
    public int noShowGraceMinutes() {
        return embedInt(EMBED_NO_SHOW_GRACE_MINUTES, "graceMinutes", 15, 0, 240);
    }

    /** 单次预约最长时长（分钟）：0 表示不限制；缺失/非法按 0 兜底，保持原有可用范围 */
    public int maxBookingMinutes() {
        return embedInt(EMBED_MAX_BOOKING_MINUTES, "maxMinutes", 0, 0, 1440);
    }

    /** 爽约限制规则：统计天数 / 触发次数 / 禁约天数，任一字段非法时仅该字段回退默认值 */
    public NoShowRule noShowRule() {
        JsonNode node = embedJson(EMBED_NO_SHOW_RESTRICTION);
        return new NoShowRule(
                readInt(node, EMBED_NO_SHOW_RESTRICTION, "windowDays", 90, 1, 365),
                readInt(node, EMBED_NO_SHOW_RESTRICTION, "threshold", 3, 0, 100),
                readInt(node, EMBED_NO_SHOW_RESTRICTION, "banDays", 7, 0, 365));
    }

    private int embedInt(String code, String field, int fallback, int min, int max) {
        return readInt(embedJson(code), code, field, fallback, min, max);
    }

    private JsonNode embedJson(String code) {
        try {
            EmbedPointDTO point = settingsClient.embedPoint(code);
            if (point == null || point.getValue() == null || point.getValue().isBlank()) {
                // 埋点缺失/配置中心短暂不可用时走内置兜底是预期路径，不该记 ERROR。
                log.warn("埋点 {} 未配置，使用内置兜底值", code);
                return null;
            }
            return objectMapper.readTree(point.getValue());
        } catch (Exception e) {
            log.error("读取埋点 {} 失败：{}", code, e.getMessage());
            return null;
        }
    }

    private int readInt(JsonNode node, String code, String field, int fallback, int min, int max) {
        if (node == null) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt() || value.asInt() < min || value.asInt() > max) {
            log.error("埋点 {} 的 {} 非法（合法范围 [{},{}]）：{}", code, field, min, max, node);
            return fallback;
        }
        return value.asInt();
    }

    /** 一次性返回本应用全部字典（前端统一从会议服务读取，不直连 settings internal API） */
    public Map<String, List<MeetingVOs.DictItemVO>> dicts() {
        Map<String, List<DictItemDTO>> raw = settingsClient.dictBatch(DICT_TYPES);
        Map<String, List<MeetingVOs.DictItemVO>> out = new LinkedHashMap<>();
        for (String type : DICT_TYPES) {
            List<MeetingVOs.DictItemVO> items = new ArrayList<>();
            for (DictItemDTO item : raw.getOrDefault(type, List.of())) {
                MeetingVOs.DictItemVO vo = new MeetingVOs.DictItemVO();
                vo.setValue(item.getValue());
                vo.setLabel(item.getLabel());
                vo.setSort(item.getSort());
                items.add(vo);
            }
            out.put(type, items);
        }
        return out;
    }

    /** 员工搜索：复用 EHR 在职员工主数据，按姓名/工号过滤，不新建人员表 */
    public List<MeetingVOs.EmployeeVO> searchEmployees(String keyword, int limit) {
        List<MeetingVOs.EmployeeVO> out = new ArrayList<>();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        try {
            R<List<Map<String, Object>>> resp = ehrOrgApi.activeEmployees();
            if (resp == null || !resp.ok() || resp.getData() == null) {
                return out;
            }
            for (Map<String, Object> row : resp.getData()) {
                String name = String.valueOf(row.getOrDefault("name", ""));
                String empNo = String.valueOf(row.getOrDefault("empNo", ""));
                if (!kw.isEmpty() && !name.toLowerCase().contains(kw) && !empNo.toLowerCase().contains(kw)) {
                    continue;
                }
                MeetingVOs.EmployeeVO vo = new MeetingVOs.EmployeeVO();
                vo.setAccountId(String.valueOf(row.getOrDefault("id", "")));
                vo.setName(name);
                vo.setEmpNo(empNo);
                out.add(vo);
                if (out.size() >= limit) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("搜索员工失败：{}", e.getMessage());
        }
        return out;
    }

    /** 按账号 ID 批量取员工姓名（详情/修改回显用）；查不到时回退为 ID */
    public Map<String, String> employeeNames(Collection<Long> ids) {
        Map<String, String> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        List<String> wanted = ids.stream().filter(id -> id != null).map(String::valueOf).distinct().toList();
        for (String id : wanted) {
            out.put(id, id);
        }
        try {
            R<List<Map<String, Object>>> resp = ehrOrgApi.activeEmployees();
            if (resp == null || !resp.ok() || resp.getData() == null) {
                return out;
            }
            for (Map<String, Object> row : resp.getData()) {
                String key = String.valueOf(row.getOrDefault("id", ""));
                if (!out.containsKey(key)) {
                    continue;
                }
                String name = String.valueOf(row.getOrDefault("name", ""));
                String empNo = String.valueOf(row.getOrDefault("empNo", ""));
                if (!name.isBlank() && !"null".equals(name)) {
                    String suffix = empNo.isBlank() || "null".equals(empNo) ? "" : "（" + empNo + "）";
                    out.put(key, name + suffix);
                }
            }
        } catch (Exception e) {
            log.warn("批量读取员工姓名失败：{}", e.getMessage());
        }
        return out;
    }

    /** 当前组织编码：仅写入预留字段，本期查询不使用 */
    public String orgCode() {
        String code = AccountContext.getDataScopeOrgCode();
        return code == null ? "" : code;
    }

    public Collection<Long> parseAccountIds(Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }
}
