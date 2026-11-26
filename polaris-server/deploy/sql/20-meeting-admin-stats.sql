-- 北极星会：新增爽约记录/会议室使用率权限，调整角色授权（全部 INSERT IGNORE / WHERE 守卫，幂等）
-- 权限码三处一致：common PermissionCodes + account PermissionConstants.BUILTIN + 前端路由/按钮。
-- 会议室管理员角色 14-meeting-init.sql 已建，这里只做幂等补齐与新增授权，不重复造角色。

INSERT IGNORE INTO `sso_ms`.`sso_permission`
  (`id`,`code`,`name`,`type`,`parent_id`,`sort`,`remark`,`created_at`,`updated_at`,`deleted`,`app_code`)
VALUES
  (2026112500000000311,'meeting:noshow:view','爽约记录',1,0,5,'我的爽约记录，全员可见',NOW(),NOW(),0,'meeting'),
  (2026112500000000312,'meeting:noshow:all','查看全部爽约',2,0,1,'仅会议室管理员',NOW(),NOW(),0,'meeting'),
  (2026112500000000313,'meeting:usage:view','会议室使用率',1,0,6,'分地区按会议室统计，仅会议室管理员',NOW(),NOW(),0,'meeting');

UPDATE `sso_ms`.`sso_permission` p
JOIN `sso_ms`.`sso_permission` m ON m.`code`='meeting:noshow:view' AND m.`app_code`='meeting' AND m.`deleted`=0
SET p.`parent_id`=m.`id`, p.`sort`=1
WHERE p.`app_code`='meeting' AND p.`code`='meeting:noshow:all'
  AND (p.`parent_id`=0 OR p.`parent_id` IS NULL);

-- 会议室管理员：保留原有 10 码，再补全部爽约/使用率；角色本身 INSERT IGNORE 幂等
INSERT IGNORE INTO `sso_ms`.`sso_role` (`id`,`code`,`app_code`,`name`,`remark`,`created_at`,`updated_at`,`deleted`) VALUES
  (2026112500000000401,'MEETING_ADMIN','meeting','会议室管理员','维护位置/大楼/楼层/会议室 + 全部爽约 + 使用率',NOW(),NOW(),0);

-- 全员基础角色：概览、预定、我的预定、新建/修改、签到、取消、我的爽约（不含位置/会议室维护、延时、释放）
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='meeting' AND p.`deleted`=0
  AND p.`code` IN ('meeting:overview:view','meeting:booking:view','meeting:booking:list',
                   'meeting:booking:create','meeting:booking:checkin','meeting:booking:cancel',
                   'meeting:noshow:view')
WHERE r.`code`='EMPLOYEE_SELF' AND r.`deleted`=0;

-- 会议室用户：补「我的爽约」，其他权限沿用 14-meeting-init.sql
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='meeting' AND p.`deleted`=0
  AND p.`code` IN ('meeting:noshow:view')
WHERE r.`code`='MEETING_USER' AND r.`deleted`=0;

-- 会议室管理员：全部爽约 + 使用率（维护权限仍由 14-meeting-init.sql 的全量授权负责）
INSERT IGNORE INTO `sso_ms`.`sso_role_permission` (`role_id`,`permission_id`)
SELECT r.`id`,p.`id` FROM `sso_ms`.`sso_role` r
JOIN `sso_ms`.`sso_permission` p ON p.`app_code`='meeting' AND p.`deleted`=0
  AND p.`code` IN ('meeting:noshow:view','meeting:noshow:all','meeting:usage:view')
WHERE r.`code`='MEETING_ADMIN' AND r.`deleted`=0;
