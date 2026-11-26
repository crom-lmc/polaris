-- 会议室图片存储策略：meeting RoomFileController 上传时固定 biz_type=meeting_room_image，
-- file-service 按 biz_type 精确匹配策略且无兜底，缺策略会抛 8102（业务类型未配置存储策略）。
-- 幂等，可重复执行；生产 file-service 若 FILE_SQL_INIT_MODE=never 需手工执行本脚本。
-- 与 polaris-file-service/src/main/resources/data.sql 中同 id 种子保持一致。

INSERT IGNORE INTO `polaris_file`.`file_storage_policy`
  (`id`,`biz_type`,`biz_name`,`max_size_mb`,`allowed_ext`,`default_visibility`,`force_private`,`quota_gb`,`ttl_days`,`enabled`,`remark`,`created_at`)
VALUES
  (9200000000000000007,'meeting_room_image','会议室图片',20,
   'jpg,jpeg,png,gif,webp','PRIVATE',1,0,0,1,
   '会议室图片强制私有，业务表 meeting_room.file_ids 只存 file_id',NOW());
