-- 修正 21-meeting-stats-refresh-data.sql 的 ID 碰撞：原 ID 与存量 meeting_booking(≤2600000000000001031) 冲突，INSERT IGNORE 跳过 159 行。
-- 本脚本先清掉本次演示占用数据（只匹配 USG 前缀，不碰存量与 NSG 爽约记录），再用更高 ID 段完整重插，幂等。
DELETE FROM `polaris_meetingroom`.`meeting_booking` WHERE `booking_no` LIKE 'USG%';

-- 基础占用：每间会议室每天 09:00-12:00、14:00-17:00 两场
INSERT IGNORE INTO `polaris_meetingroom`.`meeting_booking`
  (`id`,`booking_no`,`room_id`,`title`,`start_time`,`end_time`,`attendee_count`,
   `status`,`release_reason`,`city`,`creator_id`,`org_code`,`remark`,`created_at`,`updated_at`)
SELECT
  2600000000000020000 + (r.`id` - 2200000000000000000) * 100
    + (DATEDIFF(d.`day`, '2026-11-20') + 1) * 10 + s.`slot`,
  CONCAT('USG', DATE_FORMAT(d.`day`,'%Y%m%d'),
         LPAD(CAST(r.`id` - 2200000000000000000 AS CHAR),3,'0'), s.`slot`),
  r.`id`,
  CONCAT('使用率统计-', CASE r.`room_type`
      WHEN '常规会议室' THEN '周会' WHEN '专用会议室' THEN '项目评审' ELSE '部门会议' END),
  DATE_ADD(d.`day`, INTERVAL s.`start_hour` HOUR),
  DATE_ADD(d.`day`, INTERVAL s.`end_hour` HOUR),
  ((r.`id` + s.`slot`) % 6) + 2,
  IF(d.`day` = CURDATE(), 'PENDING', 'COMPLETED'),
  '',
  b.`city`,
  ELT(((r.`id` + DATEDIFF(d.`day`,'2026-11-20') + s.`slot`) % 8) + 1,
      '2500000000000000009','2500000000000000021','2500000000000000041','2640000000000000014',
      '2640000000000000046','2700000000000040017','2500000000000000062','2640000000000000036'),
  CASE b.`city`
      WHEN '西安' THEN 'XC000100010001' WHEN '北京' THEN 'XC000100020001'
      WHEN '上海' THEN 'XC000100030001' ELSE 'XC000100040001' END,
  '使用率统计填充数据',
  DATE_ADD(d.`day`, INTERVAL 8 HOUR),
  DATE_ADD(d.`day`, INTERVAL s.`end_hour` HOUR)
FROM (
  SELECT '2026-11-20' AS `day` UNION ALL SELECT '2026-11-21' UNION ALL SELECT '2026-11-22'
  UNION ALL SELECT '2026-11-23' UNION ALL SELECT '2026-11-24' UNION ALL SELECT '2026-11-25'
  UNION ALL SELECT '2026-11-26'
) d
JOIN (
  SELECT 1 AS `slot`, 9 AS `start_hour`, 12 AS `end_hour`
  UNION ALL SELECT 2, 14, 17
) s
JOIN `polaris_meetingroom`.`meeting_room` r ON r.`deleted`=0
  AND r.`id` BETWEEN 2200000000000000001 AND 2200000000000000048
JOIN `polaris_meetingroom`.`meeting_floor` f ON f.`id`=r.`floor_id` AND f.`deleted`=0
JOIN `polaris_meetingroom`.`meeting_building` b ON b.`id`=f.`building_id` AND b.`deleted`=0;

-- 并行预约：演示「理论使用率可超过 100%」
INSERT IGNORE INTO `polaris_meetingroom`.`meeting_booking`
  (`id`,`booking_no`,`room_id`,`title`,`start_time`,`end_time`,`attendee_count`,
   `status`,`release_reason`,`city`,`creator_id`,`org_code`,`remark`,`created_at`,`updated_at`)
SELECT
  2600000000000020000 + (r.`id` - 2200000000000000000) * 100
    + (DATEDIFF(d.`day`, '2026-11-20') + 1) * 10 + s.`slot`,
  CONCAT('USG', DATE_FORMAT(d.`day`,'%Y%m%d'),
         LPAD(CAST(r.`id` - 2200000000000000000 AS CHAR),3,'0'), s.`slot`),
  r.`id`,
  '使用率统计-并行评审',
  DATE_ADD(d.`day`, INTERVAL s.`start_hour` HOUR),
  DATE_ADD(d.`day`, INTERVAL s.`end_hour` HOUR),
  ((r.`id` + s.`slot`) % 6) + 2,
  IF(d.`day` = CURDATE(), 'PENDING', 'COMPLETED'),
  '',
  b.`city`,
  ELT(((r.`id` + DATEDIFF(d.`day`,'2026-11-20') + s.`slot`) % 8) + 1,
      '2500000000000000009','2500000000000000021','2500000000000000041','2640000000000000014',
      '2640000000000000046','2700000000000040017','2500000000000000062','2640000000000000036'),
  CASE b.`city`
      WHEN '西安' THEN 'XC000100010001' WHEN '北京' THEN 'XC000100020001'
      WHEN '上海' THEN 'XC000100030001' ELSE 'XC000100040001' END,
  '使用率统计并行预约（允许重叠）',
  DATE_ADD(d.`day`, INTERVAL 8 HOUR),
  DATE_ADD(d.`day`, INTERVAL s.`end_hour` HOUR)
FROM (
  SELECT '2026-11-20' AS `day` UNION ALL SELECT '2026-11-21' UNION ALL SELECT '2026-11-22'
  UNION ALL SELECT '2026-11-23' UNION ALL SELECT '2026-11-24' UNION ALL SELECT '2026-11-25'
  UNION ALL SELECT '2026-11-26'
) d
JOIN (
  SELECT 3 AS `slot`, 9 AS `start_hour`, 18 AS `end_hour`
  UNION ALL SELECT 4, 13, 18
) s
JOIN `polaris_meetingroom`.`meeting_room` r ON r.`deleted`=0
  AND r.`id` IN (2200000000000000001,2200000000000000002,2200000000000000015,
                 2200000000000000028,2200000000000000045,2200000000000000047)
JOIN `polaris_meetingroom`.`meeting_floor` f ON f.`id`=r.`floor_id` AND f.`deleted`=0
JOIN `polaris_meetingroom`.`meeting_building` b ON b.`id`=f.`building_id` AND b.`deleted`=0;
