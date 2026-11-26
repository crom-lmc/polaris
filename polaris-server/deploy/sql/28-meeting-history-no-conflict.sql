-- 北极星会历史预约重建（第四次）：消除同一员工同一时段多场会议 + 数据量减半
-- 问题（用户反馈）：SQL27 每会议室每天上午/下午各一场，发起人按房间独立取槽位，
--   同一员工（尤其王浩然占 20 槽）同一天同一时段被分到多个房间，日程里出现
--   09:00 两场、14:00 两场的重叠，不真实。用户明确：不做邀请人冲突校验，
--   但数据尽量避免；为凑 20~50% 使用率不必堆那么多数据。
-- 方案：DELETE 29xxx 段后重建（幂等，ID 段不变）：
--   1) 每会议室每天只 1 场正常会议，时长 120/150/180/210/240/270 分钟
--      = 22.2/27.8/33.3/38.9/44.4/50.0%（单日分母 540），起点 09:00/09:30/10:00
--      按 (seq+n)%3 错开，最晚 14:30 结束，完全落在 9-18 统计窗口内。
--   2) 发起人单射：slot = (n*该城房间数 + 城市内房间序号) % 该城人数 + 1。
--      同一天同一城市不同会议室 → 不同 slot → 不同员工，即每人每天最多 1 场正常会议。
--      王浩然不再占 20 槽，与普通员工等量（只需保证本账号有数据即可）。
--   3) 异常会议（爽约/取消）约 5% 的 room-day 额外生成，时段 16:30-17:30
--      （与正常会议不重叠、状态异常不计使用率），发起人取"异常池"独立槽位，
--      与正常池完全隔离 → 异常员工当天也不会有正常会议。
--   4) 数据量：正常约 6.2 万 + 异常约 0.3 万 ≈ 6.5 万（SQL27 为 13.2 万，减半）。
-- 区间：2021-11-27 ~ 2026-11-19 工作日，排除最近 7 天（SQL21 已覆盖，避免叠加超 100%）。
-- 主键：2900000000000000000 + seq*100000 + n*10 + slot；slot=1 正常、4 异常；唯一且不撞 26xxx。
-- 生产 sql.init.mode=never，需手工执行；纯数据变更无需重启服务。
-- 幂等：DELETE 固定段 + INSERT IGNORE(ID 段不变)，可重复执行得到相同结果。

DELETE FROM `polaris_meetingroom`.`meeting_booking`
WHERE `id` BETWEEN 2900000000000000000 AND 2999999999999999999;

INSERT IGNORE INTO `polaris_meetingroom`.`meeting_booking`
  (`id`,`booking_no`,`room_id`,`title`,`start_time`,`end_time`,`attendee_count`,
   `status`,`release_reason`,`city`,`creator_id`,`org_code`,`remark`,`created_at`,`updated_at`,`deleted`)
SELECT
  2900000000000000000 + x.`seq` * 100000 + x.`n` * 10 + x.`slot`,
  CONCAT('HST', DATE_FORMAT(x.`day`, '%Y%m%d'),
         LPAD(CAST(x.`seq` AS CHAR), 3, '0'), x.`slot`),
  x.`room_id`,
  ELT(((x.`seq` + x.`n` + x.`slot`) % 10) + 1,
      '项目周会','需求评审','技术方案讨论','月度复盘','跨部门协作',
      '迭代规划','设计评审','客户沟通','工作总结','架构讨论'),
  DATE_ADD(x.`day`, INTERVAL x.`sm` MINUTE),
  DATE_ADD(x.`day`, INTERVAL x.`em` MINUTE),
  ((x.`seq` + x.`n` + x.`slot`) % 6) + 2,
  x.`status`,
  x.`release_reason`,
  x.`city`,
  x.`account_id`,
  CASE x.`city`
      WHEN '西安' THEN 'XC000100010001' WHEN '北京' THEN 'XC000100020001'
      WHEN '上海' THEN 'XC000100060002' ELSE 'XC000100070001' END,
  CONCAT('历史数据-', x.`city`, '-档', x.`heat`, '-', x.`status`),
  DATE_ADD(x.`day`, INTERVAL 7 HOUR),
  DATE_ADD(x.`day`, INTERVAL x.`em` MINUTE),
  0
FROM (
  SELECT p.*,
    emp.`account_id`,
    CASE WHEN p.`abnormal` = 1
              THEN IF(MOD(p.`n` DIV 2, 2) = 0, 'RELEASED', 'CANCELLED')
         ELSE IF(MOD(p.`seq` * 3 + p.`n` * 5, 20) = 19, 'CHECKED_IN', 'COMPLETED')
    END AS `status`,
    CASE WHEN p.`abnormal` = 1 AND MOD(p.`n` DIV 2, 2) = 0 THEN 'NO_SHOW'
         WHEN p.`abnormal` = 1 THEN 'USER_CANCEL'
         ELSE '' END AS `release_reason`,
    CASE WHEN p.`abnormal` = 0
              THEN 540 + MOD(p.`seq` + p.`n`, 3) * 30
              ELSE 990 END AS `sm`,
    CASE WHEN p.`abnormal` = 0
              THEN 540 + MOD(p.`seq` + p.`n`, 3) * 30 + p.`dur`
              ELSE 1050 END AS `em`,
    CASE WHEN p.`abnormal` = 0 THEN 1 ELSE 4 END AS `slot`
  FROM (
    SELECT
      k.`n`,
      DATE_ADD('2021-11-27', INTERVAL k.`n` DAY) AS `day`,
      r.`id` AS `room_id`,
      r.`id` - 2200000000000000000 AS `seq`,
      b.`city`,
      (SELECT COUNT(*) FROM `polaris_meetingroom`.`meeting_room` r2
         JOIN `polaris_meetingroom`.`meeting_floor` f2 ON f2.`id` = r2.`floor_id`
         JOIN `polaris_meetingroom`.`meeting_building` b2 ON b2.`id` = f2.`building_id`
        WHERE r2.`deleted` = 0 AND b2.`city` = b.`city` AND r2.`id` <= r.`id`) AS `city_seq`,
      (SELECT COUNT(*) FROM `polaris_meetingroom`.`meeting_room` r3
         JOIN `polaris_meetingroom`.`meeting_floor` f3 ON f3.`id` = r3.`floor_id`
         JOIN `polaris_meetingroom`.`meeting_building` b3 ON b3.`id` = f3.`building_id`
        WHERE r3.`deleted` = 0 AND b3.`city` = b.`city`) AS `city_rooms`,
      MOD((r.`id` - 2200000000000000000) * 5 + k.`n` * 7, 6) AS `heat`,
      CASE MOD((r.`id` - 2200000000000000000) * 5 + k.`n` * 7, 6)
           WHEN 0 THEN 120 WHEN 1 THEN 150 WHEN 2 THEN 180
           WHEN 3 THEN 210 WHEN 4 THEN 240 ELSE 270 END AS `dur`,
      ss.`abnormal`
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
    -- 每个 room-day 必出 1 场正常(abnormal=0)；仅 1/20 概率额外出 1 场异常(abnormal=1)
    JOIN (SELECT 0 AS `abnormal` UNION ALL SELECT 1) ss
      ON ss.`abnormal` = 0
      OR MOD(k.`n` * 7 + (r.`id` - 2200000000000000000), 20) = 0
    WHERE DAYOFWEEK(DATE_ADD('2021-11-27', INTERVAL k.`n` DAY)) BETWEEN 2 AND 6
  ) p
  -- 发起人：城市内独立编号，正常池与异常池隔离
  -- 正常 slot = (n*城市房间数 + 城市内房间序号) % 城市人数 + 1  → 同天同城不同房不同人
  -- 异常 slot = 城市人数-4 + ((n*3 + 城市内房间序号) % 5)       → 独立异常池，不与正常池重叠
  JOIN (
    SELECT t.`city`, t.`slot`, t.`account_id`, t.`city_count`
    FROM (
      SELECT
        CASE WHEN a.`org_code` LIKE 'XC00010001%' THEN '西安'
             WHEN a.`org_code` LIKE 'XC00010002%' THEN '北京'
             WHEN a.`org_code` LIKE 'XC00010006%' THEN '上海'
             WHEN a.`org_code` LIKE 'XC00010007%' THEN '广州'
             ELSE '西安' END AS `city`,
        (SELECT COUNT(*) FROM `ehr`.`ehr_employee` b
          WHERE b.`account_id` IS NOT NULL AND b.`status` <> 60
            AND b.`id` <= a.`id`
            AND (CASE WHEN b.`org_code` LIKE 'XC00010001%' THEN '西安'
                      WHEN b.`org_code` LIKE 'XC00010002%' THEN '北京'
                      WHEN b.`org_code` LIKE 'XC00010006%' THEN '上海'
                      WHEN b.`org_code` LIKE 'XC00010007%' THEN '广州'
                      ELSE '西安' END)
                = (CASE WHEN a.`org_code` LIKE 'XC00010001%' THEN '西安'
                        WHEN a.`org_code` LIKE 'XC00010002%' THEN '北京'
                        WHEN a.`org_code` LIKE 'XC00010006%' THEN '上海'
                        WHEN a.`org_code` LIKE 'XC00010007%' THEN '广州'
                        ELSE '西安' END)) AS `slot`,
        a.`account_id`,
        (SELECT COUNT(*) FROM `ehr`.`ehr_employee` c
          WHERE c.`account_id` IS NOT NULL AND c.`status` <> 60
            AND (CASE WHEN c.`org_code` LIKE 'XC00010001%' THEN '西安'
                      WHEN c.`org_code` LIKE 'XC00010002%' THEN '北京'
                      WHEN c.`org_code` LIKE 'XC00010006%' THEN '上海'
                      WHEN c.`org_code` LIKE 'XC00010007%' THEN '广州'
                      ELSE '西安' END)
                = (CASE WHEN a.`org_code` LIKE 'XC00010001%' THEN '西安'
                        WHEN a.`org_code` LIKE 'XC00010002%' THEN '北京'
                        WHEN a.`org_code` LIKE 'XC00010006%' THEN '上海'
                        WHEN a.`org_code` LIKE 'XC00010007%' THEN '广州'
                        ELSE '西安' END)) AS `city_count`
      FROM `ehr`.`ehr_employee` a
      WHERE a.`account_id` IS NOT NULL AND a.`status` <> 60
    ) t
  ) emp
    ON emp.`city` = p.`city`
   AND emp.`slot` = CASE WHEN p.`abnormal` = 0
          THEN MOD(p.`n` * p.`city_rooms` + p.`city_seq`, emp.`city_count`) + 1
          ELSE emp.`city_count` - 4 + MOD(p.`n` * 3 + p.`city_seq`, 5)
        END
) x;
