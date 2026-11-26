package com.laimengchao.sso.file.module.file.service;

import java.util.Map;

public interface AttachmentSettingService {

    Map<String, String> all();

    String get(String key, String defaultValue);

    boolean bool(String key, boolean defaultValue);

    int integer(String key, int defaultValue);

    void save(Map<String, String> values);

}
