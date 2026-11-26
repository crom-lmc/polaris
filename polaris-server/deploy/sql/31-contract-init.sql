-- 合同域（北极星契）：建库建表 + 权限码 + 角色授权 + 应用注册 + 演示账号授权
-- 幂等可重复执行。生产 CONTRACT_SQL_INIT_MODE=never，必须手工执行本脚本。

CREATE DATABASE IF NOT EXISTS `polaris_contract` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `polaris_contract`;

CREATE TABLE IF NOT EXISTS `contract_partner` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '往来单位编码',
  `name` varchar(200) NOT NULL COMMENT '往来单位名称',
  `subject_type` varchar(16) NOT NULL DEFAULT 'COMPANY' COMMENT 'COMPANY/PERSON',
  `role` varchar(16) NOT NULL DEFAULT 'SUPPLIER' COMMENT 'SUPPLIER/CUSTOMER/BOTH',
  `type` varchar(64) NOT NULL DEFAULT '' COMMENT '单位类型',
  `credit_code` varchar(64) NOT NULL DEFAULT '' COMMENT '统一社会信用代码；个人为身份证号',
  `legal_person` varchar(64) NOT NULL DEFAULT '' COMMENT '法定代表人',
  `registered_capital` varchar(64) NOT NULL DEFAULT '' COMMENT '注册资本',
  `establish_date` date DEFAULT NULL COMMENT '成立日期',
  `taxpayer_type` varchar(64) NOT NULL DEFAULT '' COMMENT '纳税人性质',
  `address` varchar(300) NOT NULL DEFAULT '' COMMENT '注册地址',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `contact` varchar(64) NOT NULL DEFAULT '' COMMENT '联系人',
  `phone` varchar(64) NOT NULL DEFAULT '' COMMENT '联系电话',
  `terms` varchar(200) NOT NULL DEFAULT '' COMMENT '结算账期/条款',
  `rating` varchar(8) NOT NULL DEFAULT 'B' COMMENT '评级 A/B/C',
  `credit_limit` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '授信额度；0=不限制（销售信用校验埋点开启时生效）',
  `credit_used` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '已占用额度（手工维护）',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'COOPERATING/PENDING/STOPPED',
  `bank` varchar(200) NOT NULL DEFAULT '' COMMENT '开户行',
  `account_name` varchar(200) NOT NULL DEFAULT '' COMMENT '账户名称',
  `bank_account` varchar(128) NOT NULL DEFAULT '' COMMENT '银行账号（按权限脱敏）',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（数据权限）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_partner_code` (`code`,`deleted`),
  KEY `idx_partner_role_status` (`role`,`status`,`deleted`),
  KEY `idx_partner_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='往来单位（供应商/客户共用）';

CREATE TABLE IF NOT EXISTS `contract_material` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父节点ID；根节点为 NULL',
  `code` varchar(32) NOT NULL COMMENT '分类 01/0101/010101；物料=三级分类码+6位流水',
  `name` varchar(200) NOT NULL COMMENT '节点名称',
  `kind` varchar(16) NOT NULL COMMENT 'CATEGORY/MATERIAL',
  `spec` varchar(200) NOT NULL DEFAULT '' COMMENT '规格',
  `unit` varchar(32) NOT NULL DEFAULT '' COMMENT '计量单位',
  `reference_price` decimal(18,2) DEFAULT NULL COMMENT '参考价',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（数据权限）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_material_parent` (`parent_id`,`deleted`),
  KEY `idx_material_code` (`code`,`deleted`),
  KEY `idx_material_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料三级树（分类与物料共树）';

CREATE TABLE IF NOT EXISTS `contract_project` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `code` varchar(32) NOT NULL COMMENT 'PRJ-yyyyMMdd-XXXXXX（settings 单号规则）',
  `name` varchar(200) NOT NULL COMMENT '项目名称',
  `type` varchar(16) NOT NULL DEFAULT 'INTERNAL' COMMENT 'SALES/PURCHASE/INTERNAL',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织，由项目组带出',
  `team_code` varchar(64) NOT NULL DEFAULT '' COMMENT '组织项目组编码（EHR）',
  `cost_center_code` varchar(64) NOT NULL DEFAULT '' COMMENT '成本中心=公司主体编码（EHR）',
  `owner` varchar(64) NOT NULL DEFAULT '' COMMENT '负责人',
  `budget_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '项目预算',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/PAUSED/CLOSED/CANCELLED',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_project_code` (`code`,`deleted`),
  KEY `idx_project_org_status` (`org_code`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务项目档案';

CREATE TABLE IF NOT EXISTS `contract_header` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `no` varchar(32) NOT NULL COMMENT 'HT-CG/HT-XS-yyyy-XXXXXX（settings 单号规则）',
  `type` varchar(16) NOT NULL COMMENT 'PURCHASE/SALES',
  `name` varchar(200) NOT NULL COMMENT '合同名称',
  `partner_code` varchar(64) NOT NULL DEFAULT '' COMMENT '往来单位编码',
  `project_no` varchar(32) DEFAULT NULL COMMENT '所属项目（单向挂接）',
  `dept` varchar(64) NOT NULL DEFAULT '' COMMENT '签约部门',
  `owner` varchar(64) NOT NULL DEFAULT '' COMMENT '合同负责人',
  `signed_date` date DEFAULT NULL COMMENT '签订日期',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `end_date` date DEFAULT NULL COMMENT '到期日期',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/EFFECTIVE/EXECUTING/COMPLETED/TERMINATED',
  `payment_terms` varchar(500) NOT NULL DEFAULT '' COMMENT '付款条款',
  `subject` varchar(200) NOT NULL DEFAULT '' COMMENT '业务科目',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（数据权限）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_header_no` (`no`,`deleted`),
  KEY `idx_header_type_status` (`type`,`status`,`deleted`),
  KEY `idx_header_partner` (`partner_code`,`deleted`),
  KEY `idx_header_project` (`project_no`,`deleted`),
  KEY `idx_header_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同台账（采购/销售共用）';

CREATE TABLE IF NOT EXISTS `contract_item` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `contract_no` varchar(32) NOT NULL COMMENT 'contract_header.no',
  `line_no` int(11) NOT NULL DEFAULT 1 COMMENT '行号',
  `material_code` varchar(32) NOT NULL COMMENT 'contract_material.code（物料叶子）',
  `material_name` varchar(200) NOT NULL DEFAULT '' COMMENT '物料名称快照',
  `spec` varchar(200) NOT NULL DEFAULT '' COMMENT '规格快照',
  `unit` varchar(32) NOT NULL DEFAULT '' COMMENT '单位快照',
  `qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '数量',
  `price` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '单价',
  `amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额=qty*price',
  `delivered_qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已到货数量',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（随主合同）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_item_contract` (`contract_no`,`line_no`,`deleted`),
  KEY `idx_item_material` (`material_code`,`deleted`),
  KEY `idx_item_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同清单（金额唯一来源）';

CREATE TABLE IF NOT EXISTS `contract_payment_plan` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `contract_no` varchar(32) NOT NULL COMMENT 'contract_header.no',
  `stage` varchar(64) NOT NULL DEFAULT '' COMMENT '付款阶段（settings 字典 contract.payment_stage）',
  `due_date` date DEFAULT NULL COMMENT '计划付款日期',
  `amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '计划金额',
  `source_doc_no` varchar(32) DEFAULT NULL COMMENT '财务域来源单据号（YS/FK，由财务域生成）',
  `status` varchar(16) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/PENDING/APPROVED/PAID/REJECTED',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（随主合同）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_plan_contract` (`contract_no`,`due_date`,`deleted`),
  KEY `idx_plan_source_doc` (`source_doc_no`,`deleted`),
  KEY `idx_plan_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同付款计划';

CREATE TABLE IF NOT EXISTS `contract_purchase_plan` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `no` varchar(32) NOT NULL COMMENT 'CGJH-yyyy-XXXXXX（settings 单号规则）',
  `title` varchar(200) NOT NULL COMMENT '计划标题',
  `dept` varchar(64) NOT NULL DEFAULT '' COMMENT '申请部门',
  `applicant` varchar(64) NOT NULL DEFAULT '' COMMENT '申请人',
  `need_date` date DEFAULT NULL COMMENT '需求日期',
  `budget_subject` varchar(200) NOT NULL DEFAULT '' COMMENT '预算科目',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/CONVERTED/REJECTED',
  `contract_no` varchar(32) DEFAULT NULL COMMENT '转换后的采购合同号',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（数据权限）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_purchase_plan_no` (`no`,`deleted`),
  KEY `idx_purchase_plan_status` (`status`,`deleted`),
  KEY `idx_purchase_plan_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购计划';

CREATE TABLE IF NOT EXISTS `contract_purchase_plan_item` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `plan_no` varchar(32) NOT NULL COMMENT 'contract_purchase_plan.no',
  `material_code` varchar(32) NOT NULL COMMENT '物料编码',
  `material_name` varchar(200) NOT NULL DEFAULT '' COMMENT '物料名称快照',
  `spec` varchar(200) NOT NULL DEFAULT '' COMMENT '规格',
  `unit` varchar(32) NOT NULL DEFAULT '' COMMENT '单位',
  `qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '数量',
  `price` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '单价',
  `amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（随计划）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_plan_item_plan` (`plan_no`,`deleted`),
  KEY `idx_plan_item_material` (`material_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购计划明细';

CREATE TABLE IF NOT EXISTS `contract_acceptance` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `no` varchar(32) NOT NULL COMMENT 'JD-yyyyMMdd-XXXXXX（settings 单号规则）',
  `contract_no` varchar(32) NOT NULL COMMENT 'contract_header.no',
  `item_id` bigint(20) NOT NULL COMMENT 'contract_item.id（雪花ID）',
  `material_code` varchar(32) NOT NULL DEFAULT '' COMMENT '物料编码',
  `qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '本次验收数量',
  `date` date DEFAULT NULL COMMENT '验收日期',
  `result` varchar(24) NOT NULL DEFAULT '合格' COMMENT '合格/部分合格/不合格',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/POSTED',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（随主合同）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_acceptance_no` (`no`,`deleted`),
  KEY `idx_acceptance_contract` (`contract_no`,`deleted`),
  KEY `idx_acceptance_item` (`item_id`,`deleted`),
  KEY `idx_acceptance_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同到货验收单';

CREATE TABLE IF NOT EXISTS `contract_change_log` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `no` varchar(32) NOT NULL COMMENT 'BG-yyyyMMdd-XXXXXX（settings 单号规则）',
  `contract_no` varchar(32) NOT NULL COMMENT 'contract_header.no',
  `change_type` varchar(32) NOT NULL COMMENT 'AMOUNT/TERMS/DATE/PARTNER/OTHER',
  `before_value` varchar(1000) NOT NULL DEFAULT '' COMMENT '变更前快照',
  `after_value` varchar(1000) NOT NULL DEFAULT '' COMMENT '变更后快照',
  `reason` varchar(500) NOT NULL DEFAULT '' COMMENT '变更原因',
  `operator` varchar(64) NOT NULL DEFAULT '' COMMENT '操作人',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '归属组织（随主合同，数据权限）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_change_no` (`no`,`deleted`),
  KEY `idx_change_contract` (`contract_no`,`deleted`),
  KEY `idx_change_org` (`org_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同变更记录（次数统计与审计留痕）';

-- ===== 存量升级：MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，用 information_schema 判重后动态加列 =====
SET @col_credit_limit = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_partner' AND COLUMN_NAME='credit_limit');
SET @sql_add_credit_limit = IF(@col_credit_limit=0, 'ALTER TABLE `polaris_contract`.`contract_partner` ADD COLUMN `credit_limit` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT ''授信额度；0=不限制'' AFTER `rating`', 'SELECT 1');
PREPARE stmt_credit_limit FROM @sql_add_credit_limit; EXECUTE stmt_credit_limit; DEALLOCATE PREPARE stmt_credit_limit;
SET @col_credit_used = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_partner' AND COLUMN_NAME='credit_used');
SET @sql_add_credit_used = IF(@col_credit_used=0, 'ALTER TABLE `polaris_contract`.`contract_partner` ADD COLUMN `credit_used` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT ''已占用额度（手工维护）'' AFTER `credit_limit`', 'SELECT 1');
PREPARE stmt_credit_used FROM @sql_add_credit_used; EXECUTE stmt_credit_used; DEALLOCATE PREPARE stmt_credit_used;

-- ============ 权限码：6 个菜单 + 8 个按钮 ============
INSERT IGNORE INTO `sso_ms`.`sso_permission` (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`) VALUES
  (2026112600000000801,'contract:plan:list','采购计划',1,0,1,'',NOW(),NOW(),0,'contract'),
  (2026112600000000802,'contract:partner:list','往来单位维护',1,0,2,'',NOW(),NOW(),0,'contract'),
  (2026112600000000803,'contract:material:list','物料库',1,0,3,'',NOW(),NOW(),0,'contract'),
  (2026112600000000804,'contract:project:list','项目维护',1,0,4,'',NOW(),NOW(),0,'contract'),
  (2026112600000000805,'contract:ledger:list','合同台账',1,0,5,'',NOW(),NOW(),0,'contract'),
  (2026112600000000806,'contract:payment:list','合同付款计划',1,0,6,'',NOW(),NOW(),0,'contract'),
  (2026112600000000807,'contract:plan:edit','新增/审批采购计划',2,0,1,'',NOW(),NOW(),0,'contract'),
  (2026112600000000808,'contract:partner:edit','维护往来单位',2,0,1,'',NOW(),NOW(),0,'contract'),
  (2026112600000000809,'contract:material:edit','维护物料',2,0,1,'',NOW(),NOW(),0,'contract'),
  (2026112600000000810,'contract:project:edit','维护项目',2,0,1,'',NOW(),NOW(),0,'contract'),
  (2026112600000000811,'contract:ledger:edit','新增/编辑合同',2,0,1,'',NOW(),NOW(),0,'contract'),
  (2026112600000000812,'contract:item:edit','维护合同清单',2,0,2,'',NOW(),NOW(),0,'contract'),
  (2026112600000000813,'contract:payment:edit','发起付款',2,0,1,'',NOW(),NOW(),0,'contract'),
  (2026112600000000814,'contract:partner:bank:view','查看银行账号',2,0,2,'',NOW(),NOW(),0,'contract');

UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:plan:list' AND m.`app_code`='contract' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='contract' AND p.`code`='contract:plan:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:partner:list' AND m.`app_code`='contract' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='contract' AND p.`code`='contract:partner:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:material:list' AND m.`app_code`='contract' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='contract' AND p.`code`='contract:material:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:project:list' AND m.`app_code`='contract' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='contract' AND p.`code`='contract:project:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:ledger:list' AND m.`app_code`='contract' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='contract' AND p.`code`='contract:ledger:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:ledger:list' AND m.`app_code`='contract' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='contract' AND p.`code`='contract:item:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:payment:list' AND m.`app_code`='contract' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='contract' AND p.`code`='contract:payment:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

UPDATE `sso_ms`.`sso_permission` p JOIN `sso_ms`.`sso_permission` m ON m.`code`='contract:partner:list' AND m.`app_code`='contract' AND m.`deleted`=0 SET p.`parent_id`=m.`id` WHERE p.`app_code`='contract' AND p.`code`='contract:partner:bank:view' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);


INSERT IGNORE INTO `sso_ms`.`sso_role` (`id`,`code`,`app_code`,`name`,`remark`,`created_at`,`updated_at`,`deleted`) VALUES
  (2026112600000000611,'CONTRACT_ADMIN','contract','合同管理员','合同域全量权限',NOW(),NOW(),0),
  (2026112600000000612,'CONTRACT_USER','contract','合同用户','查询与清单维护',NOW(),NOW(),0);
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='contract' AND p.`deleted`=0
WHERE r.`code`='CONTRACT_ADMIN' AND r.`deleted`=0;
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='contract' AND p.`deleted`=0
  AND p.`code` IN ('contract:plan:list','contract:partner:list','contract:material:list','contract:project:list','contract:ledger:list','contract:item:edit')
WHERE r.`code`='CONTRACT_USER' AND r.`deleted`=0;


INSERT IGNORE INTO `sso_ms`.`sso_application`
  (`id`,`code`,`name`,`app_type`,`base_url`,`app_secret`,`icon`,`remark`,`status`,`sort`,`created_at`,`updated_at`,`deleted`,`redirect_uri`)
VALUES
  (2026112600000000502,'contract','北极星契','MANAGEMENT','https://polaris-contract.laimengchao.site','__SET_SSO_CONTRACT_APP_SECRET__','/icons/contract.svg','合同域独立应用：往来单位/物料/项目/合同台账/付款计划/采购计划',1,12,NOW(),NOW(),0,'https://polaris-contract.laimengchao.site/callback');

INSERT IGNORE INTO `sso_ms`.`sso_account_app` (`account_id`,`app_id`)
SELECT a.`id`,ap.`id` FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_application` ap ON ap.`code`='contract' AND ap.`deleted`=0
WHERE a.`deleted`=0
  AND (a.`username`='yanshi' OR a.`id` BETWEEN 2700000000000040001 AND 2700000000000040012);




-- ===== 合同域配置种子（与 settings data.sql 同 id；settings 侧 sql.init.mode=never，需手工执行） =====
-- 单号规则（5 条）
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11408,'contract','contract_purchase_plan','CGJH','-',6,1,0,1,'yyyy','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11409,'contract','contract_header_purchase','HT-CG','-',6,1,0,1,'yyyy','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11410,'contract','contract_acceptance','JD','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11411,'contract','contract_project','PRJ','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11414,'contract','contract_header_sales','HT-XS','-',6,1,0,1,'yyyy','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`) VALUES (11415,'contract','contract_change_log','BG','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());
-- 埋点（7 条）
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000020,'contract.budget.allow-over','contract','合同管理','合同金额允许超过项目预算','false',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000021,'contract.payment.remind-days','contract','合同管理','付款到期提醒天数','7',1,2,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000022,'contract.acceptance.required-before-pay','contract','合同管理','付款前必须验收','true',1,3,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000023,'contract.purchase.approval-amount','contract','合同管理','采购合同审批升级线','100000',1,4,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000024,'contract.sales.credit-check','contract','合同管理','销售合同信用校验','false',1,5,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000025,'contract.change.max-times','contract','合同管理','合同变更次数上限','3',1,6,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`, `code`, `app_code`, `module`, `name`, `value`, `built_in`, `sort`, `deleted`, `created_at`, `updated_at`) VALUES (2026112600000000026,'contract.material.code-pattern','contract','合同管理','物料编码层级/宽度/流水位','{"levels":3,"width":2,"serial":6}',1,7,0,NOW(),NOW());
-- 字典类型（9 条）
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11501,'contract.contract_type','合同类型','contract','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11502,'contract.contract_status','合同状态','contract','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11503,'contract.payment_stage','付款阶段','contract','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11504,'contract.subject_type','主体类型','contract','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11505,'project.type','项目类型','contract','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11506,'project.status','项目状态','contract','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11510,'contract.acceptance_result','验收结果','contract','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11511,'contract.supplier_status','合作状态','contract','',1,1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`, `code`, `name`, `app_code`, `remark`, `built_in`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (11512,'contract.supplier_rating','往来单位评级','contract','',1,1,0,NOW(),NOW());
-- 字典项（32 条）
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500901,'contract.contract_type','PURCHASE','采购合同',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500902,'contract.contract_type','SALES','销售合同',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500903,'contract.contract_status','DRAFT','草稿',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500904,'contract.contract_status','PENDING','审批中',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500905,'contract.contract_status','EFFECTIVE','已生效',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500906,'contract.contract_status','EXECUTING','执行中',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500907,'contract.contract_status','COMPLETED','已完成',5,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500908,'contract.contract_status','TERMINATED','已终止',6,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500909,'contract.payment_stage','PREPAY','预付款',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500910,'contract.payment_stage','GOODS','到货款',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500911,'contract.payment_stage','ACCEPT','验收款',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500912,'contract.payment_stage','WARRANTY','质保金',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500913,'contract.payment_stage','OTHER','其他付款',5,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500914,'contract.subject_type','COMPANY','企业',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500915,'contract.subject_type','PERSON','个人',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500916,'project.type','SALES','销售项目',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500917,'project.type','PURCHASE','采购项目',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500918,'project.type','INTERNAL','内部项目',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500919,'project.status','DRAFT','立项中',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500920,'project.status','ACTIVE','执行中',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500921,'project.status','PAUSED','已暂停',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500922,'project.status','CLOSED','已结项',4,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500923,'project.status','CANCELLED','已取消',5,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500924,'contract.acceptance_result','PASS','合格',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500925,'contract.acceptance_result','PARTIAL','部分合格',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500926,'contract.acceptance_result','FAIL','不合格',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500927,'contract.supplier_status','PENDING','待准入',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500928,'contract.supplier_status','ACTIVE','合作中',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500929,'contract.supplier_status','DISABLED','已停用',3,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500930,'contract.supplier_rating','A','A',1,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500931,'contract.supplier_rating','B','B',2,1,'',1,0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`, `type_code`, `value`, `label`, `sort`, `status`, `remark`, `built_in`, `deleted`, `created_at`, `updated_at`) VALUES (1500932,'contract.supplier_rating','C','C',3,1,'',1,0,NOW(),NOW());

INSERT IGNORE INTO `settings`.`cfg_approval_template` (`id`, `code`, `name`, `app_code`, `biz_type`, `biz_name`, `enabled`, `version`, `remark`, `deleted`, `created_at`, `updated_at`) VALUES (9020,'contract_purchase_default','采购合同审批流程','contract','contract.purchase','采购合同',1,1,'部门负责人 → 财务中心负责人',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_approval_template` (`id`, `code`, `name`, `app_code`, `biz_type`, `biz_name`, `enabled`, `version`, `remark`, `deleted`, `created_at`, `updated_at`) VALUES (9021,'contract_purchase_senior','采购合同审批流程（超金额档位）','contract','contract.purchase.senior','采购合同',1,1,'部门负责人 → 财务中心负责人 → 总经理终审',0,NOW(),NOW());
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9136,9020,1,'部门审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9137,9020,2,'财务中心审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9138,9021,1,'部门审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9139,9021,2,'财务中心审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_step` (`id`, `template_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `reject_action`, `deleted`, `created_at`, `updated_at`, `parent_node_id`, `branch_path`, `depth`, `ui_kind`, `sla_hours`) VALUES (9140,9021,3,'总经理终审','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9246,9020,9136,1,'部门负责人','','','[{\"type\":\"ORG_LEADER\",\"source\":\"FIELD:dept:ORG\",\"levelOffset\":0}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9247,9020,9137,2,'财务中心负责人','','','[{\"type\":\"ORG_LEADER_BY_NAME\",\"code\":\"财务中心\"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9248,9021,9138,1,'部门负责人','','','[{\"type\":\"ORG_LEADER\",\"source\":\"FIELD:dept:ORG\",\"levelOffset\":0}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9249,9021,9139,2,'财务中心负责人','','','[{\"type\":\"ORG_LEADER_BY_NAME\",\"code\":\"财务中心\"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_approval_node` (`id`, `template_id`, `step_id`, `seq`, `name`, `condition_expr`, `condition_desc`, `approvers`, `approve_mode`, `allow_self`, `skip_same`, `on_empty`, `timeout_hours`, `deleted`, `created_at`, `updated_at`, `is_fallback`, `child_chain`) VALUES (9250,9021,9140,3,'总经理','','','[{\"type\":\"ORG_LEADER_BY_NAME\",\"code\":\"晨星科技集团\"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);
INSERT IGNORE INTO `settings`.`cfg_biz_scene` (`id`, `app_code`, `biz_type`, `biz_name`, `form_schema`, `field_schema`, `table_bindings`, `perm_hint`, `source`, `sort`, `deleted`, `created_at`, `updated_at`, `subject_field`, `biz_id_kind`) VALUES (9320,'contract','contract.purchase','采购合同审批','[{\"code\":\"contractId\",\"name\":\"合同\",\"dataType\":\"STRING\"},{\"code\":\"no\",\"name\":\"合同编号\",\"dataType\":\"STRING\"},{\"code\":\"name\",\"name\":\"合同名称\",\"dataType\":\"STRING\"},{\"code\":\"amount\",\"name\":\"合同金额\",\"dataType\":\"NUMBER\"},{\"code\":\"dept\",\"name\":\"归属部门\",\"dataType\":\"ORG\"},{\"code\":\"partnerCode\",\"name\":\"往来单位\",\"dataType\":\"STRING\"}]','[{\"code\":\"no\",\"name\":\"合同编号\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"name\",\"name\":\"合同名称\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"amount\",\"name\":\"合同金额\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"dept\",\"name\":\"归属部门\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"partnerCode\",\"name\":\"往来单位\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"submitter\",\"name\":\"提交人\",\"source\":\"SYSTEM\",\"format\":\"text\"}]','{\"appCode\":\"contract\",\"subjectKey\":\"contractId\",\"tables\":[{\"table\":\"contract_header\",\"label\":\"采购合同\",\"main\":true,\"mode\":\"MAIN_ROW\"}]}','','REGISTER',20,0,NOW(),NOW(),'contractId','DOC');
INSERT IGNORE INTO `settings`.`cfg_biz_scene` (`id`, `app_code`, `biz_type`, `biz_name`, `form_schema`, `field_schema`, `table_bindings`, `perm_hint`, `source`, `sort`, `deleted`, `created_at`, `updated_at`, `subject_field`, `biz_id_kind`) VALUES (9321,'contract','contract.purchase.senior','采购合同审批（超金额档位）','[{\"code\":\"contractId\",\"name\":\"合同\",\"dataType\":\"STRING\"},{\"code\":\"no\",\"name\":\"合同编号\",\"dataType\":\"STRING\"},{\"code\":\"name\",\"name\":\"合同名称\",\"dataType\":\"STRING\"},{\"code\":\"amount\",\"name\":\"合同金额\",\"dataType\":\"NUMBER\"},{\"code\":\"dept\",\"name\":\"归属部门\",\"dataType\":\"ORG\"},{\"code\":\"partnerCode\",\"name\":\"往来单位\",\"dataType\":\"STRING\"}]','[{\"code\":\"no\",\"name\":\"合同编号\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"name\",\"name\":\"合同名称\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"amount\",\"name\":\"合同金额\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"dept\",\"name\":\"归属部门\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"partnerCode\",\"name\":\"往来单位\",\"source\":\"FORM\",\"format\":\"text\"},{\"code\":\"submitter\",\"name\":\"提交人\",\"source\":\"SYSTEM\",\"format\":\"text\"}]','{\"appCode\":\"contract\",\"subjectKey\":\"contractId\",\"tables\":[{\"table\":\"contract_header\",\"label\":\"采购合同\",\"main\":true,\"mode\":\"MAIN_ROW\"}]}','','REGISTER',21,0,NOW(),NOW(),'contractId','DOC');
SELECT 'contract init done' AS result;
