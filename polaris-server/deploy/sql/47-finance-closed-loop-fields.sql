-- ============================================================================
-- 财务域闭环字段补齐：设计稿明确字段落地
-- 1) fin_expense 加 expense_reason / invoice_type / tax_rate / tax_amount
-- 2) fin_voucher 加 voucher_type / reviewer / poster / file_ids，status 增 REVIEW（待审核）
-- 3) fin_ar_ap 加 invoice_no / invoice_type / credit_days / due_date
-- 4) fin_payment 加 stage / summary / due_date / source_doc_no / voucher_no / settle_method / file_ids
-- 5) settings 公共字典白名单放开 finance.expense_reason/invoice_type/tax_rate/voucher_type/settle_method/contract.payment_stage
--    （白名单改在代码 PublicReferenceController，需重启 settings；本脚本仅记录说明）
-- 幂等：MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，用存储过程 + information_schema 判重。
-- 环境：生产 FINANCE_SQL_INIT_MODE = never，必须手工执行。
-- ============================================================================

USE `polaris_finance`;

DROP PROCEDURE IF EXISTS `polaris_add_finance_loop_column`;
DELIMITER $$
CREATE PROCEDURE `polaris_add_finance_loop_column`(
  IN tbl_name VARCHAR(64), IN col_name VARCHAR(64), IN col_ddl VARCHAR(512))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'polaris_finance' AND TABLE_NAME = tbl_name AND COLUMN_NAME = col_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `polaris_finance`.`', tbl_name, '` ADD COLUMN ', col_ddl);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- 1) 报销：事由 / 发票类型 / 税率 / 税额
CALL polaris_add_finance_loop_column('fin_expense', 'expense_reason',
  '`expense_reason` VARCHAR(64) DEFAULT '''' COMMENT ''报销事由（settings 字典 finance.expense_reason）'' AFTER `subject_code`');
CALL polaris_add_finance_loop_column('fin_expense', 'invoice_type',
  '`invoice_type` VARCHAR(64) DEFAULT '''' COMMENT ''发票类型（settings 字典 finance.invoice_type）'' AFTER `expense_reason`');
CALL polaris_add_finance_loop_column('fin_expense', 'tax_rate',
  '`tax_rate` DECIMAL(8,4) DEFAULT NULL COMMENT ''税率（settings 字典 finance.tax_rate）'' AFTER `invoice_type`');
CALL polaris_add_finance_loop_column('fin_expense', 'tax_amount',
  '`tax_amount` DECIMAL(18,2) DEFAULT NULL COMMENT ''税额'' AFTER `tax_rate`');

-- 2) 凭证：类型 / 审核人 / 过账人 / 附件
CALL polaris_add_finance_loop_column('fin_voucher', 'voucher_type',
  '`voucher_type` VARCHAR(32) DEFAULT ''JZ'' COMMENT ''凭证类型（settings 字典 finance.voucher_type）'' AFTER `no`');
CALL polaris_add_finance_loop_column('fin_voucher', 'reviewer',
  '`reviewer` VARCHAR(64) DEFAULT '''' COMMENT ''审核人'' AFTER `creator`');
CALL polaris_add_finance_loop_column('fin_voucher', 'poster',
  '`poster` VARCHAR(64) DEFAULT '''' COMMENT ''过账人'' AFTER `reviewer`');
CALL polaris_add_finance_loop_column('fin_voucher', 'file_ids',
  '`file_ids` VARCHAR(1024) NOT NULL DEFAULT '''' COMMENT ''凭证附件 file_id，逗号分隔'' AFTER `contract_no`');

-- 3) 应收应付：发票号 / 发票类型 / 账期 / 到期日
CALL polaris_add_finance_loop_column('fin_ar_ap', 'invoice_no',
  '`invoice_no` VARCHAR(64) DEFAULT '''' COMMENT ''发票号'' AFTER `payment_stage`');
CALL polaris_add_finance_loop_column('fin_ar_ap', 'invoice_type',
  '`invoice_type` VARCHAR(64) DEFAULT '''' COMMENT ''发票类型（settings 字典 finance.invoice_type）'' AFTER `invoice_no`');
CALL polaris_add_finance_loop_column('fin_ar_ap', 'credit_days',
  '`credit_days` INT DEFAULT NULL COMMENT ''账期天数'' AFTER `invoice_type`');
CALL polaris_add_finance_loop_column('fin_ar_ap', 'due_date',
  '`due_date` DATE DEFAULT NULL COMMENT ''到期日'' AFTER `credit_days`');

-- 4) 付款单：阶段 / 事由 / 计划付款日 / 来源单据 / 凭证号 / 结算方式 / 附件
CALL polaris_add_finance_loop_column('fin_payment', 'stage',
  '`stage` VARCHAR(64) DEFAULT '''' COMMENT ''付款阶段（settings 字典 contract.payment_stage）'' AFTER `party`');
CALL polaris_add_finance_loop_column('fin_payment', 'summary',
  '`summary` VARCHAR(512) DEFAULT '''' COMMENT ''付款事由'' AFTER `amount`');
CALL polaris_add_finance_loop_column('fin_payment', 'due_date',
  '`due_date` DATE DEFAULT NULL COMMENT ''计划付款日期'' AFTER `summary`');
CALL polaris_add_finance_loop_column('fin_payment', 'source_doc_no',
  '`source_doc_no` VARCHAR(64) DEFAULT NULL COMMENT ''合同域来源单据号'' AFTER `due_date`');
CALL polaris_add_finance_loop_column('fin_payment', 'voucher_no',
  '`voucher_no` VARCHAR(40) DEFAULT NULL COMMENT ''过账凭证号'' AFTER `source_doc_no`');
CALL polaris_add_finance_loop_column('fin_payment', 'settle_method',
  '`settle_method` VARCHAR(32) DEFAULT '''' COMMENT ''结算方式（settings 字典 finance.settle_method）'' AFTER `voucher_no`');
CALL polaris_add_finance_loop_column('fin_payment', 'file_ids',
  '`file_ids` VARCHAR(1024) NOT NULL DEFAULT '''' COMMENT ''合同与验收凭证 file_id，逗号分隔'' AFTER `date`');

DROP PROCEDURE IF EXISTS `polaris_add_finance_loop_column`;

SELECT 'finance closed-loop fields done' AS result;
