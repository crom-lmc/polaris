-- 接口开放平台结构基线（MySQL 5.7，独立库 polaris_openapi）
-- 可重复执行：CREATE TABLE IF NOT EXISTS + 幂等加列（prepared statement，无 DROP）。

CREATE TABLE IF NOT EXISTS `api_open_endpoint` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `api_code` varchar(64) NOT NULL COMMENT '开放API唯一标识，如 inbound.master.list',
  `api_name` varchar(128) NOT NULL COMMENT 'API名称',
  `direction` tinyint(4) NOT NULL COMMENT '1=INBOUND 2=OUTBOUND',
  `operation_type` varchar(16) NOT NULL DEFAULT 'QUERY' COMMENT '操作类型：QUERY/CREATE/UPDATE/DELETE/EXECUTE',
  `app_code` varchar(64) NOT NULL COMMENT '目标内部应用编码 / 出站渠道编码',
  `method` varchar(16) NOT NULL DEFAULT 'POST' COMMENT 'HTTP方法',
  `path` varchar(255) NOT NULL COMMENT '对外路径',
  `target_url` varchar(512) NOT NULL DEFAULT '' COMMENT '内部转发地址',
  `auth_type` varchar(32) NOT NULL DEFAULT 'OAUTH_CLIENT' COMMENT 'OAUTH_CLIENT/SIGNATURE/INTERNAL',
  `required_perm` varchar(128) NOT NULL DEFAULT '' COMMENT '需要的权限码（sso_permission.code）',
  `rate_limit_per_min` int(11) NOT NULL DEFAULT '60' COMMENT '每分钟调用上限',
  `timeout_ms` int(11) NOT NULL DEFAULT '3000' COMMENT '超时（毫秒）',
  `retry_max` int(11) NOT NULL DEFAULT '0' COMMENT '最大重试次数（仅幂等接口）',
  `idempotent` tinyint(4) NOT NULL DEFAULT '0' COMMENT '1=幂等，允许重试',
  `mask_rules` text COMMENT '脱敏字段规则 JSON',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 2停用',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_code` (`api_code`),
  KEY `idx_dir_app` (`direction`,`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='开放API目录';

CREATE TABLE IF NOT EXISTS `api_call_log` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `trace_id` varchar(64) NOT NULL COMMENT '全链路追踪ID',
  `direction` varchar(16) NOT NULL COMMENT 'INBOUND / OUTBOUND',
  `app_code` varchar(64) NOT NULL DEFAULT '' COMMENT '调用方应用（进）/ 目标渠道（出）',
  `api_code` varchar(64) NOT NULL DEFAULT '' COMMENT '匹配的开放API标识',
  `channel` varchar(64) NOT NULL DEFAULT '' COMMENT '出站渠道编码',
  `http_method` varchar(16) NOT NULL DEFAULT '',
  `request_path` varchar(512) NOT NULL DEFAULT '',
  `req_masked` mediumtext COMMENT '脱敏后的请求报文（超长截断）',
  `resp_masked` mediumtext COMMENT '脱敏后的响应报文（超长截断）',
  `http_status` int(11) NOT NULL DEFAULT '0' COMMENT 'HTTP状态码',
  `biz_code` varchar(64) NOT NULL DEFAULT '' COMMENT '业务错误码',
  `cost_ms` int(11) NOT NULL DEFAULT '0' COMMENT '耗时（毫秒）',
  `client_ip` varchar(64) NOT NULL DEFAULT '' COMMENT '来源IP',
  `error_msg` varchar(512) NOT NULL DEFAULT '' COMMENT '异常信息',
  `created_at` datetime NOT NULL COMMENT '调用时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace` (`trace_id`),
  KEY `idx_app_time` (`app_code`,`created_at`),
  KEY `idx_api_time` (`api_code`,`created_at`),
  KEY `idx_dir_time` (`direction`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='接口调用日志（进出统一）';

-- P2 平台设置（KV）：IP 白名单、全局限流、nonce 有效期、脱敏规则等
CREATE TABLE IF NOT EXISTS `api_open_setting` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `setting_key` varchar(64) NOT NULL COMMENT '配置键',
  `setting_value` text COMMENT '配置值（IP 白名单为换行分隔的 IP/CIDR/*）',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='开放平台设置(KV)';

CREATE TABLE IF NOT EXISTS `api_outbound_channel` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码，如 email',
  `channel_name` varchar(128) NOT NULL COMMENT '渠道名称',
  `base_url` varchar(512) NOT NULL DEFAULT '' COMMENT '第三方 base-url（邮件渠道可留空）',
  `auth_type` varchar(32) NOT NULL DEFAULT 'API_KEY' COMMENT 'API_KEY / OAUTH / SIGNATURE / NONE',
  `credential_ref` varchar(512) NOT NULL DEFAULT '' COMMENT '凭证密文 ENC(base64)，AES-GCM 加密的 SMTP 账号/口令 JSON；明文绝不入库、不回显',
  `auth_config` text COMMENT '认证附加配置 JSON（邮件渠道存 host/port/from/ssl 等非敏感项）',
  `timeout_ms` int(11) NOT NULL DEFAULT '3000' COMMENT '超时（毫秒）',
  `retry_max` int(11) NOT NULL DEFAULT '0' COMMENT '最大重试次数',
  `allowed_apps` text COMMENT '允许哪些内部 app 使用，逗号分隔；空=不限制',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 2停用',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='出站第三方渠道配置';

-- 第三方对接应用主数据：应用主数据 + 密钥，归开放平台本地库唯一持有。
-- app_secret 存 SHA-256 摘要（明文仅创建/重置返回一次），令牌 sub 用本表 id（数字）。
CREATE TABLE IF NOT EXISTS `api_client_app` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID，作为令牌 sub',
  `app_code` varchar(64) NOT NULL COMMENT '第三方应用编码（唯一，对外暴露），作为 client_app_code claim',
  `app_name` varchar(128) NOT NULL COMMENT '第三方应用名称',
  `app_secret` varchar(128) NOT NULL COMMENT '应用密钥 SHA-256 摘要（校验用）',
  `app_secret_plain` varchar(128) NOT NULL DEFAULT '' COMMENT '应用密钥明文（管理端随时查看，存库便于运维交接）',
  `base_url` varchar(512) NOT NULL DEFAULT '' COMMENT '第三方回调/基址',
  `redirect_uri` varchar(512) NOT NULL DEFAULT '',
  `remark` varchar(255) NOT NULL DEFAULT '',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 2停用',
  `sort` int(11) NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='第三方对接应用主数据';

-- 第三方应用被授权调用的开放接口：一行一个接口，api_code 为授权粒度，perms 唯一来源。
CREATE TABLE IF NOT EXISTS `api_client_app_permission` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `app_code` varchar(64) NOT NULL COMMENT '第三方应用编码',
  `api_code` varchar(64) NOT NULL DEFAULT '' COMMENT '开放接口编码，对应 api_open_endpoint.api_code',
  `perm_code` varchar(128) NOT NULL COMMENT '权限码，对应 api_open_endpoint.required_perm',
  `perm_name` varchar(128) NOT NULL DEFAULT '',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '授权状态：1=启用 2=停用',
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_app_api` (`app_code`,`api_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='第三方应用接口授权';

-- 兼容已部署库：幂等新增 app_secret_plain 列（明文密钥，供管理端查看）。
SET @app_secret_plain_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_client_app' AND COLUMN_NAME = 'app_secret_plain');
SET @app_secret_plain_sql = IF(@app_secret_plain_exists = 0,
  'ALTER TABLE `api_client_app` ADD COLUMN `app_secret_plain` varchar(128) NOT NULL DEFAULT '''' COMMENT ''应用密钥明文（管理端随时查看）'' AFTER `app_secret`',
  'SELECT 1');
PREPARE app_secret_plain_stmt FROM @app_secret_plain_sql;
EXECUTE app_secret_plain_stmt;
DEALLOCATE PREPARE app_secret_plain_stmt;

-- 兼容已部署库：幂等新增 status 列（默认启用）。
SET @perm_status_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_client_app_permission' AND COLUMN_NAME = 'status');
SET @perm_status_sql = IF(@perm_status_exists = 0,
  'ALTER TABLE `api_client_app_permission` ADD COLUMN `status` tinyint(4) NOT NULL DEFAULT ''1'' COMMENT ''授权状态：1=启用 2=停用'' AFTER `perm_name`',
  'SELECT 1');
PREPARE perm_status_stmt FROM @perm_status_sql;
EXECUTE perm_status_stmt;
DEALLOCATE PREPARE perm_status_stmt;

-- 兼容已部署库：授权粒度由「权限码」升级为「接口」。
-- 1) 幂等新增 api_code 列
SET @perm_api_code_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_client_app_permission' AND COLUMN_NAME = 'api_code');
SET @perm_api_code_sql = IF(@perm_api_code_exists = 0,
  'ALTER TABLE `api_client_app_permission` ADD COLUMN `api_code` varchar(64) NOT NULL DEFAULT '''' COMMENT ''开放接口编码'' AFTER `app_code`',
  'SELECT 1');
PREPARE perm_api_code_stmt FROM @perm_api_code_sql;
EXECUTE perm_api_code_stmt;
DEALLOCATE PREPARE perm_api_code_stmt;

-- 2) 先删旧唯一键 (app_code, perm_code)：否则同一权限码展开成多接口时会被唯一键拦掉。
SET @uk_perm_old = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_client_app_permission' AND INDEX_NAME = 'uk_client_app_perm');
SET @uk_perm_drop_sql = IF(@uk_perm_old > 0, 'ALTER TABLE `api_client_app_permission` DROP INDEX `uk_client_app_perm`', 'SELECT 1');
PREPARE uk_perm_drop_stmt FROM @uk_perm_drop_sql;
EXECUTE uk_perm_drop_stmt;
DEALLOCATE PREPARE uk_perm_drop_stmt;

-- 3) 回填 api_code：按 perm_code 关联接口目录，一条旧授权对应多个接口则展开为多行。
--    仅处理 api_code 为空的历史行；INSERT IGNORE 避免与已迁移行/新授权冲突。
INSERT IGNORE INTO `api_client_app_permission` (`id`, `app_code`, `api_code`, `perm_code`, `perm_name`, `status`, `created_at`)
SELECT FLOOR(UNIX_TIMESTAMP(NOW(6)) * 1000000) + (@rn := @rn + 1), p.app_code, e.api_code,
       p.perm_code, p.perm_name, p.status, p.created_at
FROM `api_client_app_permission` p
JOIN `api_open_endpoint` e ON e.required_perm = p.perm_code
CROSS JOIN (SELECT @rn := 0) r
WHERE (p.api_code IS NULL OR p.api_code = '') AND p.perm_code IS NOT NULL AND p.perm_code <> '';

-- 4) 删除未成功回填的历史行（无对应接口目录的孤立权限码），避免留下 api_code 为空的脏数据。
DELETE FROM `api_client_app_permission` WHERE api_code IS NULL OR api_code = '';

-- 5) 建新唯一键 (app_code, api_code)。
SET @uk_api_new = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_client_app_permission' AND INDEX_NAME = 'uk_client_app_api');
SET @uk_api_add_sql = IF(@uk_api_new = 0, 'ALTER TABLE `api_client_app_permission` ADD UNIQUE KEY `uk_client_app_api` (`app_code`,`api_code`)', 'SELECT 1');
PREPARE uk_api_add_stmt FROM @uk_api_add_sql;
EXECUTE uk_api_add_stmt;
DEALLOCATE PREPARE uk_api_add_stmt;

-- 兼容已部署库：幂等新增 operation_type 列（新库已随 CREATE TABLE 建好，此处检测到即跳过）。
SET @op_type_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_open_endpoint' AND COLUMN_NAME = 'operation_type');
SET @op_type_sql = IF(@op_type_exists = 0,
  'ALTER TABLE `api_open_endpoint` ADD COLUMN `operation_type` varchar(16) NOT NULL DEFAULT ''QUERY'' COMMENT ''操作类型：QUERY/CREATE/UPDATE/DELETE/EXECUTE'' AFTER `direction`',
  'SELECT 1');
PREPARE op_type_stmt FROM @op_type_sql;
EXECUTE op_type_stmt;
DEALLOCATE PREPARE op_type_stmt;
