# 平台横向能力实证（后端视角）

> 本文件列出历次迭代中落地并线上验证过的平台能力，后端接入时先复用、不要重写。
> 所有条目均为事实描述；实现细节以仓库代码为准，发现冲突时以代码为准并修正文档。

## 1. 统一文件服务（file-service :9011）

- 调用链：业务服务 → `file-client` 的 `FileApi`（`/internal/files/**`）→ `file-service`（库 `polaris_file`）。
- 数据模型：`file_object`（对象元数据）、`file_storage_policy`（存储策略）、`file_alias`（业务别名）。**业务表只存 `file_id`，不自建附件表、不另存 URL**。
- 访问控制：PUBLIC 直链；PRIVATE 走 HMAC 签名 URL，不绕开签名接口直出。
- 已实现能力（先复用）：附件（`file_id` 关联 + CMS 存量回填）、分享（提取码随机、有效期/次数限制）、回收站（标记删除与恢复）、配额（上传前校验，超限给业务错误码）、预览（图片/PDF）、水印（图片/PDF/SVG/DOCX/XLSX/PPTX；**下载链路也要加水印，不能只加在预览**）。
- 水印设置是配置项且必须有真实引用点；新增菜单/按钮仍需权限码四联动。

## 2. 统一审计（audit-server :9012）

- 链路：网关 `JwtAuthFilter` → `AuditReporter`（WebClient 异步）全量兜底，含 GET（只采 query 串，脱敏后写 `requestParams`，**不采 JSON body**）；body 详情由业务 AOP 写。
- `audit_event`（业务审计）+ `system_log`（由 common Logback Appender 采集 WARN/ERROR）；audit-server 自身关闭采集（`polaris.system-log.enabled=false`）防递归。
- 全局设置由 `AuditSettingService` 提供：采集开关、截断阈值、`queryParams` 等，**每项必须有真实代码引用，没有实际功能的配置项要去掉**。
- 合规下限：`LOGIN` / `SENSITIVE` 必留；登录事件记**尝试登录的用户名**（失败也记）；有主体记 USER，无主体内部调用记 SYSTEM。
- 支持 CSV 导出，导出行为强制留痕（手工记一条 SENSITIVE），不提供关闭开关；导出权限码 `audit:events:export` 四联动。
- 审计范围：登录才能做的动作全审计；匿名 `/public/**` 不在范围内。审计上报失败只 warn，绝不回滚业务。
- 脱敏规则在 common `AuditDataMasker` 与 audit-client 两份，改一处必须同步另一处。

## 3. SLA 阈值

- 审批 SLA 不写死在代码常量或裸 JSON：阈值放 settings 埋点配置，由 `EmbedPointView.vue` 提供结构化编辑器并登记 code。
- 流程步骤的 `sla_hours` 在实例中用快照固化，改阈值不影响历史实例。
- 新增埋点 key 未登记结构化编辑器时页面只读，不能绕开页面直接改库。

## 4. 数据权限现状（不要照抄缺口）

- 已接入：common 的 `DataScope` / `DataScopeHelper` / `ScopeCodeCache`，EHR / CMS 的 `applyOrgScope`。
- 未接入：forum / job / settings / portal / openapi。
- 已知缺口：CMS 的 applyOrgScope 存在 fail-open、薪资未脱敏；新功能不要复用这些缺口，**空范围一律返回空集**。

## 5. 应用登记与正式名称

- 应用中心签码走 `POST /api/auth/oauth/authorize`，`redirectUri` 必须是 account 中注册的完整地址，不能改成首页。
- 正式名称取自 `sso_application.name`：sso 北极星钥、audit 北极星眼、file 北极星云、openapi 北极星桥、cms 北极星源、ehr 北极星宿、settings 北极星典、job 北极星轨、forum 北极星空、forum-admin 北极星核、portal 北极星门。
- 端口：网关 9080、auth 9001、account 9002、cms 9004、portal 9005、ehr 9006、settings 9007、job 9008、forum 9009、openapi 9010、file 9011、audit 9012；`blog`（9003）已下线迁出，不要恢复。

## 6. 不存在的后端能力（不要假设存在）

全局搜索、短信适配器、多租户、`@OperateLog` AOP、WebSocket/SSE 服务端推送、独立审批流程引擎（业务侧统一走 settings.approval）。需要时用现有机制实现或先确认需求，不要引入新依赖。
