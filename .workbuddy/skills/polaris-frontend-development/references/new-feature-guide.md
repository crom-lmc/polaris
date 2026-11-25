# 新业务功能开发规范（前端：能力读取 + UI 硬规范）

> 适用范围：新增一个业务页面（列表、表单、详情）时必须满足的平台能力读取口径与 UI 规范。
> 复杂度不在 CRUD，而在于**该接的平台能力一项都不能漏**，以及**UI 必须与全平台一致**。
> 组件与工程细节见 [前端规范](frontend-guide.md)，从代码实证的公共契约见 [common-ui-contracts.md](common-ui-contracts.md)。

---

## 1. 平台能力的前端读取口径

所有配置类数据只读 settings，不写死在前端常量。各能力的配置页面在 settings（北极星典）。

| 需求 | 前端读取方式 |
|---|---|
| 选项型数据（下拉/单选） | `GET /api/settings/public/dicts/<typeCode>`，匿名白名单无需改网关；类型按 `域.业务` 命名 |
| 埋点配置（阈值/开关） | 由后端接口随业务数据返回，前端不直接读 settings 内部接口 |
| 单号/审批/消息 | 前端只展示结果与发起动作，配置与调用全在后端 |

### 1.1 读取字典（注意本应用解包口径）

```ts
// 解包口径 A（多数应用）：request.get 直接返回 data
const options = await request.get<never, DictOption[]>(`/api/settings/public/dicts/${typeCode}`)
// 解包口径 B（sso / audit）：返回 { data: T }
const options = (await request.get<never, { data: DictOption[] }>(...)).data
```

跨应用复制 API 层必错，动手前先确认本应用是哪一种。

---

## 2. 权限（前端）

- 菜单可见性用路由 `meta.perm` + 布局菜单 `v-if="store.can(...)"`；job 应用用 `hasPerm()`。
- 按钮显隐用同一权限码字符串，与后端 `@Permit` 完全一致；**不要新建 `v-perm` 指令**（全仓无实现）。
- sso 的审批中心、消息中心**不挂 `meta.perm`**，否则普通员工进不去。
- 数据权限由服务端注入，前端隐藏不算权限控制。

---

## 3. UI 硬规范（每个新页面都要满足）

以下不是"建议"，是列表页/表单页交付前逐条自查的项。

### 3.1 列表页：分页固定 + 操作列固定 + 表头对齐

1. **分页固定在右下角**：用 `<section class="ft-scroll ft-bottom">` 包住 `a-table`，分页 `sticky bottom:0`，z-index 5（高于 sticky 操作列的 3）。空数据 `.tbl-empty` 铺满内容区。

```vue
<section class="ft-scroll ft-bottom">
  <a-table :columns="columns" :data-source="list" :pagination="false">
    <template #bodyCell="{ column, record }"> ... </template>
  </a-table>
</section>
```

2. **操作列固定在右侧且左对齐不换行**：给表格加 `class="ft-table"`，由全局 CSS 把最后一列 `position:sticky; right:0; z-index:3` 固定；**不要用 antd 的 `fixed:'right'`**（它会在表格内部再开一层横向滚动，与 `ft-scroll` 整页滚动冲突；审计 `EventsView.vue` 是历史例外）。按钮左对齐不换行靠 `.ft-table .ant-btn-link` 的 padding 规则，多个按钮用间距而非挤一行。
3. **表头与列文字对齐**：`align` 必须和实际内容一致——数字/金额 `align:'right'`、状态/标记 `align:'center'`、文本默认左；**表头和数据不能一个居中一个左对齐**；带排序的列用同一 `align`。

```ts
const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '金额', dataIndex: 'amount', key: 'amount', align: 'right' },
  { title: '状态', key: 'status', width: 90, align: 'center' },
  { title: '操作', key: 'operation', width: 110 }   // 固定交给 .ft-table CSS，不写 fixed:'right'
]
```

4. **长文本列：列宽内省略显示 + 悬浮浮层展示完整内容**（对应"显示 ...、鼠标滑过用 pop 展示全文"的口径）：
   - 用全局 `.cell-ellipsis`（`_shared/styles/responsive.css`：`display:block; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap`）让文本在列宽内以省略号收尾；外层包 `<a-tooltip :title="完整值">`，鼠标悬浮时以浮层完整展示。
   - 标准写法：`<a-tooltip :title="record.xxx"><span class="cell-ellipsis">{{ record.xxx }}</span></a-tooltip>`；空值用 `v-if` + `v-else` 显示 `-`。
   - tooltip 的 `title` 必须绑定**被省略的同一字段**，不要显示 A 字段却悬浮展示 B 字段。
   - 禁止 JS `slice()` 硬截断长文本；禁止用原生 `title` 属性代替 `a-tooltip`（样式/延迟/移动端表现不一致）。flex 行内补 `flex:1 1 auto; min-width:0` 否则省略号不生效。
   - 堆栈、入参/响应等超长内容用"行内省略 + 展开行 `<pre>` 看全文"双层策略。
5. 弹窗改抽屉、确认框 `centered:true`、抽屉内表格用 `.drawer-body-flex`（分页贴底、空态铺满）。label 与 hint 之间用 `&nbsp;`。禁用按钮配合 `a-tooltip` 说明原因。

### 3.2 响应式：至少兼容 PC / Pad

- 新页面至少在 PC（≥1024px）与 Pad（768~1024px）可用，手机端按应用定位尽量兼容。
- 后台应用在自身 CSS **之后**引入 `@shared/styles/responsive.css`，布局用 `useResponsiveCollapsed()`，侧栏窄屏变抽屉、点菜单自动收起。
- 表格在 Pad 宽度横向滚动（操作列仍固定），网格/卡片两列降级，弹层宽度 `min(原宽度, calc(100vw - 24px))`。
- 不写死 `width: 1200px`，用百分比 / flex / grid 自适应。

### 3.3 新应用必须支持白天/黑夜模式

- 颜色全部走 CSS 变量（`:root` 与 `:root[data-theme='dark']` 两套都定义），不写死色值。
- 接入 `src/stores/theme.ts` 模式：共享 cookie `polaris_theme` + 本应用 localStorage + `prefers-color-scheme`，`index.html` 内联脚本首屏前设 `data-theme`，切换组件用 `ThemeToggleIcon.vue`。
- AntD 用 `a-config-provider` + `antdTheme.darkAlgorithm/defaultAlgorithm`；共享组件的 `--primary` 用 `var(--primary, #2f54eb)` 兜底。
- 交付前浅色、深色两套都过一遍：表格、弹层、卡片、输入框、空态、图标都要可读。

---

## 4. 前端交付自查清单

- [ ] 请求集中在 `src/api/`，确认本应用解包口径、401 行为、token 键名
- [ ] 19 位雪花 ID 用字符串或本应用已有的 `quoteLongIntegers()` 转换，不用 number 承载
- [ ] 菜单/按钮权限码与后端一致，未新建 `v-perm`
- [ ] 表格 `ft-scroll ft-bottom` 分页固定右下；`.ft-table` sticky 固定操作列，左对齐不换行
- [ ] 表头 `align` 与列内容一致，无错位
- [ ] 长文本省略 + tooltip，无 JS 硬截断；label 与 hint 用 `&nbsp;`
- [ ] PC / Pad 下验证过，无横向溢出，侧栏抽屉正常
- [ ] 浅色/深色两套主题都验证过，颜色走 CSS 变量
- [ ] SSO 回调用注册的完整 `redirect_uri`，state 与 `expired=1` 分支保留
- [ ] 监听器在 `onBeforeUnmount` 成对清理
- [ ] `vue-tsc --noEmit` + `vite build` 通过；改 `_shared/` 时 11 个应用全量验证
