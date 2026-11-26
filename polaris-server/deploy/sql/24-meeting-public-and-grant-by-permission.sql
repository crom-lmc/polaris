-- 北极星会（meeting）设为全员应用；北极星门（portal）、北极星空（forum）保持全员。
-- 修复「候补授权」：账号通过角色拥有某应用的任意权限码，即补该应用的应用授权（sso_account_app），
-- 否则应用中心看不到入口、OAuth 换票会被 403。
-- 北极星云（file）不是全员应用，授权保持不变，不参与本次刷数（见 WHERE pm.app_code <> 'file'）。
-- 幂等：全部 INSERT IGNORE + UPDATE 固定值，可重复执行。

-- 1) 全员应用无条件补授权：meeting / portal / forum
INSERT IGNORE INTO `sso_ms`.`sso_account_app` (`account_id`, `app_id`)
SELECT a.`id`, p.`id`
FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_application` p
  ON p.`code` IN ('meeting', 'portal', 'forum') AND p.`status` = 1 AND p.`deleted` = 0
WHERE a.`deleted` = 0;

-- 2) 按权限码反推应用授权：角色含某 app_code 的任意有效权限码 => 补该应用授权。
--    sso 域权限码的 app_code='sso'，对应应用 code='sso-center'，需映射。
--    file 不参与（授权保持不变）。
INSERT IGNORE INTO `sso_ms`.`sso_account_app` (`account_id`, `app_id`)
SELECT DISTINCT ar.`account_id`, p.`id`
FROM `sso_ms`.`sso_account_role` ar
JOIN `sso_ms`.`sso_role` r ON r.`id` = ar.`role_id` AND r.`deleted` = 0
JOIN `sso_ms`.`sso_role_permission` rp ON rp.`role_id` = r.`id`
JOIN `sso_ms`.`sso_permission` pm ON pm.`id` = rp.`permission_id` AND pm.`deleted` = 0
JOIN `sso_ms`.`sso_application` p
  ON p.`code` = CASE pm.`app_code` WHEN 'sso' THEN 'sso-center' ELSE pm.`app_code` END
 AND p.`status` = 1 AND p.`deleted` = 0
JOIN `sso_ms`.`sso_account` a ON a.`id` = ar.`account_id` AND a.`deleted` = 0
WHERE pm.`app_code` <> 'file';

-- 3) 补 forum 授权时同步绑定 FORUM_MEMBER 角色（幂等，与规范一致）
INSERT IGNORE INTO `sso_ms`.`sso_account_role` (`account_id`, `role_id`)
SELECT a.`id`, r.`id`
FROM `sso_ms`.`sso_account` a
JOIN `sso_ms`.`sso_role` r ON r.`code` = 'FORUM_MEMBER' AND r.`deleted` = 0
WHERE a.`deleted` = 0;

-- 4) 新开账号默认应用埋点：加入 meeting（file 保持不变）
UPDATE `settings`.`cfg_embed_point`
SET `value` = '{"apps":["sso-center","portal","ehr","forum","meeting"]}', `updated_at` = NOW()
WHERE `code` = 'ehr.account.default-apps' AND `deleted` = 0;

SELECT 'grant by permission done' AS result,
       (SELECT COUNT(*) FROM `sso_ms`.`sso_account` WHERE `deleted` = 0) AS accounts,
       (SELECT COUNT(*) FROM `sso_ms`.`sso_account_app`) AS app_grants;
