-- ============================================================================
-- 应收应付「账期天数」改为 settings 字典 finance.credit_period
-- 1) 新建账期档位字典：30 / 60 / 90 / 180 / 365 天
-- 2) 公共字典白名单在 PublicReferenceController.DICT_ALLOWED 中放开，需重启 settings
-- 幂等：cfg_dict_type/cfg_dict_item 用 INSERT IGNORE，重复执行结果一致。
-- 环境：生产 SETTINGS_SQL_INIT_MODE = never，必须手工执行。
-- ============================================================================

USE `settings`;

INSERT IGNORE INTO `settings`.`cfg_dict_type`
  (`id`,`code`,`name`,`app_code`,`remark`,`built_in`,`status`,`deleted`,`created_at`,`updated_at`)
VALUES (11519,'finance.credit_period','账期档位','finance','',1,1,0,NOW(),NOW());

INSERT IGNORE INTO `settings`.`cfg_dict_item`
  (`id`,`type_code`,`value`,`label`,`sort`,`status`,`remark`,`built_in`,`deleted`,`created_at`,`updated_at`)
VALUES
  (1500939,'finance.credit_period','30','30 天',1,1,'',1,0,NOW(),NOW()),
  (1500940,'finance.credit_period','60','60 天',2,1,'',1,0,NOW(),NOW()),
  (1500941,'finance.credit_period','90','90 天',3,1,'',1,0,NOW(),NOW()),
  (1500942,'finance.credit_period','180','180 天',4,1,'',1,0,NOW(),NOW()),
  (1500943,'finance.credit_period','365','365 天',5,1,'',1,0,NOW(),NOW());

SELECT 'finance credit period dict done' AS result;
