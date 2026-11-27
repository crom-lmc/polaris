---
name: polaris-backend-development
description: Polaris 一体化企业平台后端开发流程（polaris-server/，Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.7）。当需要在该仓库新增或修改 Controller、Service、Mapper、SQL 迁移、权限码、Feign 客户端、审计接入、审批回调、定时任务，或做后端编译与部署前检查时应加载本 Skill；本 Skill 应在任何涉及 Polaris 后端代码的任务开始时优先加载。
agent_created: true
---

# Polaris 后端开发 Skill

## 角色

### 你是一名资深的后端开发工程师

先读代码再写代码：全仓 grep 确认能力归属与调用链，按本服务既有风格实现，不做望文生义的推测。

## 规则层级（工作区根 `.workbuddy/rules/`，红线不在此重复）

- **L1 红线**：`../../rules/backend-00-L1-redlines.md`（alwaysApply，MUST/MUST NOT）— 违反即拒绝交付。
- **L2 规范**：`../../rules/backend-01-L2-conventions.md`（分层/SQL/权限/审计）、`../../rules/backend-02-L2-platform.md`（平台能力接入）。
- **L3 准则**：`../../rules/backend-03-L3-guidelines.md`（SHOULD，不阻断）。

优先级：L1 > L2 > 本 Skill 流程 > L3 > 模型默认。规范与代码冲突时以代码为准，并提示同步修正文档。

## 按需加载参考

1. **分层与工程写法**：`references/backend-guide.md`（模块依赖、包结构、响应/错误码、权限、审计、SQL/Nacos、部署）。
2. **代码实证的公共模式**：`references/common-code-patterns.md`（common 底座、鉴权差异、client 契约、分页两套壳、Wrapper/逻辑删除/唯一索引坑、SchemaInitializer、错误码碰撞、JobHandler）。
3. **新业务功能**：`references/new-feature-guide.md`（平台能力决策表 + 后端接入契约 + 自查）。
4. **对外提供接口**：`references/inter-service-api.md`（何时建 `*-client`、新建步骤、internal 鉴权 fail-closed、雪花 ID/Raw VO 坑）。当任务涉及跨服务调用、新增 Feign 接口或 `/internal/**` 时必读。
5. **已落地能力**：`references/platform-capabilities.md`（文件服务/水印、审计、SLA、数据权限、应用登记、不存在的后端能力）。

## 标准流程

1. **定范围**：确认改动落在哪些模块/服务；列出可能受影响的接口、表、权限码与共享类。
2. **查归属**：从页面 → API → Controller → Service → Mapper → 表走一遍调用链；全仓 grep 关键词确认能力是否已存在。
3. **做决策**：新功能按 `../../rules/backend-02-L2-platform.md` 决策表逐项判断是否接入单号/审批/消息/埋点/字典/主数据；权限码按四联动补齐；新增配置必须有真实引用点；**新建表涉及组织归属 MUST 预留 `org_code`（orgCode 口径，不是 `org_id`），见 `references/platform-capabilities.md` 第 4 节**；涉及菜单变更 MUST 改 SSO 权限树的 `name/sort/parentCode`，保证 `/api/auth/me?app=xxx` 下发的 `menus` 与前端侧栏一致。
4. **实现**：按本服务既有分层与风格写代码；写前确认响应壳、app 域口径、Wrapper 条件、SQL 幂等策略、审计留痕方式。**Service 强制接口 + 实现**：`XxxService` 建为 interface（只放方法签名与 Javadoc，方法不写 `public`，不放常量，不保留未引用 import），实现放同级 `impl/XxxServiceImpl.java` 标 `@Service` implements 接口，所有常量 / 变量放 impl；Controller / 其它 Service / Job 一律注入接口类型，不注入 Impl，引用常量用 `XxxServiceImpl.CONST`；静态无状态方法可留在接口或独立工具类，`*Support` / `*Utils` / `*Preset` / `*Calculator` 与未命名为 `*Service` 的助手组件不强制拆分。存量单 class Service 一并按此拆分。
5. **自检**：全仓 grep 改动符号无漏改；逐条检查 L1（红线）与 L2（后端/平台）。
6. **验证**：`mvn -o -q -pl <模块> -am -DskipTests compile` 通过；失败先修复再进入下一步。
7. **收尾**：在仓库外 `F:\code\迭代文档\polaris-server\update.md` 顶部追加记录；最终回复列明需重启应用与未提交/未部署状态；未经明确指令不提交不部署。提交时备注用中文（格式 `类型(范围): 中文概述`），前后端改动必须分开提交——后端代码及平台侧配套（SQL/Nacos/网关/部署）一个 commit，前端代码另一个 commit，不混提。生产后端发布 MUST 用 `deploy/rolling-restart.sh` 滚动重启：一次一个服务、等 healthy/running、间隔 30 秒，失败即停；MUST NOT 用 `docker compose up -d` 一次拉起全部。

## 财务报销特有口径（2026-11-27 落地）

- 报销类型 MUST 取 settings 字典 `finance.expense_type`；「类型 → 会计科目」映射只维护在字典项 `remark`（JSON `subjectCode`/`subjectName`），后端解析并校验 `fin_subject`，不得在财务服务本地建配置表或写死枚举。
- 报销单 `dept` 不再信任前端推导：`create`/`update` 先按 `AccountContext` 从 EHR 员工→组织反查补齐申请人/归属部门，空部门拒绝保存（审批模板按 `dept` 解析部门负责人）。
- 报销凭证附件 MUST 走业务代理 `/api/finance/expense/files/upload` → `FileApi.upload`，`bizType=finance_expense_attachment`、`ownerType=FIN_EXPENSE`；`fin_expense.file_ids` 只存 file_id，读时 `FileApi.sign` 生成临时 URL，缺存储策略会 8102。

## 合同附件特有口径（2026-11-27 落地）

- 合同模块用独立策略 `contract_attachment`（file_storage_policy），采购计划/合同/验收三类单据共用，以 `ownerType`（`CONTRACT_PURCHASE_PLAN`/`CONTRACT_HEADER`/`CONTRACT_ACCEPTANCE`）区分；业务表只存 `file_ids`，读时 `FileApi.sign` 签发临时预览 URL，缺策略上传报 8102。
- 上传代理放合同服务 `ContractFileController`，三个端点各自挂独立按钮权限 `contract:plan:attachment:upload`、`contract:ledger:attachment:upload`、`contract:item:attachment:upload`（分别挂在采购计划/合同台账菜单下），MUST NOT 复用新增/编辑权限码。
- 公共解析/签名收口到 `ContractAttachments`（Support 类，不拆接口）；局部更新未传 `fileIds` 时必须保留原值，采购计划转合同时把计划附件带入合同。

## 预算科目业务归属口径（2026-11-27 落地）

- 预算科目是**财务域主数据**（`fin_budget_subject`），只在 finance 模块维护、用于预算额度归集；MUST NOT 出现在采购计划、合同台账的新增/转单表单、列表或详情，前置业务员不掌握该概念。
- 合同域 MUST NOT 暴露预算科目选择接口（含代理 Controller / finance-client Feign 方法 / finance internal 端点）；合同/计划 DTO、VO、Service 参数 MUST NOT 携带 budgetSubject。数据库历史列可保留兼容存量数据，实体字段注明为历史列且不再写入。
- 「设计稿字段全补」以业务归属正确为前提：设计稿里若出现归属错误的字段（如合同/计划里的预算科目），须同步修正设计稿后再补字段，不照抄错误字段。

## 合同/财务闭环字段完整性口径（2026-11-27 落地）

- 设计稿明确画出的字段 MUST 全部落到正式实现，不允许只做流程闭环而缺字段；开新单据/主表前先与设计稿 `polaris-design-web/src/views/FinanceDesign.vue`、`data/financeDemo.ts` 逐字段对照。
- 合同侧关键字段：`contract_header.plan_no`（采购计划转合同正向链路，转单时由 `createForPlan(..., planNo)` 带入）、`contract_purchase_plan.plan_date`（设计稿 `date`）、付款计划 `summary`/`voucher_no`/`file_ids`、验收单 `voucher_no`。
- 财务侧关键字段：`fin_expense` 的 `expense_type`/`reason`/`invoice_type`/`tax_rate`/`tax_amount`；`expense_reason` 与报销类型重复，新单不再写入，数据库列仅兼容存量数据。`fin_voucher` 的 `voucher_type`/`reviewer`/`poster`/`file_ids`；`fin_ar_ap` 的 `invoice_no`/`invoice_type`/`credit_days`/`due_date`；`fin_payment` 的 `stage`/`summary`/`due_date`/`source_doc_no`/`voucher_no`/`settle_method`/`file_ids`。
- 报销税额口径（2026-11-27 追加）：`tax_amount = amount × tax_rate / 100`，`tax_rate` 字典 value 为百分数（3 表示 3%），保留 2 位小数 HALF_UP；create/update 均由服务端计算，**MUST NOT 信任客户端传入的 taxAmount**，前端只做只读展示。
- 应收应付表单下拉口径（2026-11-27 追加）：账号/角色下拉必须来自主数据——合同域经 `PartnerApi.options(role, status, accountId)` 代理给财务 `/api/finance/partners/options`，调用方传当前 `AccountContext`，被调方用 `scopeCodeOf(accountId)` + `applyOrgScope(..., scopeCode)` 过滤（internal Feign 无 X-Account-Id，直接走当前上下文会 fail-closed 空集），role 匹配时同时包含 BOTH；合同号走 `ContractApi.options(accountId)`，会计科目走财务 `fin_subject`；账期档位新建 settings 字典 `finance.credit_period`（value 为天数 30/60/90/180/365），需加入 `PublicReferenceController.DICT_ALLOWED` 并随迁移重启 settings。
- 跨服回写链：财务域过账/建单后经 internal token 端点回写合同域 `POST /internal/contract/payment-plans/{id}/writeback`（源单号/凭证号/状态）与 `POST /internal/contract/acceptances/{no}/posted?voucherNo=`，合同域不自造 YS/FK/凭证号。
- 字典字段（凭证类型/报销类型/发票类型/税率/结算方式/付款阶段）存字典 value，CSV `remark` 仅承载映射；需前端跨应用读取的字典 MUST 加入 `PublicReferenceController.DICT_ALLOWED` 白名单。
- 设计稿自身只有对象描述、无真实表单/接口的部分（报销明细行、凭证借贷分录、薪资计提单、演示报表）不属"补字段"范围，需作为新功能单独立项。
- 合同付款计划（2026-11-27 追加）：付款阶段存 `contract.payment_stage` 字典 value，`stage`/`dueDate`/`amount` 均必填；`amount` 语义是「本期付款金额」，MUST <= 合同剩余金额（合同金额 - 已付 - 在途，与合同台账 `pendingAmount` 同口径），保存/更新均在服务端校验，更新时把当前未付未计划在途的计划金额加回避免自占。

## 层级编码查询口径（2026-11-27 落地）

- 合同物料分类/物料采用层级前缀编码：分类码 `01`/`0101`/`010101`（一级/二级/三级，逐层拼 2 位），物料码 = 三级分类码 + 6 位流水（如 `010101000001`）。这套编码的设计目的就是让"选中任意级分类查该分类及全部子孙分类下的数据"用一次前缀 like 完成。
- **按分类查物料 MUST 用编码前缀 `likeRight(code)`**（`ContractMaterial.code like '<分类码>%'`），MUST NOT 递归 `parent_id` 拼子孙 ID 再 `in` 查询——前缀 like 更直接、与编码设计一致，且天然支持任意层级。查前先 `selectById` 校验分类存在并 `assertCanAccessOrg(category.orgCode)`，查时照常 `applyOrgScope` 过滤物料 `org_code`。
- 前端 MUST 对任意层级分类（一/二/三级）都发查询请求，MUST NOT 写死"只有选中三级分类才查物料"——否则选中一/二级时列表永远空（2026-11-27 物料维护页曾因此显示"该分类及其下级分类下暂无物料"被要求整改）。
- 选料弹窗（`MaterialService.search`）因有"停用分类下物料不可选"约束，仍走 `withDescendantCategoryIds` + `enabledCategoryIds().retainAll` 口径，不改为纯前缀 like；该场景的父子子孙查询与"按分类查物料"是两套口径，勿混用。

## 资源索引

- `references/backend-guide.md`：后端完整规范与项目特有坑清单。
- `references/common-code-patterns.md`：从源码实证的公共模式与服务差异（含路径证据）。
- `references/new-feature-guide.md`：平台能力决策表、调用契约、后端交付自查。
- `references/inter-service-api.md`：跨服务接口提供规范（build client、internal 鉴权、精度坑）。
- `references/platform-capabilities.md`：后端视角的横向能力复用清单。
