<template>
  <div class="ft-page">
    <section class="ft-scroll overview-scroll">
    <a-row :gutter="14" class="stat-row">
      <a-col :span="6">
        <a-card class="stat-card">
          <div class="stat-label"><span class="dot" style="background:#7c5cff"></span>文件总数</div>
          <div class="stat-num">{{ fmt(overview.total) }}</div>
          <div class="stat-sub">今日新增 {{ fmt(overview.todayCount || 0) }}</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card">
          <div class="stat-label"><span class="dot" style="background:#52c41a"></span>公开文件</div>
          <div class="stat-num">{{ fmt(overview.publicCount) }}</div>
          <div class="stat-sub">占 {{ pct(overview.publicCount) }}% · 可匿名直链</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card">
          <div class="stat-label"><span class="dot" style="background:#1677ff"></span>私有文件</div>
          <div class="stat-num">{{ fmt(overview.privateCount) }}</div>
          <div class="stat-sub">占 {{ pct(overview.privateCount) }}% · 须签名访问</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card">
          <div class="stat-label"><span class="dot" style="background:#f59e0b"></span>占用空间</div>
          <div class="stat-num">{{ mb.toFixed(2) }}<span class="unit">MB</span></div>
          <div class="stat-sub">字节落本地磁盘 · 软删延后清理</div>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="14" style="margin-top:14px">
      <a-col :span="12">
        <a-card class="panel">
          <template #title>
            <span class="card-title">存储分布</span>
            <a-tooltip trigger="hover" placement="top">
              <template #title>
                <div class="hint-tip">
                  <p>按业务类型（bizType）配置大小上限、允许扩展名与默认可见性。</p>
                  <p>EHR 附件强制私有；公开文件走签名直链，私有文件必须持签名。</p>
                  <p>删除为软删，字节文件由孤儿清理任务按保留期延后物理删除。</p>
                </div>
              </template>
              <InfoCircleOutlined class="title-info" />
            </a-tooltip>
          </template>
          <div class="bars">
            <div class="bar-row">
              <span class="bar-label">公开</span>
              <div class="bar-track"><div class="bar-fill public" :style="{ width: pct(overview.publicCount) + '%' }"></div></div>
              <span class="bar-val">{{ fmt(overview.publicCount) }} · {{ pct(overview.publicCount) }}%</span>
            </div>
            <div class="bar-row">
              <span class="bar-label">私有</span>
              <div class="bar-track"><div class="bar-fill private" :style="{ width: pct(overview.privateCount) + '%' }"></div></div>
              <span class="bar-val">{{ fmt(overview.privateCount) }} · {{ pct(overview.privateCount) }}%</span>
            </div>
          </div>
          <div class="panel-foot">公开文件可匿名直链访问，私有文件须持短时效签名。</div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card class="panel">
          <template #title><span class="card-title">近 10 天上传统计</span></template>
          <template #extra>
            <a-button type="link" size="small" @click="openTrendDrawer">查看更多</a-button>
          </template>
          <div v-if="trendRows.length" class="bars">
            <div v-for="row in trendRows" :key="row.day" class="bar-row">
              <span class="bar-label">{{ String(row.day).slice(5) }}</span>
              <div class="bar-track"><div class="bar-fill alt" :style="{ width: trendWidth(row.cnt) }"></div></div>
              <span class="bar-val">{{ fmt(row.cnt) }}</span>
            </div>
          </div>
          <a-empty v-else description="近 10 天暂无上传" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="14" style="margin-top:14px">
      <a-col :span="24">
        <a-card title="按存储策略统计" class="panel">
          <a-table
            :columns="policyColumns"
            :data-source="policyRows"
            :pagination="false"
            row-key="bizType"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'cnt'">{{ fmt(record.cnt) }}</template>
              <template v-else-if="column.key === 'bytes'">{{ sizeLabel(record.bytes) }}</template>
              <template v-else-if="column.key === 'visibility'">
                <a-tag :color="record.forcePrivate || record.visibility === 'PRIVATE' ? 'blue' : 'green'">
                  {{ record.forcePrivate || record.visibility === 'PRIVATE' ? '私有' : '公开' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="14" style="margin-top:14px">
      <a-col :span="24">
        <a-card class="panel">
          <template #title><span class="card-title">最近上传</span></template>
          <template #extra>
            <a-button type="link" size="small" @click="goFiles">查看更多</a-button>
          </template>
          <a-table
            :columns="recentColumns"
            :data-source="overview.recent || []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'size'">{{ sizeLabel(record.size) }}</template>
              <template v-else-if="column.key === 'visibility'">
                <a-tag :color="record.visibility === 'PUBLIC' ? 'green' : 'blue'">
                  {{ record.visibility === 'PUBLIC' ? '公开' : '私有' }}
                </a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
    </section>

    <a-drawer
      v-model:open="trendDrawerOpen"
      title="附件上传统计"
      width="520"
      :body-style="{ paddingBottom: 24 }"
    >
      <div class="drawer-filter">
        <span class="filter-label">统计范围</span>
        <a-range-picker
          v-model:value="trendRange"
          style="width: 100%"
          :allow-clear="false"
          @change="onTrendRangeChange"
        />
      </div>
      <a-alert v-if="trendError" type="error" :message="trendError" show-icon style="margin-bottom: 12px" />
      <a-row :gutter="10" class="drawer-summary">
        <a-col :span="8"><div class="mini-card"><div class="mini-label">区间上传</div><div class="mini-num">{{ fmt(trendTotal) }}</div></div></a-col>
        <a-col :span="8"><div class="mini-card"><div class="mini-label">日均</div><div class="mini-num">{{ trendAvg }}</div></div></a-col>
        <a-col :span="8"><div class="mini-card"><div class="mini-label">单日峰值</div><div class="mini-num">{{ fmt(trendMax) }}</div></div></a-col>
      </a-row>
      <a-table
        :columns="trendColumns"
        :data-source="trendDrawerRows"
        :pagination="false"
        :loading="trendLoading"
        row-key="day"
        size="small"
        :scroll="{ y: 420 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'cnt'">{{ fmt(record.cnt) }}</template>
          <template v-else-if="column.key === 'bytes'">{{ sizeLabel(record.bytes) }}</template>
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { InfoCircleOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { getOverview, getUploadTrend, listPolicies } from '@/api/file'
import type { DailyUploadStat, Overview, StoragePolicy } from '@/api/file'

const overview = reactive<Overview>({
  total: 0, publicCount: 0, privateCount: 0, totalBytes: 0,
  todayCount: 0, byBizType: [], trend: [], recent: []
})
const policies = ref<StoragePolicy[]>([])
const router = useRouter()

const recentColumns = [
  { title: '文件名', dataIndex: 'originalName', key: 'originalName', ellipsis: true },
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType', width: 130 },
  { title: '可见性', key: 'visibility', width: 80 },
  { title: '大小', key: 'size', width: 80 },
  { title: '上传时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 }
]
const policyColumns = [
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType', width: 150 },
  { title: '策略名称', dataIndex: 'bizName', key: 'bizName', ellipsis: true },
  { title: '可见性', key: 'visibility', width: 90 },
  { title: '大小上限', dataIndex: 'maxSizeMb', key: 'maxSizeMb', width: 100 },
  { title: '允许扩展名', dataIndex: 'allowedExt', key: 'allowedExt', width: 170, ellipsis: true },
  { title: '状态', key: 'enabled', width: 80 },
  { title: '文件数', key: 'cnt', width: 90 },
  { title: '占用空间', key: 'bytes', width: 110 }
]
const trendColumns = [
  { title: '日期', dataIndex: 'day', key: 'day', width: 130 },
  { title: '上传数', key: 'cnt', width: 100 },
  { title: '字节数', key: 'bytes' }
]

interface PolicyStatRow {
  bizType: string
  bizName: string
  visibility: string
  forcePrivate: boolean
  maxSizeMb: number
  allowedExt: string
  enabled: boolean
  cnt: number
  bytes: number
}

const mb = computed(() => overview.totalBytes / 1024 / 1024)
const trendRows = computed(() => overview.trend || [])
const maxTrend = computed(() => Math.max(1, ...trendRows.value.map(r => Number(r.cnt) || 0)))
const policyRows = computed<PolicyStatRow[]>(() => {
  const stats = new Map<string, { cnt: number; bytes: number }>()
  for (const row of overview.byBizType || []) {
    stats.set(row.bizType, { cnt: Number(row.cnt) || 0, bytes: Number(row.bytes) || 0 })
  }
  const rows: PolicyStatRow[] = policies.value.map(p => ({
    bizType: p.bizType,
    bizName: p.bizName || p.bizType,
    visibility: p.defaultVisibility,
    forcePrivate: !!p.forcePrivate,
    maxSizeMb: Number(p.maxSizeMb) || 0,
    allowedExt: p.allowedExt || '',
    enabled: !!p.enabled,
    cnt: stats.get(p.bizType)?.cnt || 0,
    bytes: stats.get(p.bizType)?.bytes || 0
  }))
  for (const [bizType, stat] of stats) {
    if (!rows.some(r => r.bizType === bizType)) {
      rows.push({
        bizType, bizName: bizLabel(bizType), visibility: '', forcePrivate: false,
        maxSizeMb: 0, allowedExt: '', enabled: false, cnt: stat.cnt, bytes: stat.bytes
      })
    }
  }
  return rows.sort((a, b) => b.cnt - a.cnt)
})

const trendDrawerOpen = ref(false)
const trendLoading = ref(false)
const trendError = ref('')
const trendRange = ref<Dayjs[]>([])
const trendDrawerRows = ref<DailyUploadStat[]>([])
const trendTotal = computed(() => trendDrawerRows.value.reduce((sum, r) => sum + (Number(r.cnt) || 0), 0))
const trendAvg = computed(() => trendDrawerRows.value.length
  ? Math.round(trendTotal.value / trendDrawerRows.value.length) : 0)
const trendMax = computed(() => Math.max(0, ...trendDrawerRows.value.map(r => Number(r.cnt) || 0)))

onMounted(async () => {
  try {
    Object.assign(overview, await getOverview())
  } catch { /* 忽略：概览为空时展示 0 */ }
  try {
    policies.value = await listPolicies()
  } catch { /* 忽略：中文名映射失败时回退 bizType */ }
})

function goFiles() { void router.push('/files') }

function fmt(n: number | string | undefined) { return Number(n || 0).toLocaleString() }
function pct(n: number | undefined) { return overview.total ? Math.round(Number(n || 0) * 100 / overview.total) : 0 }
function trendWidth(cnt: number) { return `${Math.max(2, (Number(cnt) / maxTrend.value) * 100)}%` }
function sizeLabel(bytes: number | undefined) {
  const n = Number(bytes || 0)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}
function bizLabel(type: string) {
  return policies.value.find(p => p.bizType === type)?.bizName
    || ({ ehr_attachment: 'EHR 员工附件', cms_media: 'CMS 媒体', forum_post: '论坛附件', openapi_export: '开放平台导出' } as Record<string, string>)[type]
    || type
}

function openTrendDrawer() {
  trendError.value = ''
  trendRange.value = [dayjs().subtract(6, 'day').startOf('day'), dayjs()]
  trendDrawerOpen.value = true
  void loadTrend()
}

function onTrendRangeChange() {
  trendError.value = ''
  void loadTrend()
}

async function loadTrend() {
  const range = trendRange.value
  if (range.length !== 2 || !range[0] || !range[1]) {
    trendError.value = '请选择开始和结束日期'
    return
  }
  trendLoading.value = true
  try {
    trendDrawerRows.value = await getUploadTrend(range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD'))
  } catch (e) {
    trendError.value = e instanceof Error ? e.message : '统计加载失败'
    trendDrawerRows.value = []
  } finally {
    trendLoading.value = false
  }
}
</script>

<style scoped>
/* 概览页内容自适应宽度：只保留纵向滚动，禁止任何层级出现横向滚动条 */
.overview-scroll { overflow-x: hidden; overflow-y: auto; }
.overview-scroll .ant-card-body,
.overview-scroll .ant-table-content,
.overview-scroll .ant-table-body {
  overflow-x: hidden !important;
  max-width: 100%;
}

.stat-row .stat-card { border-radius: 10px; }
.stat-label { font-size: 12px; color: var(--muted); display: flex; align-items: center; gap: 7px; }
.stat-num { font-size: 28px; font-weight: 600; margin-top: 8px; font-variant-numeric: tabular-nums; }
.stat-num .unit { font-size: 14px; font-weight: 400; margin-left: 4px; color: var(--muted); }
.stat-sub { font-size: 11px; color: var(--muted); margin-top: 4px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.panel { border-radius: 10px; min-height: 240px; }
.card-title { font-weight: 600; }
.title-info { margin-left: 6px; color: var(--muted); font-size: 13px; cursor: help; }
.hint-tip { max-width: 300px; line-height: 1.7; font-size: 12px; }
.hint-tip p { margin: 0 0 6px; }
.hint-tip p:last-child { margin-bottom: 0; }
.bars { display: flex; flex-direction: column; gap: 12px; padding: 4px 0; }
.bar-row { display: flex; align-items: center; gap: 10px; font-size: 12px; }
.bar-label { width: 110px; flex-shrink: 0; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-track { flex: 1; height: 12px; background: var(--border-subtle); border-radius: 4px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 4px; }
.bar-fill.public { background: linear-gradient(90deg, #52c41a, #95de64); }
.bar-fill.private { background: linear-gradient(90deg, #1677ff, #22d3ee); }
.bar-fill.alt { background: linear-gradient(90deg, #7c5cff, #22d3ee); }
.bar-val { width: 110px; text-align: right; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.panel-foot { margin-top: 14px; font-size: 11px; color: var(--muted); }
.drawer-filter { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.filter-label { font-size: 12px; color: var(--muted); }
.drawer-summary { margin-bottom: 14px; }
.mini-card { border: 1px solid var(--border-subtle); border-radius: 8px; padding: 10px; }
.mini-label { font-size: 11px; color: var(--muted); }
.mini-num { font-size: 20px; font-weight: 600; margin-top: 4px; font-variant-numeric: tabular-nums; }
</style>
