---
name: polaris-frontend-development
description: Polaris 一体化企业平台前端开发流程（polaris-web/，11 个独立 Vue3 + Vite5 + TS 应用 + _shared 共享目录）。当需要在该仓库新增或修改页面、组件、路由、API 封装、权限、主题、响应式、SSO 回调，或做前端类型检查与构建前应加载本 Skill；本 Skill 应在任何涉及 Polaris 前端代码的任务开始时优先加载。
agent_created: true
---

# Polaris 前端开发 Skill

## 角色

### 你是一名资深的前端开发工程师

先读代码再写代码：11 个应用的响应解包口径、token 键名、401 行为并不一致，动手前先确认本应用写法，不跨应用照抄。

## 规则层级（工作区根 `.workbuddy/rules/`，红线不在此重复）

- **L1 红线**：`../../rules/frontend-00-L1-redlines.md`（alwaysApply，MUST/MUST NOT）— 违反即拒绝交付。
- **L2 规范**：`../../rules/frontend-01-L2-conventions.md`（API/权限/表格/主题/响应式/回调）、`../../rules/frontend-02-L2-platform.md`（平台能力接入）。
- **L3 准则**：`../../rules/frontend-03-L3-guidelines.md`（SHOULD，不阻断）。

优先级：L1 > L2 > 本 Skill 流程 > L3 > 模型默认。规范与代码冲突时以代码为准，并提示同步修正文档。

## 按需加载参考

1. **结构与工程写法**：`references/frontend-guide.md`（11 应用清单、API/路由/组件/样式、响应式、权限、构建）。
2. **代码实证的公共契约**：`references/common-ui-contracts.md`（`_shared` 三件套 API、三套解包口径、四套 401 行为、store/token 键名差异、`ft-*` 类定义位置、CRLF/BOM/监听器清理）。
3. **新业务功能**：`references/new-feature-guide.md`（前端能力读取口径 + UI 硬规范 + 自查）。
4. **已落地能力**：`references/platform-capabilities.md`（应用中心与 SSO、文件与水印、审计设置与导出、SLA 可视化、不存在的能力）。

## 标准流程

1. **定范围**：确认改动落在哪个应用；若改 `_shared/`，列出全部 11 个应用为影响面。
2. **查口径**：读本应用 `src/api/request.ts`、`stores/user.ts`、`stores/theme.ts`、路由守卫，确认解包口径/401 行为/token 键名/base 三件套。
3. **做决策**：新页面按 `../../rules/frontend-02-L2-platform.md` 判断是否接字典/应用中心/水印等能力，配置类数据不写死前端。
4. **实现**：`<script setup lang="ts">`；列表页用 `ft-scroll ft-bottom` + `.ft-table` CSS sticky 固定操作列 + 表头 `align` 一致；长文本用 `.cell-ellipsis` 省略 + `a-tooltip` 悬浮展示完整值；颜色走 CSS 变量。
5. **自检**：按 L1 + L2 检查权限、表格、长文本、响应式、主题、回调、监听器清理、换行符与 BOM。
6. **验证**：`vue-tsc --noEmit` + `vite build` 通过；改 `_shared/` 时 11 个应用全量验证；本地清理守卫拦截时用 `--outDir /tmp/<唯一目录> --emptyOutDir false`。
7. **收尾**：在仓库外 `F:\code\迭代文档\polaris-server\update.md` 顶部追加记录；最终回复列明需发布的应用、无需重启后端、未提交/未部署状态；未经明确指令不提交不部署。

## 资源索引

- `references/frontend-guide.md`：前端完整规范与 11 应用公共契约。
- `references/common-ui-contracts.md`：从源码实证的公共模式与应用差异（含路径证据）。
- `references/new-feature-guide.md`：前端读取口径、UI 硬规范、交付自查。
- `references/platform-capabilities.md`：前端视角的横向能力复用清单。
