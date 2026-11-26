-- ============================================================================
-- 合同台账补齐字段：预算科目（主数据）+ 来源标识
-- ============================================================================
-- 背景：
--   设计稿「新增合同」有「预算科目」下拉（财务主数据），但实现里只有手输的
--   「合同标的」占用了 subject 字段，预算科目无处存放；采购计划转合同时
--   计划里的 budgetSubject 也被写进 subject，二者语义混在一起。
--
--   本脚本给 contract_header 加两列：
--     budget_subject  预算科目（财务主数据名称），采购计划转入时固定取计划值
--     contract_source 来源 DIRECT 直接新建 / FROM_PLAN 采购计划转入
--   subject 字段语义保持不变（合同标的），仅把列注释改准确。
--
-- 幂等：MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，用存储过程 + information_schema 判断。
-- 兼容：只加列/改注释，存量数据 contract_source 默认 DIRECT、budget_subject 默认空串，
--       不影响任何 mapper（均为显式列，无 SELECT *）。
-- 环境：生产 CONTRACT_SQL_INIT_MODE=never，必须手工执行；本地库名同为 polaris_contract。
-- ============================================================================

USE `polaris_contract`;

DROP PROCEDURE IF EXISTS `polaris_add_contract_header_column`;
DELIMITER $$
CREATE PROCEDURE `polaris_add_contract_header_column`(
  IN col_name VARCHAR(64), IN col_ddl VARCHAR(500)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'polaris_contract' AND TABLE_NAME = 'contract_header'
      AND COLUMN_NAME = col_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `polaris_contract`.`contract_header` ADD COLUMN ', col_ddl);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL polaris_add_contract_header_column('budget_subject',
  '`budget_subject` varchar(200) NOT NULL DEFAULT '''' COMMENT ''预算科目（财务主数据名称，采购计划转入时固定取计划值）'' AFTER `subject`');
CALL polaris_add_contract_header_column('contract_source',
  '`contract_source` varchar(16) NOT NULL DEFAULT ''DIRECT'' COMMENT ''来源：DIRECT 直接新建 / FROM_PLAN 采购计划转入'' AFTER `budget_subject`');

DROP PROCEDURE IF EXISTS `polaris_add_contract_header_column`;

-- subject 列注释改准确（幂等：重复执行无副作用）
ALTER TABLE `polaris_contract`.`contract_header`
  MODIFY COLUMN `subject` varchar(200) NOT NULL DEFAULT '' COMMENT '合同标的';

-- 存量合同：默认来源 DIRECT（新列为 DEFAULT，历史行已自动填充，此处显式刷一次确保非空）
UPDATE `polaris_contract`.`contract_header`
SET `contract_source` = 'DIRECT'
WHERE `contract_source` IS NULL OR `contract_source` = '';
