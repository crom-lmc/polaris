-- 统一文件服务库（sql.init.mode=always，全部幂等）
CREATE TABLE IF NOT EXISTS `file_object` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `visibility` varchar(16) NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC/PRIVATE',
  `original_name` varchar(255) NOT NULL DEFAULT '' COMMENT '原始文件名',
  `content_type` varchar(128) NOT NULL DEFAULT '' COMMENT 'MIME 类型',
  `size` bigint(20) NOT NULL DEFAULT 0 COMMENT '字节数',
  `sha256` varchar(64) NOT NULL DEFAULT '' COMMENT '内容摘要',
  `storage_path` varchar(512) NOT NULL COMMENT '相对存储路径',
  `owner_type` varchar(64) NOT NULL DEFAULT '' COMMENT '归属对象类型',
  `owner_id` varchar(64) NOT NULL DEFAULT '' COMMENT '归属对象ID',
  `uploader_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '上传账号ID，内部调用为0',
  `status` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DELETED',
  `created_at` datetime NOT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`created_at`),
  KEY `idx_owner` (`owner_type`,`owner_id`),
  KEY `idx_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='统一文件元数据';

CREATE TABLE IF NOT EXISTS `file_storage_policy` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_name` varchar(128) NOT NULL DEFAULT '' COMMENT '业务名称',
  `max_size_mb` bigint(20) NOT NULL DEFAULT 20 COMMENT '单文件大小上限MB',
  `allowed_ext` varchar(512) NOT NULL DEFAULT '' COMMENT '允许的扩展名，逗号分隔，空为不限',
  `default_visibility` varchar(16) NOT NULL DEFAULT 'PRIVATE' COMMENT '默认可见性',
  `force_private` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否强制私有',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_type` (`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='文件存储策略';

-- 旧存储路径兼容映射：历史正文内嵌的静态直链迁移后靠本表指回新文件，避免图片裂开。
CREATE TABLE IF NOT EXISTS `file_alias` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `old_path` varchar(512) NOT NULL COMMENT '旧路径（含前导/，去掉host）',
  `file_id` bigint(20) NOT NULL COMMENT '新文件ID（file_object.id）',
  `source_app` varchar(32) NOT NULL DEFAULT '' COMMENT '来源应用 cms/forum/ehr',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_old_path` (`old_path`(255)),
  KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='旧存储路径兼容映射';
