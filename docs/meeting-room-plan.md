# 会议室管理（meetingroom）功能方案 v0.5

> 状态：**v0.6 已按方案实现（本地代码完成，未提交/未部署）**。业务服务 `polaris-meeting`（:9013）与前端应用 `polaris-meeting-web`（:5187）已落地；生产需先执行 `deploy/sql/14-meeting-init.sql` 并注入 `SSO_MEETING_APP_SECRET`。
> 目标：作为「新增独立业务应用」的第一个模板，跑通后端服务 + 前端应用 + 网关 + SSO + 权限码全链路。

## 一、定位与边界

| 项 | 值 |
|:---|:---|
| 形态 | 独立应用（后端 9013 / 前端 5187 / 独立库 `polaris_meetingroom`） |
| 用户 | 全体员工（预定）+ 行政/管理员（维护） |
| 强制约束 | **移动端必须兼容**（手机端样式不能乱），浅色/深色双主题 |
| 本期不做 | 审批流（只留字段）、多租户 |

---

## 二、功能地图（五大板块）

### 0. 概览

| 功能 | 说明 |
|:---|:---|
| 月历 | 周一起始，每格显示当天会议数；数据来自预约表，统计口径为「本人创建 or 被邀请人」，翻月时按目标月份重新取数；点击日期联动下方日程 |
| 我的日程 | 当日/当周我的会议列表（含本人创建与被邀请，受邀条目标注邀请人），含房间、时段、状态；签到/取消/释放仅对本人创建的预约展示 |
| 统计卡 | 所选日期的全平台有效预约数（不含取消/释放）、待签到、进行中、可用会议室数；右上角日期过滤默认今天，可选范围 [今天, 今天 + `meeting.booking.max_advance_days`]，翻月/点日期与统计卡联动 |
| 快捷入口 | 「我要预定」按钮直达预定页 |
| **移动端** | 月历切换为紧凑周视图；日程列表转卡片流 |

### 1. 位置维护（**独立页面**：城市 → 大楼 → 楼层）

> **重要修正**：城市不是自建组织架构，直接复用平台已有的 **`settings` 字典 `ehr.job_city`**（西安/北京/广州/上海）。会议室应用**不另建城市表**，下拉选项就是字典 value。

单独一个「位置维护」页面，采用 **城市下拉选择 + 大楼/楼层平铺列表**（不用树）：选择城市后，列表展示该城市下的大楼及其楼层；**不含房间**，房间在「会议室维护」页单独维护。列表顶部显示"共 N 栋大楼 · N 个楼层 · N 间会议室"。

| 层级 | 数据来源 | 维护方式 | 可新增的下级 | 删除 / 停用约束 |
|:---|:---|:---|:---|:---|
| 城市（一级） | `settings` 字典 `ehr.job_city` | **只读**，走公开接口 `GET /api/settings/public/dicts/ehr.job_city`；本页不能增删改 | **只能新增大楼** | 无删除/停用入口 |
| 大楼（二级） | `meeting_building` | 归属城市（字典 value），增删改查 + 排序；状态取字典 `meeting.location_status` | **只能新增楼层** | 下方还有楼层时禁止删除；**有会议室时禁止删除和停用** |
| 楼层（三级） | `meeting_floor` | 归属大楼，楼层号、备注，增删改查 + 排序；状态取字典 `meeting.location_status` | **无**（房间在会议室维护页新增） | **有会议室时禁止删除和停用** |

- **新增严格按层级**：页头「＋ 新增大楼」带入当前选择的城市（只读，不能跨城市）；大楼行「＋楼层」带入该大楼（只读）；楼层无新增入口
- **新增表单固定项**：弹窗顶部展示层级归属面包屑（城市 › 大楼 › 新增项），其中**城市、所属大楼为只读固定值，不允许修改**，只需填写其余内容——新增楼层填楼层号/排序/状态/备注，新增大楼填名称/地址/排序/状态/备注；服务端同样忽略前端传来的上级字段，以 URL/上下文中的层级为准
- **已维护会议室的锁定**（服务端强制，不能只靠前端禁用）：大楼/楼层只要存在会议室，删除和停用按钮禁用并标注"已维护会议室，停用/删除已锁定"；会议室数从 `meeting_room` 实时统计（大楼为其所有楼层之和）
- 城市切换：下拉选择后列表与统计随之刷新；新增城市去 settings 字典维护，本页自动读到
- 编辑：点行的"编辑"弹出字段表单（名称、地址、排序、状态、备注），保存后列表刷新
- 移动端：表格转卡片（大楼卡片内嵌楼层子列表），锁定状态与提示同样展示，不出现横向滚动
- 权限码：位置维护复用 `meeting:room:edit`，不单独设码

### 2. 会议室维护（房间台账，**独立页面**）

房间台账 = 位置（从位置维护选择城市/大楼/楼层）+ 容量 + 设备清单 + 图片 + 状态，管理员在会议应用内维护。列表页**不再内嵌树**，改用城市/大楼/楼层三级筛选下拉定位。

| 字段组 | 字段 | 说明 |
|:---|:---|:---|
| 位置 | 城市 / 大楼 / 楼层 | 城市取 `ehr.job_city` 字典 value；大楼、楼层为本应用主数据 |
| 基本信息 | 房间名、房间号、备注 | 列表与预定下拉展示房间名 |
| **类型** | `room_type`（字典 `meeting.room_type`：多功能会议室 / 专用会议室 / 常规会议室 / 圆桌会议室） | 本期新增；用于房间列表筛选与预定选房展示，只存字典 value，不冗余存名称 |
| **容量** | `capacity_tier`（**档位，字典**）、`max_capacity`（硬性上限）、`comfort_capacity`（舒适人数，建议值） | 档位取 settings 字典 `meeting.room_capacity`（小型≤8 / 中型9-20 / 大型21-50 / 特大>50），用于列表筛选与展示；两个数字字段保留，因为预定时参会人数 **≤ 硬性上限** 才允许提交，超过舒适人数提示"建议换更大房间"但不阻断 |
| 座位布局（预留） | `layout`（字典 `meeting.seat_layout`：剧院式 / 圆桌式 / 董事会式 / 长桌式） | 不同布局的容量不同，本期只留字段、不实现换算；选项走字典，不在代码里硬编码；常规/专用会议室默认长桌式，多功能厅剧院式 |
| **设备清单** | 设备类型 + 数量 + 备注 | 设备类型取 settings 字典 `meeting.device`，会议应用只读下拉；每间房可配多条，例如 投影×1、视频会议×1、白板×2。**设备不维护状态**，只记录"有什么、几台" |
| 图片 | `file_ids`（多张） | 走 `FileApi`，业务表只存 `file_id`（红线：不自建附件表） |
| 状态规则 | 状态（**字典 `meeting.room_status`**：可用 / 维护 / 停用） | 可用=可预约；维护=临时检修不可预约、可快速切回；停用=长期下线不可预约。两者都不自动取消已有预定。**取消房间级开放时段配置，默认 24 小时可预约** |
| **预约门禁** | 仅 `status=可用` 的房间可选 | 预定下拉过滤维护/停用房间，服务端提交时二次校验房间状态，防前端绕过 |
| 审批预留 | `need_approval` | 本期不接 `ApprovalApi` |

**本应用使用的 settings 字典（全部只读，不在会议应用建表）**

| 字典 code | 选项 | 用途 |
|:---|:---|:---|
| `ehr.job_city` | 西安 / 北京 / 广州 / 上海 | 城市，平台已有且在公开白名单 |
| `meeting.room_capacity` | 小型（≤8）/ 中型（9-20）/ 大型（21-50）/ 特大（>50） | 容量档位，筛选与展示 |
| `meeting.room_type` | 多功能会议室 / 专用会议室 / 常规会议室 / 圆桌会议室 | 房间类型，列表筛选与预定选房展示（**新增**） |
| `meeting.room_status` | 可用 / 维护 / 停用 | 房间状态；维护与停用均不可预约 |
| `meeting.location_status` | 可用 / 停用 | 大楼 / 楼层状态，与房间状态拆开（**新增**） |
| `meeting.device` | 投影 / 视频会议 / 白板 / 电话 / 电视屏幕 / 麦克风 | 设备类型（无设备状态字典） |
| `meeting.seat_layout` | 剧院式 / 圆桌式 / 董事会式 / 长桌式 | 座位布局，预留字段（**新增**） |
| `meeting.booking_status` | 待开始 / 待签到 / 进行中 / 已签到 / 已完成 / 已取消 / 已释放 | 预定列表状态细筛与展示（**新增**），与四张卡片分组的映射见第 4 节 |

新增 4 个 `meeting.*` 字典需在 settings `data.sql` 播种（`cfg_dict_type` + `cfg_dict_item`，`built_in=1`、`status=1`）。会议服务通过 `polaris-settings-client` 的 `SettingsClient.dictBatch(...)`（内部 `/internal/dicts/**`，`X-Internal-Token`，60s 缓存）批量拉取，前端统一从会议服务 `/api/meetingroom/dicts/**` 取；**不改 settings 公开白名单**。城市 `ehr.job_city` 已在白名单，可继续直连公开接口作为兜底。

**下拉数据源总表（本应用所有下拉逐项定源）**

| 下拉 | 数据源 | 类型 |
|:---|:---|:---|
| 城市（位置维护 / 房间筛选 / 预定） | `ehr.job_city` | 字典 |
| 大楼 / 楼层（房间筛选、预定选房） | `meeting_building` / `meeting_floor` 查询接口 | 主数据 |
| 房间（预定弹窗） | `meeting_room`（`status=可用`）查询接口 | 主数据 |
| 容量档位 | `meeting.room_capacity` | 字典 |
| 房间类型（维护筛选、预定选房展示） | `meeting.room_type` | 字典（新增） |
| 房间状态 | `meeting.room_status` | 字典 |
| 大楼 / 楼层状态 | `meeting.location_status` | 字典（新增） |
| 设备类型（房间维护、预定筛选） | `meeting.device` | 字典 |
| 座位布局 | `meeting.seat_layout` | 字典（新增） |
| 预定单状态筛选 | `meeting.booking_status` | 字典（新增，仅展示/筛选） |
| 邀请参会人 | 员工搜索接口 | 人员主数据，非字典 |

原则：**选项型、无独立业务字段、无层级归属的下拉一律走 settings 字典**；有归属关系或独立字段的（大楼 / 楼层 / 房间 / 人）走接口，不塞进字典。预定单状态机流转仍用代码常量，`meeting.booking_status` 的 value 与常量一一对应，**只允许改 label / sort，不允许改 value**，否则状态机会错乱。

- 数字容量字段与字典档位并存：档位只管展示/筛选，人数校验仍用 `max_capacity`
- 房间状态「维护 / 停用」均不可预约，服务端提交时强制校验；大楼 / 楼层用独立的 `meeting.location_status`，不再靠前端从房间状态里过滤
- 新增/编辑房间时在同一表单里维护房间类型、容量档位与设备清单；支持按房间类型、设备类型、容量档位、房间状态筛选
- 房间增删改查 + 维护 / 停用（均不自动取消历史预定）；维护端全局可见，由功能权限 `meeting:room:edit` 控制，不套预定的创建人过滤

### 3. 会议室预定（**按当前人城市自动过滤**）

> **重要修正**：预定页默认只显示**当前登录人所在部门城市**的会议室（城市来自 EHR 组织节点，见第六节），而不是全部城市。用户可切换城市查看其它城市房间（需确认是否允许跨城预定）。

| 操作 | 规则 |
|:---|:---|
| 城市过滤 | **默认自动过滤**：当前人城市 = EHR 组织节点 city（沿 path 继承二级单位，算法复用 `LeaveCityPolicyService.cityOf`）；前端城市下拉走 settings 公开字典接口 |
| **时间轴** | 预定页按所选日期展示 **00:00–24:00 时间轴**：每行一间房间，已预约时段用色块标记（**我的=青色 / 他人=蓝色**，悬浮显示时段与「预约人预约的主题」，如 `08:00-08:30 王浩然预约的需求评审会议`）；维护 / 停用房间灰底、不可点选。时间轴数据取 `meeting_booking` 中状态 IN (PENDING, CHECKED_IN) 的重叠区间。**新建预定的时段选择也用同一套时间轴**：弹窗内按所选房间 / 日期渲染 48 个 30 分钟格，占用与已过时段直接着色且不可点，先点开始格、再点更晚的空闲格即完成选择，不再用两个起止时间下拉 |
| **时段规则** | **不允许跨天**：`start_time` 与 `end_time` 必须是同一自然日且 `end > start`；**结束可选到 `24:00`**，存储为次日 `00:00:00`、展示仍按当天 24:00，不算跨天；**预约步长 30 分钟**（起止时间的分钟只能是 `00` 或 `30`），**已过去的时段不可预约**（当天 `start_time >= 服务端当前时刻`，未来日期全天可选）；房间不再配置开放起止时间，默认 24 小时可预约 |
| **可预约日期范围** | 由 settings 埋点 `meeting.booking.max_advance_days` 控制：只能约「今天 ~ 今天+N 天」，**N=0 表示只能约当天**；前端限制日期选择器 + 服务端提交强校验（见第三章「可预约天数上限」） |
| 预定 | 选大楼→楼层→房间（已按城市过滤，**仅 `status=可用` 可选**，展示容量 + 设备清单）→ 选日期 → **在弹窗时间轴上点选空闲时段**（该房间当日占用、我的预约、已过去时段直接着色）→ 填主题 → **邀请参会人（多选员工，自动回填人数）**；**提交前冲突检测 + 房间状态复检（防绕过）+ 同一自然日校验 + 日期范围校验 + 校验房间城市与用户城市一致**（防越权跨城）+ **人数 ≤ `max_capacity`** |
| 邀请参会人 | 从现有员工选择器按姓名/账号搜索、多选（排除创建人本人）；选后自动回填 `attendee_count`（允许手动修正未受邀的现场参会人），提交写 `meeting_booking_attendee`，并按模板给每位被邀请人发站内信（可选邮件），见第三章「邀请参会人：通知与模板配置」 |
| 被邀请人可见 | 被邀请人通过消息链接进入会议详情；详情接口按 `creator_id` 或 `attendee.account_id` 放行。「我的预定」列表仍只返回本人创建（本期范围），是否增加「我被邀请的」列表待确认 |
| 修改 | 仅本人 + 未开始；改时段同样跑冲突检测；**新增/移除被邀请人时补发/关闭对应通知** |
| 签到 | 开始前 15 分钟开放签到，最迟开始后 15 分钟（未签到自动释放宽限期），支持按钮/二维码 |
| 延时 | 签到后可申请延长，检测后续时段是否空闲 |
| 释放 | 提前结束（释放后时段回池）/ 取消预定 |
| 我的预定 | 已拆到独立页面，见下方第 4 节 |

> **弹窗选时交互**：选中房间后，按该房间 + 所选日期调用时间轴同一个占用查询（`/api/meetingroom/rooms/{roomId}/schedule?date=`），把 24 小时切成 48 个 30 分钟格；已被预约（他人蓝色 / 我的青色）、已过去的格子直接着色且不可点，23:30–24:00 因“结束最晚 23:30”不可选。第一次点击设开始，第二次点击更晚的空闲格设结束，两次之间若有占用则拒绝并重新选起点；已选区间用绿色高亮并实时回填表单。日期或房间变化清空当前选择重绘。前端直观展示不替代服务端校验：冲突、过去时段、跨天、日期范围、房间状态仍在提交时强校验。

### 4. 我的预定（**独立页面**：四页签分组）

从「会议室预定」页拆出：预定页只保留城市筛选 + 房间时间轴 +「新建预定 / 埋点说明」入口；原截图中的筛选条与列表移到独立菜单「我的预定」（路由 `my-bookings`，权限 `meeting:booking:list`）。列表仍**只返回本人创建的预定**（服务端按 `creator_id` 过滤，见第四节），被邀请人仍只从消息链接看详情。

分组采用**横向页签**（对齐平台审批页的样式：选中项主题色文字 + 底部 2px 下划线 + 数量角标），不用大卡片；点击页签切换下方列表，默认选「已预约」：

| 页签 | 包含的单据状态 | 对应 `meeting.booking_status` value |
|:---|:---|:---|
| 已预约 | 待开始 / 待签到 | `PENDING` |
| 已开始 | 进行中 / 已签到 | `CHECKED_IN` |
| 已结束 | 已完成 | `COMPLETED` |
| **已取消** | 已取消 + **已释放（释放归入取消，不单列）** | `CANCELLED` / `RELEASED` |

- 页签角标数字按 `creator_id` + 状态分组统计，点击页签筛选下方列表；城市 / 主题 / 状态 / 日期筛选可叠加
- 状态列仍显示原始状态；**已取消页签中释放的单据展示为「已取消（释放）」**，避免误认为是两类；状态下拉保留「已释放」细项便于检索
- 操作按页签收敛：已预约可修改 / 签到 / 取消；已开始可延时 / 释放；已结束与已取消只读详情
- 分组映射用**代码常量**（`PENDING→已预约`、`CHECKED_IN→已开始`、`COMPLETED→已结束`、`CANCELLED/RELEASED→已取消`），不依赖字典 label；`meeting.booking_status` 只允许改 label / sort，value 变更会导致分组错乱
- 移动端：页签保持单行、在自身容器内横向滚动（不撑宽页面），列表转卡片流；每个按钮按第八节权限码单独隐藏

---

## 三、我补充的功能（按优先级）

| # | 功能 | 理由 | 优先级 |
|---|---|---|---|
| 1 | **时段冲突检测** | 同一房间同一时段不能被两人占用，这是预定系统的命门，没它系统不可用 | P0 |
| 2 | **只看本人预定** | 你明确要求；服务端按 `creator_id` 过滤，**不改平台数据权限结构** | P0 |
| 3 | **邀请参会人 + 消息通知** | 本期明确要求；选人、关联表、模板化站内信/邮件全部复用平台能力 | P0 |
| 4 | **维护状态不可预约** | 本期明确要求；房间状态加「维护」，前端过滤 + 服务端强校验，临时检修不放出时段 | P0 |
| 5 | **时间轴展示 + 不跨天 + 30 分钟步长** | 本期明确要求；24 小时时间轴着色已预约时段、过去时段灰显不可约，时段限制在同一自然日、按半小时粒度选择，房间默认全天可约 | P0 |
| 6 | **可预约天数上限（settings 埋点）** | 本期明确要求；`meeting.booking.max_advance_days`，N=0 只能约当天，前端限制 + 服务端强校验 | P0 |
| 7 | 预约规则配置 | 单次最长时长已落地（埋点，0=不限制）；每日预定上限本期不做——防止恶意占房（不设工作时间段，房间默认全天可约；提前天数已由第 6 项落地） | P1 |
| 8 | 未签到自动释放 | 开始后未签到自动释放，宽限期由埋点配置（**由平台 job 调度服务驱动**，见下方落地方式） | P1 |
| 9 | 会议开始前提醒 | 接 `settings.notice` 站内信，提前 10 分钟提醒（新增规则 + Job Handler） | P1 |
| 10 | 使用率统计 | 按房间/时段的占用率，为后续增减会议室提供依据 | P2 |
| 11 | 爽约记录 | 统计未签到释放次数，超过阈值限制预定（规则可配，已落地） | P2 |
| 12 | 审批流预留 | 房间可标记"需审批"，字段先留，本期不接 `ApprovalApi` | P2 |

> P0 必须进一期；P1 建议一期顺带做（成本不高、价值明显）；P2 二期再说。

### 预约规则配置化（settings 埋点，2026-11-26 补充落地）

> 三项均为 settings `cfg_embed_point`，会议服务经 `SettingsClient` 读取（60 秒缓存），前端从 `/api/meetingroom/config/booking` 取值，不直连 settings 内部接口。每项都在 settings `EmbedPointView.vue` 的 `EDITOR_BY_CODE` 登记结构化表单，生产用 `deploy/sql/18-meeting-booking-rules.sql` 播种。

| 埋点 code | value | 默认值 | 语义 / 消费点 |
|:---|:---|:---|:---|
| `meeting.booking.no_show_grace_minutes` | `{"graceMinutes":15}` | 15 分钟 | 签到截止与 job 自动释放宽限期共用；`0` 表示会议开始即释放；替换原 `application.yml` 写死值 |
| `meeting.booking.max_duration_minutes` | `{"maxMinutes":0}` | 0（不限制） | 新建 / 修改 / 延时统一在 `validateTime()` 校验单次时长；`0` 表示不限制 |
| `meeting.booking.no_show_restriction` | `{"windowDays":90,"threshold":3,"banDays":7}` | 90 天 / 3 次 / 7 天 | 按会议开始时间统计 `status=RELEASED AND release_reason=NO_SHOW`；达到阈值后自最近一次爽约的会议日期起禁约 `banDays` 天；`threshold` 或 `banDays` 为 `0` 表示禁用 |

- 禁约规则不新增存储表，直接按历史 NO_SHOW 记录推导；新建 / 修改预定时服务端强校验，历史单据不受影响。
- `config/booking` 同时返回当前账号的 `noShowCount` / `bannedUntil`，预定页展示规则与禁约提示；服务端仍是最终边界。
- job 扫描宽限期从埋点读取，按主键游标分批 + `status=PENDING` 条件更新保证幂等，释放事件以 `operatorType=SYSTEM` 显式上报审计（失败只告警）。

### 超时未签到释放：接入平台调度服务（**不自起定时任务**）

平台已有调度中心 `polaris-job`（:9008），实证是自研扫描内核（租约选主 + 1 秒扫描 + 乐观锁认领），**不是 Quartz / XXL-Job**；业务侧不写 `@Scheduled`，而是实现 `JobHandler` 契约，由中心按 `job_info` 配置 HTTP 派发到应用 `/internal/jobs/run`（带 `X-Job-Token` + `X-Internal-Token`）。照抄 `settings` 的 `ApprovalSlaJob` + `ApprovalOpsService.scanSla()`。

| 步骤 | 落地动作 |
|:---|:---|
| 1 | 会议服务 `pom.xml` 加 `polaris-job-client` 依赖（该 client 无 Feign，靠 `RestClient` 上报 Handler 清单，网关 WebFlux 兼容） |
| 2 | 会议服务 `application.yml` 配 `job.client.center-url`、`job.client.internal-token`、`job.executor.app-code=meeting`、`job.executor.token`，均复用 `${SSO_INTERNAL_TOKEN:}`（参照 settings 的写法） |
| 3 | 新增 `MeetingNoShowReleaseJob implements JobHandler`，`name()` 返回 `meetingNoShowRelease`，`execute(ctx)` 调应用内扫描 Service；扫描逻辑查 `status=待签到 AND start_time + 宽限期 < now`，**游标分批 + 条件更新保证幂等**（`update ... where status=待签到`），释放单写 `release_reason=NO_SHOW` 并调 `AuditApi` 留痕 |
| 4 | 中心 `polaris_job.job_info` 加一行：`app_code='meeting'`、`handler='meetingNoShowRelease'`、`trigger_type='FIXED_DELAY'`、`interval_sec=60`、`timeout_sec=300`、`block_strategy='DISCARD'`、`status=1`；中心 `job.apps` 补 `meeting: ${MEETING_URL:...}`，或靠 30s 心跳注册自动发现 |

- 宽限期默认 15 分钟，从预约规则配置读取（P1 规则配置），不写死在 JobHandler 里
- 释放动作在**会议服务本地库**完成，不需要 job-service Feign 回调，也不需要新增任何 Feign 接口
- 中心已有的重试、执行日志（`job_log`）、告警（`job_alarm`）、手动触发（`POST /api/jobs/{id}/run`）直接复用
- **不做**：会议服务里加 `@Scheduled`、在 job-service 写会议室业务、为这事新增跨服务 API
- 会议前提醒（P1 第 9 项）同理可再加一个 Handler，提醒与邀请通知都走 `settings.notice`（见下节），会议前提醒不做进本次范围

### 邀请参会人：通知与模板配置（本期 P0）

平台已有完整的通知模板能力，会议应用**只按规则 code 发送，不建通知表、不做模板管理页**。实证：`settings` 库 `cfg_notice_rule`（`code` / `title_tpl` / `content_tpl` / `channel` / `email` / `enabled`）+ `notice_message`，管理端 `/api/settings/notices/rules`（`notice:view` / `notice:manage`），服务间 `NoticeApi.send` / `sendBatch` / `bizHandled`（`/internal/notice/**`，`X-Internal-Token` fail-closed）。

| 场景 | 模板 code | 触发点 | 渠道 |
|:---|:---|:---|:---|
| 邀请参会人 | `meeting.booking.invite` | 预定创建 / 修改时新增被邀请人 | 站内消息（`channel=2`）+ 可选邮件（`email=1`） |
| 会议变更 / 取消（后续） | `meeting.booking.update` / `meeting.booking.cancel` | 时间地点变更、取消时通知并调 `bizHandled` 关待办 | P1/P2，本期只留 code 规划 |

**模板配置落点**：settings 应用 →「通知规则管理」页，新增 `app_code=meeting`、`module=meeting.booking` 的规则，填 `name`、`title_tpl`、`content_tpl`、`channel`、`email`、`enabled`；可由 settings `data.sql` 播种（需 `notice:manage`）。模板是单语文本，平台无 i18n 字段，本期不支持多语言。会议应用只需约定 code 与变量名，不依赖模板文字。

**模板变量**（调用方必须写入 `NoticeSendDTO.vars`；平台渲染只替换 `vars` 中的 `{key}`，未提供的占位符会被抹空——参考 `deploy/sql/08-fix-job-alarm-notice-task-name.sql` 的修复教训）：

| 变量 | 含义 |
|:---|:---|
| `{inviterName}` | 创建人姓名 |
| `{title}` | 会议主题 |
| `{roomName}` | 会议室名称 |
| `{location}` | 城市·大楼·楼层·房间号，如「西安·高新总部·12F·A12-03」 |
| `{startTime}` / `{endTime}` | 起止时间（如「11-25 09:00」） |
| `{bookingNo}` | 预定单号 |
| `{attendeeCount}` | 参会人数 |
| `{link}` | 会议详情链接（站内信跳转，同时写 DTO 的 `link` 字段） |

模板示例——标题：`会议邀请：{title}`；内容：`{inviterName} 邀请你参加「{title}」，时间 {startTime}–{endTime}，地点 {location}（{roomName}），预定单号 {bookingNo}。详情：{link}`。

**发送链路（最小对接）**：

1. 会议服务 `pom.xml` 加 `polaris-settings-client`；配 `settings.client.base-url`、`settings.client.internal-token`（复用 `SSO_INTERNAL_TOKEN`，令牌缺失 fail-closed 503）。
2. 预定事务提交后调 `NoticeApi.sendBatch(dto)`（多被邀请人）或 `send(dto)`：`ruleCode=meeting.booking.invite`、`accountIds`=被邀请人、全部变量写入 `vars`、`bizType=meeting_booking`、`bizId`/`bizNo`/`link` 一并填。**try/catch 隔离**，通知失败只记 `log.error`，不影响预约主流程（照抄 `InterviewService` 的调用方式）。
3. `sendBatch` 给每位被邀请人落一条 `notice_message`；`email=1` 且账号中心能取到邮箱、SMTP 已配才发邮件，否则只留站内信。
4. 取消预定 / 移除被邀请人时调 `noticeApi.bizHandled` 关掉未读待办；会议前提醒后续新增 `meeting.booking.remind` 规则 + Handler。
5. 创建人本人不重复邀请；重复选人由 `meeting_booking_attendee(booking_id, account_id, deleted)` 唯一索引兜底。

- 不做：会议应用自建通知表、自建模板配置页、自建邮件发送、把 DataScope 套到被邀请人上。

### 可预约天数上限：settings 埋点配置（本期 P0）

不再给会议应用单独做配置表或配置页，直接复用 settings 已有的**埋点配置**机制（`cfg_embed_point`：通用键值对，`code` 唯一、`value` 存 JSON、有变更历史、settings「埋点管理」页可维护，权限 `embed:view` / `embed:manage`）。已核实调用链：`EmbedPointApi` → `/internal/embed-points/{code}`（`X-Internal-Token` fail-closed）→ `SettingsClient.embedPoint(code)`，客户端 **60 秒缓存**；平台已有整数上限型埋点的实际用法（`forum.level.max-levels` → `{"maxLevel":20}`，见 `LevelService.maxLevel()`），照抄即可。

| 项 | 约定 |
|:---|:---|
| 埋点 code | `meeting.booking.max_advance_days`（`app_code=meeting`、`module=会议室预约`、`name=会议室最多可提前预约天数`） |
| value | `{"maxDays":7}`，整数；**0 = 只能预约当天**（`min=今天`、`max=今天`），默认值 7 |
| 维护入口 | settings 应用 →「埋点管理」页，改 value 即可；由 settings `data.sql` 播种初始值 7（幂等 `INSERT IGNORE`） |
| settings 前端必要改造 | `EmbedPointView.vue` 的数字型编辑器当前限制 `min=1`，须放开为 **`min=0`**（含提交校验），否则页面上维护不了 0 |
| 业务侧读取 | 会议服务引入 `polaris-settings-client`，注入 `SettingsClient`，按 key 读取并解析 `maxDays`；**校验 `maxDays >= 0`**（不能照抄 `LevelService` 的 `> 0`，否则 0 被吞），缺失/非法时兜底 7 并 `log.error` |
| 前端取值 | 会议服务暴露 `GET /api/meetingroom/config/booking` 返回 `maxDays`（前端不直连 settings internal API）；日期控件设 `min=today`、`max=today+maxDays`，时间轴日期与新建预定弹窗同口径 |
| 服务端强校验 | 新建/修改预定时重新读取 `maxDays`，校验 `DATE(start_time)` 落在 `[today, today+maxDays]`；`maxDays=0` 时只允许当天，前端绕过直接调接口也会被拒 |
| 生效时延 | settings 埋点改动后最多 60 秒（客户端缓存 TTL），不重启服务 |

边界口径：
- 「今天」与「当前时刻」都取**服务端时钟**（应用服务器时区，与 `start_time` 同口径），不是浏览器时间；跨零点以服务端为准，过去时段拦截同理。
- `maxDays` 是自然日偏移，不含时分；如 `maxDays=7` 且今天 11-25，则最晚可约 12-02 全天。
- 该上限只限制**新建 / 修改时段**；历史已存在预定不受影响，取消、签到、释放、延时不校验该值（延时仍要过冲突检测）。
- 只限制"最多几天后"，**过去日期同样拒绝**（`start_time < today`），当天已过去的时段也拒绝（见第七节），避免补录绕过。
- 不做：会议应用自建配置表、把 `maxDays` 做成字典（字典是 value→label 枚举，不适合数值上限）、前端硬编码天数。

---

## 四、数据范围：只查自己（**不改平台数据权限结构**）

你的要求：「不要修改现在数据权限的结构，查数据查自己的就可以了」。按此执行，且已核实平台代码：

> **核实结论**：平台 `common DataScope` / `DataScopeHelper` 的 `scopeCode` 是**组织编码前缀**（如 `XC00010001`，支持逗号多值），并没有 `SELF / ORG / DEPT / ALL` 这种枚举；`SELF` 在仓库里只是 `ResignApply.SOURCE_SELF` 单据来源常量。所以"只看自己"**不能也不该套 DataScope**——那套机制解决的是"按组织看哪些行"，跟"只看本人创建"是两个问题。

| 设计点 | 做法 |
|:---|:---|
| 平台改动 | **零**：不改 common `DataScope` / `DataScopeHelper` / `ScopeCodeCache`，不改 account 的 `data_scope_org_code`，不新增 ScopeCode，会议服务也不实现 `DataScope` 接口 |
| 业务表字段 | `meeting_booking` 存 `creator_id`（创建人 accountId）+ **预留 `org_code`（组织编码，按平台 orgCode 口径）**；本期只用 creator_id 过滤，org_code 不参与查询，但不省这个字段 |
| 查询过滤 | 「我的预定」列表与其详情查询在 Service 层统一拼 `eq(Booking::getCreatorId, AccountContext.getAccountId())`；被邀请人经消息链接看详情的例外见第三章 |
| 写操作校验 | 修改 / 签到 / 延时 / 释放前校验 `creator_id == 当前 accountId`，不符返回 403 |
| fail-closed | `AccountContext.getAccountId()` 为空时不返回全量，直接返回空集 / 拒绝 |
| 前端职责 | 不带创建人筛选参数给后端（防伪造）；页面文案写"仅本人预定"，不写 `scope=SELF` |
| 房间台账 | 公共主数据，由 `meeting:room:edit` 功能权限控制，与上面的数据范围无关 |

后期真要扩展"看本部门/本组织"，直接用预留的 `org_code` 在会议服务里拼过滤逻辑即可（与平台 `DataScopeHelper` 的 orgCode 前缀匹配口径一致），届时再评估是否接入平台 DataScope；本期平台代码零改动。

---

## 五、城市获取链路（关键实现决策）

已全仓核实，避免重复造轮子：

| 环节 | 结论 |
|:---|:---|
| 城市数据源 | `settings` 字典 `ehr.job_city`（`cfg_dict_type` code=`ehr.job_city`，app_code=`ehr`，选项 西安/北京/广州/上海） |
| 人员城市 | 来自 EHR 组织节点 `OrgNode.city`（字典 value）；**仅 depth=2 二级单位可配置，下级沿 path 继承**（`OrgService.resolveCityForVO` / `LeaveCityPolicyService.cityOf`） |
| 现有可用接口 | 前端城市下拉：`GET /api/settings/public/dicts/ehr.job_city`（`PublicReferenceController` 白名单已含，**现成可直接用**） |
| 缺失环节 | **没有**任何 HTTP 接口返回"当前登录人的城市"；`X-Account-*` 头、`/api/accounts/me`、`/api/ehr/me`、`/api/ehr/employees/me` 都不含城市 |
| 必须新增 | ① EHR internal 接口（如 `/internal/ehr/employees/{accountId}/city`），内部复用 `LeaveCityPolicyService.cityOf(Employee)`，**不重写继承算法**；② `ehr-client` 加对应 Feign 方法；③ 会议服务 `/api/meetingroom/me` 返回当前人城市 |
| 前端取数 | 城市下拉走 settings 公开字典；当前人城市从会议服务 `/me` 取，作为预定页默认过滤值 |
| 服务端强校验 | 预定提交时必须校验房间城市 == 当前人城市（防前端绕过），空城市按 fail-closed 返回空集 |

> 待用户确认：是否允许手动切换到其它城市预定跨城会议室？默认设计是**只允许本市**（过滤 + 提交校验），切换城市仅用于查看。

## 六、数据模型（初稿）

| 表 | 关键字段 |
|:---|:---|
| ~~`meeting_region` / `meeting_device` / `meeting_capacity_dict` 等~~ | **均不建表**：城市、容量档位、房间类型、房间状态、位置状态、设备类型、座位布局、预定状态筛选全部用 settings 字典（见第二节字典清单）；设备无状态字段 |
| `meeting_building` | id, **city**(字典 value，如"西安"), name, address, sort, **status**(字典 `meeting.location_status` value), deleted |
| `meeting_floor` | id, building_id, floor_no, **status**(字典 `meeting.location_status` value), remark, deleted |
| `meeting_room` | id, floor_id, name, room_no, **room_type**(字典 `meeting.room_type` value), **capacity_tier**(字典 value), **max_capacity**, **comfort_capacity**, **layout**(字典 `meeting.seat_layout` value), file_ids(json), **status**(字典 `meeting.room_status` value), need_approval, deleted（**无 open_start / open_end**） |
| `meeting_room_device` | id, room_id, **device_code**(字典 value), **qty**, remark, deleted（**无 status 字段**） |
| `meeting_booking` | id, room_id, title, start_time, end_time, attendee_count, **status**（流程常量；展示/筛选对应字典 `meeting.booking_status` 的同名 value）, checkin_at, city, creator_id, **org_code（预留，平台 orgCode 口径）**, deleted |
| `meeting_booking_attendee` | id, booking_id, account_id, **org_code（预留）**, status（`INVITED` / `ACCEPTED` / `DECLINED`，本期只写 `INVITED`）, notified（0/1，是否已发出邀请通知）, deleted |

唯一索引：`meeting_booking(room_id, start_time, deleted)` 配合应用层冲突检测（时间重叠不能靠唯一索引，必须查重叠区间）；`meeting_booking_attendee(booking_id, account_id, deleted)` 防重复邀请。`city` 冗余在 building/booking 上，便于过滤与审计，值与字典 value 严格一致。设备清单用 `meeting_room_device` 关联表而非 `devices(json)`，目的是支持按设备类型筛选与统计；不存设备状态，房间临时不可用靠 `meeting_room.status=维护` 表达；所有字典字段只存 value，展示名称时查字典，不冗余存名称。参会人用关联表而非 `attendee_ids(json)`，便于按被邀请人查详情与后续扩展接受/拒绝状态。房间不存开放起止时间（默认 24 小时可预约）；时间轴占用区间由 `meeting_booking` 实时计算，不冗余存占用位图。

## 七、预定单状态机

```
待签到(PENDING) --签到--> 进行中(CHECKED_IN) --结束/释放--> 已完成(COMPLETED)
     |                        |
     |--取消--> 已取消(CANCELLED)   |--延时--> 仍进行中(改 end_time)
     |
     |--超时未签到--> 已释放(RELEASED)
```

- 冲突检测范围：状态 IN (PENDING, CHECKED_IN) 且时间区间重叠
- **同一自然日校验**：`end_time > start_time`，且 `DATE(end_time)=DATE(start_time)` 或 `end_time = DATE(start_time)+1天 00:00:00`（即当天 24:00，不算跨天）；其它跨天请求直接拒绝，时间轴按单日 24 小时渲染，不展示跨日连续轴
- **30 分钟步长**：`start_time` / `end_time` 的分钟必须为 `00` 或 `30`（服务端强校验，防绕过前端下拉）；结束时刻可选 `23:30` 或当天 `24:00`（存储为次日 `00:00:00`），UI 对最后一格显示为 `24:00` 并允许作为结束格
- **过去时段不可约**：当天（服务端日期）的 `start_time` 必须 `>= 服务端当前时刻`（按分钟比较，例如 09:10 时 09:00 不可选、09:30 可选）；未来日期不受此限。前端时间轴把当前时刻之前的区域灰显，新建/修改弹窗的下拉禁用已过时段，服务端提交时再校验一次（含修改时段）
- 时长也按步长约束：`end_time - start_time` 为 30 分钟的整数倍；延时同样只能延到 30 分钟刻度，且要重新跑冲突检测
- 房间置为「维护 / 停用」只关闭**新预定入口**，不自动取消已有预定；如需腾出时段由管理员手动取消/改派，恢复「可用」后自动回池
- 延时需二次检测：新 end_time 与后续预定不重叠（延时同样复检房间状态，避免维护中继续占用）
- 「超时未签到 → 已释放」由 job 中心派发 `meetingNoShowRelease` 触发，执行身份为 SYSTEM（无用户上下文）；与用户主动"取消"区分，释放原因记 `NO_SHOW`

## 八、权限码（四联动）：所有菜单 / 按钮逐一对应

**菜单级**（前端路由 `meta.perm` + 侧边栏 / 手机 Tab 渲染时过滤，无权限不显示入口）：

| 菜单 | 路由 | 权限码 |
|:---|:---|:---|
| 概览 | `/` | `meeting:overview:view` |
| 位置维护 | `/location` | `meeting:room:list`（只读入口） |
| 会议室维护 | `/rooms` | `meeting:room:list`（只读入口） |
| 会议室预定 | `/booking` | `meeting:booking:view` |
| 我的预定 | `/my-bookings` | `meeting:booking:list` |

**按钮级**（前端 `hasPerm` 隐藏，服务端每个接口独立 `@Permit`，不能只靠前端）：

| 页面 | 按钮 / 操作 | 权限码 |
|:---|:---|:---|
| 概览 | 我要预定 | `meeting:booking:create` |
| 概览 | 日历翻月、查看全部 | `meeting:overview:view` / `meeting:booking:list` |
| 位置维护 | 城市切换、列表查询、字典说明 | `meeting:room:list` |
| 位置维护 | ＋新增大楼 / ＋楼层 / 编辑 / 停用 / 删除 / 弹窗保存 | `meeting:room:edit` |
| 会议室维护 | 筛选查询、字典说明、查看图片 | `meeting:room:list` |
| 会议室维护 | ＋新增房间 / 编辑 / 停用 / 恢复可用 / 删除 / 上传图片 / 弹窗保存 | `meeting:room:edit` |
| 会议室预定 | 时间轴 / 筛选查询 / 埋点说明按钮 | `meeting:booking:view` |
| 会议室预定 | ＋新建预定、邀请参会人选人 / 移除、提交 | `meeting:booking:create` |
| 我的预定 | 页签 / 筛选查询 / 详情 | `meeting:booking:list` |
| 我的预定 | 修改（含增删被邀请人） | `meeting:booking:create` |
| 我的预定 | 签到 | `meeting:booking:checkin` |
| 我的预定 | 延时 | `meeting:booking:extend` |
| 我的预定 | 取消 | `meeting:booking:cancel` |
| 我的预定 | 释放（提前结束） | `meeting:booking:release` |

- 权限码清单：`meeting:overview:view`、`meeting:room:list`、`meeting:room:edit`、`meeting:booking:view`、`meeting:booking:list`、`meeting:booking:create`、`meeting:booking:checkin`、`meeting:booking:extend`、`meeting:booking:cancel`、`meeting:booking:release`（共 10 个，比上一版新增 `booking:view` 与 `booking:cancel`：前者让"看时间轴"和"看我的预定"可分开授权，后者把取消与释放分开授权）
- 四联动落点：common `PermissionCodes` + account `PermissionConstants.BUILTIN` + `data.sql` 播种授权（超管全量；普通角色仅配 `view/list/create/checkin` 等所需码）+ 前端路由 `meta.perm` / 按钮 `hasPerm`。前端隐藏只影响体验，服务端 `@Permit` 才是边界
- **被邀请人详情例外**：被邀请人经消息链接看详情，按「创建人 or `meeting_booking_attendee.account_id`」放行，但仍要求具有基础查看权限（`booking:list` 或 `booking:view`）；不因为拿到链接就绕过权限体系
- **不新增权限码**：邀请参会人选人走现有员工查询接口；消息模板配置归 settings 的 `notice:view` / `notice:manage`，字典维护归 `dict:view` / `dict:manage`，埋点维护归 `embed:view` / `embed:manage`，会议应用只读不重复定义
- **超时释放由 SYSTEM 执行**：job 派发的 `meetingNoShowRelease` 无用户上下文，走 `X-Job-Token` + `X-Internal-Token` 校验，不套用户权限码
- 原型「🔑 权限矩阵」按钮列出上述对应关系，并把 `data-perm` / `title` 标到每个菜单与按钮上，便于逐项核对（静态稿不真正拦截）

## 九、移动端方案（强制）

| 断点 | 布局 |
|:---|:---|
| ≥1024px | 侧边栏 + 内容区 + 月历完整视图 |
| 768–1023px (Pad) | 侧边栏收起为图标，表格保留 |
| <768px (手机) | 侧边栏→底部 TabBar；月历→周视图；列表→卡片；表单单列；操作按钮底部固定 |

所有颜色走 CSS 变量（浅/深两套），沿用 `_shared/styles/responsive.css`，不自写媒体查询。

---

## 十、下一步

1. 你确认功能范围与优先级（尤其 P1 是否进一期）
2. 确认消息模板 code / 变量是否符合你们的文案口径，以及被邀请人是否要进「我的日程」列表（本期默认只允许从消息链接看详情）
   - 同时确认第八节权限码颗粒度（尤其新增的 `meeting:booking:view` 与 `meeting:booking:cancel`）以及各角色授权范围
3. 静态原型确认样式（桌面 + 手机，含新建预定弹窗的邀请参会人组件）
4. 再进入开发：后端（含 `polaris-job-client` + `MeetingNoShowReleaseJob` + `polaris-settings-client` 字典/埋点/邀请通知）→ 网关/SSO/权限码 + 中心 `job_info`/`job.apps` 配置 + settings 字典播种（`meeting.room_type` / `meeting.location_status` / `meeting.seat_layout` / `meeting.booking_status`）、埋点播种（`meeting.booking.max_advance_days=7`）与通知规则播种 → settings 前端数字埋点 `min=0` 改造 → 前端 → 编译验证
