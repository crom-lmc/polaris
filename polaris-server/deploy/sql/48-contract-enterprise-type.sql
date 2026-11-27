-- ============================================================================
-- 往来单位「类型」改为「企业类型」字典，并迁移存量数据
-- 1) 新建 settings 字典 contract.enterprise_type（标准企业 + 个人 6 项）
-- 2) 存量 200 家往来单位按主体类型映射：COMPANY -> LLC（有限责任公司），PERSON -> PERSON（个人）
--    原 type 存的是业务分类（综合商贸/办公用品供应商/礼品供应商/…），语义错误，本次纠正为企业类型。
-- 3) settings 公共字典白名单放开 contract.enterprise_type（白名单改在代码 PublicReferenceController，需重启 settings；本脚本仅记录说明）
-- 幂等：cfg_dict_type/cfg_dict_item 用 INSERT IGNORE；存量映射按 subject_type 条件更新，重复执行结果一致。
-- 环境：生产 CONTRACT_SQL_INIT_MODE = never，必须手工执行。
-- ============================================================================

USE `settings`;

-- 企业类型字典
INSERT IGNORE INTO `settings`.`cfg_dict_type`
  (`id`,`code`,`name`,`app_code`,`remark`,`built_in`,`status`,`deleted`,`created_at`,`updated_at`)
VALUES (11518,'contract.enterprise_type','企业类型','contract','',1,1,0,NOW(),NOW());

INSERT IGNORE INTO `settings`.`cfg_dict_item`
  (`id`,`type_code`,`value`,`label`,`sort`,`status`,`remark`,`built_in`,`deleted`,`created_at`,`updated_at`)
VALUES
  (1500933,'contract.enterprise_type','PERSON','个人',1,1,'',1,0,NOW(),NOW()),
  (1500934,'contract.enterprise_type','LLC','有限责任公司',2,1,'',1,0,NOW(),NOW()),
  (1500935,'contract.enterprise_type','JSC','股份有限公司',3,1,'',1,0,NOW(),NOW()),
  (1500936,'contract.enterprise_type','SOLE','个体工商户',4,1,'',1,0,NOW(),NOW()),
  (1500937,'contract.enterprise_type','PARTNER','合伙企业',5,1,'',1,0,NOW(),NOW()),
  (1500938,'contract.enterprise_type','OTHER','其他组织',6,1,'',1,0,NOW(),NOW());

USE `polaris_contract`;

-- 存量映射：企业主体统一置为有限责任公司，个人主体置为个人
UPDATE `polaris_contract`.`contract_partner`
SET `type` = 'LLC', `updated_at` = NOW()
WHERE `subject_type` = 'COMPANY' AND `deleted` = 0 AND `type` <> 'LLC';

UPDATE `polaris_contract`.`contract_partner`
SET `type` = 'PERSON', `updated_at` = NOW()
WHERE `subject_type` = 'PERSON' AND `deleted` = 0 AND `type` <> 'PERSON';

SELECT 'contract enterprise type done' AS result;
