-- ============================================================
-- 修复财务域按钮权限未挂到菜单下的问题
-- 背景：30-finance-init.sql 只播种了按钮码，没有补 parent_id，
--       导致权限管理页里财务按钮全部平铺在最前面（parent_id=0）。
-- 口径与 PermissionConstants.FINANCE_BUILTIN 的 parentCode 一致。
-- 幂等：可重复执行；只修复当前 parent_id 为 0 的记录。
-- ============================================================

-- 新增/编辑凭证 → 凭证管理
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:voucher:list' AND m.`app_code`='finance' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='finance' AND p.`code`='finance:voucher:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

-- 发起报销 / 报销审批 → 费用报销
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:expense:list' AND m.`app_code`='finance' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='finance' AND p.`code` IN ('finance:expense:create','finance:expense:audit') AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

-- 维护预算 → 预算管理
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:budget:view' AND m.`app_code`='finance' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='finance' AND p.`code`='finance:budget:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

-- 应收应付核销 / 查看银行账号 → 应收应付
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:ar-ap:list' AND m.`app_code`='finance' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='finance' AND p.`code` IN ('finance:ar-ap:write','finance:bank:view') AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);


-- 查看薪资明文 → 财务概览
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:overview:view' AND m.`app_code`='finance' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='finance' AND p.`code`='finance:salary:view' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);
