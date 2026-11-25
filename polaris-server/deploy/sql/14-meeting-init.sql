-- 会议室管理（meeting）：权限码 + 角色授权 + 应用注册 + 演示账号授权
-- 幂等，可重复执行。生产 AUDIT/MEETING SQL_INIT_MODE=never，需手工执行本脚本。
-- sso-account 重启后 BootstrapInitializer 也会按 MEETING_BUILTIN 幂等补齐这些码；
-- 本脚本让「不重启也能立即授权」，两种路径 id 不同但 code 唯一，播种器按 code 跳过不会重复。

-- ============ 0. 独立库与表：生产 SQL_INIT_MODE=never，必须手工建库建表 ============
CREATE DATABASE IF NOT EXISTS `polaris_meetingroom` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `polaris_meetingroom`;

CREATE TABLE IF NOT EXISTS `meeting_building` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `city` varchar(64) NOT NULL COMMENT '城市，settings 字典 ehr.job_city 的 value',
  `name` varchar(128) NOT NULL COMMENT '大楼名称',
  `address` varchar(255) NOT NULL DEFAULT '' COMMENT '地址',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` varchar(32) NOT NULL DEFAULT '可用' COMMENT '字典 meeting.location_status 的 value',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_city_status` (`city`,`status`,`deleted`),
  KEY `idx_city_sort` (`city`,`sort`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会议室-大楼主数据';

CREATE TABLE IF NOT EXISTS `meeting_floor` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `building_id` bigint(20) NOT NULL COMMENT 'meeting_building.id',
  `floor_no` varchar(32) NOT NULL COMMENT '楼层号，如 12F',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` varchar(32) NOT NULL DEFAULT '可用' COMMENT '字典 meeting.location_status 的 value',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_building` (`building_id`,`deleted`),
  KEY `idx_building_sort` (`building_id`,`sort`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会议室-楼层主数据';

CREATE TABLE IF NOT EXISTS `meeting_room` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `floor_id` bigint(20) NOT NULL COMMENT 'meeting_floor.id',
  `name` varchar(128) NOT NULL COMMENT '会议室名称',
  `room_no` varchar(64) NOT NULL DEFAULT '' COMMENT '房间号',
  `room_type` varchar(64) NOT NULL DEFAULT '' COMMENT '字典 meeting.room_type 的 value',
  `capacity_tier` varchar(64) NOT NULL DEFAULT '' COMMENT '字典 meeting.room_capacity 的 value',
  `max_capacity` int(11) NOT NULL DEFAULT 0 COMMENT '硬性人数上限',
  `comfort_capacity` int(11) NOT NULL DEFAULT 0 COMMENT '舒适人数',
  `layout` varchar(64) NOT NULL DEFAULT '' COMMENT '字典 meeting.seat_layout 的 value',
  `file_ids` varchar(1000) NOT NULL DEFAULT '' COMMENT '统一文件服务 file_id，逗号分隔',
  `status` varchar(32) NOT NULL DEFAULT '可用' COMMENT '字典 meeting.room_status：可用/维护/停用',
  `need_approval` tinyint(1) NOT NULL DEFAULT 0 COMMENT '审批预留，本期不启用',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_floor` (`floor_id`,`deleted`),
  KEY `idx_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会议室台账';

CREATE TABLE IF NOT EXISTS `meeting_room_device` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `room_id` bigint(20) NOT NULL COMMENT 'meeting_room.id',
  `device_code` varchar(64) NOT NULL COMMENT '字典 meeting.device 的 value',
  `qty` int(11) NOT NULL DEFAULT 1 COMMENT '数量',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_room` (`room_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会议室设备清单';

CREATE TABLE IF NOT EXISTS `meeting_booking` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `booking_no` varchar(64) NOT NULL DEFAULT '' COMMENT '预定单号',
  `room_id` bigint(20) NOT NULL COMMENT 'meeting_room.id',
  `title` varchar(255) NOT NULL COMMENT '会议主题',
  `start_time` datetime NOT NULL COMMENT '开始时间，与 end_time 同一自然日',
  `end_time` datetime NOT NULL COMMENT '结束时间，最晚 23:30',
  `attendee_count` int(11) NOT NULL DEFAULT 1 COMMENT '参会人数（含创建人）',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CHECKED_IN/COMPLETED/CANCELLED/RELEASED',
  `checkin_at` datetime DEFAULT NULL COMMENT '签到时间',
  `release_reason` varchar(32) NOT NULL DEFAULT '' COMMENT '释放原因：NO_SHOW/MANUAL',
  `city` varchar(64) NOT NULL DEFAULT '' COMMENT '城市冗余',
  `creator_id` bigint(20) NOT NULL COMMENT '创建人账号ID',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '预留平台组织编码（非 org_id）',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_room_time` (`room_id`,`start_time`,`deleted`),
  KEY `idx_creator_status` (`creator_id`,`status`,`deleted`),
  KEY `idx_status_start` (`status`,`start_time`,`deleted`),
  KEY `idx_booking_no` (`booking_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会议室预定单';

CREATE TABLE IF NOT EXISTS `meeting_booking_attendee` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `booking_id` bigint(20) NOT NULL COMMENT 'meeting_booking.id',
  `account_id` bigint(20) NOT NULL COMMENT '被邀请人账号ID',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '预留平台组织编码',
  `status` varchar(32) NOT NULL DEFAULT 'INVITED' COMMENT 'INVITED/ACCEPTED/DECLINED',
  `notified` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已发出邀请通知',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_booking_account` (`booking_id`,`account_id`,`deleted`),
  KEY `idx_account` (`account_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会议邀请参会人';

-- ============ 1. 权限码：4 个菜单 + 6 个按钮，固定 id，INSERT IGNORE 幂等 ============
-- parent_id 先置 0，随后统一修正为父码真实 id（不依赖具体 id 值）。
INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
VALUES
  (2026112500000000301,'meeting:overview:view','概览',1,0,1,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000302,'meeting:room:list','位置与会议室维护',1,0,2,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000303,'meeting:booking:view','会议室预定',1,0,3,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000304,'meeting:booking:list','我的预定',1,0,4,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000305,'meeting:room:edit','维护位置与会议室',2,0,1,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000306,'meeting:booking:create','新建/修改预定',2,0,1,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000307,'meeting:booking:checkin','签到',2,0,1,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000308,'meeting:booking:extend','延时',2,0,2,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000309,'meeting:booking:cancel','取消预定',2,0,3,'',NOW(),NOW(),0,'meeting'),
  (2026112500000000310,'meeting:booking:release','释放会议室',2,0,4,'',NOW(),NOW(),0,'meeting');

-- 统一修正：位置维护/会议室维护按钮挂 room:list 父级
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='meeting:room:list' AND m.`app_code`='meeting' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='meeting' AND p.`code`='meeting:room:edit'
  AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

-- 预定页按钮挂 booking:view 父级
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='meeting:booking:view' AND m.`app_code`='meeting' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='meeting' AND p.`code`='meeting:booking:create'
  AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

-- 我的预定页按钮挂 booking:list 父级
UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='meeting:booking:list' AND m.`app_code`='meeting' AND m.`deleted`=0
SET p.`parent_id`=m.`id`
WHERE p.`app_code`='meeting'
  AND p.`code` IN ('meeting:booking:checkin','meeting:booking:extend','meeting:booking:cancel','meeting:booking:release')
  AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

-- ============ 2. 角色：MEETING_ADMIN（全量 10 码）/ MEETING_USER（只读 + 新建 + 签到） ============
INSERT IGNORE INTO `sso_ms`.`sso_role` (`id`,`code`,`app_code`,`name`,`remark`,`created_at`,`updated_at`,`deleted`) VALUES
  (2026112500000000401,'MEETING_ADMIN','meeting','会议室管理员','会议室管理全量：位置/房间维护 + 预定/签到/延时/取消/释放',NOW(),NOW(),0),
  (2026112500000000402,'MEETING_USER','meeting','会议室用户','员工自助：概览/位置与房间查看/预定查看与新建/签到',NOW(),NOW(),0);

-- 2.1 MEETING_ADMIN：meeting 应用全部权限
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='meeting' AND p.`deleted`=0
WHERE r.`code`='MEETING_ADMIN' AND r.`deleted`=0;

-- 2.2 MEETING_USER：概览查看 + 位置/房间查看 + 预定查看 + 新建/修改 + 签到
--     延时/取消/释放/房间维护属管理员能力；不授予。
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='meeting' AND p.`deleted`=0
  AND p.`code` IN ('meeting:overview:view','meeting:room:list','meeting:booking:view',
                   'meeting:booking:list','meeting:booking:create','meeting:booking:checkin')
WHERE r.`code`='MEETING_USER' AND r.`deleted`=0;

-- ============ 3. 应用注册：meeting ============
-- app_secret 为占位符，部署时由环境变量 SSO_MEETING_APP_SECRET / Nacos 注入后替换；
-- 严禁把真实 appSecret、内部令牌写进仓库。
INSERT IGNORE INTO `sso_ms`.`sso_application`
  (`id`,`code`,`name`,`app_type`,`base_url`,`app_secret`,`icon`,`remark`,`status`,`sort`,`created_at`,`updated_at`,`deleted`,`redirect_uri`)
VALUES
  (2026112500000000501,'meeting','会议室管理','MANAGEMENT','https://poraris-meeting.laimengchao.site',
   '__SET_SSO_MEETING_APP_SECRET__','/icons/meeting.svg','会议室管理独立应用（权限码域 meeting，前端 5187 / 后端 9013）',
   1,11,NOW(),NOW(),0,'https://poraris-meeting.laimengchao.site/callback');

-- ============ 4. 演示账号授权：SELECT JOIN 幂等，只增不减 ============
-- 演示账号 yanshi（超管，已全量授权）与内置演示账号段补会议室应用访问权。
INSERT IGNORE INTO `sso_ms`.`sso_account_app` (`account_id`,`app_id`)
SELECT a.`id`,ap.`id` FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_application` ap ON ap.`code`='meeting' AND ap.`deleted`=0
WHERE a.`deleted`=0
  AND (a.`username`='yanshi' OR a.`id` BETWEEN 2700000000000040001 AND 2700000000000040012);

-- ============ 5. settings 配置：8 个字典 + 1 个埋点 + 1 条通知规则 ============
-- 本服务 sql.init.mode=never，settings 侧配置必须手工执行；全部 INSERT IGNORE 幂等。
-- 值统一用中文（与已有 meeting.* 字典一致）；booking_status 的 value 与状态机常量一一对应，禁止改 value。
INSERT IGNORE INTO `settings`.`cfg_dict_type` (`id`,`code`,`name`,`app_code`,`remark`,`built_in`,`status`,`deleted`,`created_at`,`updated_at`) VALUES
  (15001,'meeting.room_type','会议室类型','meeting','房间类型筛选与展示',1,1,0,NOW(),NOW()),
  (15002,'meeting.location_status','位置状态','meeting','大楼/楼层状态',1,1,0,NOW(),NOW()),
  (15003,'meeting.seat_layout','座位布局','meeting','预留字段',1,1,0,NOW(),NOW()),
  (15004,'meeting.booking_status','预定状态','meeting','value 与状态机常量一致，只允许改 label/sort',1,1,0,NOW(),NOW()),
  (15005,'meeting.room_capacity','会议室容量档位','meeting','人数校验仍用硬性上限',1,1,0,NOW(),NOW()),
  (15006,'meeting.room_status','会议室状态','meeting','可用/维护/停用',1,1,0,NOW(),NOW()),
  (15007,'meeting.device','会议室设备','meeting','只记录类型与数量',1,1,0,NOW(),NOW());

INSERT IGNORE INTO `settings`.`cfg_dict_item` (`id`,`type_code`,`value`,`label`,`sort`,`status`,`remark`,`built_in`,`deleted`,`created_at`,`updated_at`) VALUES
  (1500101,'meeting.room_type','多功能会议室','多功能会议室',1,1,'',1,0,NOW(),NOW()),
  (1500102,'meeting.room_type','专用会议室','专用会议室',2,1,'',1,0,NOW(),NOW()),
  (1500103,'meeting.room_type','常规会议室','常规会议室',3,1,'',1,0,NOW(),NOW()),
  (1500104,'meeting.room_type','圆桌会议室','圆桌会议室',4,1,'',1,0,NOW(),NOW()),
  (1500201,'meeting.location_status','可用','可用',1,1,'',1,0,NOW(),NOW()),
  (1500202,'meeting.location_status','停用','停用',2,1,'',1,0,NOW(),NOW()),
  (1500301,'meeting.seat_layout','剧院式','剧院式',1,1,'',1,0,NOW(),NOW()),
  (1500302,'meeting.seat_layout','圆桌式','圆桌式',2,1,'',1,0,NOW(),NOW()),
  (1500303,'meeting.seat_layout','董事会式','董事会式',3,1,'',1,0,NOW(),NOW()),
  (1500304,'meeting.seat_layout','长桌式','长桌式',4,1,'',1,0,NOW(),NOW()),
  (1500401,'meeting.booking_status','PENDING','待开始',1,1,'',1,0,NOW(),NOW()),
  (1500402,'meeting.booking_status','CHECKED_IN','进行中',2,1,'',1,0,NOW(),NOW()),
  (1500403,'meeting.booking_status','COMPLETED','已完成',3,1,'',1,0,NOW(),NOW()),
  (1500404,'meeting.booking_status','CANCELLED','已取消',4,1,'',1,0,NOW(),NOW()),
  (1500405,'meeting.booking_status','RELEASED','已释放',5,1,'',1,0,NOW(),NOW()),
  (1500501,'meeting.room_capacity','小型（≤8）','小型（≤8）',1,1,'',1,0,NOW(),NOW()),
  (1500502,'meeting.room_capacity','中型（9-20）','中型（9-20）',2,1,'',1,0,NOW(),NOW()),
  (1500503,'meeting.room_capacity','大型（21-50）','大型（21-50）',3,1,'',1,0,NOW(),NOW()),
  (1500504,'meeting.room_capacity','特大（>50）','特大（>50）',4,1,'',1,0,NOW(),NOW()),
  (1500601,'meeting.room_status','可用','可用',1,1,'',1,0,NOW(),NOW()),
  (1500602,'meeting.room_status','维护','维护',2,1,'',1,0,NOW(),NOW()),
  (1500603,'meeting.room_status','停用','停用',3,1,'',1,0,NOW(),NOW()),
  (1500701,'meeting.device','投影','投影',1,1,'',1,0,NOW(),NOW()),
  (1500702,'meeting.device','视频会议','视频会议',2,1,'',1,0,NOW(),NOW()),
  (1500703,'meeting.device','白板','白板',3,1,'',1,0,NOW(),NOW()),
  (1500704,'meeting.device','电话','电话',4,1,'',1,0,NOW(),NOW()),
  (1500705,'meeting.device','电视屏幕','电视屏幕',5,1,'',1,0,NOW(),NOW()),
  (1500706,'meeting.device','麦克风','麦克风',6,1,'',1,0,NOW(),NOW());

INSERT IGNORE INTO `settings`.`cfg_embed_point` (`id`,`code`,`app_code`,`module`,`name`,`value`,`built_in`,`sort`,`deleted`,`created_at`,`updated_at`)
VALUES (2026112500000000001,'meeting.booking.max_advance_days','meeting','会议室预约','会议室最多可提前预约天数','{"maxDays":7}',1,1,0,NOW(),NOW());

INSERT IGNORE INTO `settings`.`cfg_notice_rule` (`id`,`code`,`app_code`,`module`,`name`,`title_tpl`,`content_tpl`,`channel`,`email`,`enabled`,`built_in`,`sort`,`receiver_account_ids`,`receiver_org_ids`,`deleted`,`created_at`,`updated_at`)
VALUES (11411,'meeting.booking.invite','meeting','会议室预约','会议室邀请参会人','会议邀请：{title}','{inviterName} 邀请你参加「{title}」，时间 {startTime}–{endTime}，地点 {location}（{roomName}），预定单号 {bookingNo}，参会人数 {attendeeCount} 人。详情：{link}',2,1,1,1,1,'','',0,NOW(),NOW());
-- 模块字段统一中文：历史数据（INSERT IGNORE 不会覆盖）用幂等 UPDATE 修正
UPDATE `settings`.`cfg_notice_rule` SET `module`='会议室预约', `updated_at`=NOW() WHERE `code`='meeting.booking.invite' AND `deleted`=0 AND `module`<>'会议室预约';

-- ============ 6. 调度任务：未签到自动释放（由 polaris-job 派发，应用内 @Scheduled 不启用） ============
INSERT IGNORE INTO `polaris_job`.`job_info`
  (`id`,`app_code`,`name`,`handler`,`description`,`trigger_type`,`cron_expr`,`interval_sec`,`timeout_sec`,`retry_times`,`retry_interval_sec`,`route_strategy`,`block_strategy`,`params`,`status`,`next_fire_time`,`owner_account_id`,`lock_version`,`deleted`,`created_at`,`updated_at`)
VALUES (910000000000000014,'meeting','未签到自动释放','meetingNoShowRelease','开始后 15 分钟未签到自动释放会议室','FIXED_DELAY',NULL,60,300,0,30,'ROUND','DISCARD','{}',1,NULL,NULL,0,0,NOW(),NOW());
