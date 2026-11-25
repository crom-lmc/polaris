-- 会议室管理库（独立库 polaris_meetingroom；全部幂等，生产由手工迁移执行）
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
  `comfort_capacity` int(11) NOT NULL DEFAULT 0 COMMENT '舒适人数（仅提示）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会议室设备清单（不维护设备状态）';

CREATE TABLE IF NOT EXISTS `meeting_booking` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `booking_no` varchar(64) NOT NULL DEFAULT '' COMMENT '预定单号（时间戳+随机，业务展示用）',
  `room_id` bigint(20) NOT NULL COMMENT 'meeting_room.id',
  `title` varchar(255) NOT NULL COMMENT '会议主题',
  `start_time` datetime NOT NULL COMMENT '开始时间，与 end_time 同一自然日',
  `end_time` datetime NOT NULL COMMENT '结束时间，最晚 23:30',
  `attendee_count` int(11) NOT NULL DEFAULT 1 COMMENT '参会人数（含创建人）',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CHECKED_IN/COMPLETED/CANCELLED/RELEASED',
  `checkin_at` datetime DEFAULT NULL COMMENT '签到时间',
  `release_reason` varchar(32) NOT NULL DEFAULT '' COMMENT '释放原因：NO_SHOW/MANUAL',
  `city` varchar(64) NOT NULL DEFAULT '' COMMENT '城市冗余，便于筛选',
  `creator_id` bigint(20) NOT NULL COMMENT '创建人账号ID',
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '预留：平台组织编码，非 org_id',
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
  `org_code` varchar(64) NOT NULL DEFAULT '' COMMENT '预留：平台组织编码',
  `status` varchar(32) NOT NULL DEFAULT 'INVITED' COMMENT 'INVITED/ACCEPTED/DECLINED，本期只写 INVITED',
  `notified` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已发出邀请通知',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_booking_account` (`booking_id`,`account_id`,`deleted`),
  KEY `idx_account` (`account_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会议邀请参会人';
