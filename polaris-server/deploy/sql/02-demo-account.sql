-- 演示账户初始化（MySQL 5.7，幂等，可重复执行）
-- 账号 yanshi / yanshi，绑定 SUPER_ADMIN 角色（全部菜单可见）；
-- 写操作由网关按用户名拦截，返回「演示账户不能进行操作」。
-- 执行：mysql -uroot -p < 02-demo-account.sql

-- ============ sso_ms：账户 + 角色 ============
USE sso_ms;

-- 1) 演示账户（BCrypt(yanshi)，$2a$10$）
INSERT IGNORE INTO `sso_account`
  (`id`, `username`, `nickname`, `email`, `data_scope_org_code`, `phone`,
   `password_hash`, `avatar_url`, `status`, `source`, `pwd_reset_required`,
   `login_fail_count`, `locked_until`, `last_login_at`, `user_id`,
   `created_at`, `updated_at`, `deleted`)
VALUES
  (7000000000000000001, 'yanshi', '演示用户', 'yanshi@xckj.com', 'XC0001', '13900001111',
   '$2a$10$VmVO/RtB142I7FhSO0ejUe7XlyV69bd/Sq4ShEL7TUSwTtzeXln5e', '', 1, 'local', 0,
   0, NULL, NULL, 7000000000000000002,
   NOW(), NOW(), 0);

-- 2) 绑定 SUPER_ADMIN 角色（拥有全部菜单/按钮权限）
INSERT IGNORE INTO `sso_account_role` (`account_id`, `role_id`)
VALUES (7000000000000000001, 2116193763556519940);

-- 3) 授权全部应用（放行跨应用单点；超管 buildLoginResult 仍按 app 域取全量权限码）
INSERT IGNORE INTO `sso_account_app` (`account_id`, `app_id`)
SELECT 7000000000000000001, `id` FROM `sso_application` WHERE `deleted` = 0;


-- ============ ehr：集团节点下的完整员工档案 ============
USE ehr;

INSERT IGNORE INTO `ehr_employee` (
  `id`, `emp_no`, `name`, `gender`, `mobile`, `email`,
  `native_place`, `native_province`, `native_city`, `native_district`, `native_detail`,
  `id_card`, `birthday`, `nation`, `marital_status`, `political_status`, `education`,
  `emergency_contact`, `emergency_phone`, `current_address`,
  `org_id`, `org_code`, `position`, `hire_date`, `status`,
  `resign_date`, `resign_reason`, `account_id`, `account_username`, `remark`,
  `deleted`, `created_at`, `updated_at`,
  `onboard_step`, `onboard_approvals`, `regularize_status`, `regularize_approvals`,
  `probation_months`, `exempt_probation`, `expected_regularize_date`,
  `source`, `source_offer_id`,
  `seniority_start_date`, `seniority_years`, `first_work_date`,
  `seniority_confirmed_at`, `seniority_confirmed_by`
) VALUES (
  7000000000000000002, 'A9001', '演示用户', 1, '13900001111', 'yanshi@xckj.com',
  '陕西省西安市雁塔区', '陕西省', '西安市', '雁塔区', '科技路 88 号 1 栋 1 单元 101',
  '11010519491231002X', '1995-05-20', 'han', 'unmarried', 'masses', 'bachelor',
  '演示联系人', '13900002222', '陕西省西安市雁塔区科技路 88 号',
  2116787731965128706, 'XC0001', '演示专员', '2024-01-15', 40,
  NULL, '', 7000000000000000001, 'yanshi', '演示账户（只读，可查看全部菜单，写操作由网关拦截）',
  0, NOW(), NOW(),
  NULL, NULL, 3, NULL,
  3, 0, '2024-04-15',
  'MANUAL', NULL,
  '2024-01-15', 2.0, '2018-07-01',
  NULL, NULL
);

-- 4) 完整子档案
INSERT IGNORE INTO `ehr_emp_bank`
  (`id`, `employee_id`, `bank`, `account_no`, `holder_name`, `branch`, `is_primary`, `remark`, `deleted`, `created_at`, `updated_at`)
VALUES
  (7000000000000001001, 7000000000000000002, 'icbc', '6222021234567890123', '演示用户', '工商银行西安雁塔支行', 1, '', 0, NOW(), NOW());

INSERT IGNORE INTO `ehr_emp_education`
  (`id`, `employee_id`, `school`, `major`, `degree`, `start_date`, `end_date`, `remark`, `deleted`, `created_at`, `updated_at`)
VALUES
  (7000000000000001002, 7000000000000000002, '演示大学', '计算机科学与技术', 'bachelor', '2014-09-01', '2018-06-30', '', 0, NOW(), NOW());

INSERT IGNORE INTO `ehr_emp_work`
  (`id`, `employee_id`, `company`, `position`, `start_date`, `end_date`, `leave_reason`, `remark`, `deleted`, `created_at`, `updated_at`)
VALUES
  (7000000000000001003, 7000000000000000002, '演示科技有限公司', '后端工程师', '2018-07-01', '2024-01-14', '个人发展', '', 0, NOW(), NOW());

INSERT IGNORE INTO `ehr_emp_family`
  (`id`, `employee_id`, `relation`, `name`, `company`, `phone`, `remark`, `deleted`, `created_at`, `updated_at`)
VALUES
  (7000000000000001004, 7000000000000000002, 'father', '演示父亲', '', '13900003333', '', 0, NOW(), NOW());
