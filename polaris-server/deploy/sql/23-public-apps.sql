-- 北极星门（portal）与北极星空（forum）为全员应用：
-- 存量账号统一补应用授权，社区前台写操作同步补 FORUM_MEMBER 角色；
-- 新开账号默认应用埋点加入 forum，避免新增账号再次漏授权。
INSERT IGNORE INTO `sso_ms`.`sso_account_app` (`account_id`, `app_id`)
SELECT a.`id`, p.`id`
FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_application` p
  ON p.`code` IN ('portal', 'forum') AND p.`status` = 1 AND p.`deleted` = 0
WHERE a.`deleted` = 0;

INSERT IGNORE INTO `sso_ms`.`sso_account_role` (`account_id`, `role_id`)
SELECT a.`id`, r.`id`
FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_role` r ON r.`code` = 'FORUM_MEMBER'
WHERE a.`deleted` = 0;

UPDATE `settings`.`cfg_embed_point`
SET `value` = '{"apps":["sso-center","portal","ehr","forum"]}', `updated_at` = NOW()
WHERE `code` = 'ehr.account.default-apps' AND `deleted` = 0;

SELECT 'public apps granted' AS result,
       (SELECT COUNT(*) FROM `sso_ms`.`sso_account` WHERE `deleted` = 0) AS accounts,
       (SELECT COUNT(*) FROM `sso_ms`.`sso_account_app` aa
          JOIN `sso_ms`.`sso_application` p ON p.`id` = aa.`app_id`
         WHERE p.`code` = 'forum') AS forum_grants;
