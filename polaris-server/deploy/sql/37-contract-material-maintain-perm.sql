-- ============================================================
-- 补齐合同域「物料维护」独立菜单权限码
-- 背景：物料维护菜单此前复用按钮码 contract:material:edit，
--       导致权限管理页「菜单」类型只有 6 条、比侧栏少一个。
-- 幂等：可重复执行；supper admin 全量授权由 sso-account 启动自动补齐。
-- ============================================================

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
VALUES
  (2026112600000000815,'contract:material-maintain:list','物料维护',1,0,4,'',NOW(),NOW(),0,'contract');

-- 菜单排序：物料维护插在物料库之后，原后三项顺延
UPDATE `sso_ms`.`sso_permission` SET `sort`=5 WHERE `app_code`='contract' AND `code`='contract:project:list' AND `deleted`=0;
UPDATE `sso_ms`.`sso_permission` SET `sort`=6 WHERE `app_code`='contract' AND `code`='contract:ledger:list' AND `deleted`=0;
UPDATE `sso_ms`.`sso_permission` SET `sort`=7 WHERE `app_code`='contract' AND `code`='contract:payment:list' AND `deleted`=0;

-- 「维护物料」按钮挂到「物料维护」菜单下（原来挂在「物料库」）
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:material-maintain:list' AND m.`app_code`='contract' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='contract' AND p.`code`='contract:material:edit' AND p.`deleted`=0;

-- 合同管理员与超级管理员补授权（超管启动也会全量补齐，这里显式写保证不重启也生效）
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`, p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='contract' AND p.`code`='contract:material-maintain:list' AND p.`deleted`=0
WHERE r.`code` IN ('CONTRACT_ADMIN','SUPER_ADMIN') AND r.`deleted`=0;
