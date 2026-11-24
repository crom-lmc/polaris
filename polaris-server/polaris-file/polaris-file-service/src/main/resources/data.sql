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
   'csv,xlsx,zip','PRIVATE',1,1,'开放平台数据导出，私有',NOW());
