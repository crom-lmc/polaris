# 新业务功能开发规范（后端：平台能力接入）

> 适用范围：后端新增一个业务功能（一张单据、一个流程、一个接口组）时必须接入的平台级能力。
> 复杂度不在 CRUD，而在于**该接的平台能力一项都不能漏**。
> 分层细节见 [后端规范](backend-guide.md)，从代码实证的公共模式见 [common-code-patterns.md](common-code-patterns.md)，已落地能力见 [platform-capabilities.md](platform-capabilities.md)。

---

## 1. 平台能力接入决策表

新增功能时逐项判断，**命中就要接，不能自己造轮子**。所有能力都在 `polaris-settings`（:9007），经 `settings-client` 的 Feign 接口调用，走 `/internal/**` + `X-Internal-Token`，不经网关。

| 需求特征 | 必须接入的能力 | settings 模块 / 表 | 调用契约 |
|---|---|---|---|
| 单据有**业务编号**（合同号/请假单号/发票号…） | 单号规则 | `coderule`（`cfg_code_rule`） | `CodeRuleApi.next(tableName)` → `GET /internal/code-rules/next`；**未配置返回空串，调用方必须兜底** |
| 流程需要**审批** | 审批引擎 | `approval` | `ApprovalApi`（start/handle/Delegate…），`/internal/approval/**`；业务侧只做单据 + 回调，**回调必须幂等** |
| 需要**发消息/待办/邮件** | 统一通知 | `notice` | `NoticeApi.send / sendBatch / bizHandled`，`POST /internal/notice/send` 等；**不要用 forum.notification / ehr.announcement / openapi.outbound 冒充通知通道** |
| 有**可配置的参数/阈值/开关** | 埋点配置 | `embed`（key → JSON 值） | `EmbedPointApi.get(code) / list()`；**data 可能为 null，调用方必须兜底** |
| 有**少量选项型数据**（下拉/单选，改动不频繁） | 数据字典 | `dict`（`cfg_dict_type` / `cfg_dict_item`） | `DictApi`；**新建类型按 `域.业务` 命名**（如 `forum.gift_category`） |
| 有**关键实体数据**（银行、学历、职位、请假类型、亲属关系、离职原因） | 业务主数据 | `bizdata` | 对应 `*Api`，`/internal/biz/**` |

**接入原则**：

1. 判断顺序：先问"是否已有字典/主数据能复用"，再决定是否新建。宁可复用，不要每个应用各建一份。
2. **字典 vs 主数据**：选项型、无额外字段 → 字典；有独立字段、需单独维护或关联 → 主数据（真表 + 注册到类别，当前固定六类）。
3. 新增埋点 key 必须在 `EmbedPointView.vue` 登记结构化编辑器；未登记的只读、不给 JSON 编辑入口，不能绕开页面改库。
4. 所有配置类数据从 settings 读，**不写死在服务本地表或代码常量**；新增/修改只改 settings 一处。

### 1.1 接入示例

```java
// 取单号：生成单据前调用，未配置时空串要兜底
String no = codeRuleApi.next("ehr_resign_apply").getData();
if (!StringUtils.hasText(no)) {
    no = fallbackNo();   // 自行兜底，不要直接把空串写进业务字段
}

// 读埋点配置：可能返回 null
R<EmbedPointDTO> point = embedPointApi.get("ehr.resign.maxLevel");
int maxLevel = point.getData() == null ? DEFAULT_MAX : parse(point.getData());

// 发消息：统一走 notice，不自己发邮件/站内信
NoticeSendDTO dto = NoticeSendDTO.builder()
        .ruleCode("notice_rule_code")   // 「消息配置」页的规则编码
        .accountIds(receiverIds)         // 收件人账号 ID 列表
        .bizType("ehr_resign")           // 业务类型
        .bizId(instanceId)               // 业务单据 ID
        .vars(Map.of("no", bizNo))       // 模板变量
        .build();
noticeApi.send(dto);
```

> 契约细节以 `polaris-settings-client/src/main/java/.../api/` 下各 Feign 接口为准；调用前读一遍对应 `*Api.java`。

---

## 2. 权限与数据范围（后端）

- 新增菜单和写操作都要有权限码，**四联动**：common `PermissionCodes`、account `PermissionConstants` 常量 + BUILTIN 定义、`deploy/sql/` 播种与授权 SQL、前端路由/按钮；**内置权限码不可删**。
- 每个写接口用 `@Permit` 声明，按本服务 Aspect 的 app 域口径写（audit 多值 `app[]`、file/forum 单值、其余注解无 `app()` 由 Aspect 硬编码）。
- 数据权限：服务端 `DataScope` / `applyOrgScope` 注入组织范围，**空范围返回空集（fail-closed）**，不要靠前端隐藏；不要照抄 CMS 的 fail-open 与薪资未脱敏缺口。
- 审批回调用 `instanceId` + 状态校验幂等；重复投递返回 200，真异常返回 5xx 才触发 settings 重试。
- 流程步骤的 `sla_hours` 在实例中用快照固化，改阈值不影响历史实例。

---

## 3. 后端交付自查清单

- [ ] 有业务编号 → 配了单号规则，调 `CodeRuleApi.next()` 且空串兜底
- [ ] 要审批 → 配了流程/场景，经 `ApprovalApi` 对接，回调幂等
- [ ] 要发消息 → 经 `NoticeApi` 发送，不是自建通知通道
- [ ] 有可配参数 → 建了埋点 key，读取有 null 兜底
- [ ] 有选项数据 → 复用或新建字典（按 `域.业务` 命名）
- [ ] 有关键实体 → 复用或新增主数据，不写死在本地表
- [ ] 所有配置类数据无写死、无空开关
- [ ] 权限码四联动齐，角色已授权，内置码未删
- [ ] 数据范围服务端注入，空范围返回空集
- [ ] `mvn -o -q -pl <模块> -am -DskipTests compile` 通过
- [ ] 迭代记录已写入仓库外 `F:\code\迭代文档\polaris-server\update.md`
