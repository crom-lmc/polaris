<template>
  <div class="ft-page">
    <section class="ft-head overview-head">
      <div>
        <h2>会议室概览</h2>
        <span class="hint">统计卡按所选日期和地区统计该区域预约；月历与日程为该地区内本人创建和受邀的会议</span>
      </div>
      <a-space>
        <span class="filter-label">地区</span>
        <a-select v-model:value="city" :options="cityOptions" placeholder="全部地区" style="width: 120px" allow-clear @change="load" />
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
                      :class="{ today: d.key === today, selected: d.key === selectedDate, empty: !d.day, 'has-events': d.events.length }"
                      :disabled="!d.day || d.key > maxDate" @click="selectDay(d.key)">
                <span v-if="d.day" class="day">{{ d.day }}</span>
                <template v-if="d.events.length">
                  <a-tooltip v-for="ev in d.events.slice(0, 2)" :key="ev.bookingId" :title="`${ev.startTime} ${ev.title}`">
                    <span class="cal-event" :class="eventClass(ev)">{{ ev.startTime }} {{ ev.title }}</span>
                  </a-tooltip>
                  <span v-if="d.events.length > 2" class="cal-event-more">还有 {{ d.events.length - 2 }} 项</span>
                </template>
                <span v-else-if="d.count" class="dot">{{ d.count }}</span>
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
import { getOverview, getDicts, cancelBooking, checkinBooking, releaseBooking, type Overview, type Booking, type DictItem, type CalendarEvent } from '@/api/meeting'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const weeks = ['一', '二', '三', '四', '五', '六', '日']
const today = dateKey(new Date())
const selectedDate = ref(today)
const cursor = ref(new Date())
/** 地区过滤：默认当前登录人部门所在地区（来自 /me 的 city），可清空看全平台 */
const city = ref<string | undefined>(store.city || undefined)
const cityOptions = ref<Array<{ label: string; value: string }>>([])
const data = reactive<Overview>({
  date: today, city: '', meetingCount: 0, toCheckinCount: 0, inProgressCount: 0,
  availableRooms: 0, maxDays: 0, calendar: {}, calendarEvents: {}, agenda: []
})

const monthLabel = computed(() => `${cursor.value.getFullYear()} 年 ${cursor.value.getMonth() + 1} 月`)
const monthKey = computed(() =>
  `${cursor.value.getFullYear()}-${String(cursor.value.getMonth() + 1).padStart(2, '0')}`)
/** 可选日期上限：[过去5年(历史数据起点), 今天 + 可提前预约天数]，天数来自 meeting.booking.max_advance_days */
const maxDate = computed(() => dayjs().add(Math.max(data.maxDays, 0), 'day').format('YYYY-MM-DD'))
/** 月历可往前翻的边界：历史预约数据为近 5 年，再往前翻没有数据 */
const minMonth = dayjs().subtract(5, 'year').format('YYYY-MM')
const canPrevMonth = computed(() => monthKey.value > minMonth)
const canNextMonth = computed(() => monthKey.value < maxDate.value.slice(0, 7))
function disabledDate(d: Dayjs) {
  const key = d.format('YYYY-MM-DD')
  return key < minMonth + '-01' || key > maxDate.value
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
  const out: Array<{ key: string; day: number; count: number; events: CalendarEvent[] }> = []
  for (let i = 0; i < offset; i++) out.push({ key: `e${i}`, day: 0, count: 0, events: [] })
  for (let d = 1; d <= total; d++) {
    const key = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    out.push({ key, day: d, count: data.calendar[key] || 0, events: data.calendarEvents[key] || [] })
  }
  while (out.length % 7) out.push({ key: `e${out.length}`, day: 0, count: 0, events: [] })
  return out
})
const dayRows = computed(() => Math.ceil(days.value.length / 7))

function timeOf(v: string) { return (v || '').slice(11, 16) }
/** 月历标签配色：已结束灰、待签到蓝、进行中绿、受邀橙 */
function eventClass(ev: CalendarEvent) {
  if (ev.status === 'COMPLETED' || ev.status === 'CANCELLED' || ev.status === 'RELEASED') return 'ev-done'
  if (ev.status === 'CHECKED_IN') return 'ev-ongoing'
  return ev.relation === 'INVITEE' ? 'ev-invitee' : 'ev-pending'
}
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
    Object.assign(data, await getOverview(selectedDate.value, monthKey.value, city.value || undefined))
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
onMounted(async () => {
  try {
    const dicts: Record<string, DictItem[]> = await getDicts()
    cityOptions.value = (dicts['ehr.job_city'] || []).map(d => ({ label: d.label, value: d.value }))
    // 当前人城市不在字典中时仍保留该项，避免默认过滤失效（雪花/自定义城市场景）
    if (city.value && !cityOptions.value.some(o => o.value === city.value)) {
      cityOptions.value.unshift({ label: city.value, value: city.value })
    }
  } catch { /* 字典失败不影响主数据加载 */ }
  await load()
})
</script>

<style scoped>
/* 内容区 a-row :gutter=12 使卡片右边缘比页面容器内缩 12px，头部补等量右内边距对齐 */
.overview-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; padding-right: 12px; }
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
  display: flex; flex-direction: column; align-items: stretch; justify-content: flex-start;
  gap: 2px; padding: 2px 3px; overflow: hidden;
}
.cal-cell:not(:disabled):hover { background: var(--header-hover, rgba(0,0,0,0.04)); }
.cal-cell:disabled:not(.empty) { cursor: not-allowed; color: var(--text-secondary, #8c8c8c); opacity: 0.55; }
.cal-cell.today { border-color: var(--primary, #2f54eb); }
/* 选中态用浅底 + 主色边框强调，不用实色背景，避免覆盖标签配色导致看不清；
   同时显式覆盖 hover，防止鼠标停留时 hover 浅底盖掉选中态 */
.cal-cell.selected,
.cal-cell.selected:not(:disabled):hover {
  background: rgba(47, 84, 235, 0.12); border-color: var(--primary, #2f54eb);
  box-shadow: inset 0 0 0 1px var(--primary, #2f54eb); color: var(--text-primary, inherit);
}
.cal-cell.selected .day { color: var(--primary, #2f54eb); font-weight: 700; }
.cal-cell.empty { cursor: default; }
.cal-cell .day { font-size: 14px; text-align: center; line-height: 16px; flex: 0 0 auto; }
.cal-cell .dot { font-size: 11px; color: var(--primary, #2f54eb); line-height: 1; align-self: center; }
.cal-event {
  display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  font-size: 10px; line-height: 15px; padding: 0 3px; border-radius: 3px; text-align: left; flex: 0 0 auto;
}
.cal-event.ev-pending { background: #e6f4ff; color: #0958d9; }
.cal-event.ev-ongoing { background: #b7eb8f; color: #135200; }
.cal-event.ev-invitee { background: #ffd591; color: #ad4e00; }
.cal-event.ev-done { background: #f0f0f0; color: #8c8c8c; }
.cal-event-more { font-size: 10px; line-height: 13px; color: var(--text-secondary, #8c8c8c); text-align: left; flex: 0 0 auto; padding: 0 3px; }
/* 选中态保留标签自身配色，不再反白，确保文字始终可读 */
</style>
