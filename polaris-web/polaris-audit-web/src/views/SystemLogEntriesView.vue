<template>
  <div class="ft-page">
    <div class="toolbar">
      <a-input-search
        v-model:value="keyword"
        placeholder="日志内容 / logger / 线程"
        style="width: 220px"
        allow-clear
        @search="reload"
      />
      <a-select v-model:value="app" style="width: 170px" :options="appOptions" @change="reload" />
      <a-select v-model:value="level" style="width: 140px" :options="levelOptions" @change="reload" />
      <a-range-picker v-model:value="dateRange" style="width: 260px" allow-clear @change="reload" />
      <a-button @click="reload">刷新</a-button>
      <a-button @click="router.push({ name: 'log-files' })">按文件查看</a-button>
    </div>

    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 12px"
      :message="alertMessage"
    />
    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom: 12px" />

    <section class="ft-scroll ft-bottom">
      <a-table
        :columns="columns"
        :data-source="records"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        :expanded-row-keys="expandedKeys"
        @expand="handleExpand"
        @change="handleTableChange"
      >
        <template #expandedRowRender="{ record }">
          <div class="stack-panel">
            <div class="stack-head">
              <span>堆栈 / 上下文</span>
              <span class="muted">{{ appLabel(record.app) }} · {{ record.logger }}</span>
            </div>
            <pre v-if="record.stackTrace" class="stack-code">{{ record.stackTrace }}</pre>
            <span v-else class="muted">该条日志没有堆栈信息</span>
          </div>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'app'">
            <span class="app-cell">{{ appLabel(record.app) }}</span>
          </template>
          <template v-else-if="column.key === 'level'">
            <a-tag :color="levelColor(record.level)">{{ record.level }}</a-tag>
          </template>
          <template v-else-if="column.key === 'logger'">
            <a-tooltip :title="record.logger"><span class="cell-ellipsis">{{ record.logger }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'thread'">
            <a-tooltip :title="record.thread"><span class="cell-ellipsis">{{ record.thread }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'message'">
            <a-tooltip :title="record.message"><span class="message-cell">{{ record.message }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'operation'">
            <a-button
              type="link"
              size="small"
              :disabled="!record.stackTrace"
              @click="toggleStack(record)"
            >
              查看堆栈
            </a-button>
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { useRouter } from 'vue-router'
import {
  fetchSystemLogs,
  fetchSystemLogApps,
  fetchSystemLogOverview,
  type SystemLog,
  type SystemLogOverview
} from '@/api/audit'
import { appLabel, ensureAppNames } from '@/utils/appLabels'

const router = useRouter()

const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '级别', key: 'level', width: 80 },
  { title: '应用', dataIndex: 'app', key: 'app', width: 180 },
  { title: 'logger', dataIndex: 'logger', key: 'logger', width: 180 },
  { title: '线程', dataIndex: 'thread', key: 'thread', width: 140 },
  { title: '日志内容', key: 'message' },
  { title: '操作', key: 'operation', width: 100 }
]
const levelOptions = [
  { label: '全部级别', value: '' },
  { label: 'ERROR', value: 'ERROR' },
  { label: 'WARN', value: 'WARN' },
  { label: 'INFO', value: 'INFO' },
  { label: 'DEBUG', value: 'DEBUG' }
]

const records = ref<SystemLog[]>([])
const loading = ref(false)
const errorMsg = ref('')
const keyword = ref('')
const app = ref('')
const level = ref('ERROR')
const dateRange = ref<[Dayjs, Dayjs] | null>([dayjs().subtract(6, 'day').startOf('day'), dayjs()])
const appOptions = ref<Array<{ label: string; value: string }>>([{ label: '全部应用', value: '' }])
const overview = ref<SystemLogOverview | null>(null)
const total = ref(0)
const current = ref(1)
const pageSize = ref(20)
const expandedKeys = ref<string[]>([])

const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

const alertMessage = computed(() => {
  const o = overview.value
  const stat = o ? `今日 ${o.today} 条，ERROR ${o.error} 条。` : ''
  return `各服务运行日志自动汇总上报，默认只采集 WARN/ERROR（可按应用调整）。口令/令牌已脱敏。${stat}`
})

onMounted(() => {
  void ensureAppNames()
  void loadApps()
  void loadOverview()
  void load()
})

function reload() {
  current.value = 1
  expandedKeys.value = []
  void load()
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetchSystemLogs({
      page: current.value,
      size: pageSize.value,
      app: app.value || undefined,
      level: level.value || undefined,
      keyword: keyword.value || undefined,
      startDate: dateRange.value?.[0]?.format('YYYY-MM-DD'),
      endDate: dateRange.value?.[1]?.format('YYYY-MM-DD')
    })
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
    records.value = []
  } finally {
    loading.value = false
  }
}

async function loadApps() {
  try {
    const res = await fetchSystemLogApps()
    appOptions.value = [
      { label: '全部应用', value: '' },
      ...(res.data || []).map((a) => ({ label: appLabel(a), value: a }))
    ]
  } catch {
    // 筛选项加载失败不阻断列表
  }
}

async function loadOverview() {
  try {
    const res = await fetchSystemLogOverview()
    overview.value = res.data || null
  } catch {
    // 汇总条数只作提示，失败不影响主列表
  }
}

function handleTableChange(pag: any) {
  current.value = pag.current
  pageSize.value = pag.pageSize
  expandedKeys.value = []
  void load()
}

function handleExpand(expanded: boolean, record: SystemLog) {
  if (expanded) {
    if (!expandedKeys.value.includes(record.id)) expandedKeys.value = [...expandedKeys.value, record.id]
  } else {
    expandedKeys.value = expandedKeys.value.filter((id) => id !== record.id)
  }
}

function toggleStack(record: SystemLog) {
  handleExpand(!expandedKeys.value.includes(record.id), record)
}

function levelColor(level?: string) {
  if (level === 'ERROR') return 'red'
  if (level === 'WARN') return 'orange'
  if (level === 'INFO') return 'blue'
  return 'default'
}
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.cell-ellipsis { display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.app-cell { font-weight: 600; white-space: nowrap; }
.message-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
  line-height: 1.5;
}
.stack-panel { padding: 4px 8px 8px 42px; }
.stack-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
}
.stack-code {
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 10px 12px;
  border-radius: 4px;
  background: var(--code-bg);
  color: var(--text-base);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.muted { color: var(--muted); font-size: 12px; }
</style>
