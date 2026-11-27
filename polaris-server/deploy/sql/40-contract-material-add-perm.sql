-- ============================================================
-- 合同域物料新增权限拆分：新增分类归「物料库」菜单，新增物料归「物料维护」菜单
-- 背景：两个新增动作此前共用按钮码 contract:material:edit（挂在物料维护下），
--       违反「权限不允许复用、按钮必须挂到所属菜单下」。
-- 整改：
--   新增 contract:material:category:add（新增分类）→ 挂在物料库菜单
--   新增 contract:material:add（新增物料）→ 挂在物料维护菜单
--   保留 contract:material:edit 仅用于编辑/删除节点（物料维护菜单）
-- 幂等：可重复执行；supper admin 全量授权也会在 sso-account 启动时补齐。
-- ============================================================
USE `sso_ms`;

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
VALUES
  (2026112600000000816,'contract:material:category:add','新增分类',2,0,1,'',NOW(),NOW(),0,'contract'),
  (2026112600000000817,'contract:material:add','新增物料',2,0,1,'',NOW(),NOW(),0,'contract');

-- 按钮挂到对应菜单：分类归物料库，物料归物料维护
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:material:list' AND m.`app_code`='contract' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='contract' AND p.`code`='contract:material:category:add' AND p.`deleted`=0;

UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:material-maintain:list' AND m.`app_code`='contract' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='contract' AND p.`code`='contract:material:add' AND p.`deleted`=0;

-- 排序：物料维护下 新增物料=1、维护物料=2
UPDATE `sso_ms`.`sso_permission` SET `sort`=2 WHERE `app_code`='contract' AND `code`='contract:material:edit' AND `deleted`=0;

-- 合同管理员与超级管理员补授权
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`, p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='contract' AND p.`code` IN ('contract:material:category:add','contract:material:add') AND p.`deleted`=0
WHERE r.`code` IN ('CONTRACT_ADMIN','SUPER_ADMIN') AND r.`deleted`=0;
