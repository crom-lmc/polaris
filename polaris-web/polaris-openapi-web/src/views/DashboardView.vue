<template>
  <div class="ft-page dash-page">
    <div class="ft-head dash-head">
      <span class="dash-title">接口调用概览</span>
      <div class="dash-toolbar">
        <a-range-picker v-model:value="range" format="YYYY-MM-DD" style="width: 380px" />
        <a-button type="primary" size="small" :loading="loading" @click="load">查询</a-button>
        <a-button size="small" @click="reset">重置</a-button>
      </div>
    </div>

    <div class="ft-scroll dash-scroll">
      <a-spin :spinning="loading">
        <!-- 指标卡：调用总量 / 成功 / 失败 / 成功率 / 平均耗时 / P95 / 接入应用数（受顶部日期过滤） -->
        <div class="stat-grid">
          <a-card v-for="card in statCards" :key="card.label" size="small" class="stat-card">
            <div class="stat-main">
              <span class="stat-mark" :style="{ background: card.color }" />
              <div class="stat-text">
                <div class="stat-value">{{ card.value }}</div>
                <div class="stat-label">{{ card.label }}</div>
              </div>
            </div>
          </a-card>
        </div>

        <!-- 调用趋势（默认最近 10 天，独立于顶部日期）+ 失败 TOP API -->
        <a-row :gutter="12" class="chart-row">
          <a-col :xs="24" :lg="16">
            <a-card title="调用趋势（按天）" size="small" class="chart-card">
              <template #extra>
                <span class="chart-extra">
                  <span class="chart-legend">
                    <i class="dot total" />调用量
                    <i class="dot fail" />失败量
                  </span>
                  <a-button type="link" size="small" @click="openTrendModal">查看更多</a-button>
                </span>
              </template>
              <a-spin :spinning="trendLoading" size="small">
                <TrendChart :rows="trendFilledRows" :height="210" />
              </a-spin>
            </a-card>
          </a-col>

          <a-col :xs="24" :lg="8">
            <a-card title="失败 TOP API" size="small" class="chart-card">
              <template #extra>
                <a-button type="link" size="small" @click="openFailedModal">查看全部</a-button>
              </template>
              <a-spin :spinning="topFailedLoading" size="small">
                <div class="bar-list">
                  <div v-for="row in topFailedRows" :key="row.apiCode" class="bar-item">
                    <div class="bar-head">
                      <span :title="row.apiCode">{{ apiName(row.apiCode) }}</span>
                      <b>{{ row.failed }}</b>
                    </div>
                    <div class="bar-track">
                      <div class="bar-fill" :style="{ width: failedPercent(row.failed) + '%' }" />
                    </div>
                  </div>
                  <a-empty v-if="!topFailedRows.length" :image-style="{ height: '40px' }" description="暂无失败记录" />
                </div>
              </a-spin>
            </a-card>
          </a-col>
        </a-row>

        <!-- 最近异常：默认最新几条，独立于顶部日期 -->
        <a-row :gutter="12" class="chart-row">
          <a-col :span="24">
            <a-card title="最近异常" size="small" class="chart-card">
              <template #extra>
                <a-button type="link" size="small" @click="openErrorsModal">查看全部</a-button>
              </template>
              <a-spin :spinning="errorsLoading" size="small">
                <a-table
                  v-if="recentErrors.length"
                  size="small"
                  :columns="recentErrorColumns"
                  :data-source="recentErrors"
                  :pagination="false"
                  row-key="traceId"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'direction'">
                      {{ directionText(record.direction) }}
                    </template>
                    <template v-else-if="column.key === 'appCode'">
                      <span :title="record.appCode">{{ appName(record.appCode) }}</span>
                    </template>
                    <template v-else-if="column.key === 'apiCode'">
                      <span :title="record.apiCode">{{ apiName(record.apiCode) }}</span>
                    </template>
                    <template v-else-if="column.key === 'httpStatus'">
                      <a-tag color="red">{{ record.httpStatus }}</a-tag>
                    </template>
                    <template v-else-if="column.key === 'bizCode'">
                      <span class="mono">{{ record.bizCode }}</span>
                    </template>
                    <template v-else-if="column.key === 'errorMsg'">
                      <span :title="record.errorMsg || ''">{{ record.errorMsg || '—' }}</span>
                    </template>
                    <template v-else-if="column.key === 'createdAt'">
                      <span class="mono">{{ record.createdAt }}</span>
                    </template>
                  </template>
                </a-table>
                <a-empty v-else :image-style="{ height: '40px' }" description="暂无异常记录" />
              </a-spin>
            </a-card>
          </a-col>
        </a-row>
      </a-spin>
    </div>

    <!-- 调用趋势 · 查看更多（支持时间过滤 + 按天明细） -->
    <a-drawer v-model:open="trendModalOpen" title="调用趋势（按天）" width="760" class="drawer-body-flex" :footer="null">
      <div class="modal-filter">
        <a-range-picker v-model:value="trendModalRange" format="YYYY-MM-DD" style="width: 320px" :allow-clear="false" />
        <a-button type="primary" size="small" :loading="trendModalLoading" @click="loadTrendModal">查询</a-button>
        <a-button size="small" @click="resetTrendModal">重置</a-button>
      </div>
      <a-spin :spinning="trendModalLoading">
        <TrendChart :rows="trendModalFilledRows" :height="300" />
        <a-table
          v-if="trendModalFilledRows.length"
          class="modal-table"
          size="small"
          :columns="trendDayColumns"
          :data-source="trendModalFilledRows"
          :pagination="{ pageSize: 10, showSizeChanger: false, showTotal: (t: number) => `共 ${t} 天` }"
          row-key="day"
        />
      </a-spin>
    </a-drawer>

    <!-- 失败 TOP API · 查看全部（支持时间过滤） -->
    <a-drawer v-model:open="failedModalOpen" title="失败 TOP API" width="680" class="drawer-body-flex" :footer="null">
      <div class="modal-filter">
        <a-range-picker v-model:value="failedModalRange" format="YYYY-MM-DD" style="width: 320px" />
        <a-button type="primary" size="small" :loading="failedModalLoading" @click="loadFailedModal">查询</a-button>
        <a-button size="small" @click="resetFailedModal">重置</a-button>
        <span class="modal-hint">不选择时间则查询全部</span>
      </div>
      <a-spin :spinning="failedModalLoading">
        <a-table
          v-if="failedModalRows.length"
          :columns="failedRankColumns"
          :data-source="failedModalRows"
          :pagination="{ pageSize: 10, showSizeChanger: false }"
          row-key="apiCode"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'apiCode'">
              <span :title="record.apiCode">{{ apiName(record.apiCode) }}</span>
            </template>
          </template>
        </a-table>
        <div v-else class="drawer-empty"><a-empty :image-style="{ height: '40px' }" description="暂无失败记录" /></div>
      </a-spin>
    </a-drawer>

    <!-- 最近异常 · 查看全部（支持时间过滤） -->
    <a-drawer v-model:open="errorsModalOpen" title="最近异常" width="960" class="drawer-body-flex" :footer="null">
      <div class="modal-filter">
        <a-range-picker v-model:value="errorsModalRange" format="YYYY-MM-DD" style="width: 320px" />
        <a-button type="primary" size="small" :loading="errorsModalLoading" @click="loadErrorsModal">查询</a-button>
        <a-button size="small" @click="resetErrorsModal">重置</a-button>
        <span class="modal-hint">不选择时间则查询全部</span>
      </div>
      <a-spin :spinning="errorsModalLoading">
        <a-table
          v-if="errorsModalRows.length"
          :columns="recentErrorColumns"
          :data-source="errorsModalRows"
          :pagination="{ pageSize: 10, showSizeChanger: false, showTotal: (t: number) => `共 ${t} 条` }"
          row-key="traceId"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'direction'">
              {{ directionText(record.direction) }}
            </template>
            <template v-else-if="column.key === 'appCode'">
              <span :title="record.appCode">{{ appName(record.appCode) }}</span>
            </template>
            <template v-else-if="column.key === 'apiCode'">
              <span :title="record.apiCode">{{ apiName(record.apiCode) }}</span>
            </template>
            <template v-else-if="column.key === 'httpStatus'">
              <a-tag color="red">{{ record.httpStatus }}</a-tag>
            </template>
            <template v-else-if="column.key === 'bizCode'">
              <span class="mono">{{ record.bizCode }}</span>
            </template>
            <template v-else-if="column.key === 'errorMsg'">
              <span :title="record.errorMsg || ''">{{ record.errorMsg || '—' }}</span>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              <span class="mono">{{ record.createdAt }}</span>
            </template>
          </template>
        </a-table>
        <div v-else class="drawer-empty"><a-empty :image-style="{ height: '40px' }" description="暂无异常记录" /></div>
      </a-spin>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import TrendChart from '@/components/TrendChart.vue'
import {
  getOverview, getTrend, getTopFailed, getRecentErrors,
  listEndpoints, listApps,
  type StatQuery, type RecentError, type ApiEndpoint, type AppInfo
} from '@/api/openapi'

type OverviewData = Awaited<ReturnType<typeof getOverview>>
type TrendRow = { day: string; total: number; failed: number }
type FailedRow = { apiCode: string; failed: number }

function formatNumber(v: number): string {
  return Number(v || 0).toLocaleString('zh-CN')
}

/* 编码 → 展示名：接口目录、第三方应用均取管理端已有列表，查不到时回退编码 */
const apiNameMap = ref<Record<string, string>>({})
const appNameMap = ref<Record<string, string>>({})
function apiName(code?: string): string {
  return (code && apiNameMap.value[code]) || code || '—'
}
function appName(code?: string): string {
  return (code && appNameMap.value[code]) || code || '—'
}
function directionText(v?: string): string {
  // 按数据流向：INBOUND（第三方调我们）=出站；OUTBOUND（我们调第三方）=入站
  const map: Record<string, string> = {
    INBOUND: '出站', OUTBOUND: '入站', IN: '出站', OUT: '入站', '1': '出站', '2': '入站'
  }
  return map[v || ''] || v || '—'
}
async function loadNames() {
  try {
    const endpoints: ApiEndpoint[] = await listEndpoints()
    apiNameMap.value = endpoints.reduce<Record<string, string>>((m, e) => {
      if (e.apiCode) m[e.apiCode] = e.apiName || e.apiCode
      return m
    }, {})
  } catch {
    // 名称不影响主流程，加载失败时保持回退编码
  }
  try {
    const apps: AppInfo[] = await listApps()
    appNameMap.value = apps.reduce<Record<string, string>>((m, a) => {
      if (a.code) m[a.code] = a.name || a.code
      return m
    }, {})
  } catch {
    // 同上
  }
}

/** 最近 n 天（含今天）的起止日期 */
function recentDays(n: number): [Dayjs, Dayjs] {
  return [dayjs().subtract(n - 1, 'day').startOf('day'), dayjs().endOf('day')]
}

/** 日期范围 → 后端 from/to 查询参数 */
function toRangeQuery(r: [Dayjs, Dayjs] | null | undefined): Pick<StatQuery, 'from' | 'to'> {
  return {
    from: r ? r[0].format('YYYY-MM-DD 00:00:00') : undefined,
    to: r ? r[1].format('YYYY-MM-DD 23:59:59') : undefined
  }
}

/** 后端按天 GROUP BY 只返回有记录的天；这里按查询范围补齐缺失日期为 0，坐标轴才能展示完整时间跨度 */
function padTrendRows(rows: TrendRow[], r: [Dayjs, Dayjs] | null): TrendRow[] {
  if (!r) return rows
  const dataMap = new Map(rows.map((row) => [row.day, row]))
  const out: TrendRow[] = []
  let cur = r[0].startOf('day')
  const end = r[1].startOf('day')
  let guard = 0
  while ((cur.isBefore(end) || cur.isSame(end, 'day')) && guard < 400) {
    const day = cur.format('YYYY-MM-DD')
    out.push(dataMap.get(day) || { day, total: 0, failed: 0 })
    cur = cur.add(1, 'day')
    guard++
  }
  return out
}

/* ---------- 指标卡（受顶部日期过滤） ---------- */
const loading = ref(false)
const range = ref<[Dayjs, Dayjs]>([dayjs(), dayjs()])
const overview = ref<OverviewData>({
  total: 0,
  success: 0,
  failed: 0,
  successRate: 0,
  avgCostMs: 0,
  p95CostMs: 0,
  appCount: 0,
  topFailed: [],
  trend: [],
  recentErrors: [],
  topApps: [],
  topApis: [],
  costDistribution: { lt100: 0, b100_500: 0, b500_1000: 0, b1000_3000: 0, gte3000: 0 }
})

/** successRate 后端可能返回 0~1 小数或 0~100 百分数，统一按百分数展示 */
const rateText = computed(() => {
  const raw = Number(overview.value.successRate || 0)
  const percent = raw > 0 && raw <= 1 ? raw * 100 : raw
  return `${percent.toFixed(1)}%`
})

const statCards = computed(() => [
  { label: '调用总量', value: formatNumber(overview.value.total), color: '#2f54eb' },
  { label: '成功数', value: formatNumber(overview.value.success), color: '#52c41a' },
  { label: '失败数', value: formatNumber(overview.value.failed), color: '#fa541c' },
  { label: '成功率', value: rateText.value, color: '#13a8a8' },
  { label: '平均耗时', value: `${formatNumber(overview.value.avgCostMs)} ms`, color: '#fa8c16' },
  { label: 'P95 耗时', value: `${formatNumber(overview.value.p95CostMs)} ms`, color: '#722ed1' },
  { label: '接入应用数', value: formatNumber(overview.value.appCount), color: '#597ef7' }
])

/* ---------- 卡片独立数据（不跟随顶部日期） ---------- */
const trendLoading = ref(false)
const trendRows = ref<TrendRow[]>([])
/** 卡片趋势默认范围：最近 10 天 */
const trendCardRange = recentDays(10)
/** 补齐空白日期后的趋势数据（图表 + 表格共用，保证 10 天完整展示） */
const trendFilledRows = computed(() => padTrendRows(trendRows.value, trendCardRange))
const topFailedLoading = ref(false)
const topFailedRows = ref<FailedRow[]>([])
const errorsLoading = ref(false)
const recentErrors = ref<RecentError[]>([])

const failedMax = computed(() => Math.max(1, ...topFailedRows.value.map((r) => r.failed)))
function failedPercent(failed: number): number {
  return Math.round((failed / failedMax.value) * 100)
}

const recentErrorColumns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '方向', dataIndex: 'direction', key: 'direction', width: 76 },
  { title: '应用', dataIndex: 'appCode', key: 'appCode', ellipsis: true },
  { title: 'API', dataIndex: 'apiCode', key: 'apiCode', ellipsis: true },
  { title: '状态', dataIndex: 'httpStatus', key: 'httpStatus', width: 70 },
  { title: '业务码', dataIndex: 'bizCode', key: 'bizCode', width: 76 },
  { title: '异常信息', dataIndex: 'errorMsg', key: 'errorMsg', ellipsis: true }
]

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await getOverview({
      from: range.value ? range.value[0].format('YYYY-MM-DD 00:00:00') : undefined,
      to: range.value ? range.value[1].format('YYYY-MM-DD 23:59:59') : undefined
    })
  } catch (e: any) {
    message.error(e?.message || '加载指标统计失败')
  } finally {
    loading.value = false
  }
}

async function loadTrend() {
  trendLoading.value = true
  try {
    trendRows.value = await getTrend(toRangeQuery(recentDays(10)))
  } catch (e: any) {
    message.error(e?.message || '加载调用趋势失败')
  } finally {
    trendLoading.value = false
  }
}

async function loadTopFailed() {
  topFailedLoading.value = true
  try {
    topFailedRows.value = await getTopFailed({ limit: 5 })
  } catch (e: any) {
    message.error(e?.message || '加载失败 API 排行失败')
  } finally {
    topFailedLoading.value = false
  }
}

async function loadRecentErrors() {
  errorsLoading.value = true
  try {
    recentErrors.value = await getRecentErrors({ limit: 5 })
  } catch (e: any) {
    message.error(e?.message || '加载最近异常失败')
  } finally {
    errorsLoading.value = false
  }
}

/** 顶部查询/重置：刷新指标卡，三个卡片按各自默认口径重新取数 */
function load() {
  void loadOverview()
  void loadTrend()
  void loadTopFailed()
  void loadRecentErrors()
}

function reset() {
  range.value = [dayjs(), dayjs()]
  load()
}

/* ---------- 弹窗：调用趋势 ---------- */
const trendModalOpen = ref(false)
const trendModalRange = ref<[Dayjs, Dayjs]>(recentDays(10))
const trendModalLoading = ref(false)
const trendModalRows = ref<TrendRow[]>([])
/** 弹窗按当前查询范围补齐空白日期 */
const trendModalFilledRows = computed(() => padTrendRows(trendModalRows.value, trendModalRange.value))
const trendDayColumns = [
  { title: '日期', dataIndex: 'day', key: 'day', width: 130 },
  { title: '调用量', dataIndex: 'total', key: 'total' },
  { title: '失败量', dataIndex: 'failed', key: 'failed' }
]

function openTrendModal() {
  trendModalRange.value = recentDays(10)
  trendModalOpen.value = true
  void loadTrendModal()
}
async function loadTrendModal() {
  trendModalLoading.value = true
  try {
    trendModalRows.value = await getTrend(toRangeQuery(trendModalRange.value))
  } catch (e: any) {
    message.error(e?.message || '加载调用趋势失败')
  } finally {
    trendModalLoading.value = false
  }
}
function resetTrendModal() {
  trendModalRange.value = recentDays(10)
  void loadTrendModal()
}

/* ---------- 弹窗：失败 TOP API ---------- */
const failedModalOpen = ref(false)
const failedModalRange = ref<[Dayjs, Dayjs] | null>(null)
const failedModalLoading = ref(false)
const failedModalRows = ref<FailedRow[]>([])
const failedRankColumns = [
  { title: '排名', key: 'rank', width: 70, customRender: ({ index }: any) => index + 1 },
  { title: 'API', dataIndex: 'apiCode', key: 'apiCode' },
  { title: '失败次数', dataIndex: 'failed', key: 'failed', width: 120 }
]

function openFailedModal() {
  failedModalRange.value = null
  failedModalOpen.value = true
  void loadFailedModal()
}
async function loadFailedModal() {
  failedModalLoading.value = true
  try {
    failedModalRows.value = await getTopFailed({ ...toRangeQuery(failedModalRange.value), limit: 100 })
  } catch (e: any) {
    message.error(e?.message || '加载失败 API 排行失败')
  } finally {
    failedModalLoading.value = false
  }
}
function resetFailedModal() {
  failedModalRange.value = null
  void loadFailedModal()
}

/* ---------- 弹窗：最近异常 ---------- */
const errorsModalOpen = ref(false)
const errorsModalRange = ref<[Dayjs, Dayjs] | null>(null)
const errorsModalLoading = ref(false)
const errorsModalRows = ref<RecentError[]>([])

function openErrorsModal() {
  errorsModalRange.value = null
  errorsModalOpen.value = true
  void loadErrorsModal()
}
async function loadErrorsModal() {
  errorsModalLoading.value = true
  try {
    errorsModalRows.value = await getRecentErrors({ ...toRangeQuery(errorsModalRange.value), limit: 100 })
  } catch (e: any) {
    message.error(e?.message || '加载最近异常失败')
  } finally {
    errorsModalLoading.value = false
  }
}
function resetErrorsModal() {
  errorsModalRange.value = null
  void loadErrorsModal()
}

onMounted(() => {
  void loadNames()
  load()
})
</script>

<style scoped>
.dash-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  flex-wrap: wrap;
}
.dash-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dash-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-base);
}
.ft-scroll.dash-scroll {
  padding-right: 2px;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}

/* 内容撑满滚动容器高度：用 flex 链替代 min-height:100%
   （嵌套 spin 结构里父级 height 为 auto，百分比 min-height 无法解析，导致撑不满、底部留白） */
.dash-scroll :deep(.ant-spin-nested-loading),
.dash-scroll :deep(.ant-spin-container) {
  flex: 1 0 auto;
  display: flex;
  flex-direction: column;
}
.dash-scroll :deep(.ant-row) {
  margin-left: 0 !important;
  margin-right: 0 !important;
}
.dash-scroll :deep(.ant-col) {
  padding-left: 6px;
  padding-right: 6px;
}
/* gutter 靠 col 的左右 padding 实现，首尾列去掉外侧 padding，和上方指标卡左右边缘对齐 */
.dash-scroll :deep(.ant-col:first-child) {
  padding-left: 0;
}
.dash-scroll :deep(.ant-col:last-child) {
  padding-right: 0;
}
.dash-scroll .chart-row:last-child {
  flex: 1 0 auto;
}
.dash-scroll .chart-row:last-child :deep(.ant-col),
.dash-scroll .chart-row:last-child .chart-card {
  height: 100%;
}

/* 指标卡自适应网格：窄屏 2 列，宽屏最多 7 列 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}
.stat-card :deep(.ant-card-body) {
  padding: 14px 16px;
}
.stat-main {
  display: flex;
  align-items: center;
  gap: 10px;
}
.stat-mark {
  width: 8px;
  height: 38px;
  border-radius: 4px;
  flex: 0 0 auto;
}
.stat-text {
  min-width: 0;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--text-base);
  font-variant-numeric: tabular-nums;
}
.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--muted);
}

.chart-row {
  margin-top: 12px;
}
.chart-card {
  height: 100%;
}
.dash-scroll .chart-row:last-child :deep(.ant-card) {
  display: flex;
  flex-direction: column;
}
.dash-scroll .chart-row:last-child :deep(.ant-card-body) {
  flex: 1;
  min-height: 240px;
}
.chart-card :deep(.ant-card-body) {
  min-height: 240px;
}
.chart-extra {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.chart-legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot.total { background: #2f54eb; }
.dot.fail { background: #fa541c; }

.bar-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0;
}
.bar-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.bar-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}
.bar-head span {
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar-track {
  height: 8px;
  border-radius: 4px;
  background: var(--border-subtle, #f0f0f0);
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 4px;
  background: #fa541c;
  transition: width 0.4s ease;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

/* 弹窗 */
.modal-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.modal-hint {
  font-size: 12px;
  color: var(--muted);
}
.modal-table {
  margin-top: 16px;
}
</style>
