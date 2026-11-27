-- ============================================================================
-- 合同域闭环字段补齐：设计稿明确字段落地
-- 1) contract_header 加 plan_no（来源采购计划号）—— 采购计划→合同正向链路
-- 2) contract_purchase_plan 加 plan_date（计划日期，设计稿 date）
-- 3) contract_payment_plan 加 summary（付款事由）、voucher_no（凭证号回写）、file_ids（合同与验收凭证）
-- 4) contract_acceptance 加 voucher_no（过账凭证号回写）
-- 5) 新增付款凭证上传按钮权限 contract:payment:attachment:upload
-- 幂等：MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，用存储过程 + information_schema 判重。
-- 环境：生产 CONTRACT_SQL_INIT_MODE = never，必须手工执行。
-- ============================================================================

USE `polaris_contract`;

DROP PROCEDURE IF EXISTS `polaris_add_contract_loop_column`;
DELIMITER $$
CREATE PROCEDURE `polaris_add_contract_loop_column`(
  IN tbl_name VARCHAR(64), IN col_name VARCHAR(64), IN col_ddl VARCHAR(512))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'polaris_contract' AND TABLE_NAME = tbl_name AND COLUMN_NAME = col_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `polaris_contract`.`', tbl_name, '` ADD COLUMN ', col_ddl);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- 1) 合同主表：来源采购计划号
CALL polaris_add_contract_loop_column('contract_header', 'plan_no',
  '`plan_no` varchar(32) DEFAULT NULL COMMENT ''来源采购计划号（FROM_PLAN 时带入）'' AFTER `contract_source`');
DROP PROCEDURE IF EXISTS `polaris_add_contract_loop_index`;
DELIMITER $$
CREATE PROCEDURE `polaris_add_contract_loop_index`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'polaris_contract' AND TABLE_NAME = 'contract_header'
      AND INDEX_NAME = 'idx_header_plan'
  ) THEN
    ALTER TABLE `polaris_contract`.`contract_header` ADD INDEX `idx_header_plan` (`plan_no`, `deleted`);
  END IF;
END$$
DELIMITER ;
CALL polaris_add_contract_loop_index();
DROP PROCEDURE IF EXISTS `polaris_add_contract_loop_index`;

-- 2) 采购计划：计划日期
CALL polaris_add_contract_loop_column('contract_purchase_plan', 'plan_date',
  '`plan_date` date DEFAULT NULL COMMENT ''计划日期（设计稿 date）'' AFTER `applicant`');

-- 3) 付款计划：付款事由 / 凭证号 / 附件
CALL polaris_add_contract_loop_column('contract_payment_plan', 'summary',
  '`summary` varchar(500) NOT NULL DEFAULT '''' COMMENT ''付款事由'' AFTER `amount`');
CALL polaris_add_contract_loop_column('contract_payment_plan', 'voucher_no',
  '`voucher_no` varchar(32) DEFAULT NULL COMMENT ''财务域过账凭证号（回写）'' AFTER `source_doc_no`');
CALL polaris_add_contract_loop_column('contract_payment_plan', 'file_ids',
  '`file_ids` varchar(1024) NOT NULL DEFAULT '''' COMMENT ''合同与验收凭证 file_id，逗号分隔'' AFTER `org_code`');

-- 4) 验收单：过账凭证号
CALL polaris_add_contract_loop_column('contract_acceptance', 'voucher_no',
  '`voucher_no` varchar(32) DEFAULT NULL COMMENT ''过账凭证号（财务域回写）'' AFTER `status`');

DROP PROCEDURE IF EXISTS `polaris_add_contract_loop_column`;

-- 5) 付款凭证上传权限（挂「合同付款计划」菜单下，独立按钮权限，不复用发起付款）
INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 2026112600000000824, 'contract:payment:attachment:upload', '上传付款凭证', 2, m.`id`, 2,
       '', NOW(), NOW(), 0, 'contract'
  FROM `sso_ms`.`sso_permission` m
 WHERE m.`code` = 'contract:payment:list' AND m.`app_code` = 'contract' AND m.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sso_ms`.`sso_permission` p
                   WHERE p.`code` = 'contract:payment:attachment:upload' AND p.`app_code` = 'contract');

INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`, p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code` = 'contract'
 AND p.`code` = 'contract:payment:attachment:upload' AND p.`deleted` = 0
WHERE r.`code` IN ('CONTRACT_ADMIN','SUPER_ADMIN') AND r.`deleted` = 0;

SELECT 'contract closed-loop fields done' AS result;
