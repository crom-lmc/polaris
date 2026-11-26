# 平台横向能力实证（前端视角）

> 本文件列出历次迭代中落地并线上验证过的平台能力，前端展示与交互时先复用、不要重造。
> 所有条目均为事实描述；实现细节以仓库代码为准，发现冲突时以代码为准并修正文档。

## 1. 统一应用中心与 SSO 回跳

- `_shared/AppCenterNav.vue` 被 11 个应用引用；后台放布局顶部，门户/论坛前台**仅登录后渲染**。
- 签码：`POST /api/auth/oauth/authorize`，参数 `responseType:'code'` / `clientId` / `redirectUri` / `state`，`credentials:'include'` 带中心会话；**必须先同步开窗，再发请求**，否则签码响应到达时弹窗被拦截。
- `redirectUri` 必须是 account 中注册的完整 `redirect_uri`，**不能改成首页**（首页回跳拿不到 `code`，表现为点了没登录）。
- 正式名称取自 `sso_application.name`：sso 北极星钥、audit 北极星眼、file 北极星云、openapi 北极星桥、cms 北极星源、ehr 北极星宿、settings 北极星典、job 北极星轨、forum 北极星空、forum-admin 北极星核、portal 北极星门。
- 各应用 `src/utils/appTitle.ts` 动态取名；登录接口返回结构 sso/audit 读 `res.data`，其余读数组本体，跨应用复制必错。
- 门户已并入 CMS 域名 `https://polaris-cms.laimengchao.site/portal/`；forum-admin 在 `/admin/`。
- 全局登出调 `/api/auth/logout` 作废旧 token，不只清 localStorage。
- 应用中心只展示 `/api/accounts/me/apps` 已授权应用；全员应用白名单 `PUBLIC_APP_CODES = ['portal','forum','meeting']`（接口失败时只保留全员 + 当前应用）。`file` 北极星云非全员应用，授权由账号显式维护，MUST NOT 加入白名单。

## 2. 文件与水印

- 文件管理界面在 `polaris-file-web`（:5185，域名 `polaris-file.laimengchao.site`）。
- **上传+预览 MUST 都走统一文件服务**（红线）：上传走本业务服务的代理接口落 `fileId`（前端不做本地落盘、不自建上传页、不另存 URL）；预览用后端签发的临时 URL。范式可直接抄 meeting：`uploadRoomImage`（`api/meeting.ts`）用 `FileReader.readAsDataURL` → `POST /api/meetingroom/files/upload` → `{fileId,url}`；`RoomsView.vue` 把 `fileId` 存进表单、`a-image :src` 取签名 URL 预览，保存时提交 `fileIds`。
- 附件/合同/档案均以 `file_id` 关联，前端展示走已有 `/api/file/{public,secure,admin,legacy}/**` 或后端签名 URL；PRIVATE 文件走 HMAC 签名 URL。
- 水印覆盖图片、PDF、SVG、DOCX、XLSX、PPTX；**下载附件必须带水印**，不能只加在预览链路。
- 水印设置是配置项且有真实引用点；新增菜单/按钮要挂权限码。

## 3. 审计全局设置与导出

- 审计设置页的每一项都必须有真实代码引用；没有实际功能的配置要去掉，不留空开关。
- 审计列表/登录审计支持 CSV 导出，导出会留痕（记一条 SENSITIVE），不提供关闭开关；导出按钮权限码 `audit:events:export`。
- 审计下拉应用取最近 30 天实际上报的 app，不是全量注册表。

## 4. SLA 阈值可视化

- 阈值在 settings 的 `EmbedPointView.vue` 有结构化编辑器页面，不是裸 JSON；相关列表/统计页要把阈值展示出来（如"积压单无 SLA 阈值"口径可见、可改）。
- **所有埋点都必须提供结构化表单维护**：在 `EmbedPointView.vue` 的 `EDITOR_BY_CODE` 登记 editor，字段名/默认值/范围按该 key 实现；未登记只读预览或裸 JSON 编辑入口都算未完成，不作为兜底方案交付。

## 5. 前端不存在的能力（不要假设存在）

全局搜索、前端共享 npm 包（只有 `_shared/` 源码目录）、WebSocket/SSE 客户端封装、短信适配器、多租户、`v-perm` 指令。需要时用现有机制实现或先确认需求，不要引入新依赖。
