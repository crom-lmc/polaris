-- 会议室预约规则配置化：未签到释放宽限期 / 单次最长时长 / 爽约限制规则
-- 幂等，可重复执行；生产 sql.init.mode=never，需手工执行本脚本。
-- 埋点改动后会议服务最多 60 秒生效（SettingsClient 缓存），无需重启；调度任务描述同步去掉写死的 15 分钟。

INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`,`code`,`app_code`,`module`,`name`,`value`,`built_in`,`sort`,`deleted`,`created_at`,`updated_at`)
VALUES
  (2026112600000000001,'meeting.booking.no_show_grace_minutes','meeting','会议室预约','未签到自动释放宽限期（分钟）','{"graceMinutes":15}',1,2,0,NOW(),NOW()),
  (2026112600000000002,'meeting.booking.max_duration_minutes','meeting','会议室预约','单次预约最长时长（分钟，0=不限制）','{"maxMinutes":0}',1,3,0,NOW(),NOW()),
  (2026112600000000003,'meeting.booking.no_show_restriction','meeting','会议室预约','爽约限制规则（统计天数/次数/禁约天数）','{"windowDays":90,"threshold":3,"banDays":7}',1,4,0,NOW(),NOW());

UPDATE `polaris_job`.`job_info`
SET `description`='开始后未签到自动释放会议室（宽限期由埋点 meeting.booking.no_show_grace_minutes 配置）', `updated_at`=NOW()
WHERE `app_code`='meeting' AND `handler`='meetingNoShowRelease' AND `deleted`=0
  AND `description` <> '开始后未签到自动释放会议室（宽限期由埋点 meeting.booking.no_show_grace_minutes 配置）';
