---
description: Polaris 后端 L3 开发准则与风格建议（SHOULD）。不阻断交付，用于对齐团队习惯与代码可读性。
globs: ["polaris-server/**"]
alwaysApply: false
level: L3
---

# 后端 L3 准则（SHOULD / 风格偏好）

> 与 L1/L2 冲突时以 L1/L2 为准。风格类偏好优先交给格式化工具与既有代码，SHOULD NOT 反复"优化"已有代码。

## 命名与组织

- SHOULD Java 类名 PascalCase、方法以动词开头、常量全大写加下划线、布尔字段用 `is/has` 前缀。
- SHOULD API 函数用 `fetchXxx` / `saveXxx` / `deleteXxx` / `exportXxx` 前缀（前端侧），后端方法名与业务动作对应。
- SHOULD 小域用扁平包结构、多域用 `module/<域>`；不强行统一 account 与 audit 两套模板。

## 复用既有能力

- SHOULD 参数校验按所在模块既有风格选（cms/ehr 用 Bean Validation，account/audit/settings 用 `Asserts`），不混搭。
- SHOULD 复用现有工具：`Asserts` / `JsonUtils` / `IdUtils` / `ValidateUtil` / `SensitiveMasker`。
- SHOULD 公共工具放到 `polaris-common`，不新建第三个共享层。
- SHOULD 注释解释"为什么"而不是复述代码；对复制模板遗留的错误注释发现即修正。

## 可读性与验证

- SHOULD 方法保持短小，复杂条件拆成有名字的私有方法；但 SHOULD NOT 为抽象而抽象。
- SHOULD 新增接口配一个正向用例与一个失败用例的人工或脚本验证。
- SHOULD 排查问题时先全仓 grep 符号与字段，再改代码；发现规范与代码冲突时先修文档。
- SHOULD 每次踩坑先补对应规则（本 `rules/` 或 Skill 参考），再改业务代码，避免同类问题反复出现。

## 维护纪律

- 规则只针对高频出错场景，SHOULD NOT 追求面面俱到；规则超过 20 条时优先合并与删减。
- L1 红线保持"少而硬"（可 grep / 可编译 / 可 review）；L2 写成可验证的"如果—那么"断言；L3 交给风格工具与代码示范。
- 规范文档与代码冲突时以代码为准，发现即同步修正；每个迭代批次回顾一次规则有效性。
