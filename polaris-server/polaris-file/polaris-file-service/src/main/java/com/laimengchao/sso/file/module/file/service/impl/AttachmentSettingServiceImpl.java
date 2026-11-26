package com.laimengchao.sso.file.module.file.service.impl;

import com.laimengchao.sso.file.module.file.service.AttachmentSettingService;
import com.laimengchao.sso.file.module.file.entity.AttachmentSetting;
import com.laimengchao.sso.file.module.file.mapper.AttachmentSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 附件全局设置：key-value 存取，各能力点按 key 读取，改动即时生效。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentSettingServiceImpl implements AttachmentSettingService {

    public static final String PREVIEW_ENABLED = "preview.enabled";
    public static final String PREVIEW_MAX_SIZE_MB = "preview.maxSizeMb";
    public static final String THUMBNAIL_MAX_EDGE = "thumbnail.maxEdge";
    public static final String DEDUP_ENABLED = "dedup.enabled";
    public static final String SIGN_TTL_SECONDS = "sign.ttlSeconds";
    public static final String AUDIT_DOWNLOAD = "audit.download";
    public static final String RECYCLE_KEEP_DAYS = "recycle.keepDays";

    private final AttachmentSettingMapper mapper;

    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        for (AttachmentSetting s : mapper.selectList(null)) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return map;
    }

    public String get(String key, String defaultValue) {
        AttachmentSetting s = mapper.selectById(key);
        return s == null || s.getSettingValue() == null ? defaultValue : s.getSettingValue();
    }

    public boolean bool(String key, boolean defaultValue) {
        String v = get(key, null);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    public int integer(String key, int defaultValue) {
        try {
            String v = get(key, null);
            return v == null || v.isBlank() ? defaultValue : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void save(Map<String, String> values) {
        if (values == null) {
            return;
        }
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                return;
            }
            AttachmentSetting s = mapper.selectById(key);
            if (s == null) {
                mapper.insert(new AttachmentSetting(key, value == null ? "" : value, LocalDateTime.now()));
            } else {
                s.setSettingValue(value == null ? "" : value);
                s.setUpdatedAt(LocalDateTime.now());
                mapper.updateById(s);
            }
        });
        log.info("附件设置已更新：keys={}", values.keySet());
    }

}
