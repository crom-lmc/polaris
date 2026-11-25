<template>
  <div class="ft-page">
    <section class="ft-head overview-head">
      <div>
        <h2>会议室概览</h2>
        <span class="hint">统计卡按所选日期统计全平台预约；月历与日程包含本人创建和受邀的会议</span>
      </div>
      <a-space>
        <span class="filter-label">统计日期</span>
        <a-date-picker v-model:value="statDate" format="YYYY-MM-DD" :disabled-date="disabledDate" />
        <a-button v-if="store.can('meeting:booking:create')" type="primary" @click="router.push('/booking')">
          我要预定
        </a-button>
        <a-button @click="load">刷新</a-button>
      </a-space>
    </section>

    <section class="ft-scroll overview-scroll">
      <a-row :gutter="12" class="stat-row">
        <a-col :xs="12" :sm="6"><a-card size="small"><a-statistic title="当日会议" :value="data.meetingCount" /></a-card></a-col>
        <a-col :xs="12" :sm="6"><a-card size="small"><a-statistic title="待签到" :value="data.toCheckinCount" /></a-card></a-col>
        <a-col :xs="12" :sm="6"><a-card size="small"><a-statistic title="进行中" :value="data.inProgressCount" /></a-card></a-col>
        <a-col :xs="12" :sm="6"><a-card size="small"><a-statistic title="可用会议室" :value="data.availableRooms" /></a-card></a-col>
      </a-row>

      <a-row :gutter="12" class="overview-row">
        <a-col :xs="24" :md="18">
          <a-card size="small" title="月历" class="fill-card">
            <div class="cal-head">
              <a-button size="small" :disabled="!canPrevMonth" @click="shiftMonth(-1)"><left-outlined /></a-button>
              <strong>{{ monthLabel }}</strong>
              <a-button size="small" :disabled="!canNextMonth" @click="shiftMonth(1)"><right-outlined /></a-button>
            </div>
            <div class="cal-week"><span v-for="w in weeks" :key="w">{{ w }}</span></div>
            <div class="cal-grid" :style="{ gridTemplateRows: `repeat(${dayRows}, minmax(44px, 1fr))` }">
              <button v-for="d in days" :key="d.key" class="cal-cell"
                      :class="{ today: d.key === today, selected: d.key === selectedDate, empty: !d.day }"
                      :disabled="!d.day || d.key < today || d.key > maxDate" @click="selectDay(d.key)">
                <span v-if="d.day" class="day">{{ d.day }}</span>
                <span v-if="d.count" class="dot">{{ d.count }}</span>
              </button>
            </div>
          </a-card>
        </a-col>
        <a-col :xs="24" :md="6">
          <a-card size="small" :title="`${selectedDate} 我的日程`" class="fill-card agenda-card">
            <a-list class="agenda-list" :data-source="data.agenda" :locale="{ emptyText: '当天没有会议' }" size="small">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :title="item.title"
                    :description="`${timeOf(item.startTime)}–${timeOf(item.endTime)} · ${item.roomName || '-'} · ${item.location || ''}${item.relation === 'INVITEE' ? ` · 邀请人：${item.creatorName || item.creatorId || '-'}` : ''}`" />
                  <template #actions>
                    <a-tag v-if="item.relation === 'INVITEE'" color="blue">受邀</a-tag>
                    <template v-if="item.relation !== 'INVITEE'">
                      <a v-if="item.status === 'PENDING' && store.can('meeting:booking:checkin')"
                         @click="doAction('checkin', item)">签到</a>
                      <a v-if="item.status === 'PENDING' && store.can('meeting:booking:cancel')"
                         @click="doAction('cancel', item)">取消</a>
                      <a v-if="item.status === 'CHECKED_IN' && store.can('meeting:booking:release')"
                         @click="doAction('release', item)">释放</a>
                    </template>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-card>
        </a-col>
      </a-row>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs, { type Dayjs } from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { getOverview, cancelBooking, checkinBooking, releaseBooking, type Overview, type Booking } from '@/api/meeting'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const weeks = ['一', '二', '三', '四', '五', '六', '日']
const today = dateKey(new Date())
const selectedDate = ref(today)
const cursor = ref(new Date())
const data = reactive<Overview>({
  date: today, meetingCount: 0, toCheckinCount: 0, inProgressCount: 0,
  availableRooms: 0, maxDays: 0, calendar: {}, agenda: []
})

const monthLabel = computed(() => `${cursor.value.getFullYear()} 年 ${cursor.value.getMonth() + 1} 月`)
const monthKey = computed(() =>
  `${cursor.value.getFullYear()}-${String(cursor.value.getMonth() + 1).padStart(2, '0')}`)
/** 可选日期：[今天, 今天 + 可提前预约天数]，天数来自 meeting.booking.max_advance_days */
const maxDate = computed(() => dayjs().add(Math.max(data.maxDays, 0), 'day').format('YYYY-MM-DD'))
const canPrevMonth = computed(() => monthKey.value > today.slice(0, 7))
const canNextMonth = computed(() => monthKey.value < maxDate.value.slice(0, 7))
function disabledDate(d: Dayjs) {
  const key = d.format('YYYY-MM-DD')
  return key < today || key > maxDate.value
}
/** 右上角统计日期过滤：与月历选中日期联动 */
const statDate = computed<Dayjs>({
  get: () => dayjs(selectedDate.value),
  set: v => {
    if (!v || disabledDate(v)) return
    const key = v.format('YYYY-MM-DD')
    selectedDate.value = key
    const d = v.toDate()
    cursor.value = new Date(d.getFullYear(), d.getMonth(), 1)
    void load()
  }
})
function dateKey(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const days = computed(() => {
  const y = cursor.value.getFullYear()
  const m = cursor.value.getMonth()
  const first = new Date(y, m, 1)
  const offset = (first.getDay() + 6) % 7
  const total = new Date(y, m + 1, 0).getDate()
  const out: Array<{ key: string; day: number; count: number }> = []
  for (let i = 0; i < offset; i++) out.push({ key: `e${i}`, day: 0, count: 0 })
  for (let d = 1; d <= total; d++) {
    const key = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    out.push({ key, day: d, count: data.calendar[key] || 0 })
  }
  while (out.length % 7) out.push({ key: `e${out.length}`, day: 0, count: 0 })
  return out
})
const dayRows = computed(() => Math.ceil(days.value.length / 7))

function timeOf(v: string) { return (v || '').slice(11, 16) }
function shiftMonth(delta: number) {
  if (delta < 0 && !canPrevMonth.value) return
  if (delta > 0 && !canNextMonth.value) return
  const next = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + delta, 1)
  cursor.value = next
  selectedDate.value = dateKey(next)
  void load()
}
function selectDay(key: string) {
  selectedDate.value = key
  void load()
}
async function load() {
  try {
    Object.assign(data, await getOverview(selectedDate.value, monthKey.value))
  } catch (e: any) {
    message.error(e?.message || '加载概览失败')
  }
}
function doAction(action: string, item: Booking) {
  const verbs: Record<string, string> = { checkin: '签到', cancel: '取消', release: '释放' }
  Modal.confirm({
    title: `确认${verbs[action]}`,
    content: `${item.roomName || '会议室'}[${item.title}]`,
    centered: true,
    onOk: async () => {
      try {
        if (action === 'checkin') await checkinBooking(item.id)
        if (action === 'cancel') await cancelBooking(item.id)
        if (action === 'release') await releaseBooking(item.id)
        message.success(`${verbs[action]}成功`)
        await load()
      } catch (e: any) { message.error(e?.message || `${verbs[action]}失败`); throw e }
    }
  })
}
onMounted(load)
</script>

<style scoped>
.overview-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.overview-head h2 { margin: 0; font-size: 17px; }
.hint { font-size: 12px; color: var(--text-secondary, #8c8c8c); }
.filter-label { font-size: 12px; color: var(--text-secondary, #8c8c8c); }
.stat-row { margin-bottom: 12px; }
.overview-scroll { display: flex; flex-direction: column; height: 100%; }
.overview-row {
  flex: 1 1 auto; align-self: stretch; display: flex; align-items: stretch;
  min-height: 360px; width: 100%;
}
.overview-row > .ant-col { display: flex; align-self: stretch; }
.fill-card { display: flex; flex-direction: column; width: 100%; height: 100%; }
.fill-card :deep(.ant-card-body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.agenda-list { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.agenda-list :deep(.ant-list) { flex: 1; display: flex; flex-direction: column; }
.agenda-list :deep(.ant-list-items) { flex: 1; }
.agenda-list :deep(.ant-list-empty-text) {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: var(--text-secondary, #8c8c8c);
}
.cal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; flex: 0 0 auto; }
.cal-week, .cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; text-align: center; }
.cal-week { flex: 0 0 auto; }
.cal-grid { flex: 1 1 auto; min-height: 0; }
.cal-week span { font-size: 12px; color: var(--text-secondary, #8c8c8c); padding: 4px 0; }
.cal-cell {
  position: relative; min-height: 44px; border: 1px solid transparent;
  border-radius: 6px; background: transparent; color: var(--text-primary, inherit); cursor: pointer;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
}
.cal-cell:not(:disabled):hover { background: var(--header-hover, rgba(0,0,0,0.04)); }
.cal-cell:disabled:not(.empty) { cursor: not-allowed; color: var(--text-secondary, #8c8c8c); opacity: 0.55; }
.cal-cell.today { border-color: var(--primary, #2f54eb); }
.cal-cell.selected { background: var(--primary, #2f54eb); color: #fff; }
.cal-cell.empty { cursor: default; }
.cal-cell .day { font-size: 14px; }
.cal-cell .dot { font-size: 11px; color: var(--primary, #2f54eb); line-height: 1; }
.cal-cell.selected .dot { color: #fff; }
</style>
