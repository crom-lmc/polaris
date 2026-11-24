-- ============================================================================
-- 统一文件服务 · 存量存储迁移辅助脚本（幂等，可重复执行）
-- ============================================================================
-- 作用：
--   给三套存量附件表加 file_id 列，作为「已迁移到 file-service」的标记；
--   file_id 为空表示仍是旧本地存储，非空表示文件内容已在 file-service 中。
--
--   本脚本只加列、不搬数据。数据搬迁由 deploy/migration/migrate_files.py 完成，
--   该脚本扫描旧磁盘 → 调 file-service /internal/files/upload → 写 file_alias；
--   随后用 deploy/migration/gen_file_id_backfill.py 生成 UPDATE 把 file_id 回填到业务表。
--
-- 兼容性：MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，故用存储过程 + information_schema
--         判断列是否已存在；重复执行不会报 Duplicate column 错误。
--         加列不影响任何现有 SQL（各 mapper 均为显式列，无 SELECT *）。
--         业务读取链路本轮保持不变，旧文件 100% 可读，零回归风险。
-- ============================================================================

-- 存储过程需要默认库上下文（表本身用全限定名，选任意存在的库即可）
USE `polaris_file`;

DROP PROCEDURE IF EXISTS `polaris_add_file_id_column`;
DELIMITER $$
CREATE PROCEDURE `polaris_add_file_id_column`(IN db_name VARCHAR(64), IN tbl_name VARCHAR(64))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = db_name AND TABLE_NAME = tbl_name AND COLUMN_NAME = 'file_id'
  ) THEN
    SET @ddl = CONCAT(
      'ALTER TABLE `', db_name, '`.`', tbl_name,
      '` ADD COLUMN `file_id` bigint(20) DEFAULT NULL ',
      'COMMENT "迁移到统一文件服务后的文件ID；为空表示仍用旧本地存储"'
    );
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- CMS 媒体表（旧直链 /api/cms/media/file/** 内嵌在文章正文里，靠 file_alias 映射）
CALL polaris_add_file_id_column('cms', 'cms_media');
-- 论坛附件表（/api/forum/attachments/{id}，正文按 ID 寻址）
CALL polaris_add_file_id_column('forum', 'forum_attachment');
-- EHR 附件表（/api/ehr/attachment/{id}/download，正文按 ID 寻址）
CALL polaris_add_file_id_column('ehr', 'ehr_attachment');

DROP PROCEDURE IF EXISTS `polaris_add_file_id_column`;

-- 迁移完成后：
--   1) file_alias 表（polaris_file 库）保存 CMS 旧路径到新文件的映射；
--   2) 执行 gen_file_id_backfill.py 回填三张业务表的 file_id；
--   3) 网关把 /api/cms/media/file/** 改指 file-service legacy；forum/EHR 按 file_id 优先读取，
--      读不到回退旧本地存储。旧文件始终保留，可随时回滚。
