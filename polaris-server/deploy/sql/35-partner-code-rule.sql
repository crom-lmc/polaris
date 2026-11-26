-- 往来单位（供应商/客户/两者）单号规则：GYS-yyyy-XXXXXX，6 位流水。
-- settings 侧 sql.init.mode=never，需手工在 settings 库执行本脚本。
-- 现有 200 条基础数据已占用 GYS-2026-000001 ~ GYS-2026-000200，
-- 故 last_no 初始化为 200，新建往来单位从第 201 号起，避免重号。
-- 幂等：INSERT IGNORE；若规则已存在且 last_no 需调整，用下方 UPDATE（谨慎）。

INSERT IGNORE INTO `settings`.`cfg_code_rule`
  (`id`, `app_code`, `table_name`, `prefix`, `separator_char`, `seq_length`, `pad_zero`,
   `reset_monthly`, `start_no`, `date_format`, `expr_format`, `last_no`, `last_period`,
   `deleted`, `created_at`, `updated_at`)
VALUES
  (11416, 'contract', 'contract_partner', 'GYS', '-', 6, 1, 0, 1, 'yyyy',
   '{prefix}-{date}-{seq}', 200, '2026', 0, NOW(), NOW());

-- 校验：
-- SELECT id, app_code, table_name, prefix, expr_format, last_no FROM settings.cfg_code_rule WHERE id = 11416;
