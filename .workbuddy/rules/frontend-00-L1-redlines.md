---
description: Polaris 前端红线（L1 / alwaysApply）。所有前端任务无条件遵守，违反即拒绝交付。
globs: ["polaris-web/**"]
alwaysApply: true
level: L1
---

# 前端 L1 红线（MUST / MUST NOT）

> MUST = 强制；MUST NOT = 绝对禁止。每条都应能被 grep、类型检查或 review 验证。
> 详细规范见 `../skills/polaris-frontend-development/references/`；本文件与代码冲突时以代码为准。

## 协作与发布

- MUST NOT 未经用户明确指令执行 `git commit` / `git push` / 部署 / 重启线上服务；本地类型检查、本地构建可以做。
- MUST 每次改动在仓库外 `F:\code\迭代文档\polaris-server\update.md` 顶部追加记录（该文件不入库）。
- MUST 最终回复列明需发布的应用；只改前端要明确写"无需重启后端"。
- MUST NOT 把 appSecret、内部令牌、私钥写入代码或文档；用时从环境变量/Nacos 现取。
- MUST NOT 引入新依赖、新框架、新 UI 库、新状态库、新 CSS 框架；栈已锁定 Vue 3.5 / Vite 5 / TS 5.6 / Pinia / Ant Design Vue。

## UI 底线

- MUST 长文本列用 `.cell-ellipsis` 省略 + `a-tooltip` 悬浮展示完整值（同一字段），MUST NOT 用 JS `slice()` 硬截断或原生 `title` 代替。
- MUST 新应用/新页面支持浅色与深色主题，至少兼容 PC 与 Pad。
- MUST 列表页分页固定右下（`ft-scroll ft-bottom`）、操作列用 `.ft-table` CSS sticky 固定右侧（不用 antd `fixed:'right'`）、表头 `align` 与内容一致。
- MUST 权限只用 `store.can()` / job `hasPerm()` / 路由 `meta.perm`，MUST NOT 新建 `v-perm` 指令（全仓无实现）。
- MUST 19 位雪花 ID 用字符串或本应用已有的 `quoteLongIntegers()` 转换，MUST NOT 用 number 承载。
- MUST 所有附件/图片的**上传与预览**走统一文件服务：上传经本业务服务的代理接口落 `fileId`（如 meeting `uploadRoomImage` → `/api/meetingroom/files/upload` → `FileApi`），预览用后端签发的临时 URL（如 `a-image :src` 取 `signed url`）；MUST NOT 前端直连自建上传页、把文件另存为 URL、或自己拼接本地静态路径绕过文件服务。

## 应用与回跳

- MUST 应用中心 callback 使用注册的完整 `redirect_uri`，MUST NOT 用首页代替（门户/论坛拿不到 `code`）。
- MUST 门户/论坛前台的应用中心仅 `v-if="store.isLoggedIn"` 后渲染。
- MUST 全局登出调用 `/api/auth/logout` 作废旧 token，MUST NOT 只清 localStorage。

## 判断纪律

- MUST 先读代码或全仓 grep 再判断能力归属，MUST NOT 望文生义（forum.notification 非通知通道、ehr.announcement 非平台公告）。11 个应用的响应解包、token 键名、401 行为不一致，跨应用复制前 MUST 先确认本应用口径。
