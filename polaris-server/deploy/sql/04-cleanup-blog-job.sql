-- 存量库幂等清理：移除已迁移出去的 blog 调度残留
-- blog 服务已独立部署，不再向调度中心上报执行器；调度中心内遗留的
-- 「阅读数落库」任务、blog 执行器注册记录、历史执行日志与告警记录一并清理。
-- 可重复执行：删除条件固定，重复执行不影响其余任务。
-- 执行：mysql -uroot -p < 04-cleanup-blog-job.sql

USE polaris_job;

-- 1) 任务定义
DELETE FROM `job_info`      WHERE `app_code` = 'blog' OR `id` = 910000000000000010;

-- 2) 历史执行日志
DELETE FROM `job_log`       WHERE `job_id` = 910000000000000010;

-- 3) 告警规则与告警发送记录
DELETE FROM `job_alarm`     WHERE `scope_type` = 'JOB' AND `scope_value` = '910000000000000010';
DELETE FROM `job_alarm_log` WHERE `job_id` = 910000000000000010;

-- 4) 执行器注册记录（心跳已停止，blog 不再使用本调度中心）
DELETE FROM `job_registry`  WHERE `app_code` = 'blog';

-- 5) 任务名称国际化备份表残留行
DELETE FROM `job_info_i18n_bak` WHERE `id` = 910000000000000010;
