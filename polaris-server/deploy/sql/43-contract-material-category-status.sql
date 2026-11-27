-- ============================================================
-- 合同域物料分类：新增启用/停用状态 + 独立按钮权限
--   1) contract_material_category.status：ENABLED（默认）/ DISABLED
--   2) contract:material:category:status 按钮挂「物料库」菜单，授权管理员
-- 幂等：列/索引用 information_schema 判断，权限用 NOT EXISTS + INSERT IGNORE。
-- 前置：已执行 41-contract-material-split.sql（分类独立表）。
-- ============================================================
USE `polaris_contract`;

DROP PROCEDURE IF EXISTS `sp_contract_category_status`;
DELIMITER $$
CREATE PROCEDURE `sp_contract_category_status`()
BEGIN
  DECLARE v_status INT DEFAULT 0;
  DECLARE v_index INT DEFAULT 0;

  SELECT COUNT(*) INTO v_status
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_material_category'
     AND COLUMN_NAME = 'status';
  IF v_status = 0 THEN
    ALTER TABLE `contract_material_category`
      ADD COLUMN `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED启用/DISABLED停用' AFTER `name`;
  END IF;

  SELECT COUNT(*) INTO v_index
    FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_material_category'
     AND INDEX_NAME = 'idx_cat_status';
  IF v_index = 0 THEN
    ALTER TABLE `contract_material_category` ADD KEY `idx_cat_status` (`status`,`deleted`);
  END IF;
END$$
DELIMITER ;
CALL `sp_contract_category_status`();
DROP PROCEDURE IF EXISTS `sp_contract_category_status`;

SELECT 'category_enabled' AS `item`, COUNT(*) AS `cnt`
  FROM `contract_material_category` WHERE `deleted` = 0 AND `status` = 'ENABLED'
UNION ALL
SELECT 'category_disabled', COUNT(*)
  FROM `contract_material_category` WHERE `deleted` = 0 AND `status` = 'DISABLED';

-- ------------------------------------------------------------
-- 权限：停用/启用分类是独立按钮，不复用编辑删除分类权限码
-- ------------------------------------------------------------
USE `sso_ms`;

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 2026112600000000820, 'contract:material:category:status', '停用启用分类', 2, m.`id`, 3,
       '', NOW(), NOW(), 0, 'contract'
  FROM `sso_ms`.`sso_permission` m
 WHERE m.`code` = 'contract:material:list' AND m.`app_code` = 'contract' AND m.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sso_ms`.`sso_permission` p
                   WHERE p.`code` = 'contract:material:category:status' AND p.`app_code` = 'contract');

INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`, p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code` = 'contract'
 AND p.`code` = 'contract:material:category:status' AND p.`deleted` = 0
WHERE r.`code` IN ('CONTRACT_ADMIN','SUPER_ADMIN') AND r.`deleted` = 0;
