-- 北极星会历史预约重建（第三次）：城市归属合理 + 状态分散 + 使用率 20%~50%
-- 问题：
--   ① SQL26 发起人按全局槽位分配，西安员工（如王浩然）约到了北京/上海/广州的会议室，不合常理；
--   ② 全部记录都是 COMPLETED，没有爽约/取消，状态单一；
--   ③ 使用率档位 120~480 分钟 = 22.2%~88.9%，偏高且部分日期重复感强。
-- 方案：DELETE 29xxx 段后重建（幂等，ID 段不变）：
--   1) 城市匹配：会议室按 building.city 定城市；发起人从「org_code 前缀对应城市」的在职已开户员工池取，
--      西安 116 人（含财务/人力/运营/集团职能，王浩然在内）、北京 30、上海 30、广州 30；
--      王浩然在西安池额外占 20 槽，保证本账号数据充足（约占西安数据 15%）。
--      booking.city 与 org_code 均按会议室城市写入，与「爽约记录」按 city 过滤口径一致。
--   2) 状态分散：每个 room-day 上午/下午各 1 场正常会议（COMPLETED 为主，5% CHECKED_IN），
--      另按哈希给约 12.5% 的 room-day 追加 1 场异常会议（RELEASED+NO_SHOW 爽约 / CANCELLED 取消，各半）。
--      异常场次时段放在 16:30-17:30，不与正常场次重叠。
--   3) 使用率 20%~50%：每个 room-day 两场正常会议时长合计落在 6 档
--      120/150/180/210/240/270 分钟 = 22.2/27.8/33.3/38.9/44.4/50.0%（单日分母 540）；
--      档位由 (seq*5 + n*7) % 6 决定，同日不同房间、同房间不同日期均分散。
--      时段：上午 09:00 起、下午 14:00 起，各 half = 档位/2 分钟，最晚 16:15 结束。
-- 区间：2021-11-27 ~ 2026-11-19 工作日，排除最近 7 天（SQL21 已覆盖，避免叠加超 100%）。
-- 主键：2900000000000000000 + seq*100000 + n*10 + slot；slot=1 上午、2 下午、4 异常；唯一且不撞 26xxx。
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
  CASE WHEN x.`extra` = 1 THEN DATE_ADD(x.`day`, INTERVAL 1050 MINUTE)
       ELSE DATE_ADD(x.`day`, INTERVAL x.`em` MINUTE) END,
  0
FROM (
  -- 状态派生：正常场次 95% COMPLETED / 5% CHECKED_IN；异常场次 爽约与取消各半
  SELECT r.*,
    CASE WHEN r.`extra` = 1
              THEN IF(MOD(r.`n` DIV 2, 2) = 0, 'RELEASED', 'CANCELLED')
         ELSE IF(MOD(r.`seq` * 3 + r.`n` * 5 + r.`slot`, 20) = 19, 'CHECKED_IN', 'COMPLETED')
    END AS `status`,
    CASE WHEN r.`extra` = 1 AND MOD(r.`n` DIV 2, 2) = 0 THEN 'NO_SHOW'
         WHEN r.`extra` = 1 THEN 'USER_CANCEL'
         ELSE '' END AS `release_reason`
  FROM (
    SELECT p.`n`, p.`day`, p.`room_id`, p.`seq`, p.`city`, p.`heat`,
           s.`slot`, s.`sm`, s.`em`, s.`extra`,
           emp.`account_id`
    FROM (
      SELECT
        k.`n`,
        DATE_ADD('2021-11-27', INTERVAL k.`n` DAY) AS `day`,
        r.`id` AS `room_id`,
        r.`id` - 2200000000000000000 AS `seq`,
        b.`city`,
        MOD((r.`id` - 2200000000000000000) * 5 + k.`n` * 7, 6) AS `heat`,
        MOD((r.`id` - 2200000000000000000) * 11 + k.`n` * 13, 8) AS `extra_flag`
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
    -- 场次：heat 决定两场正常会议时长（合计 120~270 分钟）；extra_flag=0 时追加一场异常会议
    JOIN (
      SELECT h.`heat`, 1 AS `slot`, 540 AS `sm`, 540 + h.`half` AS `em`, 0 AS `extra`
      FROM (SELECT 0 heat, 60 half UNION ALL SELECT 1,75 UNION ALL SELECT 2,90
            UNION ALL SELECT 3,105 UNION ALL SELECT 4,120 UNION ALL SELECT 5,135) h
      UNION ALL
      SELECT h.`heat`, 2 AS `slot`, 840 AS `sm`, 840 + h.`half` AS `em`, 0 AS `extra`
      FROM (SELECT 0 heat, 60 half UNION ALL SELECT 1,75 UNION ALL SELECT 2,90
            UNION ALL SELECT 3,105 UNION ALL SELECT 4,120 UNION ALL SELECT 5,135) h
      UNION ALL
      SELECT -1 AS `heat`, 4 AS `slot`, 990 AS `sm`, 1050 AS `em`, 1 AS `extra`
    ) s ON (s.`extra` = 1 AND p.`extra_flag` = 0)
        OR (s.`extra` = 0 AND s.`heat` = p.`heat`)
    -- 发起人槽位：按会议室城市匹配同城市员工。
    -- 城市内独立编号（不是全局编号），否则北京/上海/广州员工的全局序号远超 30 会匹配不上：
    --   西安 115 名普通员工（slot 1~115）+ 王浩然 20 槽（116~135）= 135；其余城市各 30。
    JOIN (
      SELECT t.`city`, t.`slot`, t.`account_id`
      FROM (
        SELECT
          CASE WHEN a.`org_code` LIKE 'XC00010001%' THEN '西安'
               WHEN a.`org_code` LIKE 'XC00010002%' THEN '北京'
               WHEN a.`org_code` LIKE 'XC00010006%' THEN '上海'
               WHEN a.`org_code` LIKE 'XC00010007%' THEN '广州'
               ELSE '西安' END AS `city`,
          (SELECT COUNT(*) FROM `ehr`.`ehr_employee` b
            WHERE b.`account_id` IS NOT NULL AND b.`status` <> 60
              AND b.`account_id` <> 2119703016825098241 AND b.`id` <= a.`id`
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
          a.`account_id`
        FROM `ehr`.`ehr_employee` a
        WHERE a.`account_id` IS NOT NULL AND a.`status` <> 60
          AND a.`account_id` <> 2119703016825098241
        UNION ALL
        SELECT '西安' AS `city`, 115 + d.`i` AS `slot`, 2119703016825098241 AS `account_id`
        FROM (SELECT 1 i UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
              UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
              UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
              UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20) d
      ) t
    ) emp
      ON emp.`city` = p.`city`
     AND emp.`slot` = MOD(p.`seq` * 13 + p.`n` * 7 + s.`slot` * 3,
                          CASE p.`city` WHEN '西安' THEN 135 ELSE 30 END) + 1
  ) r
) x;
