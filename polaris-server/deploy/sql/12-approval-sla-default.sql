-- 审批 SLA 默认阈值：存量步骤补齐 24 小时，避免超时扫描任务因 sla_hours=NULL 永不触发。
-- 幂等：只更新尚未设置阈值的有效步骤，重复执行不会覆盖管理员手工调整过的值。
-- 生产 env：settings 库；本地库名同为 settings。

-- 让以后新建步骤默认带 24 小时阈值，避免再次出现 NULL
ALTER TABLE `settings`.`cfg_approval_step`
  MODIFY COLUMN `sla_hours` int(11) DEFAULT 24
  COMMENT '时效：在当前步骤停留超过该小时数触发超时提醒，NULL=不提醒；新建步骤默认24小时';

UPDATE `settings`.`cfg_approval_step`
SET `sla_hours` = 24, `updated_at` = NOW()
WHERE `deleted` = 0 AND `sla_hours` IS NULL;

-- 验证：应返回 0 行
SELECT `id`, `name`, `sla_hours`
FROM `settings`.`cfg_approval_step`
WHERE `deleted` = 0 AND `sla_hours` IS NULL;
