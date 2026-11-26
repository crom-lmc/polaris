-- 北极星会历史预约密度补齐：每间会议室每个工作日 9:00-12:00、14:00-17:00 两场（360/540 = 66.7% ≥ 20%）
-- 生成口径：2021-11-29 ~ 2026-11-19 的周一至周五（1299 个工作日）× 48 间会议室 × 2 场 = 124704 条；
-- 排除最近 7 天（21/22-meeting-stats-refresh-data.sql 已按每天两场覆盖，叠加会超过 100%）。
-- 状态 COMPLETED（历史区间已全部过去）计入使用率；发起人取 EHR 在职账号，能映射中文姓名，避免露出 ID。
-- 全部 INSERT IGNORE，ID 用独立段 2900000000000000000 + roomSeq*100000 + dayOffset*10 + slot，不与 26/27/28 段冲突。
-- 生产 sql.init.mode=never，需手工执行本脚本；无需重启服务。
INSERT IGNORE INTO `polaris_meetingroom`.`meeting_booking`
  (`id`,`booking_no`,`room_id`,`title`,`start_time`,`end_time`,`attendee_count`,
   `status`,`release_reason`,`city`,`creator_id`,`org_code`,`remark`,`created_at`,`updated_at`,`deleted`)
SELECT
  2900000000000000000 + (r.`id` - 2200000000000000000) * 100000 + nums.`n` * 10 + s.`slot`,
  CONCAT('HST', DATE_FORMAT(nums.`day`, '%Y%m%d'),
         LPAD(CAST(r.`id` - 2200000000000000000 AS CHAR), 3, '0'), s.`slot`),
  r.`id`,
  ELT(((r.`id` + nums.`n`) % 10) + 1,
      '项目周会','需求评审','技术方案讨论','月度复盘','跨部门协作',
      '迭代规划','设计评审','客户沟通','工作总结','架构讨论'),
  DATE_ADD(nums.`day`, INTERVAL s.`start_hour` HOUR),
  DATE_ADD(nums.`day`, INTERVAL s.`end_hour` HOUR),
  ((r.`id` + nums.`n` + s.`slot`) % 6) + 2,
  IF(nums.`day` >= CURDATE(), 'PENDING', 'COMPLETED'),
  '',
  b.`city`,
  ELT(((r.`id` + nums.`n` + s.`slot`) % 8) + 1,
      '2500000000000000009','2500000000000000021','2500000000000000041','2640000000000000014',
      '2640000000000000046','2700000000000040017','2500000000000000062','2640000000000000036'),
  CASE b.`city`
      WHEN '西安' THEN 'XC000100010001' WHEN '北京' THEN 'XC000100020001'
      WHEN '上海' THEN 'XC000100030001' ELSE 'XC000100040001' END,
  '5年历史密度补齐（每工作日两场，占用率≥20%）',
  DATE_ADD(nums.`day`, INTERVAL 7 HOUR),
  DATE_ADD(nums.`day`, INTERVAL s.`end_hour` HOUR),
  0
FROM (
  SELECT
    DATE_ADD('2021-11-27', INTERVAL k.`n` DAY) AS `day`,
    k.`n` AS `n`
  FROM (
    SELECT d4.`i` * 1000 + d3.`i` * 100 + d2.`i` * 10 + d1.`i` AS `n`
    FROM (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
    WHERE d4.`i` * 1000 + d3.`i` * 100 + d2.`i` * 10 + d1.`i` <= 1818
  ) k
  WHERE DAYOFWEEK(DATE_ADD('2021-11-27', INTERVAL k.`n` DAY)) BETWEEN 2 AND 6
) nums
JOIN (
  SELECT 1 AS `slot`, 9 AS `start_hour`, 12 AS `end_hour`
  UNION ALL SELECT 2, 14, 17
) s
JOIN `polaris_meetingroom`.`meeting_room` r ON r.`deleted` = 0
  AND r.`id` BETWEEN 2200000000000000001 AND 2200000000000000048
JOIN `polaris_meetingroom`.`meeting_floor` f ON f.`id` = r.`floor_id` AND f.`deleted` = 0
JOIN `polaris_meetingroom`.`meeting_building` b ON b.`id` = f.`building_id` AND b.`deleted` = 0;
