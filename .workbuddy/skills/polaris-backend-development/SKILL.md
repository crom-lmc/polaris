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

## 合同台账菜单拆分口径（2026-11-27 落地）

- 合同台账在 SSO 权限树拆为父菜单 `contract:ledger:list` + 二级菜单 `contract:ledger:purchase:list` / `contract:ledger:sales:list`（`PermissionCodes` 与 `PermissionConstants.CONTRACT_BUILTIN` 四处齐，迁移 `deploy/sql/53-contract-ledger-menu-split.sql` 立即补权限并授权 `CONTRACT_ADMIN`/`CONTRACT_USER`/`SUPER_ADMIN`）。合同列表/详情/状态接口 `@Permit` 仍用父码，MUST NOT 把子菜单码直接拿去替换接口权限；授权 MUST 同时含父子码，否则子菜单可见但接口 403。
- 类型筛选/标签只影响前端展示与查询参数，服务端列表已支持 `type=PURCHASE|SALES`，MUST NOT 为两个菜单复制接口或表。

## 合同状态写入收口口径（2026-11-27 落地，MUST）

- 合同状态 MUST 只能由状态机推进：`DRAFT→PENDING→(EFFECTIVE|DRAFT)→EXECUTING→COMPLETED`，中途可 `EFFECTIVE|EXECUTING→TERMINATED`；终态不可再流转。采购合同 `PENDING→EFFECTIVE|DRAFT` 只能由审批中心回调 `handleApprovalResult` 写入，MUST NOT 允许手工接口改；销售合同 PENDING→EFFECTIVE 保留手工审批通过按钮。
- **EXECUTING / COMPLETED 由业务动作自动推进，MUST NOT 提供手工按钮（2026-11-27 定稿）**：`MANUAL_TRANSITIONS` 中 EFFECTIVE/EXECUTING 只允许 →TERMINATED，移除 `EFFECTIVE→EXECUTING`、`EXECUTING→COMPLETED`。首次到货验收由 `ContractItemServiceImpl.accept()` 调 `contractService.advanceIfStatus(no,"EFFECTIVE","EXECUTING")` 推进；合同全部付款计划 PAID/POSTED 且清单全部验收完成时 `PaymentPlanServiceImpl.tryCompleteOnSettled()` 调 `advanceIfStatus(no,"EXECUTING","COMPLETED")` 推进。`advanceIfStatus` 用 `eq(no)+eq(status,expected)` 乐观锁保证幂等与并发安全，不走手工白名单。
- **验收完成用派生字段、不新增合同状态（2026-11-27 定稿）**：验收完成度 = 全部清单行 `deliveredQty >= qty`（`qty<=0` 视为完成），与付款门槛 `fullyAccepted` 同口径。`ContractVO.acceptanceDone` 由 `ContractServiceImpl.decorate()` 遍历清单时顺带计算（清单非空且全部行完成），列表操作列「验收」按钮据此隐藏；`ContractItemServiceImpl.accept()` 同步硬校验「整单已验收完成不再接受新验收」。MUST NOT 为「已验收」新增合同状态——状态机 `EXECUTING` 本就覆盖验收中→验收完→在途付款，付款结清才自动 `COMPLETED`；加中间态会引入新写入点并与自动完成冲突。
- **清单/验收/付款按状态设硬门槛（2026-11-27 定稿）**：清单增删改用 `ensureEditable`（仅 DRAFT，审批后金额锁定，变更走变更流程，MUST NOT 直接改清单）；验收 `accept()` 用 `ensureAcceptable`（仅 EFFECTIVE/EXECUTING，草稿/审批中/完成/终止禁止验收，旧 `ensureContractOpen` 只拦完成/终止且 accept 根本没调，属漏洞）；付款申请 `PaymentPlanServiceImpl.apply()` 校验合同状态 EFFECTIVE/EXECUTING 且全部清单行 `deliveredQty>=qty`（验收完成才付款），与埋点 `contract.acceptance.required-before-pay` 的「合格验收单」校验并存。埋点 MUST NOT 校验验收单 `status=POSTED`——`markPosted` 只在应付单过账后由财务域回写（且当前销售推应收路径只读不写），付款申请发生在过账之前，要求先过账是流程倒置且必然死锁（验收单永远 DRAFT）；验收完成由 `allAccepted` 保证。埋点查验收结果 MUST 兼容历史 `PASS/OK` 与中文「合格」。前端按钮显隐只是可见入口，服务端校验才是防线。
- 到货验收 `accept()` 的验收日期 MUST 必填：`AcceptRequest.date` 为空抛 `ACCEPTANCE_DATE_REQUIRED`（8430），MUST NOT 像旧逻辑那样 `date==null ? LocalDate.now() : date` 自动补当天（2026-11-27 用户定稿）。验收结果统一「合格/不合格」（PASS/FAIL 归一化入库），否则付款埋点查 `result='合格'` 永远命中 0。
- 合同主信息编辑 `update()` 与删除 `delete()` MUST 仅允许 `DRAFT`（2026-11-27 用户定稿）：非草稿抛 `CONTRACT_STATUS_INVALID`「仅草稿状态的合同可编辑/删除」。生效后清单金额锁定、合同是业务凭证，变更须走变更流程；前端按钮显隐只是可见入口，服务端校验才是防线。
- 新建/编辑保存接口 MUST NOT 接受请求体 `status` 覆盖：新建固定 `DRAFT`，编辑保持原状态；状态只能走 `POST /api/contract/contracts/{id}/status`。
- `changeStatus` MUST 校验当前状态 + 合同类型对应的目标白名单，非法迁移抛 `CONTRACT_STATUS_INVALID`；前端按钮集合只是可见入口，不是唯一防线。
- 提交审批（`target=PENDING`）MUST 校验合同清单非空（查 `ContractItem` 按 `contract_no` count），空清单抛 `CONTRACT_STATUS_INVALID`「请先维护合同清单，再提交审批」——合同金额由清单派生，空清单金额恒为 0，审批线与授信校验均失去意义。付款计划可后建（合同编辑页可先建 WAITING、付款计划页有独立发起申请入口），MUST NOT 作为提交审批的前置条件。
- **付款计划编辑/删除仅待申请（2026-11-27 定稿）**：`PaymentPlanService.update()`/`delete()` MUST 仅允许 `status=WAITING`（未发起审批），发起后（PENDING/APPROVED/POSTED/PAID/REJECTED）禁止编辑删除；`update()` MUST NOT 接受请求体 `status` 覆盖（状态是流程结果，编辑保持 WAITING）。Controller 暴露 `PUT /api/contract/payment-plans/{id}`（原只暴露 DELETE，Service 有 update 但无入口）。前端列表操作列「编辑 / 删除」仅 WAITING 显示，删除走 a-popconfirm，编辑复用申请抽屉（合同锁定、只保存不发起）。
- **付款计划待审批 MUST 是真审批（2026-11-27 定稿）**：`PaymentPlanServiceImpl.apply()` 置 PENDING 后 MUST 调 `ContractApprovalLauncher` 发起审批中心实例（bizType `contract.payment` / `contract.payment.senior`，升级线埋点 `contract.payment.approval-amount` 默认 50000），MUST NOT 只改状态却不建审批实例（否则无人可审、流程死锁）。apply 不加 `@Transactional`，先落库再发起；发起失败必须把状态退回申请前的值，避免卡在 PENDING 不可重试。终审回调按 bizType 分流，通过 PENDING→APPROVED、驳回/撤回/终止 PENDING→REJECTED，仅 PENDING 更新且不留 dataScope。APPROVED=已审批待付，对接应付/付款单 MUST 在审批通过之后进行。
- **审批发起 MUST 传申请人（2026-11-27 线上故障）**：任何 `ApprovalStartDTO` 发起前 MUST 设 `applicantId = AccountContext.getAccountId()`（非空时一并设 `applicantName = getUsername()`）；settings `ApprovalEngine.start()` 直接把 applicantId 写入 `appr_instance.applicant_id`（非空无默认值），漏传 → NULL → INSERT 失败 500 → launcher 转「审批中心不可用」。EHR/CMS 各域均如此；新增 bizType 一律照此。取不到登录人时抛明确业务错误，MUST NOT 让 NULL 落到审批中心。
- **自有业务单号 MUST 先在 settings 配单号规则（2026-11-27）**：业务侧 `BizNoService.next(tableName)` 按 `table_name` 查 `settings.cfg_code_rule`，查不到返回空 → `BizNoServiceImpl` fail-closed 抛 `BIZ_NO_EMPTY`。新增带单号的单据（如付款申请 `contract_payment_plan` 的 `FK-yyyy-XXXXXX`）必须在迁移里 `INSERT IGNORE` 对应规则（id 取未用段、prefix/date_format/expr_format 与现有同格式），否则保存直接失败；规则写入后 settings 单号规则页自动可见，无代码白名单。
- 采购合同提交时清单金额达到 `contract.purchase.approval-amount` 才发起审批并停在 `PENDING`；未达线 MUST 直接置 `EFFECTIVE`，不得让单据永远停在审批中。
- 采购计划转入的清单行以备注前缀「由采购计划 」标记来源（不新增表字段）：`ContractItemServiceImpl.delete()` MUST 对来源行拒绝删除（抛 `CONTRACT_STATUS_INVALID`「采购计划转入的清单行不允许删除」），`save()` 编辑已有来源行时 MUST 强制保留原备注前缀，避免用户抹掉备注绕过禁删；已验收行仍按原规则禁删。前端隐藏删除按钮只是可见入口，服务端校验才是防线。

## 合同主信息必填与往来单位角色口径（2026-11-27 落地）

- 新建合同（`POST /api/contract/contracts`）除备注外全部必填：合同名称、往来单位、所属项目、归属部门、负责人、签订/生效/到期日期；服务端 MUST 校验，前端校验不是唯一防线。
- 往来单位按合同类型校验角色：PURCHASE 只允许 SUPPLIER/BOTH，SALES 只允许 CUSTOMER/BOTH，不匹配拒绝保存（与前端弹窗过滤同口径）。
- 合同备注用独立字段 `note`（`contract_header.note varchar(200)`，迁移 50），MUST NOT 复用 `ContractSave.remark`——后者是「变更原因」只写入 `contract_change_log` 不落主表。局部更新未传 note 时保留原值，新建落空串；长度 >200 拒绝。

## 新建单据 org_code 服务端兜底口径（2026-11-27 落地，MUST）

- 所有带 `org_code` 的业务单据，服务端新建 MUST 落非空归属组织，MUST NOT 只信前端传值：查询统一 `applyOrgScope`（`org_code LIKE 'scopeCode%'`），空值必被过滤成空集（2026-11-27 采购计划新建后列表查不到即此根因——前端不传 orgCode、后端 `nvl(req.getOrgCode(),"")` 落空串）。
- 无父表可依赖的单据（采购计划等）MUST 在服务端按 `AccountContext` 从 EHR 员工→组织树反查当前登录人部门编码落 `org_code`，口径与 `myProfile` 一致；反查不到依次退回前端传值、账号数据范围根 `currentScopeCode()`，仍为空则拒绝保存或记录 warn，不允许静默落空串。
- 有父表/项目依赖的单据（合同→项目、清单/付款计划→合同头、物料→分类）沿用从父表带出 `org_code`，不必逐单反查；编辑时若原 `org_code` 非空 MUST 保持不变（编辑人不等于创建人时不漂移），只补历史空值。
- **主子表一体，子表查询不重复过滤（2026-11-27 落地，MUST）**：明细/清单/付款计划等子表归属 MUST 跟随主表，详情/列表查子表 MUST NOT 再单独 `applyOrgScope`——调用方已通过 `require(id)→assertCanAccessOrg` 或主表列表过滤完成校验，重复过滤会误伤历史空 `org_code` 子表（2026-11-27 采购计划 CGJH-2026-000006 的 3 条明细被过滤成 0，详情明细空、明细数 0、金额 0）。子表落库仍以主表 `org_code` 为准；历史空值用幂等 SQL 按主表回填。
- **采购计划转合同完整主信息（2026-11-27 落地，MUST）**：`PlanConvertRequest` 除 partnerCode/projectNo/dept/owner 外 MUST 接收 name/signedDate/effectiveDate/endDate/note/fileIds；`convert()` 以请求值优先、计划值兜底（合同名←计划名称、签订/生效日期←计划日期、到期日期←计划需求日期、附件←计划附件），`createForPlan` MUST 落完整字段，MUST NOT 固定 signedDate=now 而其余日期留空（转合同后编辑页日期为空即此根因）。转合同成功返回合同 VO 供前端切换编辑态，明细由后端复制计划清单，前端不重复写入。

## 当前登录人/部门默认带出口径（2026-11-27 落地）

- 所有「申请人/需求部门/归属部门」默认当前登录人及其部门时，MUST 由服务端按 `AccountContext.getAccountId()` 从 EHR 员工（`activeEmployees`）→ 组织树（`tree`）反查，提供 `GET /<域>/<资源>/my-profile` 端点（如 finance `/api/finance/expenses/my-profile`、contract `/api/contract/purchase-plans/my-profile`）。
- **MUST NOT 依赖前端用 `/api/auth/me` 的 `accountId` 本地匹配 EHR 员工**——雪花 ID 19 位在浏览器以 number 解析会丢精度，`String(accountId)` 匹配失败导致部门静默为空（2026-11-27 采购计划「需求部门」带不出来即此根因）。前端本地目录推导只作 EHR 不可用时的兜底。
- 服务端反查失败（账号上下文空 / EHR 无员工 / 员工无 orgId / 组织不存在）时返回空串字段并 `log.warn`，不抛异常拖垮表单；新建单据落库时以服务端反查值为准，前端传值仅兜底（审批按部门名称解析负责人，部门必须准确）。

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
