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
3. **做决策**：新功能按 `../../rules/backend-02-L2-platform.md` 决策表逐项判断是否接入单号/审批/消息/埋点/字典/主数据；权限码按四联动补齐；新增配置必须有真实引用点；**新建表涉及组织归属 MUST 预留 `org_code`（orgCode 口径，不是 `org_id`），见 `references/platform-capabilities.md` 第 4 节**。
4. **实现**：按本服务既有分层与风格写代码；写前确认响应壳、app 域口径、Wrapper 条件、SQL 幂等策略、审计留痕方式。
5. **自检**：全仓 grep 改动符号无漏改；逐条检查 L1（红线）与 L2（后端/平台）。
6. **验证**：`mvn -o -q -pl <模块> -am -DskipTests compile` 通过；失败先修复再进入下一步。
7. **收尾**：在仓库外 `F:\code\迭代文档\polaris-server\update.md` 顶部追加记录；最终回复列明需重启应用与未提交/未部署状态；未经明确指令不提交不部署。

## 资源索引

- `references/backend-guide.md`：后端完整规范与项目特有坑清单。
- `references/common-code-patterns.md`：从源码实证的公共模式与服务差异（含路径证据）。
- `references/new-feature-guide.md`：平台能力决策表、调用契约、后端交付自查。
- `references/inter-service-api.md`：跨服务接口提供规范（build client、internal 鉴权、精度坑）。
- `references/platform-capabilities.md`：后端视角的横向能力复用清单。
