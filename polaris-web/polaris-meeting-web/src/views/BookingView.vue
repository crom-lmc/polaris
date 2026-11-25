<template>
  <div class="ft-page">
    <section class="ft-head toolbar">
      <a-select v-model:value="city" :options="cityOptions" placeholder="城市" style="width: 130px" @change="load" />
      <a-date-picker v-model:value="dateValue" format="YYYY-MM-DD" :disabled-date="disabledDate"
                     placeholder="选择日期" style="width: 140px" />
      <span class="hint">可预约：{{ today }} ~ {{ maxDate }}（最多 {{ maxDays }} 天）</span>
      <span class="grow" />
      <a-tooltip v-if="!store.can('meeting:booking:create')" title="需要新建预定权限">
        <a-button type="primary" disabled>＋ 新建预定</a-button>
      </a-tooltip>
      <a-button v-else type="primary" @click="openCreate()">＋ 新建预定</a-button>
      <a-button @click="load">刷新</a-button>
    </section>

    <div class="rule-line">
      单次最长 {{ maxMinutesText }} · 会议开始后 {{ graceMinutes }} 分钟未签到自动释放 ·
      <template v-if="noShowThreshold > 0">
        近 {{ noShowWindowDays }} 天爽约 {{ noShowCount }}/{{ noShowThreshold }} 次，达到后禁约 {{ noShowBanDays }} 天
      </template>
      <template v-else>爽约限制未启用</template>
    </div>

    <a-alert v-if="bannedUntil" type="warning" show-icon banner
             :message="`爽约次数达到限制，${bannedUntil} 前不可预约会议室`" />
    <a-alert v-if="!city" type="warning" show-icon message="未获取到当前人员城市，暂无法预定会议室" banner />
    <a-alert v-else-if="!schedule.rooms.length" type="info" show-icon message="所选城市暂无可展示会议室" banner />

    <section class="ft-scroll hide-vscroll">
      <a-card size="small" title="当日会议室时间轴（00:00–24:00，30 分钟一格）">
        <div class="timeline-legend">
          <span><i class="legend mine" />我的预约</span>
          <span><i class="legend others" />他人预约</span>
          <span><i class="legend past" />已过去</span>
          <span><i class="legend disabled" />维护/停用</span>
        </div>
        <div class="timeline-scroll">
          <div class="timeline-grid">
            <div class="hour-row">
              <span class="room-col room-head">会议室</span>
              <span class="hour-scale"><b v-for="h in 24" :key="h">{{ String(h - 1).padStart(2, '0') }}</b></span>
            </div>
            <div v-for="room in schedule.rooms" :key="room.roomId" class="timeline-row">
              <div class="room-col">
                <a-tooltip :title="`${room.name}（${room.status}）`">
                  <span class="room-name">{{ room.name }}</span>
                </a-tooltip>
                <small class="room-devices">{{ deviceText(room) }}</small>
              </div>
              <SlotGrid :slots="room.slots" />
            </div>
          </div>
        </div>
      </a-card>
    </section>

    <a-modal v-model:open="formOpen" title="新建预定" :width="modalWidth" :confirm-loading="saving"
             centered @ok="submit">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="会议室" required>
              <a-select v-model:value="form.roomId" :options="roomOptions" placeholder="选择可用会议室"
                        @change="onModalDateChange" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="日期" required>
              <a-select v-model:value="form.date" :options="dateOptions" style="width: 100%"
                        :get-popup-container="(trigger: HTMLElement) => trigger.parentElement" @change="onModalDateChange" />
            </a-form-item>
          </a-col>
          <a-col :span="10">
            <a-form-item label="会议主题" required><a-input v-model:value="form.title" placeholder="请输入会议主题" /></a-form-item>
          </a-col>
        </a-row>
        <a-form-item :label="`选择时段（已选：${rangeText || '点击开始格，再点击更晚的空闲格'}）`" required>
          <div class="slot-scroll">
            <SlotGrid :slots="modalSlots" selectable hour-scale :value="range" @pick="pickSlot" />
          </div>
          <div v-if="!modalSlots.some(s => s.state === 'FREE')" class="hint slot-tip">
            当前日期已无可预约时段，请切换日期或会议室
          </div>
          <div v-else-if="!range" class="hint slot-tip">绿色为可预约时段：点击开始格，再点击更晚的空闲格确定结束</div>
          <div v-if="durationExceeded" class="hint slot-tip warn">
            已选 {{ selectedMinutes }} 分钟，超过单次最长 {{ maxMinutes }} 分钟，请缩短时段
          </div>
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="参会人数" required>
              <a-input-number v-model:value="form.attendeeCount" :min="1" :max="selectedRoom?.maxCapacity || 999"
                              style="width: 100%" />
              <span v-if="selectedRoom && form.attendeeCount > selectedRoom.comfortCapacity" class="muted">
                超过舒适人数 {{ selectedRoom.comfortCapacity }}，建议换更大房间
              </span>
            </a-form-item>
          </a-col>
          <a-col :span="16">
            <a-form-item label="邀请参会人（可选）">
              <a-select v-model:value="form.attendeeIds" mode="multiple" :options="employeeOptions"
                        :filter-option="false" placeholder="输入姓名或工号搜索" allow-clear
                        @search="onEmployeeSearch" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import SlotGrid from '@/components/SlotGrid.vue'
import {
  getDicts, getBookingConfig, getSchedule, searchEmployees, createBooking,
  type Slot, type RoomSchedule, type Schedule, type Employee, type DictItem
} from '@/api/meeting'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const dicts = reactive<Record<string, DictItem[]>>({})
const city = ref<string>()
const date = ref(dayjs().format('YYYY-MM-DD'))
const maxDays = ref(7)
const graceMinutes = ref(15)
const maxMinutes = ref(0)
const noShowWindowDays = ref(90)
const noShowThreshold = ref(3)
const noShowBanDays = ref(7)
const noShowCount = ref(0)
const bannedUntil = ref('')
const maxMinutesText = computed(() => maxMinutes.value > 0 ? `${maxMinutes.value} 分钟` : '不限制')
const schedule = reactive<Schedule>({ date: date.value, rooms: [] })
const today = dayjs().format('YYYY-MM-DD')
const maxDate = computed(() => dayjs().add(maxDays.value, 'day').format('YYYY-MM-DD'))
const cityOptions = computed(() => (dicts['ehr.job_city'] || []).map(d => ({ label: d.label, value: d.value })))
const roomOptions = computed(() => schedule.rooms
  .filter(r => r.status === '可用')
  .map(r => ({ label: r.name, value: r.roomId })))
/** 会议室名下方设备摘要：字典 meeting.device 的 value→label，形如「麦克风√ 投影仪√」 */
function deviceText(room: RoomSchedule) {
  const items = room.devices || []
  if (!items.length) return '—'
  const labels = dicts['meeting.device'] || []
  return items.map(code => {
    const label = labels.find(d => d.value === code)?.label || code
    return `${label}√`
  }).join(' ')
}
const weekNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const dateOptions = computed(() => Array.from({ length: maxDays.value + 1 }, (_, i) => {
  const d = dayjs().add(i, 'day')
  const key = d.format('YYYY-MM-DD')
  const label = i === 0 ? '今天' : i === 1 ? '明天' : `${d.format('MM-DD')} ${weekNames[d.day()]}`
  return { label: `${label}（${key}）`, value: key, short: label }
}))
/** 顶部日期选择框：页面内部仍用字符串日期，这里做 Dayjs 与字符串的转换 */
const dateValue = computed<Dayjs>({
  get: () => dayjs(date.value),
  set: v => { if (v) selectDate(v.format('YYYY-MM-DD')) }
})
function disabledDate(d: Dayjs) {
  const key = d.format('YYYY-MM-DD')
  return key < today || key > maxDate.value
}

async function load() {
  if (!city.value) { schedule.rooms = []; return }
  try {
    Object.assign(schedule, await getSchedule({ date: date.value, city: city.value }))
  } catch (e: any) { message.error(e?.message || '加载时间轴失败') }
}
function selectDate(key: string) {
  if (date.value === key) return
  date.value = key
  void load()
}

const formOpen = ref(false)
const saving = ref(false)
/** 弹窗加宽到能完整显示 48 格整点标签，窄屏自动撑满可用宽度 */
const modalWidth = computed(() => (typeof window !== 'undefined' ? Math.max(1100, Math.min(1500, window.innerWidth - 80)) : 1280))
const form = reactive({
  roomId: '', date: date.value, title: '', attendeeCount: 1,
  attendeeIds: [] as string[], remark: ''
})
const range = ref<{ start: string; end: string }>()
const rangeText = computed(() => range.value ? `${range.value.start}–${range.value.end}` : '')
/** 结束格显示 24:00 时，按次日 00:00 提交（仍属同一自然日预约） */
function endDateTime(date: string, end: string) {
  return end === '24:00'
    ? `${dayjs(date).add(1, 'day').format('YYYY-MM-DD')} 00:00:00`
    : `${date} ${end}:00`
}
const employeeOptions = ref<Array<{ label: string; value: string }>>([])

const selectedRoom = computed(() => schedule.rooms.find(r => r.roomId === form.roomId))
const modalSlots = computed<Slot[]>(() => {
  const room: RoomSchedule | undefined = schedule.rooms.find(r => r.roomId === form.roomId && r.date === form.date)
  return room?.slots || []
})
/** 已选格数 × 30 分钟；最后一格结束显示 24:00，用格子位置算时长避免解析跨日时间 */
const selectedMinutes = computed(() => {
  const r = range.value
  if (!r) return 0
  const slots = modalSlots.value
  const from = slots.findIndex(s => s.start === r.start)
  const to = slots.findIndex(s => s.end === r.end)
  return from >= 0 && to >= from ? (to - from + 1) * 30 : 0
})
const durationExceeded = computed(() => maxMinutes.value > 0 && selectedMinutes.value > maxMinutes.value)

function openCreate() {
  // 当天已无可预约时段时默认选明天，避免打开就是一片斜纹、不知道怎么点
  const todayRoom = schedule.rooms.find(r => r.date === date.value)
  const defaultDate = todayRoom?.slots.some(s => s.state === 'FREE') ? date.value : dateOptions.value[1]?.value || date.value
  Object.assign(form, {
    roomId: roomOptions.value[0]?.value || '', date: defaultDate, title: '',
    attendeeCount: 1, attendeeIds: [], remark: ''
  })
  range.value = undefined
  // 打开弹窗即预加载员工默认列表，参会人下拉一点开就有数据
  void onEmployeeSearch('')
  formOpen.value = true
  // 默认日期若有变动，按所选日期拉取时段
  if (defaultDate !== date.value) void onModalDateChange()
}
function resetRange() { range.value = undefined }
async function onModalDateChange() {
  resetRange()
  if (!form.date) return
  try {
    const data = await getSchedule({ date: form.date, city: city.value, roomId: form.roomId || undefined })
    const found = data.rooms[0]
    const idx = schedule.rooms.findIndex(r => r.roomId === found?.roomId)
    if (found && idx >= 0) schedule.rooms[idx] = found
    else if (found) schedule.rooms.push(found)
  } catch (e: any) { message.error(e?.message || '加载所选日期时段失败') }
}

function pickSlot(cell: Slot) {
  const slots = modalSlots.value
  const index = slots.findIndex(s => s.start === cell.start)
  const current = range.value
  if (!current) {
    range.value = { start: cell.start, end: cell.end }
    return
  }
  if (cell.start <= current.start) {
    range.value = { start: cell.start, end: cell.end }
    return
  }
  const between = slots.slice(indexOfStart(slots, current.start), index + 1)
  if (between.some(s => s.state !== 'FREE')) {
    message.warning('两次点击之间已有占用，请重新选择起点')
    range.value = { start: cell.start, end: cell.end }
    return
  }
  range.value = { start: current.start, end: cell.end }
}
function indexOfStart(slots: Slot[], start: string) {
  const i = slots.findIndex(s => s.start === start)
  return i < 0 ? 0 : i
}

async function onEmployeeSearch(kw: string) {
  // 空关键词也拉取默认列表（后端不过滤、返回前 N 个在职员工），聚焦即可选，不再显示"暂无数据"
  try {
    const list: Employee[] = await searchEmployees(kw?.trim() || '')
    employeeOptions.value = list.map(e => ({ label: `${e.name}（${e.empNo}）`, value: e.accountId }))
  } catch { employeeOptions.value = [] }
}

async function submit() {
  if (!form.roomId || !form.title.trim() || !range.value) {
    message.warning('请选择会议室、填写主题并在时间轴上选择时段'); return
  }
  if (bannedUntil.value) {
    message.warning(`爽约次数达到限制，${bannedUntil.value} 前不可预约会议室`); return
  }
  if (durationExceeded.value) {
    message.warning(`单次预约最长 ${maxMinutes.value} 分钟，请缩短时段`); return
  }
  saving.value = true
  try {
    await createBooking({
      roomId: form.roomId, title: form.title.trim(),
      startTime: `${form.date} ${range.value.start}:00`,
      endTime: endDateTime(form.date, range.value.end),
      attendeeCount: form.attendeeCount, attendeeIds: form.attendeeIds, remark: form.remark
    })
    message.success('预定成功')
    formOpen.value = false
    await load()
  } catch (e: any) { message.error(e?.message || '预定失败') }
  finally { saving.value = false }
}

onMounted(async () => {
  try {
    Object.assign(dicts, await getDicts())
    const config = await getBookingConfig()
    maxDays.value = config.maxDays
    graceMinutes.value = config.graceMinutes
    maxMinutes.value = config.maxMinutes
    noShowWindowDays.value = config.noShowWindowDays
    noShowThreshold.value = config.noShowThreshold
    noShowBanDays.value = config.noShowBanDays
    noShowCount.value = config.noShowCount
    bannedUntil.value = config.bannedUntil || ''
    if (!city.value && cityOptions.value.length) city.value = cityOptions.value[0].value
    else city.value = store.city || city.value
    await load()
  } catch (e: any) { message.error(e?.message || '初始化预定页失败') }
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.rule-line { margin: -4px 0 10px; font-size: 11px; color: var(--text-secondary, #8c8c8c); line-height: 18px; }
.grow { flex: 1 1 auto; }
.slot-tip.warn { color: var(--warning-color, #faad14); }
.hint, .muted { font-size: 11px; color: var(--text-secondary, #8c8c8c); }
.timeline-legend { display: flex; gap: 16px; font-size: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.legend { display: inline-block; width: 12px; height: 12px; margin-right: 4px; vertical-align: -2px; }
.legend.mine { background: #13c2c2; }
.legend.others { background: #4c8dff; }
.legend.past { background: repeating-linear-gradient(45deg, #f0f0f0 0 3px, #dedede 3px 6px); }
.legend.disabled { background: #bfbfbf; }
/* overflow-x:auto 会让 overflow-y 计算成 auto 而产生垂直滚动条；内容高度自适应，不需纵向滚动 */
.timeline-scroll { overflow-x: auto; overflow-y: hidden; scrollbar-width: none; -ms-overflow-style: none; }
.timeline-scroll::-webkit-scrollbar:vertical { width: 0; display: none; }
/* grid 让所有行共享同一列宽：左列由最长会议室名撑开，时间格统一左对齐、不穿模 */
.timeline-grid { display: grid; grid-template-columns: max-content minmax(720px, 1fr); min-width: 1080px; font-size: 12px; }
.hour-row, .timeline-row { display: contents; }
.hour-row .room-col, .hour-row .hour-scale { height: 24px; }
.room-head { display: flex; align-items: center; }
/* 小时数字居中在每两个 30 分钟格子的交界上方 */
.hour-scale { display: grid; grid-template-columns: repeat(24, 1fr); font-size: 10px; color: var(--text-secondary, #8c8c8c); }
.hour-scale b { font-weight: 400; text-align: center; border-left: 1px solid var(--border, #eee); }
/* 会议室行：左侧名称 + 设备两行，右侧时间格；行间留距离并加分割线 */
.room-col {
  display: flex; flex-direction: column; justify-content: center; gap: 2px;
  padding: 4px 10px 4px 0; min-width: 0;
}
.room-name { font-size: 12px; line-height: 15px; white-space: nowrap; }
.room-devices { font-size: 10px; line-height: 12px; color: var(--text-secondary, #8c8c8c); white-space: nowrap; }
.timeline-row { border-bottom: 1px solid var(--border, #f0f0f0); }
.timeline-row .room-col { border-bottom: 1px solid var(--border, #f0f0f0); }
.timeline-row :deep(.slot-wrap) { border-bottom: 1px solid var(--border, #f0f0f0); }
/* 时间格高度容纳两行文字（名称+设备），显示时间的格子可占两格高 */
.timeline-row :deep(.slot-cell) { height: 34px; }
/* 弹窗时段条：48 格完整显示，鼠标经过不出现滚动条 */
.slot-scroll { overflow-x: auto; scrollbar-width: none; -ms-overflow-style: none; }
.slot-scroll::-webkit-scrollbar { width: 0; height: 0; display: none; }
.slot-tip { margin-top: 6px; }
.slot-scroll :deep(.slot-cell) { justify-content: center; align-items: center; }
.slot-scroll :deep(.slot-cell) span { font-size: 10px; }
/* 页面右侧不显示垂直滚动条（仍可滚动），横向滚动条保留 */
.hide-vscroll { scrollbar-width: none; -ms-overflow-style: none; }
.hide-vscroll::-webkit-scrollbar:vertical { width: 0; display: none; }
</style>
