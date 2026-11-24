-- ============================================================
-- 审计中心拆分迁移：sso_ms.sso_audit_event / sso_audit_log / sso_login_log
--                 + ehr.ehr_audit_log  →  polaris_audit.audit_event
-- ------------------------------------------------------------
-- 执行前提：
--   1. audit-server 已启动过一次（schema.sql 已建 polaris_audit.audit_event）；
--   2. 旧库仍在原处（本脚本只读旧表，不删除数据）；
--   3. 建议在停写窗口或低峰期执行，避免迁移过程中新增记录漏迁。
-- 幂等：按派生后的固定 ID + NOT EXISTS 过滤，重复执行不会插入重复行。
-- 四类来源用不同 ID 偏移，避免雪花 ID 与自增 ID 在目标表碰撞。
-- 兼容：部分环境从未部署过 sso_audit_event。用临时视图做空源，脚本结束后自动删除，
--       既不影响真实表，也不会因源表缺失而中断其余三类迁移。
-- ============================================================

-- ---------------- 0. 审计事件源表兼容视图 ----------------
DROP VIEW IF EXISTS sso_ms.sso_audit_event_mig;
SET @audit_event_src_exists := (SELECT COUNT(*) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'sso_ms' AND TABLE_NAME = 'sso_audit_event' AND TABLE_TYPE = 'BASE TABLE');
SET @sql := IF(@audit_event_src_exists = 1,
  'CREATE VIEW sso_ms.sso_audit_event_mig AS SELECT * FROM sso_ms.sso_audit_event',
  'CREATE VIEW sso_ms.sso_audit_event_mig AS SELECT NULL AS id, NULL AS app, NULL AS event_category, NULL AS action, NULL AS http_method, NULL AS request_path, NULL AS operator_type, NULL AS operator_id, NULL AS operator_name, NULL AS target_type, NULL AS target_id, NULL AS detail, NULL AS client_ip, NULL AS user_agent, NULL AS created_at FROM DUAL WHERE 1 = 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------- 1. 统一审计事件（网关 ACCESS / 登录 LOGIN / 业务 BUSINESS） ----------------
INSERT INTO polaris_audit.audit_event
  (id, app, event_category, action, http_method, request_path, operator_type,
   operator_id, operator_name, target_type, target_id, detail, client_ip, user_agent,
   login_type, result, fail_reason, created_at)
SELECT
  src.id,
  IF(src.app IS NULL OR src.app = '', 'sso', src.app),
  IF(src.event_category IS NULL OR src.event_category = '', 'ACCESS', src.event_category),
  IFNULL(src.action, ''),
  IFNULL(src.http_method, ''),
  IFNULL(src.request_path, ''),
  IFNULL(src.operator_type, 'USER'),
  IFNULL(CAST(src.operator_id AS CHAR), ''),
  IFNULL(src.operator_name, 'SYSTEM'),
  IFNULL(src.target_type, ''),
  IFNULL(src.target_id, ''),
  IFNULL(src.detail, ''),
  IFNULL(src.client_ip, ''),
  IFNULL(src.user_agent, ''),
  IF(src.event_category = 'LOGIN', SUBSTRING_INDEX(SUBSTRING_INDEX(IFNULL(src.detail, ''), 'loginType=', -1), ';', 1), ''),
  IF(src.detail LIKE 'result=fail%', 'fail', 'success'),
  IF(src.event_category = 'LOGIN' AND LOCATE(';reason=', IFNULL(src.detail, '')) > 0,
     SUBSTRING_INDEX(SUBSTRING_INDEX(src.detail, ';reason=', -1), ';', 1), ''),
  IFNULL(src.created_at, NOW())
FROM sso_ms.sso_audit_event_mig src
WHERE NOT EXISTS (SELECT 1 FROM polaris_audit.audit_event t WHERE t.id = src.id);

-- ---------------- 2. SSO 敏感操作审计 → SENSITIVE ----------------
INSERT INTO polaris_audit.audit_event
  (id, app, event_category, action, http_method, request_path, operator_type,
   operator_id, operator_name, target_type, target_id, detail, client_ip, user_agent,
   login_type, result, fail_reason, created_at)
SELECT
  src.id + 2000000000000000000,
  'sso',
  'SENSITIVE',
  IFNULL(src.action, ''),
  '', '',
  'USER',
  CAST(src.operator_id AS CHAR),
  IFNULL(src.operator_name, 'SYSTEM'),
  IFNULL(src.target_type, ''),
  IFNULL(CAST(src.target_id AS CHAR), ''),
  IFNULL(src.fields, ''),
  IFNULL(src.ip, ''),
  IFNULL(src.ua, ''),
  '',
  IF(src.fields LIKE 'result=fail%', 'fail', 'success'),
  '',
  IFNULL(src.created_at, NOW())
FROM sso_ms.sso_audit_log src
WHERE NOT EXISTS (SELECT 1 FROM polaris_audit.audit_event t WHERE t.id = src.id + 2000000000000000000);

-- ---------------- 3. 登录日志 → LOGIN（含失败原因与登录方式） ----------------
INSERT INTO polaris_audit.audit_event
  (id, app, event_category, action, http_method, request_path, operator_type,
   operator_id, operator_name, target_type, target_id, detail, client_ip, user_agent,
   login_type, result, fail_reason, created_at)
SELECT
  src.id + 4000000000000000000,
  'sso',
  'LOGIN',
  IF(src.result = 'success', 'LOGIN_SUCCESS', 'LOGIN_FAIL'),
  'POST',
  '/api/auth/login',
  'USER',
  IFNULL(CAST(src.account_id AS CHAR), ''),
  IFNULL(src.username, ''),
  'ACCOUNT',
  IF(src.account_id IS NULL, IFNULL(src.username, ''), CAST(src.account_id AS CHAR)),
  CONCAT('result=', IFNULL(src.result, 'success'),
         IF(src.result = 'success' OR src.fail_reason IS NULL OR src.fail_reason = '', '', CONCAT(';reason=', src.fail_reason)),
         ';loginType=', IFNULL(src.login_type, 'password')),
  IFNULL(src.client_ip, ''),
  IFNULL(src.user_agent, ''),
  IFNULL(src.login_type, 'password'),
  IFNULL(src.result, 'success'),
  IFNULL(src.fail_reason, ''),
  IFNULL(src.created_at, NOW())
FROM sso_ms.sso_login_log src
WHERE NOT EXISTS (SELECT 1 FROM polaris_audit.audit_event t WHERE t.id = src.id + 4000000000000000000);

-- ---------------- 4. EHR 敏感操作审计 → SENSITIVE ----------------
INSERT INTO polaris_audit.audit_event
  (id, app, event_category, action, http_method, request_path, operator_type,
   operator_id, operator_name, target_type, target_id, detail, client_ip, user_agent,
   login_type, result, fail_reason, created_at)
SELECT
  src.id + 6000000000000000000,
  'ehr',
  'SENSITIVE',
  IFNULL(src.action, ''),
  '', '',
  'USER',
  CAST(src.operator_id AS CHAR),
  IFNULL(src.operator_name, 'SYSTEM'),
  IFNULL(src.target_type, ''),
  IFNULL(CAST(src.target_id AS CHAR), ''),
  IFNULL(src.fields, ''),
  IFNULL(src.ip, ''),
  IFNULL(src.ua, ''),
  '',
  IF(src.fields LIKE 'result=fail%', 'fail', 'success'),
  '',
  IFNULL(src.created_at, NOW())
FROM ehr.ehr_audit_log src
WHERE NOT EXISTS (SELECT 1 FROM polaris_audit.audit_event t WHERE t.id = src.id + 6000000000000000000);

-- ---------------- 5. 旧权限码下线（菜单/按钮已移入统一审计中心） ----------------
UPDATE sso_ms.sso_permission
   SET deleted = 1
 WHERE (app_code = 'sso' AND code IN ('log', 'account:log:view'))
    OR (app_code = 'ehr' AND code IN ('audit', 'audit:view'));

DROP VIEW IF EXISTS sso_ms.sso_audit_event_mig;

-- ---------------- 6. 校验 ----------------
-- SELECT event_category, COUNT(*) FROM polaris_audit.audit_event GROUP BY event_category;
-- SELECT app, event_category, COUNT(*) FROM polaris_audit.audit_event GROUP BY app, event_category ORDER BY 3 DESC;
-- 迁移并核对无误后，可手工执行（本脚本刻意不自动删，留回滚窗口）：
-- DROP TABLE sso_ms.sso_audit_event;
-- DROP TABLE sso_ms.sso_audit_log;
-- DROP TABLE sso_ms.sso_login_log;
-- DROP TABLE ehr.ehr_audit_log;
