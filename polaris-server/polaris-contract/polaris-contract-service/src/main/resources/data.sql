-- 合同域少量演示种子（幂等）。字典、埋点、单号规则统一由 settings 维护，不在本库重复配置。
INSERT IGNORE INTO `contract_partner` (`id`,`code`,`name`,`subject_type`,`role`,`type`,`credit_code`,`legal_person`,`registered_capital`,`establish_date`,`taxpayer_type`,`address`,`remark`,`contact`,`phone`,`terms`,`rating`,`status`,`bank`,`account_name`,`bank_account`,`org_code`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000010001,'GYS-B','供应商 B · 服务器','COMPANY','SUPPLIER','电子设备','91640300MA75K2X81T','刘志强','5000 万元','2012-04-18','一般纳税人','深圳市南山区科技园','','刘洋','138****2041','30 天账期','A','COOPERATING','招商银行深圳分行科技园支行','深圳市锐辰信息技术有限公司','7559 0123 4560 0012 345','XC00010003','2026-11-26 18:30:00','2026-11-26 18:30:00',0),
(2026112600000010002,'KH-A','客户 A · 西安研发中心','COMPANY','CUSTOMER','科技研发','91610131MA7B2N9C5U','何振华','1 亿元','2008-05-16','一般纳税人','西安市高新区','','何静','139****1088','30 天账期','A','COOPERATING','工商银行陕西分行高新支行','西安启明科技有限公司','3700 1122 3344 5566 778','XC00010002','2026-11-26 18:30:00','2026-11-26 18:30:00',0);

INSERT IGNORE INTO `contract_material` (`id`,`parent_id`,`code`,`name`,`kind`,`spec`,`unit`,`reference_price`,`org_code`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000020001,NULL,'01','原材料','CATEGORY','','',NULL,'XC0001','2026-11-26 18:30:00','2026-11-26 18:30:00',0),
(2026112600000020002,2026112600000020001,'0101','电子元器件','CATEGORY','','',NULL,'XC0001','2026-11-26 18:30:00','2026-11-26 18:30:00',0),
(2026112600000020003,2026112600000020002,'010101','计算与存储设备','CATEGORY','','',NULL,'XC0001','2026-11-26 18:30:00','2026-11-26 18:30:00',0),
(2026112600000020004,2026112600000020003,'010101000001','服务器整机','MATERIAL','2U 双路 / 64G / 2T','台',300000.00,'XC0001','2026-11-26 18:30:00','2026-11-26 18:30:00',0),
(2026112600000020005,2026112600000020003,'010101000002','CPU 模块','MATERIAL','32 核','片',48000.00,'XC0001','2026-11-26 18:30:00','2026-11-26 18:30:00',0);

INSERT IGNORE INTO `contract_project` (`id`,`code`,`name`,`type`,`org_code`,`team_code`,`cost_center_code`,`owner`,`budget_amount`,`start_date`,`end_date`,`status`,`remark`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000030001,'PRJ-20260915-000004','研发中心服务器扩容项目','PURCHASE','XC00010003','XC00010003','polaris','李思远',820000.00,'2026-09-20','2026-12-31','ACTIVE','','2026-11-26 18:30:00','2026-11-26 18:30:00',0);

INSERT IGNORE INTO `contract_header` (`id`,`no`,`type`,`name`,`partner_code`,`project_no`,`dept`,`owner`,`signed_date`,`effective_date`,`end_date`,`status`,`payment_terms`,`subject`,`org_code`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000040001,'HT-CG-2026-000918','PURCHASE','研发服务器及配套采购','GYS-B','PRJ-20260915-000004','研发中心','李思远','2026-09-18','2026-09-20','2026-12-31','EXECUTING','20% 预付款 / 70% 到货验收 / 10% 质保金','固定资产','XC00010003','2026-11-26 18:30:00','2026-11-26 18:30:00',0);

INSERT IGNORE INTO `contract_item` (`id`,`contract_no`,`line_no`,`material_code`,`material_name`,`spec`,`unit`,`qty`,`price`,`amount`,`delivered_qty`,`remark`,`org_code`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000050001,'HT-CG-2026-000918',1,'010101000001','服务器整机','2U 双路 / 64G / 2T','台',2.0000,300000.00,600000.00,2.0000,'','XC00010003','2026-11-26 18:30:00','2026-11-26 18:30:00',0),
(2026112600000050002,'HT-CG-2026-000918',2,'010101000002','CPU 模块','32 核','片',3.0000,48000.00,144000.00,3.0000,'','XC00010003','2026-11-26 18:30:00','2026-11-26 18:30:00',0);

INSERT IGNORE INTO `contract_payment_plan` (`id`,`contract_no`,`stage`,`due_date`,`amount`,`source_doc_no`,`status`,`org_code`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000060001,'HT-CG-2026-000918','PREPAY','2026-11-18',156000.00,'','WAITING','XC00010003','2026-11-26 18:30:00','2026-11-26 18:30:00',0);

INSERT IGNORE INTO `contract_purchase_plan` (`id`,`no`,`title`,`dept`,`applicant`,`need_date`,`budget_subject`,`status`,`contract_no`,`org_code`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000070001,'CGJH-2026-000018','研发服务器扩容计划','研发中心','李思远','2026-10-15','固定资产','CONVERTED','HT-CG-2026-000918','XC00010003','2026-11-26 18:30:00','2026-11-26 18:30:00',0);

INSERT IGNORE INTO `contract_purchase_plan_item` (`id`,`plan_no`,`material_code`,`material_name`,`spec`,`unit`,`qty`,`price`,`amount`,`org_code`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000080001,'CGJH-2026-000018','010101000001','服务器整机','2U 双路 / 64G / 2T','台',2.0000,300000.00,600000.00,'XC00010003','2026-11-26 18:30:00','2026-11-26 18:30:00',0);

INSERT IGNORE INTO `contract_acceptance` (`id`,`no`,`contract_no`,`item_id`,`material_code`,`qty`,`date`,`result`,`status`,`org_code`,`created_at`,`updated_at`,`deleted`) VALUES
(2026112600000090001,'JD-20261126-000001','HT-CG-2026-000918',2026112600000050001,'010101000001',2.0000,'2026-11-26','合格','POSTED','XC00010003','2026-11-26 18:30:00','2026-11-26 18:30:00',0);
