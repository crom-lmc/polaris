<script setup lang="ts">
const flowSteps = ['业务单据', '审批通过', '生成凭证', '过账入表', '账表与报表']
const purchaseFlow = ['采购计划', '合同与清单', '付款计划', '应付单', '凭证', '账表']
const salesFlow = ['销售合同', '合同清单', '验收 / 应收单', '凭证', '账表']

const objectColumns = [
  { title: '对象', dataIndex: 'object', key: 'object', width: 150 },
  { title: '关注点', dataIndex: 'focus', key: 'focus', width: 220 },
  { title: '关键字段', dataIndex: 'fields', key: 'fields', width: 300 },
  { title: '状态', dataIndex: 'states', key: 'states', width: 200 },
  { title: '去向', dataIndex: 'next', key: 'next', width: 280 }
]
const objectRows = [
  {
    object: '会计科目', focus: '借贷方向与科目归集',
    fields: '科目编码、名称、级次、余额方向（DEBIT/CREDIT）、类别（ASSET/LIABILITY/EQUITY/INCOME/EXPENSE）、启用状态',
    states: '启用 / 停用', next: '供凭证、报销单、应收应付单选用；停用后不再被新单据采用'
  },
  {
    object: '预算科目', focus: '年度额度与科目对应',
    fields: '编码、名称、年度、年度额度、关联会计科目编码、启用状态',
    states: '启用 / 停用', next: '合同域通过 finance-client 只读引用'
  },
  {
    object: '预算额度', focus: '部门年度可用额度',
    fields: '部门、年度、额度、历史归档、合同承诺占用 / 已付 / 在途、已用、可用、执行率与预警标记',
    states: '按年度维护', next: '合同承诺由合同域跨服写入；已用 = 历史归档 + 已过账凭证'
  },
  {
    object: '费用报销单', focus: '垫付费用该不该报',
    fields: '单号、申请人、事由、金额、部门、科目、报销日期、关联凭证号',
    states: '草稿 / 待审批 / 已通过 / 已驳回 / 已入账 / 已付款',
    next: '审批通过后生成凭证并过账，付款后进入账表'
  },
  {
    object: '应付单', focus: '欠供应商多少钱、是否到期',
    fields: '单号、往来单位、合同号、金额、未核销余额、账龄天数、科目、凭证号',
    states: '草稿 / 待审批 / 已通过 / 已驳回 / 已入账 / 已付款',
    next: '审批通过后过账生成凭证；核销后余额进入账龄统计'
  },
  {
    object: '应收单', focus: '客户欠多少钱',
    fields: '单号、往来单位、合同号、验收来源、金额、未核销余额、账龄天数、凭证号',
    states: '草稿 / 待审批 / 已通过 / 已驳回 / 已入账 / 已付款',
    next: '销售合同验收后生成应收单，核销回款后形成账龄与回款统计'
  },
  {
    object: '付款单', focus: '按合同付款的申请与执行',
    fields: '单号、合同号、往来单位、金额、日期、状态',
    states: '按接口状态展示', next: '由合同付款申请生成，付款后回写预算合同已付'
  },
  {
    object: '会计凭证', focus: '来源可追溯、借贷记账',
    fields: '凭证号、来源类型（MANUAL/EXPENSE/AR/AP）、来源单号、摘要、科目、金额、状态、合同号',
    states: '草稿 / 已入账', next: '过账后形成预算已用、月度收支与账表数据'
  }
]
</script>

<template>
  <section class="ft-page">
    <section class="ft-scroll">
      <a-alert type="info" show-icon class="drawer-alert" message="单据到账表的统一口径"
               description="采购、报销与销售业务单据经审批后生成会计凭证，凭证过账形成预算已用、往来余额、账龄与报表数据。单号与记账由服务端生成，前端只展示与发起动作。" />
      <a-card class="panel-card" title="采购到付款：计划 → 合同 → 付款 → 账表" size="small">
        <a-steps :current="purchaseFlow.length" size="small">
          <a-step v-for="s in purchaseFlow" :key="s" :title="s" />
        </a-steps>
        <p class="hint flow-note">合同本身占用预算额度，真正入账的是审批通过后的应付单与凭证；合同承诺列单独列示，不重复计入已用。</p>
      </a-card>
      <a-card class="panel-card" title="销售到回款：合同 → 验收应收 → 账表" size="small">
        <a-steps :current="salesFlow.length" size="small">
          <a-step v-for="s in salesFlow" :key="s" :title="s" />
        </a-steps>
        <p class="hint flow-note">销售合同按验收节点生成应收单；应收单审批、过账、核销后形成账龄与回款统计。</p>
      </a-card>
      <a-card class="panel-card" title="单据到账表的流转" size="small">
        <a-steps :current="flowSteps.length" size="small">
          <a-step v-for="s in flowSteps" :key="s" :title="s" />
        </a-steps>
      </a-card>
      <a-card class="panel-card" title="统计口径" size="small">
        <div class="guide">
          <div class="guide-line"><b>月度收支</b><span>按凭证日期归集月份；收入取 AR 来源凭证，支出取其它来源凭证，来自凭证列表接口。</span></div>
          <div class="guide-line"><b>预算执行率</b><span>已用 = 历史归档 + 已过账凭证；预警阈值由后端埋点返回（红色 / 黄色标记），前端不自行设定阈值。</span></div>
          <div class="guide-line"><b>往来账龄</b><span>账龄按单据日期至今天数计算，分档区间来自后端埋点；未核销（已通过 / 已入账）才计入余额与账龄。</span></div>
          <div class="guide-line"><b>金额脱敏</b><span>薪资类往来金额由服务端按 finance:salary:view 权限脱敏，无权限时前端展示 **** 。</span></div>
          <div class="guide-line"><b>报表</b><span>财务报表页只列出可从现有接口取数的报表及数据来源；后端暂无专用报表接口的三表不提供生成动作。</span></div>
        </div>
      </a-card>
      <a-card class="panel-card" title="对象说明" size="small">
        <a-table class="ft-table" style="--ft-min-width: 1180px" row-key="object" :columns="objectColumns"
                 :data-source="objectRows" :pagination="false"
                 :class="{ 'tbl-empty-mode': !objectRows.length }"
                 :scroll="objectRows.length ? { x: 1180 } : undefined">
          <template #bodyCell="{ column, record }">
            <template v-if="['fields', 'next', 'focus'].includes(String(column.key))">
              <a-tooltip :title="record[column.key as string]">
                <span class="cell-ellipsis">{{ record[column.key as string] }}</span>
              </a-tooltip>
            </template>
          </template>
          <template #emptyText><div class="tbl-empty"><a-empty description="暂无对象说明" /></div></template>
        </a-table>
      </a-card>
    </section>
  </section>
</template>

<style scoped>
.guide { display: flex; flex-direction: column; gap: 10px; }
.guide-line { display: flex; gap: 12px; align-items: baseline; font-size: 12px; line-height: 1.7; }
.guide-line b { flex: 0 0 90px; color: var(--primary); }
.guide-line span { color: var(--muted); }
</style>
