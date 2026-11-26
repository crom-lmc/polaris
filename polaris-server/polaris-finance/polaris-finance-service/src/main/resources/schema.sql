-- 北极星账（财务域）DDL：7 张业务表；生产环境 sql.init.mode=never，需手工执行本脚本。
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
