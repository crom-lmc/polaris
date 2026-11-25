-- 审计事件导出：权限码播种 + 角色授权 + 清理已下线的空配置键
-- 生产 AUDIT_SQL_INIT_MODE=never，需手工执行本脚本；幂等可重复。

-- ============ 1. 权限码：导出审计事件（按钮码，挂到「审计事件」菜单下） ============
INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 20261125000000009,'audit:events:export','导出审计事件',2,p.`id`,3,'',NOW(),NOW(),0,'audit'
FROM `sso_ms`.`sso_permission` p
WHERE p.`code`='audit:events' AND p.`app_code`='audit';

-- 若审计事件菜单码缺失则退化为挂根，保证权限树不丢按钮
UPDATE `sso_ms`.`sso_permission`
SET `parent_id`=0
WHERE `code`='audit:events:export' AND `app_code`='audit'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `sso_ms`.`sso_permission` WHERE `code`='audit:events' AND `app_code`='audit') x);

-- ============ 2. 角色授权：审计管理员可导出 ============
-- 2.1 全量补 audit 按钮/菜单给 AUDIT_ADMIN（沿用全局设置脚本口径，含新增导出码）
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='audit' AND p.`deleted`=0
WHERE r.`code`='AUDIT_ADMIN' AND r.`deleted`=0;

-- 2.2 审计查看员不授予导出（导出属敏感操作）
DELETE rp FROM `sso_ms`.`sso_role_permission` rp
JOIN `sso_ms`.`sso_role` r ON r.`id`=rp.`role_id` AND r.`code`='AUDIT_VIEWER'
JOIN `sso_ms`.`sso_permission` p ON p.`id`=rp.`permission_id`
WHERE p.`code`='audit:events:export';

-- ============ 3. 清理已下线的两个配置键 ============
-- collect.jsonBody：网关只采集 query，body 由业务层 AOP 记入 detail，无独立接管点
-- collect.queueCapacity：队列容量由 common 模块 polaris.system-log.queue-size 控制，不在此处配置
-- export.recordBehavior：导出行为合规必留、无条件记录，不作为可配置项
DELETE FROM `polaris_audit`.`audit_setting`
WHERE `setting_key` IN ('collect.jsonBody','collect.queueCapacity','export.recordBehavior');
