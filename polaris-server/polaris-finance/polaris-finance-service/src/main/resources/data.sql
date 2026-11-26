-- 财务域最小种子：会计科目 / 预算科目 / 预算额度。配置类开关与单号规则统一在 settings 维护，此处不写死。
INSERT IGNORE INTO `fin_subject` (`id`, `code`, `name`, `level`, `balance_direction`, `category`, `status`, `org_code`, `created_at`, `updated_at`, `deleted`) VALUES
(2026112600000000001, '1001', '库存现金', 1, 'DEBIT', 'ASSET', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000002, '1002', '银行存款', 1, 'DEBIT', 'ASSET', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000003, '1122', '应收账款', 1, 'DEBIT', 'ASSET', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000004, '2202', '应付账款', 1, 'CREDIT', 'LIABILITY', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000005, '6001', '主营业务收入', 1, 'CREDIT', 'INCOME', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000006, '6601', '销售费用', 1, 'DEBIT', 'EXPENSE', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000007, '6602', '管理费用', 1, 'DEBIT', 'EXPENSE', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000008, '660201', '职工薪酬', 2, 'DEBIT', 'EXPENSE', 'ENABLED', 'XC0001', NOW(), NOW(), 0);

INSERT IGNORE INTO `fin_budget_subject` (`id`, `code`, `name`, `year`, `annual_amount`, `account_subject_code`, `status`, `org_code`, `created_at`, `updated_at`, `deleted`) VALUES
(2026112600000000101, 'BS-2026-001', '研发费用预算', 2026, 5200000.00, '6602', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000102, 'BS-2026-002', '市场推广预算', 2026, 1800000.00, '6601', 'ENABLED', 'XC0001', NOW(), NOW(), 0),
(2026112600000000103, 'BS-2026-003', '行政办公预算', 2026, 720000.00, '6602', 'ENABLED', 'XC0001', NOW(), NOW(), 0);

INSERT IGNORE INTO `fin_budget` (`id`, `dept`, `year`, `amount`, `history`, `contract_committed`, `contract_paid`, `contract_in_flight`, `org_code`, `created_at`, `updated_at`, `deleted`) VALUES
(2026112600000000201, '研发中心', 2026, 5200000.00, 2960000.00, 0.00, 0.00, 0.00, 'XC00010003', NOW(), NOW(), 0),
(2026112600000000202, '市场部', 2026, 1800000.00, 1660000.00, 0.00, 0.00, 0.00, 'XC00010004', NOW(), NOW(), 0),
(2026112600000000203, '销售部', 2026, 2400000.00, 1300000.00, 0.00, 0.00, 0.00, 'XC00010002', NOW(), NOW(), 0),
(2026112600000000204, '人力资源部', 2026, 960000.00, 400000.00, 0.00, 0.00, 0.00, 'XC00010005', NOW(), NOW(), 0),
(2026112600000000205, '行政部', 2026, 720000.00, 335000.00, 0.00, 0.00, 0.00, 'XC00010001', NOW(), NOW(), 0);
