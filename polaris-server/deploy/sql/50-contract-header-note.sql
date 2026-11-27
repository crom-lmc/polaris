-- ============================================================================
-- 合同主信息新增「备注」（非必填，200 字）
-- 说明：ContractSave.remark 已被「变更原因（仅用于变更留痕）」占用，不写入主表，
--       故备注使用独立字段 note，避免语义冲突。
-- 幂等：information_schema 判重后加列，重复执行无副作用。
-- 环境：生产 CONTRACT_SQL_INIT_MODE = never，必须手工执行。
-- ============================================================================

USE `polaris_contract`;

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'polaris_contract' AND TABLE_NAME = 'contract_header' AND COLUMN_NAME = 'note'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `polaris_contract`.`contract_header` ADD COLUMN `note` varchar(200) NOT NULL DEFAULT '''' COMMENT ''备注（非必填，200字）'' AFTER `subject`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'contract header note done' AS result;
