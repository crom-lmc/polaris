<template>
  <div class="ft-page">
    <section class="ft-head tabs-head">
      <div class="tabs">
        <button v-for="t in tabs" :key="t.key" :class="{ active: tab === t.key }" @click="switchTab(t.key)">
          {{ t.label }} <em>{{ counts[t.key] || 0 }}</em>
        </button>
      </div>
    </section>
    <section class="ft-head toolbar">
      <a-select v-model:value="city" :options="cityOptions" placeholder="城市" style="width: 120px" allow-clear @change="reload" />
      <a-select v-model:value="status" :options="statusOptions" placeholder="状态" style="width: 130px" allow-clear @change="reload" />
      <a-input-search v-model:value="keyword" placeholder="主题/预定单号" style="width: 190px" allow-clear @search="reload" />
      <a-range-picker v-model:value="rangeDates" format="YYYY-MM-DD" @change="reload" />
      <a-button @click="reload">刷新</a-button>
      <span class="grow" />
      <span class="hint">仅显示本人创建的预定；被邀请人可从消息链接查看会议详情</span>
    </section>

    <section class="ft-scroll ft-bottom">
      <a-table class="ft-table booking-table" row-key="id" :columns="columns" :data-source="rows"
               :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :loading="loading" :pagination="pagination" @change="onTableChange"
               :scroll="rows.length ? { x: 1200 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <a-tooltip :title="record.title"><span class="cell-ellipsis">{{ record.title }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'date'">
            {{ record.startTime?.slice(0, 10) }}
          </template>
          <template v-else-if="column.key === 'time'">
            {{ timeRange(record) }}
          </template>
          <template v-else-if="column.key === 'room'">
            <a-tooltip :title="record.location"><span class="cell-ellipsis">{{ record.roomName }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a @click="openDetail(record.id)">详情</a>
              <a v-if="record.status === 'PENDING' && store.can('meeting:booking:create')" @click="openEdit(record)">修改</a>
              <a v-if="record.status === 'PENDING' && store.can('meeting:booking:checkin')" @click="act('checkin', record)">签到</a>
              <a v-if="record.status === 'CHECKED_IN' && store.can('meeting:booking:extend')" @click="openExtend(record)">延时</a>
              <a v-if="record.status === 'PENDING' && store.can('meeting:booking:cancel')" @click="act('cancel', record)">取消</a>
              <a v-if="['PENDING','CHECKED_IN'].includes(record.status) && store.can('meeting:booking:release')"
                 @click="act('release', record)">释放</a>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无预定" /></div></template>
      </a-table>
    </section>

    <a-modal v-model:open="detailOpen" title="会议详情" :footer="null" :width="560" centered>
      <a-descriptions :column="1" bordered size="small">
        <a-descriptions-item label="主题">{{ detail.title }}</a-descriptions-item>
        <a-descriptions-item label="预定单号">{{ detail.bookingNo }}</a-descriptions-item>
        <a-descriptions-item label="时间">{{ detail.startTime }} – {{ displayEnd(detail) }}</a-descriptions-item>
        <a-descriptions-item label="地点">{{ detail.location || detail.roomName }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ statusText(detail) }}</a-descriptions-item>
        <a-descriptions-item label="参会人数">{{ detail.attendeeCount }}</a-descriptions-item>
        <a-descriptions-item label="参会人">{{ attendeeText(detail.attendeeIds) }}</a-descriptions-item>
        <a-descriptions-item label="备注">{{ detail.remark || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <a-modal v-model:open="editOpen" title="修改预定" :width="860" :confirm-loading="saving" centered @ok="submitEdit">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="6">
            <a-form-item label="日期">
              <a-date-picker v-model:value="editForm.date" value-format="YYYY-MM-DD" format="YYYY-MM-DD"
                             style="width: 100%" @change="onEditDateChange" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="会议室"><a-select v-model:value="editForm.roomId" :options="editRoomOptions" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="主题"><a-input v-model:value="editForm.title" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="参会人数">
              <a-input-number v-model:value="editForm.attendeeCount" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item :label="`选择新时段（${editRangeText}）`" required>
          <div class="slot-scroll">
            <SlotGrid :slots="editSlots" selectable allow-mine :mine-booking-id="editing?.id"
                      :value="editRange" @pick="pickEditSlot" />
          </div>
        </a-form-item>
        <a-form-item label="邀请参会人">
          <a-select v-model:value="editForm.attendeeIds" mode="multiple" :options="employeeOptions"
                    :filter-option="false" @search="onEmployeeSearch" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="extendOpen" title="申请延时" :confirm-loading="saving" centered @ok="submitExtend">
      <p>当前结束：{{ editing?.endTime }}</p>
      <a-time-picker v-model:value="extendTime" format="HH:mm" :minute-step="30" :use12-hours="false" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import SlotGrid from '@/components/SlotGrid.vue'
import {
  getDicts, listBookings, getBookingCounts, getBooking, checkinBooking, cancelBooking,
  releaseBooking, extendBooking, updateBooking, getSchedule, searchEmployees, getEmployeeNames,
  type Booking, type DictItem, type Slot
} from '@/api/meeting'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const route = useRoute()
const tabs = [
  { key: 'booked', label: '已预约' }, { key: 'started', label: '已开始' },
  { key: 'ended', label: '已结束' }, { key: 'cancelled', label: '已取消' }
]
const tab = ref('booked')
const counts = reactive<Record<string, number>>({ booked: 0, started: 0, ended: 0, cancelled: 0 })
const dicts = reactive<Record<string, DictItem[]>>({})
const city = ref<string>()
const status = ref<string>()
const keyword = ref('')
/** 查询类日期范围默认最近 7 天（含今天），与平台规范一致 */
const rangeDates = ref<[dayjs.Dayjs, dayjs.Dayjs] | null>([dayjs().subtract(6, 'day').startOf('day'), dayjs()])
const rows = ref<Booking[]>([])
const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const detailOpen = ref(false)
const detail = reactive<Partial<Booking>>({})
const extendOpen = ref(false)
const editing = ref<Booking>()
const extendTime = ref<dayjs.Dayjs>()
const editOpen = ref(false)
const editRange = ref<{ start: string; end: string }>()
const editSchedule = ref<{ rooms: Array<{ roomId: string; name: string; slots: Slot[] }> }>({ rooms: [] })
const employeeOptions = ref<Array<{ label: string; value: string }>>([])
const attendeeNames = ref<Record<string, string>>({})
function attendeeText(ids?: string[]) {
  return (ids || []).map(id => attendeeNames.value[id] || id).join('、') || '-'
}
async function loadAttendeeNames(ids?: string[]) {
  const list = (ids || []).filter(Boolean)
  if (!list.length) { attendeeNames.value = {}; return }
  try { attendeeNames.value = await getEmployeeNames(list) }
  catch { attendeeNames.value = {} }
}
const editForm = reactive({
  roomId: '', title: '', attendeeCount: 1, attendeeIds: [] as string[], remark: '', date: ''
})
const editRangeText = computed(() => editRange.value ? `${editRange.value.start}–${editRange.value.end}` : '点击空闲格重新选择')
const editRoomOptions = computed(() => editSchedule.value.rooms.map(r => ({ label: r.name, value: r.roomId })))
const editSlots = computed<Slot[]>(() => {
  const room = editSchedule.value.rooms.find(r => r.roomId === editForm.roomId)
  return room?.slots || []
})
const cityOptions = computed(() => (dicts['ehr.job_city'] || []).map(d => ({ label: d.label, value: d.value })))
const statusOptions = computed(() => (dicts['meeting.booking_status'] || []).map(d => ({ label: d.label, value: d.value })))
function statusLabel(value: string) { return statusOptions.value.find(o => o.value === value)?.label || value }
function statusText(record: Partial<Booking>) {
  return record.status === 'RELEASED' ? '已取消（释放）' : statusLabel(record.status || '')
}
function statusColor(value: string) {
  if (value === 'PENDING') return 'blue'
  if (value === 'CHECKED_IN') return 'green'
  if (value === 'COMPLETED') return 'default'
  return value === 'RELEASED' ? 'orange' : 'red'
}
function timeOf(v: string) { return (v || '').slice(11, 16) }
/** 结束于次日 00:00 按业务口径展示为当天 24:00，不算跨天 */
function midnightEnd(record: Partial<Booking>) {
  const s = record.startTime, e = record.endTime
  return !!s && !!e && e.slice(11, 16) === '00:00' && e.slice(0, 10) !== s.slice(0, 10)
}
function displayEnd(record: Partial<Booking>) {
  return !record.endTime ? '' : midnightEnd(record) ? `${record.startTime!.slice(0, 10)} 24:00:00` : record.endTime
}
/** 日期已在独立列展示，这里只显示时段；结束于次日 00:00 按业务口径展示为 24:00 */
function timeRange(record: Booking) {
  const end = midnightEnd(record) ? '24:00' : timeOf(record.endTime)
  return `${timeOf(record.startTime)} – ${end}`
}
function endSlotOf(record: Booking) { return midnightEnd(record) ? '24:00' : record.endTime.slice(11, 16) }
function endDateTime(date: string, end: string) {
  return end === '24:00'
    ? `${dayjs(date).add(1, 'day').format('YYYY-MM-DD')} 00:00:00`
    : `${date} ${end}:00`
}
const columns = [
  { title: '主题', key: 'title', width: 200 },
  { title: '日期', key: 'date', width: 120 },
  { title: '时间', key: 'time', width: 220 },
  { title: '会议室', key: 'room', width: 180 },
  { title: '状态', key: 'status', align: 'center', width: 130 },
  { title: '操作', key: 'action', align: 'left', width: 300 }
]
const pagination = computed(() => ({
  current: page.value, pageSize: size.value, total: total.value,
  showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条`
}))

async function loadCounts() {
  try {
    Object.assign(counts, await getBookingCounts({
      city: city.value || undefined, status: status.value || undefined,
      keyword: keyword.value.trim() || undefined,
      from: rangeDates.value?.[0].format('YYYY-MM-DD'), to: rangeDates.value?.[1].format('YYYY-MM-DD')
    }))
  } catch { /* 角标失败不影响列表 */ }
}
async function load() {
  loading.value = true
  try {
    const res = await listBookings({
      tab: tab.value, city: city.value, status: status.value, keyword: keyword.value || undefined,
      from: rangeDates.value?.[0].format('YYYY-MM-DD'), to: rangeDates.value?.[1].format('YYYY-MM-DD'),
      page: page.value, size: size.value
    })
    rows.value = res.list || []
    total.value = res.total || 0
  } catch (e: any) { message.error(e?.message || '加载预定失败') }
  finally { loading.value = false }
}
function reload() { page.value = 1; void Promise.all([load(), loadCounts()]) }
function switchTab(key: string) { tab.value = key; reload() }
function onTableChange(p: any) { page.value = p.current; size.value = p.pageSize; void load() }

async function openDetail(id: string) {
  try {
    Object.assign(detail, await getBooking(id))
    await loadAttendeeNames(detail.attendeeIds)
    detailOpen.value = true
  } catch (e: any) { message.error(e?.message || '加载详情失败') }
}
function act(action: string, record: Booking) {
  const verbs: Record<string, string> = { checkin: '签到', cancel: '取消', release: '释放' }
  Modal.confirm({
    title: `确认${verbs[action]}`,
    content: `${record.roomName || '会议室'}[${record.title}]`,
    centered: true,
    onOk: async () => {
      try {
        if (action === 'checkin') await checkinBooking(record.id)
        if (action === 'cancel') await cancelBooking(record.id)
        if (action === 'release') await releaseBooking(record.id)
        message.success(`${verbs[action]}成功`)
        await Promise.all([load(), loadCounts()])
      } catch (e: any) { message.error(e?.message || `${verbs[action]}失败`); throw e }
    }
  })
}
function openExtend(record: Booking) {
  editing.value = record
  extendTime.value = dayjs(record.endTime.replace(' ', 'T'))
  extendOpen.value = true
}
async function submitExtend() {
  if (!editing.value || !extendTime.value) return
  saving.value = true
  try {
    // 结束基准取当前结束时间；选 00:00 表示延到当天 24:00（次日 00:00）
    const base = dayjs(editing.value.endTime.replace(' ', 'T'))
    let target = base.hour(extendTime.value.hour()).minute(extendTime.value.minute()).second(0).millisecond(0)
    if (!target.isAfter(base)) target = target.add(1, 'day')
    const endTime = target.format('YYYY-MM-DD HH:mm:ss')
    await extendBooking(editing.value.id, endTime)
    message.success('延时成功')
    extendOpen.value = false
    await load()
  } catch (e: any) { message.error(e?.message || '延时失败') }
  finally { saving.value = false }
}
async function openEdit(record: Booking) {
  editing.value = record
  editForm.roomId = record.roomId
  editForm.title = record.title
  editForm.attendeeCount = record.attendeeCount
  editForm.attendeeIds = [...(record.attendeeIds || [])]
  editForm.remark = record.remark || ''
  editForm.date = record.startTime.slice(0, 10)
  editRange.value = { start: record.startTime.slice(11, 16), end: endSlotOf(record) }
  await loadAttendeeNames(editForm.attendeeIds)
  employeeOptions.value = editForm.attendeeIds.map(id => ({ label: attendeeNames.value[id] || id, value: id }))
  try {
    editSchedule.value = await getSchedule({ date: editForm.date, city: record.city })
  } catch { editSchedule.value = { rooms: [] } }
  editOpen.value = true
}
/** 修改弹窗里本人当前预定的格子可重新选择；他人/其他预定仍视为占用 */
function editableSlot(s: Slot) {
  return s.state === 'FREE' || (s.state === 'MINE' && !!editing.value && s.bookingId === editing.value.id)
}
function pickEditSlot(cell: Slot) {
  const slots = editSlots.value
  const idx = slots.findIndex(s => s.start === cell.start)
  const cur = editRange.value
  if (!cur || cell.start <= cur.start) {
    editRange.value = { start: cell.start, end: cell.end }
    return
  }
  const startIdx = slots.findIndex(s => s.start === cur.start)
  if (slots.slice(startIdx < 0 ? 0 : startIdx, idx + 1).some(s => !editableSlot(s))) {
    message.warning('所选区间包含已占用时段，请重新选择起点')
    editRange.value = { start: cell.start, end: cell.end }
    return
  }
  editRange.value = { start: cur.start, end: cell.end }
}
async function onEditDateChange() {
  editRange.value = undefined
  if (!editForm.date) { editSchedule.value = { rooms: [] }; return }
  try {
    editSchedule.value = await getSchedule({ date: editForm.date, city: editing.value?.city })
  } catch { editSchedule.value = { rooms: [] } }
}
async function onEmployeeSearch(kw: string) {
  if (!kw.trim()) { employeeOptions.value = []; return }
  try {
    const list = await searchEmployees(kw)
    employeeOptions.value = list.map(e => ({ label: `${e.name}（${e.empNo}）`, value: e.accountId }))
  } catch { employeeOptions.value = [] }
}
async function submitEdit() {
  if (!editing.value || !editRange.value) { message.warning('请重新选择时段'); return }
  saving.value = true
  try {
    await updateBooking(editing.value.id, {
      roomId: editForm.roomId, title: editForm.title,
      startTime: `${editForm.date} ${editRange.value.start}:00`,
      endTime: endDateTime(editForm.date, editRange.value.end),
      attendeeCount: editForm.attendeeCount, attendeeIds: editForm.attendeeIds, remark: editForm.remark
    })
    message.success('修改成功')
    editOpen.value = false
    await Promise.all([load(), loadCounts()])
  } catch (e: any) { message.error(e?.message || '修改失败') }
  finally { saving.value = false }
}

onMounted(async () => {
  try {
    Object.assign(dicts, await getDicts())
    await Promise.all([loadCounts(), load()])
    const id = route.query.detail as string
    if (id) await openDetail(id)
  } catch (e: any) { message.error(e?.message || '初始化我的预定失败') }
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.tabs-head { margin-bottom: 12px; }
.grow { flex: 1 1 auto; }
.hint { font-size: 11px; color: var(--text-secondary, #8c8c8c); }
.tabs { display: flex; gap: 20px; border-bottom: 1px solid var(--border, #eee); }
.tabs button {
  border: 0; background: transparent; padding: 8px 2px; cursor: pointer;
  color: var(--text-secondary, #8c8c8c); font-size: 14px; border-bottom: 2px solid transparent;
}
.tabs button.active { color: var(--primary, #2f54eb); border-bottom-color: var(--primary, #2f54eb); font-weight: 600; }
.tabs em { font-style: normal; font-size: 11px; margin-left: 4px; }
.booking-table { min-width: 1200px; }
/* 空态无横向数据：去掉最小宽度，避免底部出现横向滚动条 */
.booking-table.tbl-empty-mode { min-width: 0; }
.slot-scroll { overflow-x: auto; }
</style>
