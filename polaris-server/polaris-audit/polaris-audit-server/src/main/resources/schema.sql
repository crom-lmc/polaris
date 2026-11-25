-- 统一审计中心库（sql.init.mode=always，全部幂等）
-- 网关 ACCESS、账号 LOGIN/SENSITIVE、EHR SENSITIVE 统一写本表；只追加，不修改、不删除。
CREATE TABLE IF NOT EXISTS `audit_event` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `app` varchar(32) NOT NULL DEFAULT '' COMMENT '来源应用：sso/ehr/cms/forum/file/openapi/job/settings/portal',
  `event_category` varchar(16) NOT NULL DEFAULT 'ACCESS' COMMENT 'ACCESS/LOGIN/BUSINESS/SENSITIVE',
  `action` varchar(128) NOT NULL DEFAULT '' COMMENT '动作码：GET /path、LOGIN_SUCCESS、VIEW 等',
  `http_method` varchar(16) NOT NULL DEFAULT '' COMMENT 'HTTP 方法，非访问事件为空',
  `request_path` varchar(512) NOT NULL DEFAULT '' COMMENT '请求路径，非访问事件为空',
  `operator_type` varchar(16) NOT NULL DEFAULT 'SYSTEM' COMMENT 'USER/SYSTEM',
  `operator_id` varchar(64) NOT NULL DEFAULT '' COMMENT '操作人账号ID；登录失败或 SYSTEM 可为空',
  `operator_name` varchar(64) NOT NULL DEFAULT '' COMMENT '用户名；登录事件填尝试登录的用户名',
  `target_type` varchar(64) NOT NULL DEFAULT '' COMMENT '对象类型：ACCOUNT/EMPLOYEE/FILE 等',
  `target_id` varchar(64) NOT NULL DEFAULT '' COMMENT '对象ID，可为业务字符串',
  `detail` varchar(2000) NOT NULL DEFAULT '' COMMENT 'result=success/fail 及非敏感详情；禁止写密码/令牌',
  `request_params` text COMMENT '请求入参（已脱敏）：query+JSON body 摘要；非 JSON/文件流不采集',
  `http_status` int(11) DEFAULT NULL COMMENT 'HTTP 状态码；空表示非网关事件',
  `biz_code` int(11) DEFAULT NULL COMMENT '业务返回码 R.code；空表示未知',
  `response_summary` varchar(2000) NOT NULL DEFAULT '' COMMENT '响应结果摘要（已脱敏）：R.code/msg 或 HTTP 状态描述',
  `cost_ms` bigint(20) DEFAULT NULL COMMENT '处理耗时毫秒：网关从进入到响应完成',
  `client_ip` varchar(64) NOT NULL DEFAULT '' COMMENT '客户端IP',
  `user_agent` varchar(512) NOT NULL DEFAULT '' COMMENT 'User-Agent',
  `login_type` varchar(32) NOT NULL DEFAULT '' COMMENT '登录方式：password/email/wechat',
  `result` varchar(16) NOT NULL DEFAULT '' COMMENT 'success/fail',
  `fail_reason` varchar(500) NOT NULL DEFAULT '' COMMENT '登录失败原因',
  `created_at` datetime NOT NULL COMMENT '事件发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_created` (`created_at`),
  KEY `idx_app_category_time` (`app`,`event_category`,`created_at`),
  KEY `idx_operator_time` (`operator_name`,`created_at`),
  KEY `idx_action_time` (`action`,`created_at`),
  KEY `idx_login_time_result` (`event_category`,`created_at`,`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='平台统一审计事件（单表事件流）';

-- 系统运行日志：各服务 Logback Appender 异步批量上报，与审计事件分开存储。
-- 只追加；保留期按天清理（audit.retention-days 或后续调度任务）。
CREATE TABLE IF NOT EXISTS `system_log` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `app` varchar(64) NOT NULL DEFAULT '' COMMENT '来源应用：sso-gateway/sso-auth/sso-account/cms/ehr/forum/file/settings/job/portal/openapi',
  `level` varchar(16) NOT NULL DEFAULT 'INFO' COMMENT 'TRACE/DEBUG/INFO/WARN/ERROR',
  `logger` varchar(255) NOT NULL DEFAULT '' COMMENT 'logger 名（通常类全限定名）',
  `thread` varchar(128) NOT NULL DEFAULT '' COMMENT '线程名',
  `message` text NOT NULL COMMENT '日志正文（已脱敏）',
  `stack_trace` text NOT NULL COMMENT '异常堆栈（已脱敏，无异常为空）',
  `created_at` datetime NOT NULL COMMENT '日志发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_created` (`created_at`),
  KEY `idx_app_level_time` (`app`,`level`,`created_at`),
  KEY `idx_level_time` (`level`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='平台系统运行日志（各服务 Logback 上报）';

-- 审计全局设置：K-V 集中存采集/忽略/留存/脱敏/导出策略，运行时可改，审计服务自消费即时生效。
-- 注意：生产 AUDIT_SQL_INIT_MODE=never，新表需随部署手工执行本文件（CREATE TABLE IF NOT EXISTS 幂等）。
CREATE TABLE IF NOT EXISTS `audit_setting` (
  `setting_key` varchar(128) NOT NULL COMMENT '配置项 key，点分命名（见 AuditSettingService 常量）',
  `setting_value` varchar(2000) NOT NULL DEFAULT '' COMMENT '配置值：布尔 0/1，数字文本，列表逗号分隔',
  `updated_at` datetime NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='审计全局设置（K-V）';
