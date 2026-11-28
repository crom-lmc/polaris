-- 合同到货签收（2026-11-28）
-- 口径：签收与验收完全独立——签收累加 contract_item.signed_qty（到货款放行依据），验收累加 delivered_qty（验收款放行依据）。
-- 到货款门槛：所选清单行 signed_qty > 0，不再要求合格验收单；验收款仍要求全部验收完成。
-- 幂等：ADD COLUMN / CREATE TABLE / INSERT IGNORE 均可重复执行。

USE `polaris_contract`;

-- 1) 清单行加已签收数量
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_item' AND COLUMN_NAME='signed_qty');
SET @sql = IF(@col=0, 'ALTER TABLE `contract_item` ADD COLUMN `signed_qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT ''已到货签收数量（到货款放行依据）'' AFTER `amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 签收单表（QS-yyyyMMdd-XXXXXX）
CREATE TABLE IF NOT EXISTS `contract_sign` (
  `id`            bigint        NOT NULL COMMENT '雪花ID',
  `no`            varchar(32)   NOT NULL COMMENT 'QS-yyyyMMdd-XXXXXX',
  `contract_no`   varchar(32)   NOT NULL COMMENT 'contract_header.no',
  `item_id`       bigint        NOT NULL COMMENT 'contract_item.id',
  `material_code` varchar(32)   NOT NULL DEFAULT '' COMMENT '物料编码快照',
  `qty`           decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '本次签收数量',
  `date`          date          DEFAULT NULL COMMENT '签收日期',
  `signer`        varchar(64)   NOT NULL DEFAULT '' COMMENT '签收人',
  `remark`        varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
  `status`        varchar(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 留痕单',
  `org_code`      varchar(64)   NOT NULL DEFAULT '' COMMENT '归属组织',
  `file_ids`      varchar(1024) NOT NULL DEFAULT '' COMMENT '签收凭证 file_id，逗号分隔',
  `created_at`    datetime      NOT NULL,
  `updated_at`    datetime      DEFAULT NULL,
  `deleted`       tinyint(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sign_no` (`no`, `deleted`),
  KEY `idx_sign_contract` (`contract_no`, `deleted`),
  KEY `idx_sign_item` (`item_id`, `deleted`),
  KEY `idx_sign_org` (`org_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同到货签收单（到货款放行依据，与验收独立）';

-- 3) 单号规则：QS-yyyyMMdd-XXXXXX（id 11418）
USE `settings`;
INSERT IGNORE INTO `cfg_code_rule` (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`)
VALUES (11418,'contract','contract_sign','QS','-',6,1,0,1,'yyyyMMdd','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());

-- 4) 签收权限：独立按钮码，挂合同台账菜单下（sort 5）
USE `sso_ms`;
INSERT IGNORE INTO `sso_permission` (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`) VALUES
  (2026112600000000825,'contract:sign:edit','到货签收',2,0,5,'',NOW(),NOW(),0,'contract');
UPDATE `sso_permission` p JOIN `sso_permission` m ON m.`code`='contract:ledger:list' AND m.`app_code`='contract' AND m.`deleted`=0
   SET p.`parent_id`=m.`id`
 WHERE p.`app_code`='contract' AND p.`code`='contract:sign:edit' AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);
-- 合同管理员全量角色：补授签收码（管理员授权为全量，重新执行也不重复）
INSERT IGNORE INTO `sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_role` r JOIN `sso_permission` p
  ON p.`app_code`='contract' AND p.`code`='contract:sign:edit' AND p.`deleted`=0
 WHERE r.`code`='CONTRACT_ADMIN' AND r.`deleted`=0;

-- 校验
SELECT 'contract_signoff' AS `entity`,
       (SELECT COUNT(*) FROM `polaris_contract`.`contract_sign`) AS `signs`,
       (SELECT COUNT(*) FROM `sso_ms`.`sso_permission` WHERE `code`='contract:sign:edit' AND `deleted`=0) AS `perms`;
