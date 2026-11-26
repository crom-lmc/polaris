-- 财务域（北极星账）：建库建表 + 权限码 + 角色授权 + 应用注册 + 演示账号授权
-- 幂等可重复执行。生产 FINANCE_SQL_INIT_MODE=never，必须手工执行本脚本。
-- sso-account 重启后 BootstrapInitializer 会按 FINANCE_BUILTIN 幂等补齐权限码。

CREATE DATABASE IF NOT EXISTS `polaris_finance` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `polaris_finance`;

CREATE TABLE IF NOT EXISTS `fin_subject` (
  `id` BIGINT NOT NULL COMMENT '雪花主键',
  `code` VARCHAR(64) NOT NULL COMMENT '科目编码',
  `name` VARCHAR(128) NOT NULL COMMENT '科目名称',
  `level` INT NOT NULL DEFAULT 1 COMMENT '科目级次',
  `balance_direction` VARCHAR(16) NOT NULL DEFAULT 'DEBIT' COMMENT 'DEBIT/CREDIT',
  `category` VARCHAR(64) DEFAULT '' COMMENT '科目类别',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `org_code` VARCHAR(32) DEFAULT NULL COMMENT '归属组织编码（数据权限）',
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fin_subject_code` (`code`, `deleted`),
  KEY `idx_fin_subject_org` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目主数据';

CREATE TABLE IF NOT EXISTS `fin_budget_subject` (
  `id` BIGINT NOT NULL,
  `code` VARCHAR(64) NOT NULL COMMENT '预算科目编码',
  `name` VARCHAR(128) NOT NULL COMMENT '预算科目名称',
  `year` INT NOT NULL COMMENT '年度',
  `annual_amount` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '年度额度',
  `account_subject_code` VARCHAR(64) DEFAULT NULL COMMENT '关联会计科目编码',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  `org_code` VARCHAR(32) DEFAULT NULL,
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fin_budget_subject_code` (`code`, `year`, `deleted`),
  KEY `idx_fin_budget_subject_org` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算科目主数据';

CREATE TABLE IF NOT EXISTS `fin_expense` (
  `id` BIGINT NOT NULL,
  `no` VARCHAR(40) NOT NULL COMMENT 'BX-yyyyMMdd-XXXXXX',
  `applicant` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(512) NOT NULL,
  `amount` DECIMAL(18,2) NOT NULL,
  `dept` VARCHAR(64) DEFAULT '',
  `subject` VARCHAR(128) DEFAULT '',
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED/POSTED/PAID',
  `voucher_no` VARCHAR(40) DEFAULT NULL,
  `date` DATE DEFAULT NULL,
  `org_code` VARCHAR(32) DEFAULT NULL,
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fin_expense_no` (`no`, `deleted`),
  KEY `idx_fin_expense_org_status` (`org_code`, `status`),
  KEY `idx_fin_expense_voucher` (`voucher_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用报销单';

CREATE TABLE IF NOT EXISTS `fin_voucher` (
  `id` BIGINT NOT NULL,
  `no` VARCHAR(40) NOT NULL COMMENT 'PZ-yyyyMMdd-XXXXXX（按月重置）',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'EXPENSE/AR/AP/MANUAL',
  `source_no` VARCHAR(40) DEFAULT '' COMMENT '来源业务单号（追溯）',
  `summary` VARCHAR(512) DEFAULT '',
  `subject` VARCHAR(128) DEFAULT '',
  `amount` DECIMAL(18,2) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/POSTED',
  `date` DATE DEFAULT NULL,
  `creator` VARCHAR(64) DEFAULT '',
  `dept` VARCHAR(64) DEFAULT '',
  `contract_no` VARCHAR(64) DEFAULT NULL,
  `org_code` VARCHAR(32) DEFAULT NULL,
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fin_voucher_no` (`no`, `deleted`),
  KEY `idx_fin_voucher_source` (`source_no`, `source_type`),
  KEY `idx_fin_voucher_org_status` (`org_code`, `status`),
  KEY `idx_fin_voucher_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计凭证';

CREATE TABLE IF NOT EXISTS `fin_ar_ap` (
  `id` BIGINT NOT NULL,
  `no` VARCHAR(40) NOT NULL COMMENT 'YS/YF-yyyyMMdd-XXXXXX',
  `party` VARCHAR(128) NOT NULL COMMENT '往来单位',
  `type` VARCHAR(8) NOT NULL COMMENT 'AR 应收 / AP 应付',
  `amount` DECIMAL(18,2) NOT NULL,
  `contract_no` VARCHAR(64) DEFAULT NULL,
  `voucher_no` VARCHAR(40) DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `date` DATE DEFAULT NULL,
  `subject` VARCHAR(128) DEFAULT '',
  `summary` VARCHAR(512) DEFAULT '',
  `payment_stage` VARCHAR(64) DEFAULT NULL,
  `creator` VARCHAR(64) DEFAULT '',
  `dept` VARCHAR(64) DEFAULT '',
  `written_off_amount` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '累计已核销金额',
  `org_code` VARCHAR(32) DEFAULT NULL,
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fin_ar_ap_no` (`no`, `deleted`),
  KEY `idx_fin_ar_ap_contract` (`contract_no`, `type`),
  KEY `idx_fin_ar_ap_org_status` (`org_code`, `status`),
  KEY `idx_fin_ar_ap_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应收应付单';

CREATE TABLE IF NOT EXISTS `fin_budget` (
  `id` BIGINT NOT NULL,
  `dept` VARCHAR(64) NOT NULL,
  `year` INT NOT NULL,
  `amount` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '年度预算额度',
  `history` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '历史归档已用',
  `contract_committed` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同承诺占用（合同域写入）',
  `contract_paid` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同已付（合同域写入）',
  `contract_in_flight` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同在途（合同域写入）',
  `org_code` VARCHAR(32) DEFAULT NULL,
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fin_budget_dept_year` (`dept`, `year`, `deleted`),
  KEY `idx_fin_budget_org` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算额度';

CREATE TABLE IF NOT EXISTS `fin_payment` (
  `id` BIGINT NOT NULL,
  `no` VARCHAR(40) NOT NULL COMMENT 'FK-yyyyMMdd-XXXXXX',
  `contract_no` VARCHAR(64) NOT NULL,
  `party` VARCHAR(128) DEFAULT '',
  `amount` DECIMAL(18,2) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PAID/CANCELLED',
  `date` DATE DEFAULT NULL,
  `org_code` VARCHAR(32) DEFAULT NULL,
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fin_payment_no` (`no`, `deleted`),
  KEY `idx_fin_payment_contract` (`contract_no`),
  KEY `idx_fin_payment_org_status` (`org_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款单';

-- ============ 权限码：7 个菜单 + 8 个按钮 ============
INSERT IGNORE INTO `sso_ms`.`sso_permission` (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`) VALUES
  (2026112600000000701,'finance:overview:view','财务概览',1,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000702,'finance:voucher:list','凭证管理',1,0,2,'',NOW(),NOW(),0,'finance'),
  (2026112600000000703,'finance:expense:list','费用报销',1,0,3,'',NOW(),NOW(),0,'finance'),
  (2026112600000000704,'finance:budget:view','预算管理',1,0,4,'',NOW(),NOW(),0,'finance'),
  (2026112600000000705,'finance:ar-ap:list','应收应付',1,0,5,'',NOW(),NOW(),0,'finance'),
  (2026112600000000706,'finance:report:view','财务报表',1,0,6,'',NOW(),NOW(),0,'finance'),
  (2026112600000000707,'finance:master:list','主数据维护',1,0,7,'',NOW(),NOW(),0,'finance'),
  (2026112600000000708,'finance:voucher:edit','新增/编辑凭证',2,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000709,'finance:expense:create','发起报销',2,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000710,'finance:expense:audit','报销审批',2,0,2,'',NOW(),NOW(),0,'finance'),
  (2026112600000000711,'finance:budget:edit','维护预算',2,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000712,'finance:ar-ap:write','应收应付核销',2,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000713,'finance:master:edit','维护科目',2,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000714,'finance:salary:view','查看薪资明文',2,0,1,'',NOW(),NOW(),0,'finance'),
  (2026112600000000715,'finance:bank:view','查看银行账号',2,0,2,'',NOW(),NOW(),0,'finance');

-- 按钮挂到对应菜单下（幂等：仅修复 parent_id 仍为 0 的记录）
UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:voucher:list' AND m.`app_code`='finance' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='finance' AND p.`code`='finance:voucher:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);
UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:expense:list' AND m.`app_code`='finance' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='finance' AND p.`code` IN ('finance:expense:create','finance:expense:audit') AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);
UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:budget:view' AND m.`app_code`='finance' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='finance' AND p.`code`='finance:budget:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);
UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:ar-ap:list' AND m.`app_code`='finance' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='finance' AND p.`code` IN ('finance:ar-ap:write','finance:bank:view') AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);
UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:master:list' AND m.`app_code`='finance' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='finance' AND p.`code`='finance:master:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);
UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='finance:overview:view' AND m.`app_code`='finance' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='finance' AND p.`code`='finance:salary:view' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);


INSERT IGNORE INTO `sso_ms`.`sso_role` (`id`,`code`,`app_code`,`name`,`remark`,`created_at`,`updated_at`,`deleted`) VALUES
  (2026112600000000601,'FINANCE_ADMIN','finance','财务管理员','财务域全量权限',NOW(),NOW(),0),
  (2026112600000000602,'FINANCE_USER','finance','财务用户','报销与查询',NOW(),NOW(),0);
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='finance' AND p.`deleted`=0
WHERE r.`code`='FINANCE_ADMIN' AND r.`deleted`=0;
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='finance' AND p.`deleted`=0
  AND p.`code` IN ('finance:overview:view','finance:expense:list','finance:expense:create','finance:voucher:list','finance:ar-ap:list','finance:budget:view')
WHERE r.`code`='FINANCE_USER' AND r.`deleted`=0;


INSERT IGNORE INTO `sso_ms`.`sso_application`
  (`id`,`code`,`name`,`app_type`,`base_url`,`app_secret`,`icon`,`remark`,`status`,`sort`,`created_at`,`updated_at`,`deleted`,`redirect_uri`)
VALUES
  (2026112600000000501,'finance','北极星账','MANAGEMENT','https://polaris-finance.laimengchao.site','__SET_SSO_FINANCE_APP_SECRET__','/icons/finance.svg','财务域独立应用：凭证/报销/预算/应收应付/科目主数据',1,12,NOW(),NOW(),0,'https://polaris-finance.laimengchao.site/callback');

INSERT IGNORE INTO `sso_ms`.`sso_account_app` (`account_id`,`app_id`)
SELECT a.`id`,ap.`id` FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_application` ap ON ap.`code`='finance' AND ap.`deleted`=0
WHERE a.`deleted`=0
  AND (a.`username`='yanshi' OR a.`id` BETWEEN 2700000000000040001 AND 2700000000000040012);




-- ===== 财务域配置种子（与 settings data.sql 同 id；settings 侧 sql.init.mode=never，需手工执行） =====
-- 单号规则（7 条）
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11401,'finance','fin_expense','BX','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11402,'finance','fin_other_expense','QT','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11403,'finance','fin_payroll','XZ','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11404,'finance','fin_ar_ap','YS','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11405,'finance','fin_voucher','PZ','-',6,1,1,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11406,'finance','fin_budget_adjust','BG','-',6,1,1,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11407,'finance','fin_payment','FK','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
-- 埋点（8 条）
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000010,'finance.voucher.auto-post','finance','财务管理','审批通过自动生成凭证','false',1,8,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000011,'finance.aging.buckets','finance','财务管理','账龄分档','[30,60,90,180,365]',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000012,'finance.budget.alert-ratio','finance','财务管理','预算执行率预警线(%)','80',1,2,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000013,'finance.budget.control-mode','finance','财务管理','预算控制模式','"REMIND"',1,3,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000014,'finance.expense.approval-amount','finance','财务管理','报销高级审批线','5000',1,4,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000015,'finance.period.close-day','finance','财务管理','次月关账日','5',1,5,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000016,'finance.receivable.writeoff-mode','finance','财务管理','应收核销模式','"FIFO"',1,6,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000017,'finance.voucher.period-check','finance','财务管理','凭证期间校验','true',1,7,0,NOW(),NOW());
-- 字典类型（7 条）
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11507,'finance.doc_status','业务单据状态','finance','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11508,'finance.voucher_type','凭证类型','finance','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11509,'finance.expense_reason','报销事由','finance','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11513,'finance.invoice_type','发票类型','finance','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11514,'finance.tax_rate','税率档位','finance','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11515,'finance.salary_item','薪资项目','finance','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11516,'finance.settle_method','结算方式','finance','',1,1,0,NOW(),NOW());
-- 字典项（34 条）
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500801,'finance.doc_status','DRAFT','草稿',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500802,'finance.doc_status','PENDING','待审批',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500803,'finance.doc_status','APPROVED','已通过',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500804,'finance.doc_status','REJECTED','已驳回',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500805,'finance.doc_status','POSTED','已入账',5,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500806,'finance.doc_status','PAID','已付款',6,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500807,'finance.voucher_type','JZ','记账凭证',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500808,'finance.voucher_type','SK','收款凭证',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500809,'finance.voucher_type','FK','付款凭证',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500810,'finance.voucher_type','ZZ','转账凭证',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500811,'finance.expense_reason','TRAVEL','差旅费',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500812,'finance.expense_reason','OFFICE','办公费',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500813,'finance.expense_reason','ENTERTAIN','招待费',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500814,'finance.expense_reason','TRAIN','培训费',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500815,'finance.expense_reason','OTHER','其他',5,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500816,'finance.invoice_type','VAT_SPECIAL','增值税专票',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500817,'finance.invoice_type','VAT_NORMAL','增值税普票',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500818,'finance.invoice_type','RECEIPT','收据',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500819,'finance.invoice_type','OTHER','其他',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500820,'finance.tax_rate','0','0%',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500821,'finance.tax_rate','1','1%',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500822,'finance.tax_rate','3','3%',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500823,'finance.tax_rate','6','6%',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500824,'finance.tax_rate','9','9%',5,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500825,'finance.tax_rate','13','13%',6,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500826,'finance.salary_item','BASE','基本工资',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500827,'finance.salary_item','PERFORMANCE','绩效',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500828,'finance.salary_item','ALLOWANCE','补贴',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500829,'finance.salary_item','SOCIAL','社保',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500830,'finance.salary_item','FUND','公积金',5,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500831,'finance.settle_method','TRANSFER','银行转账',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500832,'finance.settle_method','CASH','现金',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500833,'finance.settle_method','ACCEPTANCE','承兑汇票',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500834,'finance.settle_method','OTHER','其他',4,1,'',1,0,NOW(),NOW());

INSERT IGNORE INTO `settings`.`cfg_approval_template` (`id`, `code`, `name`, `app_code`, `biz_type`, `biz_name`, `enabled`, `version`, `remark`, `deleted`, `created_at`, `updated_at`) VALUES (9018,'finance_expense_default','费用报销审批流程','finance','finance.expense','费用报销',1,1,'部门负责人 → 财务中心负责人',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_approval_template` (`id`, `code`, `name`, `app_code`, `biz_type`, `biz_name`, `enabled`, `version`, `remark`, `deleted`, `created_at`, `updated_at`) VALUES (9019,'finance_expense_senior','费用报销审批流程（超金额档位）','finance','finance.expense.senior','费用报销',1,1,'部门负责人 → 财务中心负责人 → 总经理终审',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9131,9018,1,'部门审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9132,9018,2,'财务中心审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9133,9019,1,'部门审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9134,9019,2,'财务中心审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9135,9019,3,'总经理终审','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9241,9018,9131,1,'部门负责人','','','[{\"type\":\"ORG_LEADER\",\"source\":\"FIELD:dept:ORG\",\"levelOffset\":0}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9242,9018,9132,2,'财务中心负责人','','','[{\"type\":\"ORG_LEADER_BY_NAME\",\"code\":\"财务中心\"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9243,9019,9133,1,'部门负责人','','','[{\"type\":\"ORG_LEADER\",\"source\":\"FIELD:dept:ORG\",\"levelOffset\":0}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9244,9019,9134,2,'财务中心负责人','','','[{\"type\":\"ORG_LEADER_BY_NAME\",\"code\":\"财务中心\"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9245,9019,9135,3,'总经理','','','[{\"type\":\"ORG_LEADER_BY_NAME\",\"code\":\"晨星科技集团\"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_biz_scene` (`id`, `app_code`, `biz_type`, `biz_name`, `form_schema`, `field_schema`, `table_bindings`, `perm_hint`, `source`, `sort`, `deleted`, `created_at`, `updated_at`, `subject_field`, `biz_id_kind`) VALUES (9318,'finance','finance.expense','费用报销审批','[{\"code\":\"expenseId\",\"name\":\"报销单\",\"dataType\":\"STRING\"},{\"code\":\"no\",\"name\":\"单号\",\"dataType\":\"STRING\"},{\"code\":\"amount\",\"name\":\"报销金额\",\"dataType\":\"NUMBER\"},{\"code\":\"dept\",\"name\":\"归属部门\",\"dataType\":\"ORG\"},{\"code\":\"reason\",\"name\":\"事由\",\"dataType\":\"STRING\"},{\"code\":\"applicant\",\"name\":\"申请人\",\"dataType\":\"STRING\"}]','[{\"code\":\"no\",\"name\":\"报销单号\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"amount\",\"name\":\"报销金额\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"dept\",\"name\":\"归属部门\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"reason\",\"name\":\"报销事由\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"applicant\",\"name\":\"申请人\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"submitter\",\"name\":\"提交人\",\"source\":\"SYSTEM\",\"format\":\"text\"}]','{\"appCode\":\"finance\",\"subjectKey\":\"expenseId\",\"tables\":[{\"table\":\"fin_expense\",\"label\":\"费用报销单\",\"main\":true,\"mode\":\"MAIN_ROW\"}]}','','REGISTER',18,0,NOW(),NOW(),'expenseId','DOC');
INSERT IGNORE INTO `settings`.`cfg_biz_scene` (`id`, `app_code`, `biz_type`, `biz_name`, `form_schema`, `field_schema`, `table_bindings`, `perm_hint`, `source`, `sort`, `deleted`, `created_at`, `updated_at`, `subject_field`, `biz_id_kind`) VALUES (9319,'finance','finance.expense.senior','费用报销审批（超金额档位）','[{\"code\":\"expenseId\",\"name\":\"报销单\",\"dataType\":\"STRING\"},{\"code\":\"no\",\"name\":\"单号\",\"dataType\":\"STRING\"},{\"code\":\"amount\",\"name\":\"报销金额\",\"dataType\":\"NUMBER\"},{\"code\":\"dept\",\"name\":\"归属部门\",\"dataType\":\"ORG\"},{\"code\":\"reason\",\"name\":\"事由\",\"dataType\":\"STRING\"},{\"code\":\"applicant\",\"name\":\"申请人\",\"dataType\":\"STRING\"}]','[{\"code\":\"no\",\"name\":\"报销单号\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"amount\",\"name\":\"报销金额\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"dept\",\"name\":\"归属部门\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"reason\",\"name\":\"报销事由\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"applicant\",\"name\":\"申请人\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"submitter\",\"name\":\"提交人\",\"source\":\"SYSTEM\",\"format\":\"text\"}]','{\"appCode\":\"finance\",\"subjectKey\":\"expenseId\",\"tables\":[{\"table\":\"fin_expense\",\"label\":\"费用报销单\",\"main\":true,\"mode\":\"MAIN_ROW\"}]}','','REGISTER',19,0,NOW(),NOW(),'expenseId','DOC');
SELECT 'finance init done' AS result;
