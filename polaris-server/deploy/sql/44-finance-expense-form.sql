-- ============================================================================
-- 财务报销申请补齐：报销类型字典 + 科目映射 + 归属部门 + 报销凭证附件
-- ============================================================================
-- 1) fin_expense 新增 expense_type / subject_code / file_ids，存量数据默认空串；
-- 2) settings 新增 finance.expense_type 字典，字典项 remark 用 JSON 承载
--    「报销类型 → 会计科目」映射（subjectCode/subjectName），财务人员只在配置中心维护；
-- 3) fin_subject 补培训费/其他费用两个明细科目，供字典映射引用；
-- 4) file_storage_policy 新增 finance_expense_attachment 策略，缺策略上传报 8102。
-- 幂等：MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，用存储过程 + information_schema 判重；
--       数据种子全部 INSERT IGNORE，可重复执行。
-- 环境：生产 FINANCE_SQL_INIT_MODE / SETTINGS_SQL_INIT_MODE / FILE_SQL_INIT_MODE = never，必须手工执行。
-- ============================================================================

DROP PROCEDURE IF EXISTS `polaris_add_fin_expense_column`;
DELIMITER $$
CREATE PROCEDURE `polaris_add_fin_expense_column`(
  IN col_name VARCHAR(64), IN col_ddl VARCHAR(500)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'polaris_finance' AND TABLE_NAME = 'fin_expense'
      AND COLUMN_NAME = col_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `polaris_finance`.`fin_expense` ADD COLUMN ', col_ddl);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL polaris_add_fin_expense_column('expense_type',
  '`expense_type` varchar(64) NOT NULL DEFAULT '''' COMMENT ''settings 字典 finance.expense_type 的 value'' AFTER `applicant`');
CALL polaris_add_fin_expense_column('subject_code',
  '`subject_code` varchar(64) NOT NULL DEFAULT '''' COMMENT ''报销类型映射的会计科目编码'' AFTER `subject`');
CALL polaris_add_fin_expense_column('file_ids',
  '`file_ids` varchar(1024) NOT NULL DEFAULT '''' COMMENT ''报销凭证附件 file_id，逗号分隔'' AFTER `subject_code`');

DROP PROCEDURE IF EXISTS `polaris_add_fin_expense_column`;

-- 会计科目：报销类型映射引用的两个明细科目
INSERT IGNORE INTO `polaris_finance`.`fin_subject`
  (`id`,`code`,`name`,`level`,`balance_direction`,`category`,`status`,`org_code`,`created_at`,`updated_at`,`deleted`)
VALUES
  (2026112600000030051,'660205','培训费',2,'DEBIT','EXPENSE','ENABLED','XC0001',NOW(),NOW(),0),
  (2026112600000030052,'660206','其他费用',2,'DEBIT','EXPENSE','ENABLED','XC0001',NOW(),NOW(),0);

-- 报销类型字典：remark 的 JSON 由财务在配置中心维护，后端解析并校验科目有效
INSERT IGNORE INTO `settings`.`cfg_dict_type`
  (`id`,`code`,`name`,`app_code`,`remark`,`built_in`,`status`,`deleted`,`created_at`,`updated_at`)
VALUES (11517,'finance.expense_type','报销类型','finance','remark 维护 subjectCode/subjectName 映射',1,1,0,NOW(),NOW());

INSERT IGNORE INTO `settings`.`cfg_dict_item`
  (`id`,`type_code`,`value`,`label`,`sort`,`status`,`remark`,`built_in`,`deleted`,`created_at`,`updated_at`)
VALUES
  (1500867,'finance.expense_type','TRAVEL','差旅费',1,1,'{"subjectCode":"660102","subjectName":"差旅费"}',1,0,NOW(),NOW()),
  (1500868,'finance.expense_type','OFFICE','办公费',2,1,'{"subjectCode":"660202","subjectName":"办公费"}',1,0,NOW(),NOW()),
  (1500869,'finance.expense_type','ENTERTAIN','业务招待费',3,1,'{"subjectCode":"660103","subjectName":"业务招待费"}',1,0,NOW(),NOW()),
  (1500870,'finance.expense_type','TRAIN','培训费',4,1,'{"subjectCode":"660205","subjectName":"培训费"}',1,0,NOW(),NOW()),
  (1500871,'finance.expense_type','OTHER','其他费用',5,1,'{"subjectCode":"660206","subjectName":"其他费用"}',1,0,NOW(),NOW());

-- 报销凭证附件存储策略：与 polaris-file-service/src/main/resources/data.sql 同 id 种子保持一致
INSERT IGNORE INTO `polaris_file`.`file_storage_policy`
  (`id`,`biz_type`,`biz_name`,`max_size_mb`,`allowed_ext`,`default_visibility`,`force_private`,`quota_gb`,`ttl_days`,`enabled`,`remark`,`created_at`)
VALUES
  (9200000000000000008,'finance_expense_attachment','财务报销凭证附件',20,
   'jpg,jpeg,png,pdf,ofd,doc,docx,xls,xlsx,zip','PRIVATE',1,0,0,1,
   '报销凭证强制私有，业务表 fin_expense.file_ids 只存 file_id；按报销单权限下载',NOW());

SELECT 'finance expense form done' AS result;
