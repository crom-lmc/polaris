-- 存储策略种子（INSERT IGNORE 幂等）；ID 用固定值保证重复执行稳定
INSERT IGNORE INTO `file_storage_policy`
  (`id`,`biz_type`,`biz_name`,`max_size_mb`,`allowed_ext`,`default_visibility`,`force_private`,`enabled`,`remark`,`created_at`)
VALUES
  (9200000000000000001,'ehr_attachment','EHR 员工附件',20,
   'jpg,jpeg,png,pdf,doc,docx,xls,xlsx,zip,txt','PRIVATE',1,1,'员工档案附件强制私有；合同/存档 txt 需纳入',NOW()),
  (9200000000000000002,'cms_media','CMS 媒体资源',50,
   'jpg,jpeg,png,gif,webp,svg,pdf,mp4','PUBLIC',0,1,'站点公开图片与附件',NOW()),
  (9200000000000000003,'forum_post','论坛帖子附件',10,
   'jpg,jpeg,png,gif,webp,pdf,svg','PUBLIC',0,1,'帖子图片与附件；历史封面含 svg',NOW()),
  (9200000000000000004,'openapi_export','开放平台导出文件',30,
   'csv,xlsx,zip','PRIVATE',1,1,'开放平台数据导出，私有',NOW()),
  (9200000000000000005,'notice_attachment','站内消息附件',20,
   'jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,zip','PRIVATE',1,1,'站内信附件强制私有，仅消息接收人可下载',NOW()),
  (9200000000000000006,'announcement_attachment','内部通知附件',20,
   'jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,zip','PRIVATE',1,1,'公告正文插图与附件强制私有，按公告可见范围下载',NOW()),
  (9200000000000000007,'meeting_room_image','会议室图片',20,
   'jpg,jpeg,png,gif,webp','PRIVATE',1,1,'会议室图片强制私有，业务表 meeting_room.file_ids 只存 file_id',NOW()),
  (9200000000000000008,'finance_expense_attachment','财务报销凭证附件',20,
   'jpg,jpeg,png,pdf,ofd,doc,docx,xls,xlsx,zip','PRIVATE',1,1,'报销凭证强制私有，业务表 fin_expense.file_ids 只存 file_id；按报销单权限下载',NOW()),
  (9200000000000000009,'contract_attachment','合同业务附件',20,
   'jpg,jpeg,png,pdf,ofd,doc,docx,xls,xlsx,zip','PRIVATE',1,1,'采购计划/合同/验收共用；强制私有，业务表 file_ids 只存 file_id，按单据权限预览',NOW());

-- 水印配置种子（INSERT IGNORE 幂等）：默认给员工附件（含合同/档案）启用水印，其余业务可按需在页面新增
-- 注：合同附件统一按 ehr_attachment 上传，不再单独配置 ehr_contract
INSERT IGNORE INTO `file_watermark_config`
  (`id`,`biz_type`,`enabled`,`content`,`font_size`,`color`,`opacity`,`rotation`,`layout`,`gap`,`file_types`,`scenes`,`created_at`)
VALUES
  (9300000000000000002,'ehr_attachment',1,'内部资料\n{username} · {date}',24,'#8B5CF6',28,-24,'TILE',46,'IMAGE,PDF,SVG','VIEW,SHARE',NOW());

-- 附件全局设置种子（INSERT IGNORE 幂等）
INSERT IGNORE INTO `file_attachment_setting` (`setting_key`,`setting_value`,`updated_at`) VALUES
  ('preview.enabled','true',NOW()),
  ('preview.maxSizeMb','50',NOW()),
  ('thumbnail.maxEdge','320',NOW()),
  ('dedup.enabled','true',NOW()),
  ('sign.ttlSeconds','300',NOW()),
  ('audit.download','true',NOW()),
  ('recycle.keepDays','30',NOW());
