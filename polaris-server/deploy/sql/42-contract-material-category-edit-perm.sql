-- ============================================================
-- 合同域分类维护权限：分类库页面需要编辑/删除分类，独立权限码不复用物料编辑码
-- 新增 contract:material:category:edit → 挂「物料库」菜单
-- 幂等，可重复执行。
-- ============================================================
USE `sso_ms`;

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 2026112600000000818, 'contract:material:category:edit', '编辑删除分类', 2, m.`id`, 2,
       '', NOW(), NOW(), 0, 'contract'
  FROM `sso_ms`.`sso_permission` m
 WHERE m.`code` = 'contract:material:list' AND m.`app_code` = 'contract' AND m.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sso_ms`.`sso_permission` p
                   WHERE p.`code` = 'contract:material:category:edit' AND p.`app_code` = 'contract');

INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`, p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code` = 'contract'
 AND p.`code` IN ('contract:material:category:add','contract:material:category:edit') AND p.`deleted` = 0
WHERE r.`code` IN ('CONTRACT_ADMIN','SUPER_ADMIN') AND r.`deleted` = 0;
