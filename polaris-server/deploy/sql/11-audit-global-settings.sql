-- 审计全局设置：权限码播种 + 角色授权 + 建表
-- 幂等，可重复执行。生产 AUDIT_SQL_INIT_MODE=never，权限码与表都需手工执行本脚本。
-- 说明：sso-account 重启后 BootstrapInitializer 也会按 AUDIT_BUILTIN 幂等补齐这些码；
--       本脚本让「不重启也能立即授权」，两种路径 id 不同但 code 唯一，播种器按 code 跳过不会重复。

-- ============ 0. 先补审计应用访问权（否则角色有按钮码也进不了应用） ============
-- 由 sso_account_app 控制，见文末。

-- ============ 1. 权限码：菜单 + 查看/编辑 + 5 个分区，固定 id，INSERT IGNORE 幂等 ============
-- 注意：sso_permission.id 为有符号 bigint（上限 9223372036854775807），固定 id 必须小于该值。
-- audit:setting 菜单码历史已存在（id=9223372036854775807），INSERT IGNORE 按 code 唯一键跳过，保留原行。
-- 子码 parent_id 先置 0，随后统一修正为菜单码真实 id（不依赖具体 id 值）。
INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
VALUES
  (20261125000000001,'audit:setting','全局设置',1,0,5,'',NOW(),NOW(),0,'audit'),
  (20261125000000002,'audit:setting:view','查看全局设置',2,0,1,'',NOW(),NOW(),0,'audit'),
  (20261125000000003,'audit:setting:edit','编辑全局设置',2,0,2,'',NOW(),NOW(),0,'audit'),
  (20261125000000004,'audit:setting:collect','设置-采集策略',2,0,3,'',NOW(),NOW(),0,'audit'),
  (20261125000000005,'audit:setting:ignore','设置-忽略规则',2,0,4,'',NOW(),NOW(),0,'audit'),
  (20261125000000006,'audit:setting:retention','设置-留存与清理',2,0,5,'',NOW(),NOW(),0,'audit'),
  (20261125000000007,'audit:setting:privacy','设置-脱敏与隐私',2,0,6,'',NOW(),NOW(),0,'audit'),
  (20261125000000008,'audit:setting:export','设置-导出与合规',2,0,7,'',NOW(),NOW(),0,'audit');

-- 统一修正：所有 audit:setting:* 子码挂到菜单码真实 id 下
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='audit:setting' AND m.`app_code`='audit'
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='audit' AND p.`code` LIKE 'audit:setting:%' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

-- ============ 2. 角色授权 ============
-- 2.1 AUDIT_ADMIN：补齐全部 audit 权限（含 8 个设置码）
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='audit' AND p.`deleted`=0
WHERE r.`code`='AUDIT_ADMIN' AND r.`deleted`=0;

-- 2.2 AUDIT_VIEWER：清理旧规则误授的设置权限
--     审计查看员只看事件/登录，不接触系统日志与全局设置。
DELETE rp FROM `sso_ms`.`sso_role_permission` rp
JOIN `sso_ms`.`sso_role` r ON r.`id`=rp.`role_id` AND r.`code`='AUDIT_VIEWER'
JOIN `sso_ms`.`sso_permission` p ON p.`id`=rp.`permission_id`
WHERE p.`app_code`='audit' AND (p.`code` LIKE '%system-log%' OR p.`code` LIKE 'audit:setting%');

-- ============ 3. 应用访问权：确保管理员与查看员都能进入 audit 应用 ============
INSERT IGNORE INTO `sso_ms`.`sso_account_app` (`account_id`,`app_id`)
SELECT a.`id`, ap.`id`
FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_role` r ON r.`code`='AUDIT_ADMIN' AND r.`deleted`=0
JOIN `sso_ms`.`sso_account_role` ar ON ar.`account_id`=a.`id` AND ar.`role_id`=r.`id`
JOIN `sso_ms`.`sso_application` ap ON ap.`code`='audit' AND ap.`deleted`=0
WHERE a.`deleted`=0;

INSERT IGNORE INTO `sso_ms`.`sso_account_app` (`account_id`,`app_id`)
SELECT a.`id`, ap.`id`
FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_role` r ON r.`code`='AUDIT_VIEWER' AND r.`deleted`=0
JOIN `sso_ms`.`sso_account_role` ar ON ar.`account_id`=a.`id` AND ar.`role_id`=r.`id`
JOIN `sso_ms`.`sso_application` ap ON ap.`code`='audit' AND ap.`deleted`=0
WHERE a.`deleted`=0;

-- ============ 4. 审计设置表（幂等，生产手工执行） ============
CREATE TABLE IF NOT EXISTS `polaris_audit`.`audit_setting` (
  `setting_key` varchar(128) NOT NULL COMMENT '配置项 key，点分命名',
  `setting_value` varchar(2000) NOT NULL DEFAULT '' COMMENT '配置值',
  `updated_at` datetime NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='审计全局设置（K-V）';
