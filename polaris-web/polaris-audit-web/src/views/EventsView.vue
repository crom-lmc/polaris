<template>
  <div class="ft-page">
    <div class="toolbar">
      <a-input-search
        v-model:value="keyword"
        placeholder="操作人 / 动作"
        style="width: 220px"
        allow-clear
        @search="handleSearch"
      />
      <a-select v-model:value="app" style="width: 190px" :options="appOptions" @change="handleSearch" />
      <a-select v-model:value="category" style="width: 150px" :options="categoryOptions" @change="handleSearch" />
      <a-select v-model:value="result" style="width: 120px" :options="resultOptions" @change="handleSearch" />
      <a-range-picker v-model:value="dateRange" style="width: 260px" @change="handleSearch" />
      <a-button @click="load">刷新</a-button>
    </div>

    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 12px"
      message="所有登录后动作全部记录（GET 也算）；网关兜底全量并记录入参（脱敏）、HTTP 状态与耗时，业务层补业务码与语义；点行首箭头查看完整入参/出参。内部调用记为 SYSTEM，登录事件记为尝试登录的用户名。"
    />
    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom: 12px" />

    <section class="ft-scroll ft-bottom">
      <a-table
        :columns="columns"
        :data-source="records"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
      :scroll="{ x: 2020 }"
      @change="handleTableChange"
    >
        <template #expandedRowRender="{ record }">
          <div class="detail-panel">
            <div class="detail-row"><span class="detail-label">请求入参</span><pre class="detail-code">{{ record.requestParams || '（未采集）' }}</pre></div>
            <div class="detail-row"><span class="detail-label">响应结果</span><pre class="detail-code">{{ record.responseSummary || '（未采集）' }}</pre></div>
            <div class="detail-grid">
              <span><b>HTTP 状态：</b>{{ record.httpStatus ?? '-' }}</span>
              <span><b>业务码：</b>{{ record.bizCode ?? '-' }}</span>
              <span><b>耗时：</b>{{ record.costMs != null ? record.costMs + ' ms' : '-' }}</span>
              <span><b>结果：</b>{{ eventResult(record) === 'fail' ? '失败' : '成功' }}</span>
            </div>
            <div class="detail-row"><span class="detail-label">详情</span><span class="detail-text">{{ record.detail || '-' }}</span></div>
            <div class="detail-row"><span class="detail-label">完整路径</span><span class="detail-text detail-mono">{{ record.requestPath || '-' }}</span></div>
            <div class="detail-row"><span class="detail-label">User-Agent</span><span class="detail-text">{{ record.userAgent || '-' }}</span></div>
          </div>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'app'">
            <span class="app-cell">{{ appLabel(record.app) }}</span>
          </template>
          <template v-else-if="column.key === 'category'">
            <a-tag :color="categoryColor(record.eventCategory)">{{ categoryLabel(record.eventCategory) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'result'">
            <a-tag :color="eventResult(record) === 'fail' ? 'red' : 'green'">
              {{ eventResult(record) === 'fail' ? '失败' : '成功' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'operator'">
            <span>{{ record.operatorName }}</span>
            <a-tag v-if="record.operatorType === 'SYSTEM'" color="default" style="margin-left: 6px">内部</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-tooltip :title="record.requestPath"><span class="action-cell">{{ record.action }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'params'">
            <a-tooltip v-if="record.requestParams" :title="record.requestParams">
              <span class="cell-ellipsis muted">{{ record.requestParams }}</span>
            </a-tooltip>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <span :class="statusClass(record.httpStatus)">{{ record.httpStatus ?? '-' }}</span>
          </template>
          <template v-else-if="column.key === 'cost'">
            <span class="muted">{{ record.costMs != null ? record.costMs + 'ms' : '-' }}</span>
          </template>
          <template v-else-if="column.key === 'detail'">
            <a-tooltip v-if="record.detail" :title="record.detail"><span class="cell-ellipsis muted">{{ record.detail }}</span></a-tooltip>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'userAgent'">
            <a-tooltip v-if="record.userAgent" :title="record.userAgent"><span class="cell-ellipsis ua">{{ record.userAgent }}</span></a-tooltip>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'operation'">
            <a-space :size="4">
              <a-button type="link" size="small" :disabled="!record.requestParams" @click="openDrawer('request', record)">入参</a-button>
              <a-button type="link" size="small" :disabled="!record.responseSummary" @click="openDrawer('response', record)">响应</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </section>

    <a-drawer
      v-model:open="drawerOpen"
      :title="drawerTitle"
      width="560"
      :body-style="{ padding: '16px 24px' }"
    >
      <template #extra>
        <a-button size="small" :disabled="drawerEmpty" @click="copyJson">复制</a-button>
      </template>
      <a-alert
        v-if="drawerEmpty"
        type="info"
        show-icon
        message="该记录未采集到此字段（网关仅在能读到请求体/响应体时记录，且已脱敏）。"
        style="margin-bottom: 12px"
      />
      <pre v-else class="json-view">{{ drawerJson }}</pre>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import { fetchAuditEvents, type AuditEvent } from '@/api/audit'
import { appCodes, appLabel, ensureAppNames } from '@/utils/appLabels'

const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '类别', key: 'category', width: 90 },
  { title: '来源', dataIndex: 'app', key: 'app', width: 170 },
  { title: '操作人', key: 'operator', width: 140 },
  { title: '动作', key: 'action', width: 200 },
  { title: '方法', dataIndex: 'httpMethod', key: 'httpMethod', width: 76 },
  { title: '状态', key: 'status', width: 70 },
  { title: '业务码', dataIndex: 'bizCode', key: 'bizCode', width: 80 },
  { title: '结果', key: 'result', width: 72 },
  { title: '耗时', key: 'cost', width: 76 },
  { title: '入参（脱敏）', key: 'params', width: 200 },
  { title: 'IP', dataIndex: 'clientIp', key: 'clientIp', width: 120 },
  { title: '详情', key: 'detail', width: 160 },
  { title: '操作', key: 'operation', width: 110, fixed: 'right' }
]

const categoryOptions = [
  { label: '全部类别', value: '' },
  { label: '访问', value: 'ACCESS' },
  { label: '登录', value: 'LOGIN' },
  { label: '业务', value: 'BUSINESS' },
  { label: '敏感操作', value: 'SENSITIVE' }
]
const resultOptions = [
  { label: '全部结果', value: '' },
  { label: '成功', value: 'success' },
  { label: '失败', value: 'fail' }
]

const records = ref<AuditEvent[]>([])
const appOptions = computed(() => [
  { label: '全部应用', value: '' },
  ...appCodes([...records.value.map((r) => r.app), app.value]).map((code) => ({ label: appLabel(code), value: code }))
])
const loading = ref(false)
const errorMsg = ref('')
const keyword = ref('')
const app = ref('')
const category = ref('')
const result = ref('')
const dateRange = ref<[Dayjs, Dayjs] | null>(null)
const total = ref(0)
const current = ref(1)
const pageSize = ref(20)

const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

onMounted(() => {
  void ensureAppNames()
  void load()
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetchAuditEvents({
      page: current.value,
      size: pageSize.value,
      app: app.value || undefined,
      eventCategory: category.value || undefined,
      result: result.value || undefined,
      action: keyword.value || undefined,
      operator: keyword.value || undefined,
      startDate: dateRange.value?.[0]?.format('YYYY-MM-DD'),
      endDate: dateRange.value?.[1]?.format('YYYY-MM-DD')
    })
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function handleSearch() { current.value = 1; load() }
function handleTableChange(pag: any) { current.value = pag.current; pageSize.value = pag.pageSize; load() }
function categoryLabel(c: string) { return { ACCESS: '访问', LOGIN: '登录', BUSINESS: '业务', SENSITIVE: '敏感操作' }[c] || c || '-' }
function categoryColor(c: string) { return { ACCESS: 'blue', LOGIN: 'green', BUSINESS: 'purple', SENSITIVE: 'orange' }[c] || 'default' }
/** 优先用后端回填的 result；老数据无 result 时回退解析 detail */
function eventResult(r: AuditEvent): string {
  if (r.result === 'success' || r.result === 'fail') return r.result
  return r.detail?.startsWith('result=fail') ? 'fail' : 'success'
}
function statusClass(status?: number) {
  if (status == null) return 'muted'
  if (status >= 500) return 'status-bad'
  if (status >= 400) return 'status-warn'
  return 'status-ok'
}
// 操作列抽屉：查看入参/响应，JSON 字符串漂亮打印，非 JSON 回退原文
const drawerOpen = ref(false)
const drawerTitle = ref('')
const drawerJson = ref('')
const drawerEmpty = computed(() => !drawerJson.value)
let drawerRaw = ''

function formatJson(raw?: string): string {
  if (!raw || !raw.trim()) return ''
  const text = raw.trim()
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch { /* 非合法 JSON，回退原文 */ }
  return text
}

function openDrawer(kind: 'request' | 'response', record: AuditEvent) {
  drawerTitle.value = kind === 'request' ? '请求入参（脱敏）' : '响应结果（脱敏）'
  if (kind === 'request') {
    drawerRaw = record.requestParams || ''
    drawerJson.value = formatJson(drawerRaw)
  } else {
    // 响应摘要本身是 "HTTP 200" 这类文本，组装成结构化 JSON 展示，并带业务语义
    drawerRaw = record.responseSummary || ''
    const summary = drawerRaw.trim()
    let payload: any = summary
    const m = /^HTTP\s+(\d{3})(?:\s+(.*))?$/i.exec(summary)
    if (m) payload = { httpStatus: Number(m[1]), reason: m[2] || undefined, summary }
    const structured = {
      result: eventResult(record),
      httpStatus: record.httpStatus ?? null,
      bizCode: record.bizCode ?? null,
      costMs: record.costMs ?? null,
      response: payload,
      detail: record.detail || ''
    }
    drawerJson.value = JSON.stringify(structured, null, 2)
  }
  drawerOpen.value = true
}

async function copyJson() {
  if (!drawerRaw) return
  try {
    await navigator.clipboard.writeText(drawerJson.value || drawerRaw)
    message.success('已复制')
  } catch {
    message.error('复制失败，请手动选择')
  }
}
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.app-cell { font-weight: 600; white-space: nowrap; }
.muted { color: var(--muted); }
.ua { font-size: 12px; }
/* 长文本按列宽自适应省略，完整内容由 a-tooltip 悬浮展示 */
.cell-ellipsis { display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.action-cell { font-family: ui-monospace, monospace; font-size: 12px; }
.status-ok { color: #52c41a; }
.status-warn { color: #faad14; }
.status-bad { color: #ff4d4f; }
.detail-panel { padding: 8px 12px 12px 48px; background: var(--bg-subtle, rgba(127,127,127,.06)); }
.detail-row { display: flex; align-items: flex-start; gap: 12px; margin: 6px 0; }
.detail-label { flex: 0 0 76px; color: var(--muted); font-size: 12px; padding-top: 2px; }
.detail-code { margin: 0; font-family: ui-monospace, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
.detail-text { font-size: 12px; word-break: break-all; }
.detail-mono { font-family: ui-monospace, monospace; }
.detail-grid { display: flex; gap: 24px; flex-wrap: wrap; margin: 10px 0; font-size: 12px; }
.detail-grid b { color: var(--muted); font-weight: 500; }
.json-view {
  margin: 0;
  padding: 14px 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text, inherit);
  background: var(--bg-subtle, rgba(127, 127, 127, .08));
  border: 1px solid var(--border, rgba(127, 127, 127, .2));
  border-radius: 6px;
  max-height: 70vh;
  overflow: auto;
}
</style>
