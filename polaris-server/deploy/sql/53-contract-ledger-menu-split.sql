-- 合同台账拆分为「采购合同 / 销售合同」二级菜单（2026-11-27）
-- 幂等可重复执行。菜单定义同时写入 PermissionConstants.CONTRACT_BUILTIN（sso-account 重启自动补齐），
-- 本脚本让存量库无需重启账号服务即可下发新菜单并完成角色授权。
-- 合同列表/详情/状态接口仍以 contract:ledger:list 作 @Permit，故父菜单权限码保留并继续授权。

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 2026112700000000831, 'contract:ledger:purchase:list', '采购合同', 1, m.`id`, 1,
       '', NOW(), NOW(), 0, 'contract'
  FROM `sso_ms`.`sso_permission` m
 WHERE m.`code` = 'contract:ledger:list' AND m.`app_code` = 'contract' AND m.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sso_ms`.`sso_permission` p
                   WHERE p.`code` = 'contract:ledger:purchase:list' AND p.`app_code` = 'contract');

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 2026112700000000832, 'contract:ledger:sales:list', '销售合同', 1, m.`id`, 2,
       '', NOW(), NOW(), 0, 'contract'
  FROM `sso_ms`.`sso_permission` m
 WHERE m.`code` = 'contract:ledger:list' AND m.`app_code` = 'contract' AND m.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sso_ms`.`sso_permission` p
                   WHERE p.`code` = 'contract:ledger:sales:list' AND p.`app_code` = 'contract');

-- 两个子菜单与父菜单一起授予现有合同角色，避免子菜单可见但接口 403
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`, p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code` = 'contract'
 AND p.`code` IN ('contract:ledger:list','contract:ledger:purchase:list','contract:ledger:sales:list')
 AND p.`deleted` = 0
WHERE r.`code` IN ('CONTRACT_ADMIN','CONTRACT_USER','SUPER_ADMIN') AND r.`deleted` = 0;

SELECT 'contract ledger menu split done' AS result;
