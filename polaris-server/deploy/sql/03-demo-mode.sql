-- 存量库幂等迁移：安全配置表新增「演示模式开关」列（MySQL 5.7 无 ADD COLUMN IF NOT EXISTS）
-- 新库由 settings 的 schema.sql / data.sql 直接带 demo_mode，本脚本仅服务存量库升级。
-- 可重复执行：列已存在时跳过。
-- 执行：mysql -uroot -p < 03-demo-mode.sql

SET @dbname = DATABASE();

-- 1) 加列（默认 0 关闭，存量数据不受影响）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'cfg_security_config'
        AND COLUMN_NAME = 'demo_mode') = 0,
    'ALTER TABLE `cfg_security_config` ADD COLUMN `demo_mode` tinyint(4) NOT NULL DEFAULT 0 COMMENT ''演示模式开关 1 开启（演示账号可登录）/ 0 关闭'' AFTER `verify_code_expire_minutes`',
    'SELECT ''cfg_security_config.demo_mode 已存在，跳过'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 确保单行配置存在（demo_mode 由 DDL 默认值提供；首行缺失时补齐最小行）
INSERT IGNORE INTO `cfg_security_config` (`id`, `demo_mode`) VALUES (1, 0);
