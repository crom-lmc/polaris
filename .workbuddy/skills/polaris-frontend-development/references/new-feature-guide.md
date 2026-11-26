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
| 埋点配置（阈值/开关） | 由后端接口随业务数据返回，前端不直接读 settings 内部接口；settings 侧**每个**埋点都要在 `EmbedPointView.vue` 登记结构化表单，禁止裸 JSON 编辑/只读兜底 |
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

### 3.0 页面留白：header 搜索栏与卡片之间要有间距

- **MUST** `<section class="ft-head">` 内的标题、筛选控件（下拉/日期框/搜索框/按钮）之间留可见间距，且 `ft-head` 与下方内容卡片之间留间距（约 12px）。全局 `.ft-head` 只有 `flex:0 0 auto`，本身不提供任何间距，靠各页面自己写。
- 统一写法：`ft-head` 加 `.toolbar` 类并定义 `.toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; flex-wrap:wrap; }`；标题与筛选组之间用 `<span class="head-divider" />`（1px 竖线）或 `margin-left` 分隔。**MUST NOT** 把标题和一堆 `a-space` 控件直接塞进无样式的 `ft-head`（会全部贴在一起，meeting 使用率页曾因此返工）。
- 图表/卡片页同理：`ft-head.toolbar` + `margin-bottom:12px` + 卡片，不要让筛选栏底边贴住卡片顶边。

### 3.1 列表页：分页固定 + 操作列固定 + 表头对齐

1. **分页固定在右下角**：用 `<section class="ft-scroll ft-bottom">` 包住 `a-table`，分页 `sticky bottom:0`，z-index 5（高于 sticky 操作列的 3）。

2. **空数据铺满高度（必须与空态插槽同时落地）**：在 `a-table` 上绑 `:class="{ 'tbl-empty-mode': !loading && !list.length }"`，`#emptyText` 用 `<div class="tbl-empty"><a-empty v-if="!loading" description="暂无数据" /></div>`；并在本应用 `global.css`（或页面 scoped `:deep()`）定义 `.tbl-empty-mode` 表格链 + `.tbl-empty`，否则空态只占一行、下方大片留白。**只写插槽不绑触发类/不定义样式是最常见漏项**（meeting 会议室维护页曾因此返工）。

```vue
<section class="ft-scroll ft-bottom">
  <a-table :columns="columns" :data-source="list" :pagination="false"
           :class="{ 'tbl-empty-mode': !loading && !list.length }">
    <template #bodyCell="{ column, record }"> ... </template>
    <template #emptyText>
      <div class="tbl-empty"><a-empty v-if="!loading" description="暂无数据" /></div>
    </template>
  </a-table>
</section>
```

```css
/* global.css（全局版）；若在 <style scoped> 用 :deep() 包裹内层选择器 */
.tbl-empty-mode .ant-table-container,
.tbl-empty-mode .ant-table-content { flex: 1 0 auto; display: flex; flex-direction: column; }
.tbl-empty-mode .ant-table-content > table { height: 100%; }
.tbl-empty-mode .ant-table-placeholder > td { height: 100%; padding: 0; border-bottom: none; }
.tbl-empty { height: 100%; min-height: 240px; display: flex; align-items: center; justify-content: center; }
```

> 有分页的页面用 `ft-bottom`（分页条 sticky 贴底）；无分页页（`pagination="false"`）同样要铺满空态，只是不显示分页条。

> **空态不出现横向滚动条**：固定写 `:scroll="{ x: 1080 }"` 会在零数据时仍渲染横向滚动条。MUST 按数据条件绑定 `:scroll="list.length ? { x: 1080 } : undefined"`，并在空态 CSS 兜底 `.tbl-empty-mode .ant-table-content { overflow-x: hidden }`；MUST NOT 让空表出现横向滚动条。

3. **操作列固定在右侧且左对齐不换行**：给表格加 `class="ft-table"`，由全局 CSS 把最后一列 `position:sticky; right:0; z-index:3` 固定；**不要用 antd 的 `fixed:'right'`**（它会在表格内部再开一层横向滚动，与 `ft-scroll` 整页滚动冲突；审计 `EventsView.vue` 是历史例外）。按钮左对齐不换行靠 `.ft-table .ant-btn-link` 的 padding 规则，多个按钮用间距而非挤一行。
4. **表头与列文字对齐**：`align` 必须和实际内容一致——数字/金额 `align:'right'`、状态/标记 `align:'center'`、文本默认左；**表头和数据不能一个居中一个左对齐**；带排序的列用同一 `align`。

```ts
const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '金额', dataIndex: 'amount', key: 'amount', align: 'right' },
  { title: '状态', key: 'status', width: 90, align: 'center' },
  { title: '操作', key: 'operation', width: 110 }   // 固定交给 .ft-table CSS，不写 fixed:'right'
]
```

5. **长文本列：列宽内省略显示 + 悬浮浮层展示完整内容**（对应"显示 ...、鼠标滑过用 pop 展示全文"的口径）：
   - 用全局 `.cell-ellipsis`（`_shared/styles/responsive.css`：`display:block; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap`）让文本在列宽内以省略号收尾；外层包 `<a-tooltip :title="完整值">`，鼠标悬浮时以浮层完整展示。
   - 标准写法：`<a-tooltip :title="record.xxx"><span class="cell-ellipsis">{{ record.xxx }}</span></a-tooltip>`；空值用 `v-if` + `v-else` 显示 `-`。
   - tooltip 的 `title` 必须绑定**被省略的同一字段**，不要显示 A 字段却悬浮展示 B 字段。
   - 禁止 JS `slice()` 硬截断长文本；禁止用原生 `title` 属性代替 `a-tooltip`（样式/延迟/移动端表现不一致）。flex 行内补 `flex:1 1 auto; min-width:0` 否则省略号不生效。
   - 堆栈、入参/响应等超长内容用"行内省略 + 展开行 `<pre>` 看全文"双层策略。
6. 弹窗改抽屉、确认框 `centered:true`、抽屉内表格用 `.drawer-body-flex`（分页贴底、空态铺满）。label 与 hint 之间用 `&nbsp;`。禁用按钮配合 `a-tooltip` 说明原因。
7. **查询类日期范围默认最近一周**：搜索/筛选/统计用途的 `a-range-picker`（含弹窗/抽屉内统计范围）初始值与重置值都设为「含今天的最近 7 个自然日」——`[dayjs().subtract(6,'day').startOf('day'), dayjs()]`；字符串口径用 `dayjs().subtract(6,'day').format('YYYY-MM-DD')` ~ `dayjs().format('YYYY-MM-DD')`。首次进入即有数据，不要留 `null`/空数组/仅今天让用户无结果可看。仅表单录入类（请假申请、教育/工作经历、合同）与按月统计 `picker="month"` 可保留自定义默认或空值。已有参考：meeting `MyBookingsView.vue`、audit `EventsView.vue`、openapi `StatView.vue`/`LogView.vue`/`DashboardView.vue`、file `OverviewView.vue` 趋势抽屉、sso `ApprovalListView.vue` 运营统计、ehr `HrLedgerView.vue` 台账。

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
- [ ] 本应用 `public/favicon.svg` 与应用中心 `icons/<code>.svg` 图形/配色一致，Nginx 对 `favicon.svg` 禁缓存；应用中心卡片名从 `/api/accounts/me/apps` 动态取，不写死 `ALL_APPS.name`（meeting 改名后曾仍显示旧名）
- [ ] 表格 `ft-scroll ft-bottom` 分页固定右下；`.ft-table` sticky 固定操作列，左对齐不换行；页签角标与列表共用筛选条件并同步刷新（范围内无数据时角标为 0）；空表无横向滚动条（`:scroll` 按数据条数条件绑定）
- [ ] 列表默认每页 20 条并开 `showSizeChanger`（不写死 5），翻页/换 size 走同一 `@change`，换 size 回到第 1 页（meeting 爽约记录页曾写死 5）
- [ ] 表头 `align` 与列内容一致，无错位
- [ ] 状态/枚举比较与提交值同后端常量、字典 value 一致，未在前端另造编码
- [ ] 树形/父子层级表格的「新增/编辑/删除」MUST 按行类型分流到各自的接口与表单（如楼层行走 `updateFloor(floorId)`、大楼行走 `updateBuilding(buildingId)`），MUST NOT 共用父级入口导致 ID 串用（meeting 位置维护曾把楼层 ID 传 `updateBuilding` 报 8302）
- [ ] 长文本省略 + tooltip，无 JS 硬截断；label 与 hint 用 `&nbsp;`
- [ ] 后端存逗号分隔 String、提交 DTO 是 List 的字段（如会议室 `fileIds`），TS 类型写 `string[] | string`，回填/提交统一归一为数组；MUST NOT 用 `record.xxx || []` 直接回填（字符串原样提交会让 Jackson 反序列化 ArrayList 失败报「系统异常」）
- [ ] 表单字段多、一屏放不下（含上传/多段列表/时间轴）时用 `a-drawer`（placement=right + #footer + body 留底部 padding），不用 `a-modal`；简单确认/详情仍可用 modal
- [ ] 时段选择/时间轴 MUST 用与新建预定一致的整点标尺（两位小时 `00`~`23`，每个标签跨两个 30 分钟格居中，格内不再塞时间文字）；MUST NOT 把 `00:00` 塞进 15px 的格子里被截成 `00:0`（meeting 修改预定弹窗曾因此返工）
- [ ] 移动端 48 格时间轴放不下时 MUST 拆成两行（`00:00–12:00`、`12:00–24:00`），每行 24 格并各自带半日小时标尺；MUST NOT 让固定宽度弹窗裁掉左侧字段或让用户横向拖动找格子（meeting 修改预定曾因此看不到开始时间下拉）
- [ ] 表单里的时间范围若允许修改，开始/结束 MUST 可独立编辑（30 分钟粒度下拉，结束含 24:00）并与时段轴双向同步；MUST NOT 只能点格子改结束、开始时间无法单独修改（meeting 修改预定曾如此）。时段轴统一两点选择：**第一格**直接作为新开始，**第二格以其起点**作为结束（点 18:00 格 → 结束 18:00），PC/移动端一致；第二格只作边界、不计入区间，占用格也应可点（区间内部占用仍要拦截）；MUST NOT 沿用原区间只延长结束或点内部格只改起点（meeting 修改预定曾返工两次）
- [ ] 可搜索下拉（邀请参会人等）MUST 空关键词也有默认列表（如 `searchEmployees('')`），已选项置顶合并；MUST NOT 只有输入后才出数据或只回显已选 ID 造成「暂无数据」（meeting 修改预定曾如此）
- [ ] 查询/筛选/统计日期范围默认最近 7 天（含今天），重置同样回到最近 7 天；表单录入类与月份统计除外；单日期统计过滤默认今天且不可选过去日期、最大可选日期来自配置（meeting 概览曾因此统计为 0）
- [ ] 查询日期选择器允许清空（不加 `:allow-clear="false"`），清空即「不限日期」需前后端同时生效：前端传空 from/to，后端不得兜底回最近 7 天（meeting 爽约记录页曾前后端一起踩坑）
- [ ] PC / Pad 下验证过，无横向溢出，侧栏抽屉正常
- [ ] 浅色/深色两套主题都验证过，颜色走 CSS 变量
- [ ] SSO 回调用注册的完整 `redirect_uri`，state 与 `expired=1` 分支保留
- [ ] 监听器在 `onBeforeUnmount` 成对清理
- [ ] `vue-tsc --noEmit` + `vite build` 通过；改 `_shared/` 时 11 个应用全量验证
- [ ] 站点 Nginx 入口 HTML 禁缓存、`/assets/` 长缓存 immutable；发版后旧入口浏览器需硬刷新一次
- [ ] 增量发布静态资源未用 `rsync --delete` 删除旧 hash 资源，旧资源保留至少 7 天再按 mtime 清理
