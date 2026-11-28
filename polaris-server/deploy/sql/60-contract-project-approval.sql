-- 项目档案接入审批中心（2026-11-28）
-- 口径：立项中（DRAFT）提交审批后由上级领导一级审批；通过→执行中（ACTIVE），驳回→立项中（DRAFT）可修改再提交。
-- 模板：上级领导（ORG_LEADER levelOffset=1，取 FORM 里 dept 对应组织的上级负责人），审批人解析不到则 BLOCK 阻塞。
-- 幂等：全部 INSERT IGNORE，可重复执行。

USE `settings`;

INSERT IGNORE INTO `cfg_approval_template`
  (`id`,`code`,`name`,`app_code`,`biz_type`,`biz_name`,`enabled`,`version`,`remark`,`deleted`,`created_at`,`updated_at`)
VALUES
  (9026,'contract_project_default','项目档案审批流程','contract','contract.project','项目档案',1,1,'上级领导一级审批，通过后项目转执行中',0,NOW(),NOW());

INSERT IGNORE INTO `cfg_approval_step`
  (`id`,`template_id`,`seq`,`name`,`condition_expr`,`condition_desc`,`reject_action`,`deleted`,`created_at`,`updated_at`,`parent_node_id`,`branch_path`,`depth`,`ui_kind`,`sla_hours`)
VALUES
  (9150,9026,1,'上级领导审批','','','END',0,NOW(),NOW(),NULL,'',0,'APPROVER',24);

INSERT IGNORE INTO `cfg_approval_node`
  (`id`,`template_id`,`step_id`,`seq`,`name`,`condition_expr`,`condition_desc`,`approvers`,`approve_mode`,`allow_self`,`skip_same`,`on_empty`,`timeout_hours`,`deleted`,`created_at`,`updated_at`,`is_fallback`,`child_chain`)
VALUES
  (9260,9026,9150,1,'上级领导','','','[{"type":"ORG_LEADER","source":"FIELD:dept:ORG","levelOffset":1}]','ANY',0,1,'BLOCK',NULL,0,NOW(),NOW(),0,0);

-- 存量修复：历史手工「启动」留在 ACTIVE 的项目不受影响；若有审批中但无在途实例的项目，重置为 DRAFT 让用户可以重新提交。
USE `polaris_contract`;
UPDATE `contract_project` p
   SET p.`status` = 'DRAFT', p.`updated_at` = NOW()
 WHERE p.`status` = 'PENDING'
   AND p.`deleted` = 0
   AND NOT EXISTS (
     SELECT 1 FROM `settings`.`appr_instance` i
      WHERE i.`biz_type` = 'contract.project'
        AND i.`biz_id` = CAST(p.`id` AS CHAR)
        AND i.`status` = 0
        AND i.`deleted` = 0);

USE `settings`;
-- 校验：模板/步骤/节点数量
SELECT 'project_approval_config' AS `entity`,
       (SELECT COUNT(*) FROM `cfg_approval_template` WHERE `biz_type` = 'contract.project' AND `deleted`=0) AS `templates`,
       (SELECT COUNT(*) FROM `cfg_approval_step` WHERE `template_id` = 9026 AND `deleted`=0) AS `steps`,
       (SELECT COUNT(*) FROM `cfg_approval_node` WHERE `template_id` = 9026 AND `deleted`=0) AS `nodes`;
