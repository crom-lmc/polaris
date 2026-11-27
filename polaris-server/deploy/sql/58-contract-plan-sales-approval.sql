-- 采购计划 / 销售合同接入审批中心（2026-11-27）
-- 口径：采购计划提交后由审批中心审批（PENDING→APPROVED 可转合同），销售合同提交后由审批中心审批（PENDING→EFFECTIVE）。
-- 模板：部门负责人 → 财务中心负责人；与付款计划/采购合同同构，节点取已验证的 ORG_LEADER 配置。
-- 幂等：全部 INSERT IGNORE，可重复执行。

USE `settings`;

INSERT IGNORE INTO `cfg_approval_template`
  (`id`,`code`,`name`,`app_code`,`biz_type`,`biz_name`,`enabled`,`version`,`remark`,`deleted`,`created_at`,`updated_at`)
VALUES
  (9024,'contract_plan_default','采购计划审批流程','contract','contract.plan','采购计划',1,1,'部门负责人 → 财务中心负责人',0,NOW(),NOW()),
  (9025,'contract_sales_default','销售合同审批流程','contract','contract.sales','销售合同',1,1,'部门负责人 → 财务中心负责人',0,NOW(),NOW());

INSERT IGNORE INTO `cfg_approval_step`
  (`id`,`template_id`,`seq`,`name`,`condition_expr`,`condition_desc`,`reject_action`,`deleted`,`created_at`,`updated_at`,`parent_node_id`,`branch_path`,`depth`,`ui_kind`,`sla_hours`)
VALUES
  (9146,9024,1,'部门审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24),
  (9147,9024,2,'财务中心审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24),
  (9148,9025,1,'部门审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24),
  (9149,9025,2,'财务中心审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);

INSERT IGNORE INTO `cfg_approval_node`
  (`id`,`template_id`,`step_id`,`seq`,`name`,`condition_expr`,`condition_desc`,`approvers`,`approve_mode`,`allow_self`,`skip_same`,`on_empty`,`timeout_hours`,`deleted`,`created_at`,`updated_at`,`is_fallback`,`child_chain`)
VALUES
  (9256,9024,9146,1,'部门负责人','','','[{"type":"ORG_LEADER","source":"FIELD:dept:ORG","levelOffset":0}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0),
  (9257,9024,9147,2,'财务中心负责人','','','[{"type":"ORG_LEADER_BY_NAME","code":"财务中心"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0),
  (9258,9025,9148,1,'部门负责人','','','[{"type":"ORG_LEADER","source":"FIELD:dept:ORG","levelOffset":0}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0),
  (9259,9025,9149,2,'财务中心负责人','','','[{"type":"ORG_LEADER_BY_NAME","code":"财务中心"}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);

-- 存量修复：旧逻辑留下的 PENDING 单据没有审批实例，重置为 DRAFT 让用户可以重新提交（走审批中心）。
-- 只在审批中心确实没有在途实例时重置，避免误伤刚发起的审批。
USE `polaris_contract`;
UPDATE `contract_purchase_plan` p
   SET p.`status` = 'DRAFT', p.`updated_at` = NOW()
 WHERE p.`status` = 'PENDING'
   AND p.`deleted` = 0
   AND NOT EXISTS (
     SELECT 1 FROM `settings`.`appr_instance` i
      WHERE i.`biz_type` = 'contract.plan'
        AND i.`biz_id` = CAST(p.`id` AS CHAR)
        AND i.`status` = 0
        AND i.`deleted` = 0);

UPDATE `contract_header` h
   SET h.`status` = 'DRAFT', h.`updated_at` = NOW()
 WHERE h.`status` = 'PENDING'
   AND h.`type` = 'SALES'
   AND h.`deleted` = 0
   AND NOT EXISTS (
     SELECT 1 FROM `settings`.`appr_instance` i
      WHERE i.`biz_type` = 'contract.sales'
        AND i.`biz_id` = CAST(h.`id` AS CHAR)
        AND i.`status` = 0
        AND i.`deleted` = 0);

USE `settings`;
-- 校验：模板/步骤/节点数量
SELECT 'plan_sales_approval_config' AS `entity`,
       (SELECT COUNT(*) FROM `cfg_approval_template` WHERE `biz_type` IN ('contract.plan','contract.sales') AND `deleted`=0) AS `templates`,
       (SELECT COUNT(*) FROM `cfg_approval_step` WHERE `template_id` IN (9024,9025) AND `deleted`=0) AS `steps`,
       (SELECT COUNT(*) FROM `cfg_approval_node` WHERE `template_id` IN (9024,9025) AND `deleted`=0) AS `nodes`;
