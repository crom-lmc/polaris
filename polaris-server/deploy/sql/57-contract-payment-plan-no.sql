-- 付款申请独立单号：contract_payment_plan.no（FK-yyyy-XXXXXX，settings 单号规则）
-- 背景：原「来源单据」列显示的是财务域回写的 source_doc_no（YS/FK），发起申请前为空，
--       前端_display_为「待申请」，易被误认为无单号。现给付款计划自身生成独立单号。
-- 幂等：加列/加索引用 information_schema 守卫，规则与回填均可重复执行。

-- 1) 表结构：加 no 列与索引（MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，用存储过程守卫）
USE `polaris_contract`;
DROP PROCEDURE IF EXISTS `sp_add_payment_plan_no`;
DELIMITER $$
CREATE PROCEDURE `sp_add_payment_plan_no`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_payment_plan' AND COLUMN_NAME='no') THEN
    ALTER TABLE `contract_payment_plan`
      ADD COLUMN `no` varchar(32) DEFAULT NULL COMMENT '付款申请单号 FK-yyyy-XXXXXX（settings 单号规则，创建时生成）'
      AFTER `id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA='polaris_contract' AND TABLE_NAME='contract_payment_plan' AND INDEX_NAME='idx_plan_no') THEN
    ALTER TABLE `contract_payment_plan` ADD KEY `idx_plan_no` (`no`,`deleted`);
  END IF;
END$$
DELIMITER ;
CALL `sp_add_payment_plan_no`();
DROP PROCEDURE IF EXISTS `sp_add_payment_plan_no`;

-- 2) 单号规则：FK-yyyy-XXXXXX（id 11417，table_name=contract_payment_plan）
--    线上 settings 原先没有此规则，不配置会让 PaymentPlanServiceImpl.save() 取号时空号失败
--    （BizNoServiceImpl.next() fail-closed 抛 BIZ_NO_EMPTY）。settings 配置页面按 table_name 从库里读，
--    本条 INSERT 写入后即自动出现在「单号规则配置」页，无需代码白名单。
INSERT IGNORE INTO `settings`.`cfg_code_rule`
  (`id`,`app_code`,`table_name`,`prefix`,`separator_char`,`seq_length`,`pad_zero`,`reset_monthly`,`start_no`,`date_format`,`expr_format`,`last_no`,`last_period`,`deleted`,`created_at`,`updated_at`)
VALUES
  (11417,'contract','contract_payment_plan','FK','-',6,1,0,1,'yyyy','{prefix}-{date}-{seq}',0,'',0,NOW(),NOW());

-- 3) 存量回填：对尚无单号的付款计划按 id 顺序补 FK-yyyy-XXXXXX（yyyy 取 created_at）。
--    用会话变量递增，起始号 1；回填后把 cfg_code_rule.last_no 推进到已用最大号。
SET @pp_seq := 0;
UPDATE `polaris_contract`.`contract_payment_plan` p
   SET p.`no` = CONCAT('FK-', DATE_FORMAT(p.`created_at`,'%Y'), '-', LPAD((@pp_seq := @pp_seq + 1), 6, '0'))
 WHERE (p.`no` IS NULL OR p.`no` = '')
   AND p.`deleted` = 0
 ORDER BY p.`id`;

-- 推进规则流水：取当前年度已回填的最大流水，更新 last_no（its value = 已用到号）
UPDATE `settings`.`cfg_code_rule` r
   SET r.`last_no` = GREATEST(COALESCE(r.`last_no`,0),
        COALESCE((SELECT CAST(RIGHT(MAX(p.`no`),6) AS UNSIGNED)
           FROM `polaris_contract`.`contract_payment_plan` p
          WHERE p.`no` LIKE CONCAT('FK-',DATE_FORMAT(NOW(),'%Y'),'-%')), 0))
 WHERE r.`table_name` = 'contract_payment_plan' AND r.`deleted` = 0;

-- 4) 校验
SELECT 'payment_plan_no' AS `entity`,
       (SELECT COUNT(*) FROM `settings`.`cfg_code_rule` WHERE `table_name`='contract_payment_plan' AND `deleted`=0) AS `rules`,
       (SELECT COUNT(*) FROM `polaris_contract`.`contract_payment_plan` WHERE `deleted`=0) AS `plans`,
       (SELECT COUNT(*) FROM `polaris_contract`.`contract_payment_plan` WHERE `deleted`=0 AND (`no` IS NULL OR `no`='')) AS `no_missing`,
       (SELECT `last_no` FROM `settings`.`cfg_code_rule` WHERE `table_name`='contract_payment_plan' AND `deleted`=0) AS `last_no`;
