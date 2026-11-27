<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  listVouchers, listExpenses, listArAp, listBudgets, getVoucher,
  type VoucherVO, type ArApVO, type BudgetVO
} from '@/api/finance'
import { dateOf, fmtDate, sourceLabel, statusColor, statusLabel, yuan, yuanOrMasked } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const range = ref<[Dayjs, Dayjs] | null>([dayjs().subtract(6, 'day').startOf('day'), dayjs()])

const allVouchers = ref<VoucherVO[]>([])
const arAps = ref<ArApVO[]>([])
const budgets = ref<BudgetVO[]>([])
const voucherTotal = ref(0)
const expensePending = ref(0)

async function load() {
  loading.value = true
  try {
    const [v, pending, a, b] = await Promise.all([
      listVouchers({ page: 1, size: 500 }),
      listExpenses({ page: 1, size: 1, status: 'PENDING' }),
      listArAp({ page: 1, size: 500 }),
      listBudgets({ page: 1, size: 500, year: dayjs().year() })
    ])
    allVouchers.value = v.list || []
    voucherTotal.value = v.total
    expensePending.value = pending.total
    arAps.value = a.list || []
    budgets.value = b.list || []
  } catch (e: any) {
    message.error(e?.message || '加载概览数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const inRange = (d?: string) => {
  if (!d) return false
  const from = dateOf(range.value?.[0])
  const to = dateOf(range.value?.[1])
  return (!from || d >= from) && (!to || d <= to)
}
const rangedVouchers = computed(() => allVouchers.value.filter((v) => inRange(v.date)))

/** 近 6 个月收支：收入取 AR 来源凭证，支出取其它来源凭证，全部来自真实列表 */
const months = computed(() => {
  const buckets: { label: string; income: number; expense: number }[] = []
  for (let i = 5; i >= 0; i--) {
    buckets.push({ label: dayjs().subtract(i, 'month').format('YYYY-MM'), income: 0, expense: 0 })
  }
  for (const v of allVouchers.value) {
    const key = (v.date || '').slice(0, 7)
    const bucket = buckets.find((b) => b.label === key)
    if (!bucket) continue
    if (v.sourceType === 'AR') bucket.income += Number(v.amount || 0)
    else bucket.expense += Number(v.amount || 0)
  }
  return buckets
})
const chartMax = computed(() => Math.max(1, ...months.value.flatMap((m) => [m.income, m.expense])))
function barHeight(v: number) { return `${Math.max(4, Math.round((v / chartMax.value) * 100))}%` }

const arBalance = computed(() => arAps.value
  .filter((a) => a.type === 'AR' && (a.status === 'APPROVED' || a.status === 'POSTED'))
  .reduce((sum, a) => sum + Number(a.balance || 0), 0))
const apBalance = computed(() => arAps.value
  .filter((a) => a.type === 'AP' && (a.status === 'APPROVED' || a.status === 'POSTED'))
  .reduce((sum, a) => sum + Number(a.balance || 0), 0))
const overdue = computed(() => arAps.value
  .filter((a) => a.type === 'AR' && a.ageDays > 30 && (a.status === 'APPROVED' || a.status === 'POSTED'))
  .reduce((sum, a) => sum + Number(a.balance || 0), 0))
const pendingCount = computed(() => expensePending.value + arAps.value.filter((a) => a.status === 'PENDING').length)
const budgetAmount = computed(() => budgets.value.reduce((sum, b) => sum + Number(b.amount || 0), 0))
const budgetUsed = computed(() => budgets.value.reduce((sum, b) => sum + Number(b.used || 0), 0))
const budgetRate = computed(() => budgetAmount.value ? Math.round((budgetUsed.value / budgetAmount.value) * 100) : 0)

const cards = computed(() => [
  { label: '本期收入', value: yuan(months.value[5]?.income), hint: 'AR 来源凭证 · 当月', trend: `${months.value.length} 个月趋势`, to: '/ar-ap' },
  { label: '本期支出', value: yuan(months.value[5]?.expense), hint: '非 AR 凭证 · 当月', trend: `${voucherTotal.value} 张凭证`, to: '/voucher' },
  { label: '待审批单据', value: String(pendingCount.value), hint: '报销 + 应收应付', trend: '需财务处理', to: '/expense' },
  { label: '逾期应收', value: yuanOrMasked(overdue.value), hint: '账龄 > 30 天', trend: '需催收', to: '/ar-ap' },
  { label: '应收余额', value: yuanOrMasked(arBalance.value), hint: '未核销 · 已通过/已入账', trend: `${arAps.value.filter((a) => a.type === 'AR').length} 笔`, to: '/ar-ap' },
  { label: '应付余额', value: yuanOrMasked(apBalance.value), hint: '未付款 · 已通过/已入账', trend: `${arAps.value.filter((a) => a.type === 'AP').length} 笔`, to: '/ar-ap' },
  { label: '预算执行率', value: `${budgetRate.value}%`, hint: '已用 / 年度额度', trend: `${budgets.value.length} 个部门`, to: '/budget' },
  { label: '预算已用', value: yuan(budgetUsed.value), hint: `年度额度 ${yuan(budgetAmount.value)}`, trend: '含历史归档', to: '/budget' }
])

const columns = [
  { title: '凭证号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '摘要', key: 'summary', width: 240 },
  { title: '来源单据', key: 'source', width: 190 },
  { title: '会计科目', dataIndex: 'subject', key: 'subject', width: 160 },
  { title: '金额', key: 'amount', align: 'right' as const, width: 120 },
  { title: '状态', key: 'status', align: 'center' as const, width: 100 },
  { title: '制单人', dataIndex: 'creator', key: 'creator', width: 90 },
  { title: '日期', dataIndex: 'date', key: 'date', width: 110 },
  { title: '操作', key: 'action', width: 100 }
]
const recentVouchers = computed(() => rangedVouchers.value.slice(0, 5))

const traceOpen = ref(false)
const traceLoading = ref(false)
const trace = ref<VoucherVO | null>(null)
async function openTrace(id: string) {
  traceOpen.value = true
  traceLoading.value = true
  try {
    trace.value = await getVoucher(id)
  } catch (e: any) {
    message.error(e?.message || '加载凭证详情失败')
    trace.value = null
  } finally {
    traceLoading.value = false
  }
}
function goTraceSource() {
  const no = trace.value?.sourceNo
  if (!no) return
  traceOpen.value = false
  router.push({ path: '/voucher', query: { keyword: no } })
}
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <a-range-picker v-model:value="range" format="YYYY-MM-DD" />
      <span class="grow" />
      <a-button :loading="loading" @click="load">刷新</a-button>
    </section>
    <section class="ft-scroll">
      <a-spin :spinning="loading">
        <div class="stat-grid four">
          <div v-for="s in cards" :key="s.label" class="stat-card clickable" @click="router.push(s.to)">
            <span class="st-label">{{ s.label }}</span>
            <strong class="st-value">{{ s.value }}</strong>
            <span class="st-foot"><b class="trend-up">{{ s.trend }}</b><span class="muted">{{ s.hint }}</span></span>
          </div>
        </div>
        <a-card class="panel-card" title="月度收支趋势（近 6 个月，由凭证按来源汇总）" size="small">
          <div class="chart-legend">
            <span><i class="dot income" />收入（AR 来源凭证）</span>
            <span><i class="dot expense" />支出（其它来源凭证）</span>
          </div>
          <div class="chart">
            <div v-for="m in months" :key="m.label" class="bar-group">
              <div class="bars">
                <i class="bar income" :style="{ height: barHeight(m.income) }" />
                <i class="bar expense" :style="{ height: barHeight(m.expense) }" />
              </div>
              <span class="bar-label">{{ m.label }}</span>
            </div>
          </div>
        </a-card>
        <a-card class="panel-card" title="最近凭证（点击来源可追溯到源单据 / 合同）" size="small">
          <a-table class="ft-table" style="--ft-min-width: 1290px" row-key="id" :columns="columns"
                   :data-source="recentVouchers" :pagination="false" :loading="loading"
                   :class="{ 'tbl-empty-mode': !loading && !recentVouchers.length }"
                   :scroll="recentVouchers.length ? { x: 1290 } : undefined">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'summary'">
                <a-tooltip :title="record.summary"><span class="cell-ellipsis">{{ record.summary }}</span></a-tooltip>
              </template>
              <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'source'">
                <a-tooltip v-if="record.sourceNo" :title="`${sourceLabel(record.sourceType)} · ${record.sourceNo}`">
                  <a class="cell-ellipsis" @click="openTrace(record.id)">{{ sourceLabel(record.sourceType) }} · {{ record.sourceNo }}</a>
                </a-tooltip>
                <span v-else class="muted">{{ sourceLabel(record.sourceType) }}</span>
              </template>
              <template v-else-if="column.key === 'subject'">
                <a-tooltip :title="record.subject"><span class="cell-ellipsis">{{ record.subject || '-' }}</span></a-tooltip>
              </template>
              <template v-else-if="column.key === 'date'">{{ fmtDate(record.date) }}</template>
              <template v-else-if="column.key === 'action'"><a @click="openTrace(record.id)">查看</a></template>
            </template>
            <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="所选范围内暂无凭证" /></div></template>
          </a-table>
        </a-card>
      </a-spin>
    </section>

    <a-drawer v-model:open="traceOpen" title="凭证来源追溯" :width="520" placement="right">
      <a-spin :spinning="traceLoading">
      <a-descriptions v-if="trace" :column="1" bordered size="small">
        <a-descriptions-item label="凭证号">{{ trace.no }}</a-descriptions-item>
        <a-descriptions-item label="来源类型">{{ sourceLabel(trace.sourceType) }}</a-descriptions-item>
        <a-descriptions-item label="来源单据">{{ trace.sourceNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="摘要">{{ trace.summary }}</a-descriptions-item>
        <a-descriptions-item label="会计科目">{{ trace.subject }}</a-descriptions-item>
        <a-descriptions-item label="金额">{{ yuan(trace.amount) }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ statusLabel(trace.status) }}</a-descriptions-item>
        <a-descriptions-item label="关联合同">{{ trace.contractNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="归属部门">{{ trace.dept || '-' }}</a-descriptions-item>
        <a-descriptions-item label="制单人">{{ trace.creator || '-' }}</a-descriptions-item>
        <a-descriptions-item label="日期">{{ fmtDate(trace.date) }}</a-descriptions-item>
      </a-descriptions>
      </a-spin>
      <template #footer>
        <div class="drawer-footer">
          <a-button v-if="trace?.sourceNo" @click="goTraceSource">查看来源单据</a-button>
          <a-button type="primary" @click="traceOpen = false">关闭</a-button>
        </div>
      </template>
    </a-drawer>
  </section>
</template>
