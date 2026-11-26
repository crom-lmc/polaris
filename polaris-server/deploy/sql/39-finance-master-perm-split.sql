-- ============================================================
-- 财务域主数据权限拆分：取消复用，菜单各建独立码，按钮挂到对应菜单
-- 背景：会计科目/预算科目两个页面共用 finance:master:list（菜单码）
--       和 finance:master:edit（按钮码），违反「权限不允许复用」；
--       数据口径菜单无权限码。
-- 整改：
--   菜单 finance:account-subject:list / finance:budget-subject:list（挂 master:list 下）
--   菜单 finance:lineage:view（数据口径，根级）
--   按钮 finance:account-subject:edit / finance:budget-subject:edit（挂各自菜单下）
--   下线旧按钮 finance:master:edit（已被拆分，无代码引用）
-- 幂等：可重复执行。
-- ============================================================
USE `sso_ms`;

-- 1) 删除已下线的旧按钮码 finance:master:edit（先清角色授权，再删权限记录）
DELETE rp FROM `sso_ms`.`sso_role_permission` rp
JOIN `sso_ms`.`sso_permission` p ON p.`id`=rp.`permission_id`
WHERE p.`app_code`='finance' AND p.`code`='finance:master:edit';
DELETE FROM `sso_ms`.`sso_permission`
WHERE `app_code`='finance' AND `code`='finance:master:edit';

-- 2) 播种 5 个新权限码（3 菜单 + 2 按钮）
INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`) VALUES
  (2026112600000000716,'finance:lineage:view','数据口径',1,0,8,'',NOW(),NOW(),0,'finance'),
  (2026112600000000717,'finance:account-subject:list','会计科目',1,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000718,'finance:budget-subject:list','预算科目',1,0,2,'',NOW(),NOW(),0,'finance'),
  (2026112600000000719,'finance:account-subject:edit','维护会计科目',2,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000720,'finance:budget-subject:edit','维护预算科目',2,0,1,'',NOW(),NOW(),0,'finance');

-- 3) 挂 parent：两个二级菜单挂主数据维护，按钮挂各自菜单
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:master:list' AND m.`app_code`='finance' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='finance' AND p.`code` IN ('finance:account-subject:list','finance:budget-subject:list');

UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:account-subject:list' AND m.`app_code`='finance' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='finance' AND p.`code`='finance:account-subject:edit';

UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:budget-subject:list' AND m.`app_code`='finance' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='finance' AND p.`code`='finance:budget-subject:edit';

-- 4) 给财务管理员与超管授权新码（超管启动也会全量补齐，这里显式写保证不重启也生效）
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`, p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='finance' AND p.`deleted`=0
  AND p.`code` IN ('finance:lineage:view','finance:account-subject:list','finance:budget-subject:list',
                   'finance:account-subject:edit','finance:budget-subject:edit')
WHERE r.`code` IN ('FINANCE_ADMIN','SUPER_ADMIN') AND r.`deleted`=0;
