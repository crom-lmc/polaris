<template>
  <section class="ft-page">
    <section class="ft-head tabs-head">
      <div class="tabs">
        <button :class="{ active: tab === 'mine' }" @click="switchTab('mine')">
          我的爽约记录 <em>{{ state.mine.total }}</em>
        </button>
        <button v-if="canAll" :class="{ active: tab === 'all' }" @click="switchTab('all')">
          全部爽约记录 <em>{{ state.all.total }}</em>
        </button>
      </div>
    </section>
    <section class="ft-head toolbar">
      <span class="filter-label">地区</span>
      <a-select v-model:value="city" :options="cityOptions" placeholder="全部地区"
                style="width: 130px" allow-clear @change="reload" />
      <span class="filter-label">统计区间</span>
      <a-range-picker v-model:value="range" format="YYYY-MM-DD" @change="reload" />
      <a-button @click="reload">刷新</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <a-table
        class="noshow-table"
        size="small"
        row-key="bookingId"
        :columns="currentColumns"
        :data-source="currentState.list"
        :loading="currentState.loading"
        :class="{ 'tbl-empty-mode': !currentState.loading && !currentState.list.length }"
        :scroll="currentState.list.length ? { x: tab === 'all' ? 640 : 520 } : undefined"
        :pagination="paginationOf(tab)"
        @change="(p: any) => onTableChange(tab, p)"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <a-tooltip :title="record.title"><span class="cell-ellipsis">{{ record.title }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'room'">
            <a-tooltip :title="roomText(record)"><span class="cell-ellipsis">{{ roomText(record) }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'time'">{{ timeRange(record) }}</template>
          <template v-else-if="column.key === 'creator'">
            <a-tooltip :title="record.creatorName || record.creatorId">
              <span class="cell-ellipsis">{{ record.creatorName || '-' }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'releasedAt'">{{ fmtTime(record.releasedAt) }}</template>
        </template>
        <template #emptyText>
          <div class="tbl-empty">
            <a-empty v-if="!currentState.loading"
                     :description="tab === 'all' ? '区间内没有爽约记录' : '区间内没有我的爽约记录'" />
          </div>
        </template>
      </a-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import { getDicts, getNoShows, type DictItem, type NoShowRecord } from '@/api/meeting'
import { useUserStore } from '@/stores/user'

type Scope = 'mine' | 'all'
const store = useUserStore()
const canAll = computed(() => store.can('meeting:noshow:all'))
const tab = ref<Scope>('mine')
const city = ref<string | undefined>(store.city || undefined)
const cityOptions = ref<Array<{ label: string; value: string }>>([])
/** 默认最近一周；清空后为 null，表示不限日期 */
const range = ref<Dayjs[] | null>([dayjs().subtract(6, 'day').startOf('day'), dayjs()])
const pageSize = ref(20)

const state = reactive({
  mine: { list: [] as NoShowRecord[], total: 0, page: 1, loading: false },
  all: { list: [] as NoShowRecord[], total: 0, page: 1, loading: false }
})
const currentState = computed(() => state[tab.value])
const currentColumns = computed(() => tab.value === 'all' ? allColumns : mineColumns)

const mineColumns = [
  { title: '会议主题', dataIndex: 'title', key: 'title', width: 120 },
  { title: '会议室', key: 'room', width: 150 },
  { title: '会议时间', key: 'time', width: 130 },
  { title: '释放时间', dataIndex: 'releasedAt', key: 'releasedAt', width: 110 }
]
const allColumns = [
  { title: '会议主题', dataIndex: 'title', key: 'title', width: 120 },
  { title: '会议室', key: 'room', width: 140 },
  { title: '会议时间', key: 'time', width: 125 },
  { title: '发起人', key: 'creator', width: 90 },
  { title: '释放时间', dataIndex: 'releasedAt', key: 'releasedAt', width: 105 }
]

function fmtTime(value?: string) {
  return value ? dayjs(value).format('MM-DD HH:mm') : '-'
}
function timeRange(record: NoShowRecord) {
  return `${fmtTime(record.startTime)}–${fmtTime(record.endTime)}`
}
function roomText(record: NoShowRecord) {
  return [record.roomName, record.location].filter(Boolean).join(' · ') || '-'
}
function totalLabel(total: number) {
  return `共 ${total} 条`
}
function paginationOf(scope: Scope) {
  const s = state[scope]
  return {
    current: s.page, pageSize: pageSize.value, total: s.total,
    showSizeChanger: true, showTotal: totalLabel
  }
}
function switchTab(next: Scope) {
  if (next === 'all' && !canAll.value) return
  tab.value = next
}
/** 翻页与每页条数走同一入口：换 size 时回到第一页 */
function onTableChange(scope: Scope, p: any) {
  const nextSize = Number(p?.pageSize) || pageSize.value
  state[scope].page = nextSize === pageSize.value ? (Number(p?.current) || 1) : 1
  pageSize.value = nextSize
  void load(scope)
}
function params(scope: Scope) {
  return {
    from: range.value?.[0]?.format('YYYY-MM-DD'),
    to: range.value?.[1]?.format('YYYY-MM-DD'),
    city: city.value || undefined,
    page: state[scope].page,
    size: pageSize.value
  }
}
async function load(scope: Scope) {
  if (scope === 'all' && !canAll.value) return
  state[scope].loading = true
  try {
    const res = await getNoShows(scope, params(scope))
    state[scope].list = res?.list || []
    state[scope].total = res?.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载爽约记录失败')
  } finally {
    state[scope].loading = false
  }
}
function reload() {
  state.mine.page = 1
  state.all.page = 1
  void Promise.all([load('mine'), load('all')])
}
onMounted(async () => {
  try {
    const dicts: Record<string, DictItem[]> = await getDicts()
    cityOptions.value = (dicts['ehr.job_city'] || []).map(d => ({ label: d.label, value: d.value }))
    if (city.value && !cityOptions.value.some(o => o.value === city.value)) {
      cityOptions.value.unshift({ label: city.value, value: city.value })
    }
  } catch { /* 字典失败不影响列表 */ }
  reload()
})
</script>

<style scoped>
.tabs-head { margin-bottom: 12px; }
.tabs { display: flex; gap: 20px; border-bottom: 1px solid var(--border, #eee); }
.tabs button {
  border: 0; background: transparent; padding: 8px 2px; cursor: pointer;
  color: var(--text-secondary, #8c8c8c); font-size: 14px; border-bottom: 2px solid transparent;
}
.tabs button.active { color: var(--primary, #2f54eb); border-bottom-color: var(--primary, #2f54eb); font-weight: 600; }
.tabs em { font-style: normal; font-size: 11px; margin-left: 4px; }
.filter-label { font-size: 12px; color: var(--muted, #8a9099); }
/* 筛选栏与页签、表格之间都留 12px，避免组件贴在一起 */
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
@media (max-width: 767px) {
  .tabs { gap: 14px; overflow-x: auto; white-space: nowrap; scrollbar-width: none; }
  .tabs::-webkit-scrollbar { display: none; }
  .tabs button { flex: 0 0 auto; font-size: 13px; }
}
</style>
