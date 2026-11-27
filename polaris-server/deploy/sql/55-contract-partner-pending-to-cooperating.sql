-- 往来单位：把存量「待准入（PENDING）」的供应商/客户批量更新为「合作中（COOPERATING）」（2026-11-27）
-- 背景：合同表单与采购/销售合同台账的往来单位下拉只拉取 status='COOPERATING'，
--       历史种子数据中存在大量 PENDING 单位，导致这些单位在新建合同时选不到。
-- 幂等：WHERE 条件只命中 PENDING，重复执行第二次影响 0 行；不改动已停用（STOPPED）单位。
-- 执行：MySQL 5.7，需指定 utf8mb4 字符集。

USE `polaris_contract`;

UPDATE `contract_partner`
   SET `status` = 'COOPERATING', `updated_at` = NOW()
 WHERE `deleted` = 0
   AND `status` = 'PENDING'
   AND `role` IN ('SUPPLIER', 'CUSTOMER', 'BOTH');

-- 校验：合作中数量 / 剩余待准入数量（后者应为 0）
SELECT `status`, `role`, COUNT(*) AS `cnt`
  FROM `contract_partner`
 WHERE `deleted` = 0
 GROUP BY `status`, `role`
 ORDER BY `status`, `role`;
