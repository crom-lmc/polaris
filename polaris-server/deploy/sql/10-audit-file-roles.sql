-- 审计与附件管理角色：4 个子公司研发部及下属项目子部门授权
-- 幂等，可重复执行。组织码见 ehr.ehr_org_node：
--   西安研发部 XC000100010001（子项目 0001-0003）
--   北京研发部 XC000100020001（子项目 0001-0003）
--   上海研发部 XC000100060002（子项目 0001-0002）
--   广州研发部 XC000100070001（子项目 0001-0004）
-- 授权规则：
--   审计角色 -> 研发部本级 + 全部子部门账号；本级为管理员，子部门为查看员
--   附件角色 -> 仅研发部本级账号（管理员）；查看员角色预置备用，本期不绑定人员

-- 1. 角色
INSERT IGNORE INTO `sso_role` (`id`,`code`,`app_code`,`name`,`remark`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112400000000201,'AUDIT_ADMIN','audit','审计管理员','审计全量：概览/审计事件/登录审计/系统日志',NOW(),NOW(),0),
(2026112400000000202,'AUDIT_VIEWER','audit','审计查看员','审计只读：概览/审计事件/登录审计（不含系统日志）',NOW(),NOW(),0),
(2026112400000000203,'FILE_ADMIN','file','附件管理员','附件全量：文件管理/上传下载删除/存储策略/附件设置水印',NOW(),NOW(),0),
(2026112400000000204,'FILE_VIEWER','file','附件查看员','附件只读：概览/文件列表/策略查看/水印查看',NOW(),NOW(),0);

-- 2. 角色 -> 权限
-- 审计管理员：audit 应用全部权限
INSERT IGNORE INTO `sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_role` r
JOIN `sso_permission` p ON p.`app_code`='audit' AND p.`deleted`=0
WHERE r.`code`='AUDIT_ADMIN' AND r.`deleted`=0;

-- 审计查看员：除系统日志与全局设置（含编辑）外的 audit 权限；系统日志含排查细节、设置含编辑均仅管理员可见
INSERT IGNORE INTO `sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_role` r
JOIN `sso_permission` p ON p.`app_code`='audit' AND p.`deleted`=0
  AND p.`code` NOT LIKE '%system-log%' AND p.`code` NOT LIKE 'audit:setting%'
WHERE r.`code`='AUDIT_VIEWER' AND r.`deleted`=0;

-- 附件管理员：file 应用全部权限
INSERT IGNORE INTO `sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_role` r
JOIN `sso_permission` p ON p.`app_code`='file' AND p.`deleted`=0
WHERE r.`code`='FILE_ADMIN' AND r.`deleted`=0;

-- 附件查看员：菜单 + 只读按钮，不含上传/下载/删除/策略编辑/水印编辑
INSERT IGNORE INTO `sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_role` r
JOIN `sso_permission` p ON p.`app_code`='file' AND p.`deleted`=0
  AND p.`code` IN ('file:overview','file:files','file:policy','file:attachment-setting',
                   'file:overview:view','file:list','file:policy:list','file:watermark:view')
WHERE r.`code`='FILE_VIEWER' AND r.`deleted`=0;

-- 3. 账号 -> 角色
-- 研发部本级（12 位组织码）-> 审计管理员
INSERT IGNORE INTO `sso_account_role` (`account_id`,`role_id`)
SELECT a.`id`,r.`id` FROM `sso_account` a
JOIN `sso_role` r ON r.`deleted`=0 AND r.`code`='AUDIT_ADMIN'
WHERE a.`deleted`=0 AND a.`data_scope_org_code` IN (
  'XC000100010001','XC000100020001','XC000100060002','XC000100070001');

-- 研发部下属项目子部门（16 位组织码）-> 审计查看员
INSERT IGNORE INTO `sso_account_role` (`account_id`,`role_id`)
SELECT a.`id`,r.`id` FROM `sso_account` a
JOIN `sso_role` r ON r.`deleted`=0 AND r.`code`='AUDIT_VIEWER'
WHERE a.`deleted`=0 AND LENGTH(a.`data_scope_org_code`)=18 AND (
  a.`data_scope_org_code` LIKE 'XC000100010001%' OR a.`data_scope_org_code` LIKE 'XC000100020001%'
  OR a.`data_scope_org_code` LIKE 'XC000100060002%' OR a.`data_scope_org_code` LIKE 'XC000100070001%');

-- 研发部本级 -> 附件管理员（附件角色只给本级，子部门不授予）
INSERT IGNORE INTO `sso_account_role` (`account_id`,`role_id`)
SELECT a.`id`,r.`id` FROM `sso_account` a
JOIN `sso_role` r ON r.`deleted`=0 AND r.`code`='FILE_ADMIN'
WHERE a.`deleted`=0 AND a.`data_scope_org_code` IN (
  'XC000100010001','XC000100020001','XC000100060002','XC000100070001');

-- 4. 应用访问权（等同 AccountServiceImpl.grantAppsForRoles 的行为，只增不减）
INSERT IGNORE INTO `sso_account_app` (`account_id`,`app_id`)
SELECT a.`id`,ap.`id` FROM `sso_account` a
JOIN `sso_application` ap ON ap.`deleted`=0 AND ap.`code`='audit'
WHERE a.`deleted`=0 AND (
  a.`data_scope_org_code` IN ('XC000100010001','XC000100020001','XC000100060002','XC000100070001')
  OR (LENGTH(a.`data_scope_org_code`)=18 AND (
    a.`data_scope_org_code` LIKE 'XC000100010001%' OR a.`data_scope_org_code` LIKE 'XC000100020001%'
    OR a.`data_scope_org_code` LIKE 'XC000100060002%' OR a.`data_scope_org_code` LIKE 'XC000100070001%')));

INSERT IGNORE INTO `sso_account_app` (`account_id`,`app_id`)
SELECT a.`id`,ap.`id` FROM `sso_account` a
JOIN `sso_application` ap ON ap.`deleted`=0 AND ap.`code`='file'
WHERE a.`deleted`=0 AND a.`data_scope_org_code` IN (
  'XC000100010001','XC000100020001','XC000100060002','XC000100070001');
