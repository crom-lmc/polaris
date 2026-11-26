-- 北极星会历史预约重分布：使用率差异化 + 发起人覆盖全体在职员工
-- 问题：SQL25 每会议室每工作日固定 09-12 / 14-17 两场 = 360/540 = 66.7%，48 间房全一样，
--       且发起人只有 8 个账号完全均分（各 15690 条），其余员工一条没有。
-- 方案：删除 29xxx 段后按「会议室固定热度 + 每日小幅波动」重建约 12.9 万条：
--       热度 = (会议室序号*7 % 5) + ((会议室序号*5 + 日期偏移*11) % 3)  → 0~6
--       0~6 档分别占用 120/180/240/300/360/420/480 分钟 → 使用率 22.2%~88.9%
--       · 同一会议室长期热度稳定（按周/月看也有高低差异）
--       · 每日叠加波动（按日看柱子错落）
--       全部 >= 20%，满足此前「每会议室工作日使用率 20% 以上」要求。
-- 发起人：从 EHR 动态取全部在职已开户员工（account_id 非空且 status<>60，与
--       activeEmployees 接口同口径，共 206 人，中文名可正常映射）：
--       · 205 名员工各占 1 个槽位（每人约 570 条）
--       · 王浩然（2119703016825098241）占 20 个槽位（约 9%，1.1 万条），保证本账号数据充足
--       按 (seq*13 + n*7 + slot*3) % 225 取槽位，206 人全部覆盖、非均分。
-- 区间：2021-11-27 ~ 2026-11-19 工作日，排除最近 7 天（SQL21 已覆盖，避免叠加超 100%）。
-- 生产 sql.init.mode=never，需手工执行；纯数据变更无需重启服务。
-- 幂等：DELETE 固定段 + INSERT IGNORE(ID 段不变)，可重复执行得到相同结果。

DELETE FROM `polaris_meetingroom`.`meeting_booking`
WHERE `id` BETWEEN 2900000000000000000 AND 2999999999999999999;

INSERT IGNORE INTO `polaris_meetingroom`.`meeting_booking`
  (`id`,`booking_no`,`room_id`,`title`,`start_time`,`end_time`,`attendee_count`,
   `status`,`release_reason`,`city`,`creator_id`,`org_code`,`remark`,`created_at`,`updated_at`,`deleted`)
SELECT
  2900000000000000000 + p.`seq` * 100000 + p.`n` * 10 + s.`slot`,
  CONCAT('HST', DATE_FORMAT(p.`day`, '%Y%m%d'),
         LPAD(CAST(p.`seq` AS CHAR), 3, '0'), s.`slot`),
  p.`room_id`,
  ELT(((p.`seq` + p.`n` + s.`slot`) % 10) + 1,
      '项目周会','需求评审','技术方案讨论','月度复盘','跨部门协作',
      '迭代规划','设计评审','客户沟通','工作总结','架构讨论'),
  DATE_ADD(p.`day`, INTERVAL s.`sh` HOUR),
  DATE_ADD(p.`day`, INTERVAL s.`eh` HOUR),
  ((p.`seq` + p.`n` + s.`slot`) % 6) + 2,
  'COMPLETED',
  '',
  p.`city`,
  emp.`account_id`,
  CASE p.`city`
      WHEN '西安' THEN 'XC000100010001' WHEN '北京' THEN 'XC000100020001'
      WHEN '上海' THEN 'XC000100030001' ELSE 'XC000100040001' END,
  CONCAT('历史数据-热度', p.`heat`),
  DATE_ADD(p.`day`, INTERVAL 7 HOUR),
  DATE_ADD(p.`day`, INTERVAL s.`eh` HOUR),
  0
FROM (
  SELECT
    k.`n`,
    DATE_ADD('2021-11-27', INTERVAL k.`n` DAY) AS `day`,
    r.`id` AS `room_id`,
    r.`id` - 2200000000000000000 AS `seq`,
    b.`city`,
    ((r.`id` - 2200000000000000000) * 7 % 5)
      + (((r.`id` - 2200000000000000000) * 5 + k.`n` * 11) % 3) AS `heat`
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
  JOIN `polaris_meetingroom`.`meeting_room` r ON r.`deleted` = 0
    AND r.`id` BETWEEN 2200000000000000001 AND 2200000000000000048
  JOIN `polaris_meetingroom`.`meeting_floor` f ON f.`id` = r.`floor_id` AND f.`deleted` = 0
  JOIN `polaris_meetingroom`.`meeting_building` b ON b.`id` = f.`building_id` AND b.`deleted` = 0
  WHERE DAYOFWEEK(DATE_ADD('2021-11-27', INTERVAL k.`n` DAY)) BETWEEN 2 AND 6
) p
JOIN (
  SELECT 0 heat, 9 sh, 10 eh, 1 slot UNION ALL SELECT 0, 14, 15, 2
  UNION ALL SELECT 1,  9, 12, 1
  UNION ALL SELECT 2,  9, 11, 1 UNION ALL SELECT 2, 14, 16, 2
  UNION ALL SELECT 3,  9, 12, 1 UNION ALL SELECT 3, 14, 16, 2
  UNION ALL SELECT 4,  9, 12, 1 UNION ALL SELECT 4, 14, 17, 2
  UNION ALL SELECT 5,  9, 12, 1 UNION ALL SELECT 5, 13, 16, 2 UNION ALL SELECT 5, 17, 18, 3
  UNION ALL SELECT 6,  9, 12, 1 UNION ALL SELECT 6, 13, 17, 2 UNION ALL SELECT 6, 17, 18, 3
) s ON s.`heat` = p.`heat`
-- 发起人槽位表：205 名普通在职员工各 1 槽（序号按主键稳定排序），王浩然追加 20 槽，
-- 合计 225 槽；取模分配使 206 人都能分到数据、且非均分。
JOIN (
  SELECT slot, account_id FROM (
    SELECT
      (SELECT COUNT(*) FROM `ehr`.`ehr_employee` b
        WHERE b.`account_id` IS NOT NULL AND b.`status` <> 60
          AND b.`account_id` <> 2119703016825098241 AND b.`id` <= a.`id`) AS slot,
      a.`account_id`
    FROM `ehr`.`ehr_employee` a
    WHERE a.`account_id` IS NOT NULL AND a.`status` <> 60
      AND a.`account_id` <> 2119703016825098241
  ) normal_emp
  UNION ALL
  SELECT 205 + d.`i` AS slot, 2119703016825098241 AS account_id
  FROM (SELECT 1 i UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
        UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
        UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
        UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20) d
) emp ON emp.`slot` = ((p.`seq` * 13 + p.`n` * 7 + s.`slot` * 3) % 225) + 1;
