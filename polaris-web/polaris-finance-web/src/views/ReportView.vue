<script setup lang="ts">
import { useRouter } from 'vue-router'

interface ReportItem {
  title: string
  desc: string
  tag: string
  tagColor: string
  source: string
  path?: string
}

/** 后端未提供报表生成接口：只列出可从现有接口取数的报表并标注真实数据来源 */
const reports: ReportItem[] = [
  {
    title: '凭证明细表', desc: '按来源类型、状态与日期输出凭证列表，支持按来源单号追溯源单据。',
    tag: '凭证', tagColor: 'blue', source: 'GET /api/finance/vouchers', path: '/voucher'
  },
  {
    title: '月度收支趋势表', desc: '按凭证来源汇总收入（AR）与支出（其它来源），按月份分布。',
    tag: '统计', tagColor: 'blue', source: 'GET /api/finance/vouchers（前端按日期聚合）', path: '/overview'
  },
  {
    title: '费用报销明细表', desc: '按申请人、部门、状态与日期输出报销单及关联凭证。',
    tag: '费用', tagColor: 'green', source: 'GET /api/finance/expenses', path: '/expense'
  },
  {
    title: '部门预算执行表', desc: '预算额度、历史归档、本期发生、已用、可用额度与合同承诺列示。',
    tag: '预算', tagColor: 'orange', source: 'GET /api/finance/budgets', path: '/budget'
  },
  {
    title: '应收账龄分析表', desc: '按后端埋点分档统计未核销应收金额与占比。',
    tag: '往来', tagColor: 'purple', source: 'GET /api/finance/ar-ap/aging', path: '/ar-ap'
  },
  {
    title: '应收应付明细表', desc: '往来单位、金额、未核销余额、账龄、合同号与凭证号明细。',
    tag: '往来', tagColor: 'purple', source: 'GET /api/finance/ar-ap', path: '/ar-ap'
  },
  {
    title: '付款单明细表', desc: '按合同号与状态查询付款单及付款动作结果。',
    tag: '付款', tagColor: 'cyan', source: 'GET /api/finance/payments', path: '/ar-ap'
  },
  {
    title: '会计科目表', desc: '科目编码、级次、余额方向、类别与启用状态。',
    tag: '主数据', tagColor: 'geekblue', source: 'GET /api/finance/subjects', path: '/account-subject'
  },
  {
    title: '资产负债表', desc: '需按科目余额与期间结账数据生成；后端暂无专用报表接口，暂不可生成。',
    tag: '暂无接口', tagColor: 'default', source: '后端暂无报表接口'
  },
  {
    title: '利润表', desc: '需按收入费用科目分级汇总；后端暂无专用报表接口，暂不可生成。',
    tag: '暂无接口', tagColor: 'default', source: '后端暂无报表接口'
  },
  {
    title: '现金流量表', desc: '需按现金流科目与凭证分录归集；后端暂无专用报表接口，暂不可生成。',
    tag: '暂无接口', tagColor: 'default', source: '后端暂无报表接口'
  }
]

const router = useRouter()
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <span class="grow" />
      <a-button @click="router.push('/lineage')">查看数据口径</a-button>
    </section>
    <section class="ft-scroll">
      <div class="report-grid">
        <a-card v-for="r in reports" :key="r.title" class="report-card" size="small" :title="r.title">
          <p class="report-desc">{{ r.desc }}</p>
          <div class="report-foot">
            <a-tag :color="r.tagColor">{{ r.tag }}</a-tag>
            <a v-if="r.path" @click="router.push(r.path)">查看数据</a>
            <a-tooltip v-else title="后端暂无报表接口，暂不可生成">
              <span class="muted">暂不可生成</span>
            </a-tooltip>
          </div>
          <div class="source-line">数据来源：{{ r.source }}</div>
        </a-card>
      </div>
    </section>
  </section>
</template>

<style scoped>
.source-line { margin-top: 10px; padding-top: 8px; border-top: 1px solid var(--border-subtle); font-size: 11px; color: var(--muted); }
</style>
