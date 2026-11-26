-- =============================================================================
-- 会议室预定：接入配置中心「单号规则」并刷新历史单号
--
-- 背景：原有 meeting_booking.booking_no 由业务侧自行拼接（MT+时间戳+随机数），
--       且历史造数脚本生成 HST/USG/NSG 等多种前缀，格式与平台单号规则不一致。
-- 目标：
--   1) 在 settings.cfg_code_rule 新增 meeting_booking 规则（前缀 MT，6 位流水）；
--   2) 把全部历史预定的单号按该规则统一刷新，格式 MT-yyyyMMdd-XXXXXX；
--   3) 规则 last_no 对齐历史最大流水，保证新预定取号不重复。
--
-- 规则参数：prefix=MT, separator=-, seq_length=6, pad_zero=1,
--           reset_monthly=0（全局流水不重置：历史跨 5 年且含未来日期，
--                            按月重置会导致不同月份单号序号重复、新取号冲突）,
--           start_no=1, date_format=yyyyMMdd, expr={prefix}-{date}-{seq}
--
-- 执行环境：MySQL 5.7（无窗口函数，用用户变量生成行号）；
--           生产 sql.init.mode=never，本脚本需手工执行。
-- 幂等性：单号刷新为确定性覆盖，重复执行单号结果相同；规则行按固定 id 重建。
-- 注意：last_no 取执行时的历史条数。系统上线产生新预定后（last_no 已随取号递增），
--       切勿重跑本脚本，否则 last_no 回退会与新单号冲突。仅首次部署时执行一次。
-- 执行前务必先备份 meeting_booking 与 cfg_code_rule 表。
-- =============================================================================

-- 1) 单号规则（先删后插保证可重复执行；id=11314 固定，与 data.sql 种子一致）
DELETE FROM `settings`.`cfg_code_rule` WHERE `id` = 11314;
INSERT INTO `settings`.`cfg_code_rule`
  (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`,
   `pad_zero`, `reset_monthly`, `start_no`, `date_format`, `expr_format`,
   `last_no`, `last_period`, `deleted`, `created_at`, `updated_at`)
VALUES
  (11314, 'meeting', 'meeting_booking', 'MT', '-', 6,
   1, 0, 1, 'yyyyMMdd', '{prefix}-{date}-{seq}',
   (SELECT c FROM (SELECT COUNT(*) AS c FROM `polaris_meetingroom`.`meeting_booking` WHERE `deleted` = 0) t),
   DATE_FORMAT(NOW(), '%Y%m'), 0, NOW(), NOW());

-- 2) 刷新历史单号：按 start_time/id 排序生成 1..N 的全局流水，日期取各自会议日期
--    用中间表落映射，避免 UPDATE ... ORDER BY 在 JOIN 场景下的不确定性。
DROP TEMPORARY TABLE IF EXISTS `tmp_booking_no_seq`;
CREATE TEMPORARY TABLE `tmp_booking_no_seq` (
  `id`  BIGINT NOT NULL PRIMARY KEY,
  `seq` INT NOT NULL,
  `day` VARCHAR(8) NOT NULL
) ENGINE=MEMORY;

SET @rn := 0;
INSERT INTO `tmp_booking_no_seq` (`id`, `seq`, `day`)
SELECT
  t.`id`,
  (@rn := @rn + 1) AS `seq`,
  DATE_FORMAT(t.`start_time`, '%Y%m%d') AS `day`
FROM (
  SELECT `id`, `start_time`
  FROM `polaris_meetingroom`.`meeting_booking`
  WHERE `deleted` = 0
  ORDER BY `start_time`, `id`
) t;

UPDATE `polaris_meetingroom`.`meeting_booking` mb
JOIN `tmp_booking_no_seq` s ON s.`id` = mb.`id`
SET mb.`booking_no` = CONCAT('MT-', s.`day`, '-', LPAD(s.`seq`, 6, '0'));

DROP TEMPORARY TABLE IF EXISTS `tmp_booking_no_seq`;
