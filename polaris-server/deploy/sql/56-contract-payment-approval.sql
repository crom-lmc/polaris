-- 合同付款计划审批：新增审批模板与升级线埋点（2026-11-27）
-- 口径：付款申请必须先走审批中心，终审通过（PENDING→APPROVED）后才进入后续应付/付款单据对接。
-- 模板：部门负责人 → 财务中心负责人；本期金额超过升级线时再追加「总经理终审」。
-- 幂等：全部 INSERT IGNORE，可重复执行。

USE `settings`;

INSERT IGNORE INTO `cfg_embed_point`
  (`id`,`code`,`app_code`,`module`,`name`,`value`,`built_in`,`sort`,`deleted`,`created_at`,`updated_at`)
VALUES
  (2026112600000000027,'contract.payment.approval-amount','contract','合同管理','付款计划审批升级线','50000',1,8,0,NOW(),NOW());

INSERT IGNORE INTO `cfg_approval_template`
  (`id`,`code`,`name`,`app_code`,`biz_type`,`biz_name`,`enabled`,`version`,`remark`,`deleted`,`created_at`,`updated_at`)
VALUES
  (9022,'contract_payment_default','付款计划审批流程','contract','contract.payment','付款计划',1,1,'部门负责人 → 财务中心负责人',0,NOW(),NOW()),
  (9023,'contract_payment_senior','付款计划审批流程（超金额档位）','contract','contract.payment.senior','付款计划',1,1,'部门负责人 → 财务中心负责人 → 总经理终审',0,NOW(),NOW());

INSERT IGNORE INTO `cfg_approval_step`
  (`id`,`template_id`,`seq`,`name`,`condition_expr`,`condition_desc`,`reject_action`,`deleted`,`created_at`,`updated_at`,`parent_node_id`,`branch_path`,`depth`,`ui_kind`,`sla_hours`)
VALUES
  (9141,9022,1,'部门审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24),
  (9142,9022,2,'财务中心审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24),
  (9143,9023,1,'部门审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24),
  (9144,9023,2,'财务中心审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24),
  (9145,9023,3,'总经理终审','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);

INSERT IGNORE INTO `cfg_approval_node`
  (`id`,`template_id`,`step_id`,`seq`,`name`,`condition_expr`,`condition_desc`,`approvers`,`approve_mode`,`allow_self`,`skip_same`,`on_empty`,`timeout_hours`,`deleted`,`created_at`,`updated_at`,`is_fallback`,`child_chain`)
VALUES
  (9251,9022,9141,1,'部门负责人','','','[{"type":"ORG_LEADER","source":"FIELD:dept:ORG","levelOffset":0}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0),
  (9252,9022,9142,2,'财务中心负责人','','','[{"type":"ORG_LEADER_BY_NAME","code":"财务中心"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0),
  (9253,9023,9143,1,'部门负责人','','','[{"type":"ORG_LEADER","source":"FIELD:dept:ORG","levelOffset":0}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0),
  (9254,9023,9144,2,'财务中心负责人','','','[{"type":"ORG_LEADER_BY_NAME","code":"财务中心"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0),
  (9255,9023,9145,3,'总经理','','','[{"type":"ORG_LEADER_BY_NAME","code":"晨星科技集团"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);

-- 存量修复：旧逻辑留下的 PENDING 付款计划没有审批实例，重置为 WAITING 让用户可以重新发起审批。
-- 只在审批中心确实没有在途实例时重置，避免误伤刚发起的审批。
USE `polaris_contract`;
UPDATE `contract_payment_plan` p
   SET p.`status` = 'WAITING', p.`updated_at` = NOW()
 WHERE p.`status` = 'PENDING'
   AND p.`deleted` = 0
   AND NOT EXISTS (
     SELECT 1 FROM `settings`.`appr_instance` i
      WHERE i.`biz_type` IN ('contract.payment','contract.payment.senior')
        AND i.`biz_id` = CAST(p.`id` AS CHAR)
        AND i.`status` = 0
        AND i.`deleted` = 0);

USE `settings`;
-- 校验：模板/步骤/节点数量与埋点值
SELECT 'payment_approval_config' AS `entity`,
       (SELECT COUNT(*) FROM `cfg_approval_template` WHERE `biz_type` IN ('contract.payment','contract.payment.senior') AND `deleted`=0) AS `templates`,
       (SELECT COUNT(*) FROM `cfg_approval_step` WHERE `template_id` IN (9022,9023) AND `deleted`=0) AS `steps`,
       (SELECT COUNT(*) FROM `cfg_approval_node` WHERE `template_id` IN (9022,9023) AND `deleted`=0) AS `nodes`,
       (SELECT `value` FROM `cfg_embed_point` WHERE `code`='contract.payment.approval-amount' AND `deleted`=0) AS `amount`;
