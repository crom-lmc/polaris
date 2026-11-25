---
description: Polaris 前端 L3 开发准则与风格建议（SHOULD）。不阻断交付，用于对齐团队习惯与代码可读性。
globs: ["polaris-web/**"]
alwaysApply: false
level: L3
---

# 前端 L3 准则（SHOULD / 风格偏好）

> 与 L1/L2 冲突时以 L1/L2 为准。风格类偏好优先交给格式化工具与既有代码，SHOULD NOT 反复"优化"已有代码。

## 命名与组织

- SHOULD 页面组件 `XxxView.vue`、布局 `XxxLayout.vue`、目录 kebab-case、组件文件 PascalCase。
- SHOULD API 函数用 `fetchXxx` / `saveXxx` / `deleteXxx` / `exportXxx` 前缀；类型与后端 VO 对齐。
- SHOULD 请求集中在 `src/api/`，类型定义随接口就近放，不散落在视图里。

## 复用既有能力

- SHOULD 复用现有工具：`appTitle.ts` / `token.ts` / `ThemeToggleIcon.vue` / `confirmAction()`。
- SHOULD 公共组件放 `_shared/`，不新建第三个共享层。
- SHOULD 注释解释"为什么"而不是复述代码。

## 可读性与验证

- SHOULD 方法保持短小，复杂条件拆成有名字的私有方法；但 SHOULD NOT 为抽象而抽象。
- SHOULD 新增页面配一个正向用例与一个失败用例的人工验证（含浅色/深色、PC/Pad）。
- SHOULD 排查问题时先全仓 grep 符号与字段，再改代码；发现规范与代码冲突时先修文档。
- SHOULD 每次踩坑先补对应规则（本 `rules/` 或 Skill 参考），再改业务代码。

## 维护纪律

- 规则只针对高频出错场景，SHOULD NOT 追求面面俱到；规则超过 20 条时优先合并与删减。
- L1 红线保持"少而硬"（可 grep / 可类型检查 / 可 review）；L2 写成可验证的"如果—那么"断言；L3 交给风格工具与代码示范。
- 规范文档与代码冲突时以代码为准，发现即同步修正；每个迭代批次回顾一次规则有效性。
