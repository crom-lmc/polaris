-- ============================================================
-- 合同域物料分类拆表：分类是分类，物料是物料
-- 背景：原 contract_material 用 kind(CATEGORY/MATERIAL) 混存分类与物料，
--       分类通过 parent_id 组成三级树，物料也用 parent_id 挂在三级分类下。
-- 整改：
--   1) 新建 contract_material_category：只存三级分类树（保留原分类 ID/code）
--   2) contract_material 原地改造为纯物料表：新增 category_id 指向三级分类，
--      删除 CATEGORY 行与 kind/parent_id 列（保留原物料 ID/code，旧单据引用不断裂）
-- 幂等：可重复执行；分类迁移用 NOT EXISTS，列变更用 information_schema 判断。
-- 前置：本地/生产已执行 34-contract-basedata-v2.sql（50 分类 + 100 物料）。
-- ============================================================
USE `polaris_contract`;

-- 1. 分类独立表
CREATE TABLE IF NOT EXISTS `contract_material_category` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父分类ID；根节点为 NULL',
  `code` varchar(32) NOT NULL COMMENT '分类编码 01 / 0101 / 010101',
  `name` varchar(200) NOT NULL COMMENT '分类名称',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（数据权限）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cat_parent` (`parent_id`,`deleted`),
  KEY `idx_cat_code` (`code`,`deleted`),
  KEY `idx_cat_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料三级分类（分类独立表）';

-- 2. 迁移 + 列改造（存储过程内幂等）
DROP PROCEDURE IF EXISTS `sp_contract_material_split`;
DELIMITER $$
CREATE PROCEDURE `sp_contract_material_split`()
BEGIN
  DECLARE v_kind INT DEFAULT 0;
  DECLARE v_catid INT DEFAULT 0;
  DECLARE v_parent INT DEFAULT 0;

  SELECT COUNT(*) INTO v_kind
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_material' AND COLUMN_NAME = 'kind';
  SELECT COUNT(*) INTO v_catid
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_material' AND COLUMN_NAME = 'category_id';
  SELECT COUNT(*) INTO v_parent
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_material' AND COLUMN_NAME = 'parent_id';

  -- 2.1 分类行 → 分类表（保留原 ID / code / 组织 / 时间）
  IF v_kind > 0 THEN
    INSERT INTO `contract_material_category`
      (`id`,`parent_id`,`code`,`name`,`org_code`,`created_at`,`updated_at`,`deleted`)
    SELECT m.`id`, m.`parent_id`, m.`code`, m.`name`, m.`org_code`,
           m.`created_at`, m.`updated_at`, m.`deleted`
      FROM `contract_material` m
     WHERE m.`kind` = 'CATEGORY'
       AND NOT EXISTS (SELECT 1 FROM `contract_material_category` c WHERE c.`id` = m.`id`);
  END IF;

  -- 2.2 物料表新增 category_id
  IF v_catid = 0 THEN
    ALTER TABLE `contract_material`
      ADD COLUMN `category_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '所属三级分类ID' AFTER `id`;
  END IF;

  -- 2.3 物料的 category_id = 原 parent_id（物料本就挂三级分类）
  IF v_kind > 0 THEN
    UPDATE `contract_material` m SET m.`category_id` = m.`parent_id`
     WHERE m.`kind` = 'MATERIAL' AND m.`category_id` = 0;

    -- 2.4 删除分类行与 kind 列
    DELETE FROM `contract_material` WHERE `kind` = 'CATEGORY';
    ALTER TABLE `contract_material` DROP COLUMN `kind`;
  END IF;

  -- 2.5 删除 parent_id 列
  IF v_parent > 0 THEN
    ALTER TABLE `contract_material` DROP COLUMN `parent_id`;
  END IF;

  -- 2.6 索引
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_material'
                    AND INDEX_NAME = 'idx_material_category') THEN
    ALTER TABLE `contract_material` ADD KEY `idx_material_category` (`category_id`,`deleted`);
  END IF;
END$$
DELIMITER ;
CALL `sp_contract_material_split`();
DROP PROCEDURE IF EXISTS `sp_contract_material_split`;

-- 3. 校验：分类 50、物料 100、物料 category_id 全部指向三级分类（code 长度 6）
SELECT 'category' AS `entity`, COUNT(*) AS `cnt` FROM `contract_material_category` WHERE `deleted` = 0
UNION ALL
SELECT 'material', COUNT(*) FROM `contract_material` WHERE `deleted` = 0
UNION ALL
SELECT 'bad_material_category', COUNT(*) FROM `contract_material` m
  LEFT JOIN `contract_material_category` c ON c.`id` = m.`category_id` AND c.`deleted` = 0
 WHERE m.`deleted` = 0 AND (c.`id` IS NULL OR LENGTH(c.`code`) <> 6);
