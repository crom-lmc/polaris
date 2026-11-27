-- ============================================================================
-- 合同模块附件：采购计划 / 合同 / 验收统一走独立存储策略 contract_attachment
-- ============================================================================
-- 1) contract_purchase_plan / contract_header / contract_acceptance 各加 file_ids；
-- 2) polaris_file.file_storage_policy 新增 contract_attachment（与 file 服务 data.sql 同 id 种子）；
-- 3) 缺策略上传会报 8102（FileErrorCode.POLICY_NOT_FOUND），必须先执行本脚本。
-- 幂等：MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，用存储过程 + information_schema 判重；
--       策略种子 INSERT IGNORE，可重复执行。
-- 环境：生产 CONTRACT_SQL_INIT_MODE / FILE_SQL_INIT_MODE = never，必须手工执行。
-- ============================================================================

DROP PROCEDURE IF EXISTS `polaris_add_contract_file_column`;
DELIMITER $$
CREATE PROCEDURE `polaris_add_contract_file_column`(IN tbl_name VARCHAR(64))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'polaris_contract' AND TABLE_NAME = tbl_name
      AND COLUMN_NAME = 'file_ids'
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `polaris_contract`.`', tbl_name,
      '` ADD COLUMN `file_ids` varchar(1024) NOT NULL DEFAULT '''' COMMENT ',
      '''附件 file_id，逗号分隔（统一文件服务 contract_attachment 策略）'' AFTER `org_code`');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL polaris_add_contract_file_column('contract_purchase_plan');
CALL polaris_add_contract_file_column('contract_header');
CALL polaris_add_contract_file_column('contract_acceptance');

DROP PROCEDURE IF EXISTS `polaris_add_contract_file_column`;

-- 合同业务附件策略：三类单据共用，owner_type 区分 CONTRACT_PURCHASE_PLAN / CONTRACT_HEADER / CONTRACT_ACCEPTANCE
INSERT IGNORE INTO `polaris_file`.`file_storage_policy`
  (`id`,`biz_type`,`biz_name`,`max_size_mb`,`allowed_ext`,`default_visibility`,`force_private`,`quota_gb`,`ttl_days`,`enabled`,`remark`,`created_at`)
VALUES
  (9200000000000000009,'contract_attachment','合同业务附件',20,
   'jpg,jpeg,png,pdf,ofd,doc,docx,xls,xlsx,zip','PRIVATE',1,0,0,1,
   '采购计划/合同/验收共用，强制私有；业务表 file_ids 只存 file_id，按单据权限预览',NOW());

-- 附件上传是独立按钮权限：分别挂「采购计划」「合同台账」菜单，不与新增/编辑权限复用
USE `sso_ms`;

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 2026112600000000821, 'contract:plan:attachment:upload', '上传采购计划附件', 2, m.`id`, 2,
       '', NOW(), NOW(), 0, 'contract'
  FROM `sso_ms`.`sso_permission` m
 WHERE m.`code` = 'contract:plan:list' AND m.`app_code` = 'contract' AND m.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sso_ms`.`sso_permission` p
                   WHERE p.`code` = 'contract:plan:attachment:upload' AND p.`app_code` = 'contract');

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 2026112600000000822, 'contract:ledger:attachment:upload', '上传合同附件', 2, m.`id`, 3,
       '', NOW(), NOW(), 0, 'contract'
  FROM `sso_ms`.`sso_permission` m
 WHERE m.`code` = 'contract:ledger:list' AND m.`app_code` = 'contract' AND m.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sso_ms`.`sso_permission` p
                   WHERE p.`code` = 'contract:ledger:attachment:upload' AND p.`app_code` = 'contract');

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
SELECT 2026112600000000823, 'contract:item:attachment:upload', '上传验收凭证', 2, m.`id`, 4,
       '', NOW(), NOW(), 0, 'contract'
  FROM `sso_ms`.`sso_permission` m
 WHERE m.`code` = 'contract:ledger:list' AND m.`app_code` = 'contract' AND m.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sso_ms`.`sso_permission` p
                   WHERE p.`code` = 'contract:item:attachment:upload' AND p.`app_code` = 'contract');

INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`, p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code` = 'contract'
 AND p.`code` IN ('contract:plan:attachment:upload','contract:ledger:attachment:upload','contract:item:attachment:upload')
 AND p.`deleted` = 0
WHERE r.`code` IN ('CONTRACT_ADMIN','SUPER_ADMIN') AND r.`deleted` = 0;

SELECT 'contract attachment done' AS result;
