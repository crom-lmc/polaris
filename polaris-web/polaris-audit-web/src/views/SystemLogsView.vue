<template>
  <div class="ft-page">
    <div class="toolbar">
      <a-select v-model:value="app" style="width: 170px" :options="appOptions" @change="loadGroups" />
      <a-range-picker v-model:value="dateRange" style="width: 260px" :allow-clear="true" @change="loadGroups" />
      <span class="hint">按「应用 + 日期」归档，点「查看」以终端风格浏览当天日志</span>
      <a-button style="margin-left: auto" @click="router.push({ name: 'system-logs' })">按条目查看</a-button>
      <a-button @click="loadAll">刷新</a-button>
    </div>

    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 12px"
      :message="`各服务运行日志由 Logback 异步批量上报（INFO/WARN/ERROR 全量，口令令牌已脱敏）。列表每行代表某应用某天的日志文件，共 ${groups.length} 个。`"
    />
    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom: 12px" />

    <section class="ft-scroll ft-bottom">
      <a-table
        :columns="cols"
        :data-source="groups"
        :loading="loading"
        row-key="rowKey"
        :pagination="{ pageSize: 20, showTotal: (t: number) => `共 ${t} 个日志文件` }"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'app'">
            <span class="app-cell">{{ appLabel(record.app) }}</span>
          </template>
          <template v-else-if="column.key === 'err'">
            <span :class="{ 'num-danger': record.err > 0 }">{{ record.err }}</span>
          </template>
          <template v-else-if="column.key === 'warn'">
            <span :class="{ 'num-warn': record.warn > 0 }">{{ record.warn }}</span>
          </template>
          <template v-else-if="column.key === 'range'">
            <span class="muted">{{ rangeText(record) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openLog(record)">查看</a-button>
          </template>
        </template>
      </a-table>
    </section>

    <!-- 终端风格日志查看大抽屉 -->
    <a-drawer
      v-model:open="drawerOpen"
      :width="drawerWidth"
      :title="null"
      :closable="false"
      placement="right"
      class="log-drawer"
    >
      <template #title>
        <div class="drawer-title">
          <span class="dot" :class="drawerLevelClass"></span>
          <b>{{ appLabel(currentApp) }}</b>
          <span class="sep">/</span>
          <span>{{ currentDay }}</span>
          <span class="sub">日志流</span>
        </div>
      </template>

      <div class="term-toolbar">
        <a-select v-model:value="streamLevel" style="width: 110px" :options="levelOptions" @change="loadStream" />
        <a-input-search
          v-model:value="streamKeyword"
          placeholder="搜索内容 / 堆栈"
          style="width: 220px"
          allow-clear
          @search="loadStream"
        />
        <a-select v-model:value="streamLimit" style="width: 130px" :options="limitOptions" @change="loadStream" />
        <a-button @click="loadStream">刷新</a-button>
        <a-button @click="downloadLog">下载</a-button>
        <span class="term-count">{{ streamRows.length }} 行</span>
        <a-button type="text" class="close-btn" @click="drawerOpen = false">✕</a-button>
      </div>

      <div class="terminal" ref="terminalRef">
        <div v-if="streamLoading" class="term-empty">加载中…</div>
        <div v-else-if="!streamRows.length" class="term-empty">当天没有匹配的日志</div>
        <template v-else>
          <div v-for="(row, i) in streamRows" :key="row.id" class="log-line" :class="'lv-' + lowLevel(row.level)">
            <span class="ln">{{ i + 1 }}</span>
            <span class="ts">{{ shortTime(row.createdAt) }}</span>
            <span class="lv">{{ row.level }}</span>
            <span class="thr">[{{ row.thread }}]</span>
            <span class="msg">{{ row.message }}</span>
            <template v-if="row.stackTrace">
              <div v-for="(sl, si) in splitStack(row.stackTrace)" :key="si" class="log-line stack-line">
                <span class="ln"></span><span class="stack-text">{{ sl }}</span>
              </div>
            </template>
          </div>
        </template>
      </div>
      <div class="term-footer">
        <span>时间正序 · 最近 {{ streamLimit }} 条 · 已脱敏</span>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { useRouter } from 'vue-router'
import {
  fetchLogFileGroups, fetchLogStream, fetchSystemLogApps,
  type LogFileGroup, type SystemLog
} from '@/api/audit'
import { appLabel, ensureAppNames } from '@/utils/appLabels'

const router = useRouter()

interface GroupRow extends LogFileGroup { rowKey: string }

const cols = [
  { title: '应用', key: 'app', width: 190 },
  { title: '日期', dataIndex: 'day', key: 'day', width: 130 },
  { title: '日志条数', dataIndex: 'cnt', key: 'cnt', width: 110 },
  { title: 'ERROR', dataIndex: 'err', key: 'err', width: 100 },
  { title: 'WARN', dataIndex: 'warn', key: 'warn', width: 100 },
  { title: '起止时间', key: 'range' },
  { title: '操作', key: 'action', width: 90 }
]
const levelOptions = [
  { label: '全部级别', value: '' },
  { label: 'ERROR', value: 'ERROR' },
  { label: 'WARN', value: 'WARN' },
  { label: 'INFO', value: 'INFO' },
  { label: 'DEBUG', value: 'DEBUG' }
]
const limitOptions = [
  { label: '最近 300 行', value: 300 },
  { label: '最近 1000 行', value: 1000 },
  { label: '最近 3000 行', value: 3000 },
  { label: '最近 5000 行', value: 5000 }
]

const groups = ref<GroupRow[]>([])
const appOptions = ref<Array<{ label: string; value: string }>>([{ label: '全部应用', value: '' }])
const loading = ref(false)
const errorMsg = ref('')
const app = ref('')
const dateRange = ref<[Dayjs, Dayjs] | null>([dayjs().subtract(6, 'day').startOf('day'), dayjs()])

const drawerOpen = ref(false)
const drawerWidth = 'min(1100px, 92vw)'
const currentApp = ref('')
const currentDay = ref('')
const streamLevel = ref('')
const streamKeyword = ref('')
const streamLimit = ref(1000)
const streamRows = ref<SystemLog[]>([])
const streamLoading = ref(false)
const terminalRef = ref<HTMLElement | null>(null)

const drawerLevelClass = computed(() => {
  const e = streamRows.value.filter(r => r.level === 'ERROR').length
  return e > 0 ? 'has-error' : ''
})

onMounted(() => { void ensureAppNames(); loadAll(); loadApps() })

function loadAll() { loadGroups() }

async function loadGroups() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetchLogFileGroups({
      app: app.value || undefined,
      startDate: dateRange.value?.[0]?.format('YYYY-MM-DD'),
      endDate: dateRange.value?.[1]?.format('YYYY-MM-DD')
    })
    groups.value = (res.data || []).map(g => ({ ...g, rowKey: `${g.app}_${g.day}` }))
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
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
  } catch { /* 筛选项加载失败不阻断列表 */ }
}

function openLog(record: GroupRow) {
  currentApp.value = record.app
  currentDay.value = record.day
  streamLevel.value = ''
  streamKeyword.value = ''
  streamLimit.value = 1000
  drawerOpen.value = true
  loadStream()
}

async function loadStream() {
  if (!currentApp.value || !currentDay.value) return
  streamLoading.value = true
  try {
    const res = await fetchLogStream({
      app: currentApp.value,
      day: currentDay.value,
      level: streamLevel.value || undefined,
      keyword: streamKeyword.value || undefined,
      limit: streamLimit.value
    })
    streamRows.value = res.data || []
    await nextTick()
    if (terminalRef.value) terminalRef.value.scrollTop = 0
  } catch (e: any) {
    errorMsg.value = e?.message || '加载日志流失败'
    streamRows.value = []
  } finally {
    streamLoading.value = false
  }
}

function downloadLog() {
  const header = '# 时间        级别   线程                    内容\n'
  const lines = streamRows.value.map(r => {
    const stack = r.stackTrace ? '\n' + r.stackTrace : ''
    return `${fmtTime(r.createdAt)}  ${r.level.padEnd(5)}  [${r.thread}]  ${r.message}${stack}`
  })
  const blob = new Blob([header + lines.join('\n')], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${currentApp.value}_${currentDay.value}.log`
  a.click()
  URL.revokeObjectURL(url)
}

function lowLevel(l?: string) { return (l || '').toLowerCase() }
function shortTime(s?: string) {
  if (!s) return ''
  // 2026-11-24T09:37:32.123 -> 09:37:32.123
  const t = s.replace(' ', 'T')
  const idx = t.indexOf('T')
  return idx >= 0 ? t.slice(idx + 1, idx + 13) : s
}
function fmtTime(s?: string) { return s ? s.replace('T', ' ').slice(0, 23) : '' }
function splitStack(s?: string) { return s ? s.split(/\r?\n/).filter(Boolean) : [] }
function rangeText(row: GroupRow) {
  return `${shortTime(row.first_at)} ~ ${shortTime(row.last_at)}`
}
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.hint { color: var(--muted); font-size: 12px; }
.app-cell { font-weight: 600; }
.num-danger { color: #e5484d; font-weight: 700; }
.num-warn { color: #f5a524; font-weight: 700; }

/* 抽屉标题 */
.drawer-title { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.drawer-title .dot { width: 8px; height: 8px; border-radius: 50%; background: #30a46c; }
.drawer-title .dot.has-error { background: #e5484d; }
.drawer-title .sep { color: var(--muted); }
.drawer-title .sub { color: var(--muted); font-size: 12px; font-weight: 400; }

/* 终端工具条 */
.term-toolbar { display: flex; gap: 8px; align-items: center; padding-bottom: 12px; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.term-count { color: var(--muted); font-size: 12px; margin-left: 4px; }
.term-toolbar .close-btn { margin-left: auto; font-size: 16px; }

/* 终端区：深色底、等宽字、行号 */
.terminal {
  margin-top: 12px;
  height: calc(100vh - 210px);
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  padding: 10px 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
}
.term-empty { padding: 40px; text-align: center; color: #808080; }
.log-line { display: flex; flex-wrap: wrap; padding: 0 12px; white-space: pre-wrap; word-break: break-all; }
.log-line:hover { background: #2a2a2a; }
.log-line .ln { flex: 0 0 42px; text-align: right; padding-right: 12px; color: #6e6e6e; user-select: none; }
.log-line .ts { flex: 0 0 96px; color: #858585; }
.log-line .lv { flex: 0 0 52px; font-weight: 700; }
.log-line .thr { flex: 0 0 190px; color: #9cdcfe; overflow: hidden; text-overflow: ellipsis; }
.log-line .msg { flex: 1 1 100%; padding-left: 42px; }
.log-line.lv-error .lv { color: #f14c4c; }
.log-line.lv-error .msg { color: #f48771; }
.log-line.lv-warn .lv { color: #cca700; }
.log-line.lv-warn .msg { color: #dcdcaa; }
.log-line.lv-info .lv { color: #4ec9b0; }
.log-line.lv-debug .lv { color: #808080; }
.stack-line { padding-left: 0; }
.stack-line .stack-text { flex: 1 1 100%; padding-left: 42px; color: #9a9a9a; }
.term-footer { margin-top: 8px; color: var(--muted); font-size: 11px; text-align: right; }
</style>
