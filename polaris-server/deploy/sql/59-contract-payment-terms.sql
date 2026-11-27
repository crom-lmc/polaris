-- 合同付款节点化 + 销售客户验收（2026-11-27）
-- 口径：建合同时配置付款节点（比例合计100%、必含验收款）；付款申请引用节点，按节点阶段差异化门槛。
-- 存量兼容：term_id 允许为空，空值沿用原「全部验收完成后付款」逻辑，不强制迁移（方案六）。
-- 幂等：ADD COLUMN / INDEX / CREATE TABLE 前置判断，DELETE 只删未使用的 OTHER 字典，可重复执行。

USE `polaris_contract`;

CREATE TABLE IF NOT EXISTS `contract_payment_term` (
  `id`            bigint        NOT NULL COMMENT '雪花ID',
  `contract_no`   varchar(32)   NOT NULL COMMENT 'contract_header.no',
  `stage`         varchar(32)   NOT NULL COMMENT 'PREPAY/GOODS/ACCEPT/WARRANTY',
  `ratio`         decimal(5,2)  NOT NULL COMMENT '比例%，如 20.00',
  `amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合同额×比例，建节点时锁定',
  `due_date`      date          DEFAULT NULL COMMENT '计划付款日（选填）',
  `remark`        varchar(300)  NOT NULL DEFAULT '',
  `org_code`      varchar(64)   NOT NULL DEFAULT '',
  `created_at`    datetime      NOT NULL,
  `updated_at`    datetime      DEFAULT NULL,
  `deleted`       tinyint(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_term_contract` (`contract_no`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同付款节点（建合同时配置，比例合计100%）';

-- MySQL 5.7 无 ADD COLUMN IF NOT EXISTS：用 information_schema 判断
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_header' AND COLUMN_NAME='warranty_due_date');
SET @sql = IF(@col=0, 'ALTER TABLE `contract_header` ADD COLUMN `warranty_due_date` date DEFAULT NULL COMMENT ''质保到期日，质保金节点放行依据'' AFTER `end_date`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_payment_plan' AND COLUMN_NAME='term_id');
SET @sql = IF(@col=0, 'ALTER TABLE `contract_payment_plan` ADD COLUMN `term_id` bigint DEFAULT NULL COMMENT ''contract_payment_term.id''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_payment_plan' AND COLUMN_NAME='item_ids');
SET @sql = IF(@col=0, 'ALTER TABLE `contract_payment_plan` ADD COLUMN `item_ids` varchar(512) NOT NULL DEFAULT '''' COMMENT ''GOODS 阶段所选清单行ID，逗号分隔''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_payment_plan' AND INDEX_NAME='idx_plan_term');
SET @sql = IF(@idx=0, 'ALTER TABLE `contract_payment_plan` ADD KEY `idx_plan_term` (`term_id`, `deleted`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 销售客户验收：客户验收单号（选填）+ 推应收回写的应收单号（幂等）+ 推应收失败标记
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_acceptance' AND COLUMN_NAME='customer_no');
SET @sql = IF(@col=0, 'ALTER TABLE `contract_acceptance` ADD COLUMN `customer_no` varchar(64) NOT NULL DEFAULT '''' COMMENT ''客户验收单号（销售，选填）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_acceptance' AND COLUMN_NAME='ar_no');
SET @sql = IF(@col=0, 'ALTER TABLE `contract_acceptance` ADD COLUMN `ar_no` varchar(64) NOT NULL DEFAULT '''' COMMENT ''推送生成的应收单号，非空即已推，防重复''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_acceptance' AND COLUMN_NAME='ar_error');
SET @sql = IF(@col=0, 'ALTER TABLE `contract_acceptance` ADD COLUMN `ar_error` varchar(300) NOT NULL DEFAULT '''' COMMENT ''推应收失败原因，供重试''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- settings：逻辑删除「其他付款」字典项（线上存量付款计划全部为 PREPAY/ACCEPT，无使用）
-- 方案文档写的 id 1500813 有误，实际初始化为 1500913；用 value 定位更稳。
USE `settings`;
UPDATE `cfg_dict_item`
   SET `deleted` = 1, `status` = 0, `updated_at` = NOW()
 WHERE `type_code` = 'contract.payment_stage' AND `value` = 'OTHER';

-- 校验
SELECT 'contract_payment_terms' AS `entity`,
       (SELECT COUNT(*) FROM `polaris_contract`.`contract_payment_term`) AS `terms`;
