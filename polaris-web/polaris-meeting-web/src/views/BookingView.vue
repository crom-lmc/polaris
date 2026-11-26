<template>
  <div class="ft-page">
    <!-- 移动端：顶部只留城市/日期/类型/更多，筛选 chip 在下方展开，底部按钮确认 -->
    <section v-if="mobile" class="ft-head mobile-head">
      <div class="mobile-filter-row">
        <a-select v-model:value="city" :options="cityOptions" placeholder="城市" class="mf-city" size="small" @change="load" />
        <a-select :value="date" :options="dateOptions" class="mf-date" size="small" @change="selectDate" />
        <a-select v-model:value="filterType" placeholder="会议室类型" class="mf-type" size="small" allow-clear
                  :options="dictOptions('meeting.room_type')" />
        <button class="mf-more" :class="{ active: moreOpen }" @click="moreOpen = !moreOpen">
          更多<span v-if="activeFilterCount"> ·{{ activeFilterCount }}</span>
        </button>
      </div>
      <div v-if="moreOpen" class="mobile-more-panel">
        <div class="mf-title">会议人数</div>
        <div class="mf-chips">
          <button v-for="t in dicts['meeting.room_capacity'] || []" :key="t.value" class="mf-chip"
                  :class="{ active: filterTiers.includes(t.value) }" @click="toggleChip(filterTiers, t.value)">
            {{ t.label }}
          </button>
        </div>
        <div class="mf-title">会议室所在楼层</div>
        <div class="mf-chips">
          <button v-for="f in floorOptions" :key="f" class="mf-chip"
                  :class="{ active: filterFloors.includes(f) }" @click="toggleChip(filterFloors, f)">
            {{ f }}
          </button>
        </div>
        <div class="mf-footer">
          <a-button size="small" @click="resetFilters">重置</a-button>
          <a-button size="small" type="primary" @click="moreOpen = false">确认筛选</a-button>
        </div>
      </div>
    </section>

    <section v-else class="ft-head toolbar">
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

    <div v-if="!mobile" class="rule-line">
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

    <!-- 移动端卡片列表：每间会议室一行 08:00–22:00 时间条，点空闲格直接选时 -->
    <section v-if="mobile && mobileView === 'card'" class="ft-scroll mobile-scroll">
      <div class="mobile-list-bar">
        <div class="ml-legend">
          <span><i class="legend mine" />我的</span>
          <span><i class="legend others" />已约</span>
          <span><i class="legend past" />已过期</span>
        </div>
        <a @click="mobileView = 'timeline'">时间轴模式</a>
      </div>
      <div v-if="!filteredRooms.length" class="mobile-empty"><a-empty description="没有符合条件的会议室" /></div>
      <div v-for="room in filteredRooms" :key="room.roomId" class="room-card">
        <div class="rc-head">
          <div class="rc-title">
            <strong>{{ room.name }}</strong>
            <a-tag v-if="roomTypeLabel(room)" color="orange">{{ roomTypeLabel(room) }}</a-tag>
            <a-tag v-if="roomNeedApproval(room)" color="blue">需审批</a-tag>
          </div>
          <a @click="openIntroFor(room.roomId)">简介</a>
        </div>
        <div class="rc-sub">{{ room.maxCapacity }} 人 · {{ deviceText(room) }}</div>
        <div class="rc-slots">
          <button v-for="s in workSlots(room)" :key="s.start" class="rc-slot" :class="[s.state]"
                  :disabled="s.state !== 'FREE'" @click="openPicker(room, s)">
            {{ s.start.slice(0, 2) }}
          </button>
        </div>
      </div>
    </section>

    <section v-else class="ft-scroll hide-vscroll">
      <div v-if="mobile" class="mobile-list-bar timeline-bar">
        <span class="hint">当日 00:00–24:00 时间轴</span>
        <a @click="mobileView = 'card'">列表模式</a>
      </div>
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

    <a-modal v-model:open="formOpen" title="新建预定" :width="modalWidth" centered>
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="mobile ? 24 : 8">
            <a-form-item label="会议室" required>
              <a-select v-if="!mobile" v-model:value="form.roomId" :options="roomOptions" placeholder="选择可用会议室"
                        @change="onModalDateChange" />
              <!-- 手机端从卡片选会议室→选时段后才进表单，此处只读展示 -->
              <a-input v-else :value="selectedRoomName" readonly />
            </a-form-item>
          </a-col>
          <a-col :span="mobile ? 12 : 6">
            <a-form-item label="日期" required>
              <a-select v-if="!mobile" v-model:value="form.date" :options="dateOptions" style="width: 100%"
                        :get-popup-container="(trigger: HTMLElement) => trigger.parentElement" @change="onModalDateChange" />
              <a-input v-else :value="form.date" readonly />
            </a-form-item>
          </a-col>
          <a-col :span="mobile ? 12 : 10">
            <a-form-item label="会议主题" required><a-input v-model:value="form.title" placeholder="请输入会议主题" /></a-form-item>
          </a-col>
        </a-row>
        <!-- 手机端时段在进入表单前已选好，只读展示；PC 仍用时间轴选择 -->
        <a-form-item v-if="mobile" label="已选时段">
          <div class="mr-value">{{ rangeText || '—' }}</div>
        </a-form-item>
        <a-form-item v-else :label="`选择时段（已选：${rangeText || '点击开始格，再点击更晚的空闲格'}）`" required>
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
          <a-col :span="mobile ? 24 : 8">
            <a-form-item label="参会人数" required>
              <a-input-number v-model:value="form.attendeeCount" :min="1" :max="selectedRoom?.maxCapacity || 999"
                              style="width: 100%" />
              <span v-if="selectedRoom && form.attendeeCount > selectedRoom.comfortCapacity" class="muted">
                超过舒适人数 {{ selectedRoom.comfortCapacity }}，建议换更大房间
              </span>
            </a-form-item>
          </a-col>
          <a-col :span="mobile ? 24 : 16">
            <a-form-item label="邀请参会人（可选）">
              <a-select v-model:value="form.attendeeIds" mode="multiple" :options="employeeOptions"
                        :filter-option="false" placeholder="输入姓名或工号搜索" allow-clear
                        @search="onEmployeeSearch" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
      <template #footer>
        <div class="create-footer">
          <a-tooltip v-if="!form.roomId" title="请先选择会议室">
            <span><a-button disabled>会议室简介</a-button></span>
          </a-tooltip>
          <a-button v-else @click="openRoomIntro">会议室简介</a-button>
          <span class="grow" />
          <a-button @click="formOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">确定</a-button>
        </div>
      </template>
    </a-modal>

    <!-- 移动端时间选择：垂直 30 分钟刻度，点起点再点更晚的空闲格，底部确认进入表单 -->
    <a-modal v-model:open="pickerOpen" title="时间选择" :width="mobile ? '100%' : 520" centered :footer="null"
             wrap-class-name="meeting-picker-modal">
      <div class="picker-head">
        <div class="ph-name">
          <strong>{{ pickerRoom?.name }}</strong>
          <a-tag v-if="roomNeedApproval(pickerRoom)" color="blue">需审批</a-tag>
        </div>
        <div class="ph-links">
          <a v-if="pickerRoom" @click="openIntroFor(pickerRoom.roomId)">简介</a>
          <a v-if="pickerRoom" @click="gotoRoomBookings(pickerRoom.roomId)">预定记录</a>
        </div>
      </div>
      <div class="picker-tip">点击开始时间，再点击更晚的空闲时间确定结束（30 分钟一格）</div>
      <div class="picker-list">
        <div v-for="s in pickerSlots" :key="s.start" class="picker-row" :class="[s.state, { chosen: isPicked(s) }]"
             @click="pickForPicker(s)">
          <i class="pr-dot" />
          <span class="pr-time">{{ s.start }}</span>
          <span class="pr-label">{{ pickerLabel(s) }}</span>
        </div>
      </div>
      <div class="picker-footer">
        <span class="pf-text">已选择时间 {{ pickerDate }}<template v-if="pickerRange"> {{ pickerRange.start }}–{{ pickerRange.end }}</template></span>
        <a-button type="primary" :disabled="!pickerRange || !store.can('meeting:booking:create')" @click="confirmPicker">
          立即预定
        </a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="introOpen" title="会议室简介" :width="mobile ? '100%' : 760" centered :footer="null">
      <a-spin :spinning="introLoading">
        <div v-if="introRoom" class="room-intro">
          <div class="intro-top">
            <div class="intro-image" :class="{ 'intro-mobile': mobile }">
              <a-image v-if="introRoom.roomImages?.length" :src="introRoom.roomImages[0]" :width="mobile ? '100%' : 320" />
              <a-empty v-else description="暂无会议室图片" />
            </div>
            <div class="intro-info">
              <h3>{{ introRoom.name }}<small v-if="introRoom.roomNo">（{{ introRoom.roomNo }}）</small></h3>
              <a-descriptions :column="1" size="small" bordered>
                <a-descriptions-item label="位置">
                  {{ [introRoom.city, introRoom.buildingName, introRoom.floorNo].filter(Boolean).join(' · ') || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="类型">{{ dictLabel('meeting.room_type', introRoom.roomType) }}</a-descriptions-item>
                <a-descriptions-item label="座位布局">{{ dictLabel('meeting.seat_layout', introRoom.layout) }}</a-descriptions-item>
                <a-descriptions-item label="容量">
                  最多 {{ introRoom.maxCapacity }} 人（舒适 {{ introRoom.comfortCapacity || '-' }} 人）
                </a-descriptions-item>
                <a-descriptions-item label="状态">{{ introRoom.status || '-' }}</a-descriptions-item>
              </a-descriptions>
            </div>
          </div>
          <div class="intro-section">
            <h4>设备清单</h4>
            <div v-if="introRoom.devices?.length" class="intro-devices">
              <a-tag v-for="(d, i) in introRoom.devices" :key="i" color="blue">
                {{ deviceLabel(d.deviceCode) }} ×{{ d.qty }}<template v-if="d.remark">（{{ d.remark }}）</template>
              </a-tag>
            </div>
            <span v-else class="muted">暂无设备信息</span>
          </div>
          <div v-if="introRoom.remark" class="intro-section">
            <h4>备注</h4>
            <div class="intro-remark">{{ introRoom.remark }}</div>
          </div>
        </div>
        <a-empty v-else-if="!introLoading" description="暂无会议室信息" />
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs, { type Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import SlotGrid from '@/components/SlotGrid.vue'
import {
  getDicts, getBookingConfig, getSchedule, searchEmployees, createBooking, getRoom, listRooms,
  type Slot, type RoomSchedule, type Schedule, type Employee, type DictItem, type Room
} from '@/api/meeting'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const router = useRouter()
/** 与 MeetingLayout 的底部导航共用 767px 断点，避免页面形态和导航不一致 */
const mobile = ref(false)
const mq = window.matchMedia('(max-width: 767px)')
const syncMobile = () => { mobile.value = mq.matches }
onMounted(() => { syncMobile(); mq.addEventListener('change', syncMobile) })
onBeforeUnmount(() => mq.removeEventListener('change', syncMobile))
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

/* ---- 移动端筛选：会议室元数据由 /rooms 单独取，schedule 只给档期 ---- */
const roomMeta = ref<Record<string, Room>>({})
const filterType = ref<string>()
const filterTiers = ref<string[]>([])
const filterFloors = ref<string[]>([])
const moreOpen = ref(false)
const mobileView = ref<'card' | 'timeline'>('card')
function dictOptions(type: string) {
  return (dicts[type] || []).map(d => ({ label: d.label, value: d.value }))
}
function floorLabel(v?: string) {
  if (!v) return ''
  const n = v.match(/^(\d+)(?:F|层)?$/)
  return n ? `${n[1]}层` : v
}
function toggleChip(list: string[], value: string) {
  const i = list.indexOf(value)
  if (i >= 0) list.splice(i, 1)
  else list.push(value)
}
function resetFilters() {
  filterType.value = undefined
  filterTiers.value = []
  filterFloors.value = []
  moreOpen.value = false
}
const activeFilterCount = computed(() =>
  (filterType.value ? 1 : 0) + filterTiers.value.length + filterFloors.value.length)
/** schedule 不返回楼层/类型/审批标记，用会议室详情补齐（按 roomId 关联） */
function metaOf(room?: { roomId?: string; id?: string } | null) {
  return room ? roomMeta.value[room.roomId || room.id || ''] : undefined
}
function roomTypeLabel(room?: RoomSchedule | null) {
  return room ? dictLabel('meeting.room_type', metaOf(room)?.roomType || '') : ''
}
function roomNeedApproval(room?: RoomSchedule | null) {
  return !!metaOf(room || null)?.needApproval
}
const floorOptions = computed(() => Array.from(new Set(
  Object.values(roomMeta.value).map(m => floorLabel(m.floorNo)).filter(Boolean)
)).sort((a, b) => a.localeCompare(b, 'zh-CN')))
const filteredRooms = computed(() => schedule.rooms.filter(r => {
  const m = metaOf(r)
  if (filterType.value && m?.roomType !== filterType.value) return false
  if (filterTiers.value.length && !filterTiers.value.includes(m?.capacityTier || '')) return false
  if (filterFloors.value.length && !filterFloors.value.includes(floorLabel(m?.floorNo))) return false
  return true
}))
/** 卡片时间条只展示工作时段 08:00–22:00，每 30 分钟一格 */
function workSlots(room: RoomSchedule) {
  return room.slots.filter(s => s.start >= '08:00' && s.start < '22:00')
}
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
function dictLabel(type: string, value?: string) {
  if (!value) return '-'
  return (dicts[type] || []).find(d => d.value === value)?.label || value
}
function deviceLabel(code: string) { return dictLabel('meeting.device', code) }
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
  if (!city.value) { schedule.rooms = []; roomMeta.value = {}; return }
  // 元数据失败不能拖垮档期加载：单独吞掉错误，卡片上只少几个标签
  const roomsPromise = listRooms({ city: city.value }).catch(() => [] as Room[])
  try {
    const [data, rooms] = await Promise.all([
      getSchedule({ date: date.value, city: city.value }), roomsPromise
    ])
    Object.assign(schedule, data)
    roomMeta.value = Object.fromEntries(rooms.map(r => [r.id, r]))
  } catch (e: any) { message.error(e?.message || '加载时间轴失败') }
}
function selectDate(key: string) {
  if (date.value === key) return
  date.value = key
  void load()
}

const formOpen = ref(false)
const saving = ref(false)
/** 弹窗加宽到能完整显示 48 格整点标签；手机直接全宽，表单改单列 */
const modalWidth = computed(() => mobile.value ? '100%' :
  (typeof window !== 'undefined' ? Math.max(1100, Math.min(1500, window.innerWidth - 80)) : 1280))
const form = reactive({
  roomId: '', date: date.value, title: '', attendeeCount: 1,
  attendeeIds: [] as string[], remark: ''
})
const range = ref<{ start: string; end: string }>()
const rangeText = computed(() => range.value ? `${range.value.start}–${range.value.end}` : '')
/** 结束格显示 24:00 时，按次日 00:00 提交（仍属同一自然日预约）；LocalDateTime 必须走 ISO 的 T 分隔 */
function endDateTime(date: string, end: string) {
  return end === '24:00'
    ? `${dayjs(date).add(1, 'day').format('YYYY-MM-DD')}T00:00:00`
    : `${date}T${end}:00`
}
const employeeOptions = ref<Array<{ label: string; value: string }>>([])

/** 会议室简介：详情接口返回 fileId 对应的临时签名预览 URL，前端不做本地拼路径 */
const introOpen = ref(false)
const introLoading = ref(false)
const introRoom = ref<Room | null>(null)
async function openRoomIntro() {
  if (!form.roomId) return
  introOpen.value = true
  introLoading.value = true
  introRoom.value = null
  try {
    introRoom.value = await getRoom(form.roomId)
  } catch (e: any) { message.error(e?.message || '加载会议室简介失败') }
  finally { introLoading.value = false }
}

const selectedRoom = computed(() => schedule.rooms.find(r => r.roomId === form.roomId))
/** 表单里会议室只读展示用：优先会议室名，找不到时回退到 id */
const selectedRoomName = computed(() => selectedRoom.value?.name || form.roomId)
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

/* ---- 移动端垂直时间选择：08:00–22:00，30 分钟一行 ---- */
const pickerOpen = ref(false)
const pickerRoom = ref<RoomSchedule>()
const pickerDate = ref('')
const pickerRange = ref<{ start: string; end: string }>()
const pickerSlots = computed<Slot[]>(() => {
  const room = schedule.rooms.find(r => r.roomId === pickerRoom.value?.roomId)
  return (room?.slots || []).filter(s => s.start >= '08:00' && s.start < '22:00')
})
function isPicked(s: Slot) {
  const r = pickerRange.value
  return !!r && s.start >= r.start && s.start < r.end
}
function pickerLabel(s: Slot) {
  if (s.state === 'FREE') return '空闲'
  if (s.state === 'MINE') return `我的预约${s.title ? ' · ' + s.title : ''}`
  if (s.state === 'OTHERS') {
    const who = (s.creatorName || '他人').replace(/（[^）]*）$/, '')
    return `${who}预约的${s.title || '会议'}`
  }
  if (s.state === 'PAST') return '已过去'
  if (s.state === 'DISABLED') return '房间不可用'
  return s.state === 'UNSELECTABLE' ? '结束最晚 23:30' : s.state
}
function pickForPicker(cell: Slot) {
  if (cell.state !== 'FREE') return
  const slots = pickerSlots.value
  const current = pickerRange.value
  if (!current || cell.start <= current.start) {
    pickerRange.value = { start: cell.start, end: cell.end }
    return
  }
  const from = slots.findIndex(s => s.start === current.start)
  const to = slots.findIndex(s => s.start === cell.start)
  if (slots.slice(from < 0 ? 0 : from, to + 1).some(s => s.state !== 'FREE')) {
    message.warning('两次点击之间已有占用，请重新选择起点')
    pickerRange.value = { start: cell.start, end: cell.end }
    return
  }
  pickerRange.value = { start: current.start, end: cell.end }
}
function openPicker(room: RoomSchedule, cell: Slot) {
  pickerRoom.value = room
  pickerDate.value = date.value
  pickerRange.value = { start: cell.start, end: cell.end }
  pickerOpen.value = true
}
function confirmPicker() {
  if (!pickerRoom.value || !pickerRange.value) return
  form.roomId = pickerRoom.value.roomId
  form.date = pickerDate.value
  range.value = { ...pickerRange.value }
  pickerOpen.value = false
  void onEmployeeSearch('')
  formOpen.value = true
}
function openIntroFor(roomId: string) {
  form.roomId = roomId
  void openRoomIntro()
}
function gotoRoomBookings(roomId: string) {
  pickerOpen.value = false
  void router.push({ path: '/my-bookings', query: { room: roomId } })
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
      startTime: `${form.date}T${range.value.start}:00`,
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
.hour-scale b { font-weight: 400; text-align: center; white-space: nowrap; border-left: 1px solid var(--border, #eee); }
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
/* 弹窗时段条：48 格在宽屏弹窗内本就放得下；overflow-x:auto 会连带把格子向上浮出的时间提示裁掉半截 */
.slot-scroll { overflow: visible; }
.slot-tip { margin-top: 6px; }
.slot-scroll :deep(.slot-cell) { justify-content: center; align-items: center; }
.slot-scroll :deep(.slot-cell) span { font-size: 10px; }
/* 页面右侧不显示垂直滚动条（仍可滚动），横向滚动条保留 */
.hide-vscroll { scrollbar-width: none; -ms-overflow-style: none; }
.hide-vscroll::-webkit-scrollbar:vertical { width: 0; display: none; }
/* 新建预定弹窗底部：左侧放会议室简介，取消/确定保持右对齐 */
.create-footer { display: flex; align-items: center; gap: 8px; }
.room-intro { min-height: 240px; }
.intro-top { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
.intro-image {
  width: 320px; flex: 0 0 320px; min-height: 220px;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg-page, #fafafa); border: 1px solid var(--border-subtle, #f0f0f0); border-radius: 6px;
}
.intro-info { flex: 1 1 320px; min-width: 0; }
.intro-info h3 { margin: 0 0 12px; font-size: 18px; }
.intro-info h3 small { margin-left: 6px; font-size: 12px; font-weight: 400; color: var(--color-text-secondary, #8c8c8c); }
.intro-section { margin-top: 16px; }
.intro-section h4 { margin: 0 0 8px; font-size: 14px; }
.intro-devices { display: flex; flex-wrap: wrap; gap: 6px; }
.intro-remark { line-height: 1.6; white-space: pre-wrap; color: var(--color-text-secondary, #595959); }

/* ===== 移动端：筛选 chip / 会议室卡片 / 垂直时间轴，配色沿用上面的状态色 ===== */
.mobile-head {
  padding: 8px 8px 6px; background: var(--panel-bg, var(--header-bg));
  border: 1px solid var(--border-subtle, #f0f0f0); border-radius: 6px;
}
.mobile-filter-row { display: flex; align-items: center; gap: 6px; }
.mf-city { width: 34%; min-width: 0; }
.mf-date { width: 26%; min-width: 0; }
.mf-type { flex: 1 1 auto; min-width: 0; }
.mf-more {
  flex: 0 0 auto; border: 0; background: transparent; color: var(--primary, #2f54eb);
  font-size: 13px; padding: 4px 2px; cursor: pointer;
}
.mf-more.active { font-weight: 600; }
.mobile-more-panel { margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--border, #eee); }
.mf-title { font-size: 12px; color: var(--text-secondary, #8c8c8c); margin: 6px 0 8px; }
.mf-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.mf-chip {
  border: 1px solid var(--border, #e5e6eb); background: var(--panel-bg, #fff);
  color: var(--text-base, #1f2329); border-radius: 4px; padding: 5px 9px; font-size: 12px;
}
.mf-chip.active {
  border-color: var(--primary, #2f54eb); color: var(--primary, #2f54eb);
  background: rgba(47, 84, 235, 0.08);
}
.mf-footer { display: flex; gap: 8px; margin-top: 12px; }
.mf-footer .ant-btn { flex: 1 1 0; }
.mobile-scroll { padding: 8px 4px; }
.mobile-list-bar {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  font-size: 12px; color: var(--text-secondary, #8c8c8c); padding: 2px 4px 8px;
}
.ml-legend { display: flex; gap: 10px; }
.ml-legend .legend { width: 10px; height: 10px; margin-right: 3px; }
.mobile-empty { padding: 24px 0; }
.room-card {
  background: var(--panel-bg, var(--header-bg)); border: 1px solid var(--border-subtle, #f0f0f0);
  border-radius: 8px; padding: 10px 10px 12px; margin-bottom: 10px;
}
.rc-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.rc-title { display: flex; align-items: center; gap: 6px; min-width: 0; flex-wrap: wrap; }
.rc-title strong { font-size: 14px; }
.rc-title .ant-tag { margin: 0; font-size: 10px; line-height: 16px; padding: 0 4px; }
.rc-head > a { flex: 0 0 auto; font-size: 12px; }
.rc-sub {
  font-size: 11px; color: var(--text-secondary, #8c8c8c); margin: 4px 0 8px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.rc-slots {
  display: grid; grid-template-columns: repeat(28, minmax(0, 1fr)); gap: 1px;
  background: var(--border, #e5e6eb); padding: 1px; border-radius: 3px; overflow: hidden;
}
.rc-slot {
  border: 0; padding: 0; min-width: 0; height: 15px; font-size: 0;
  background: var(--panel-bg, #fff);
}
.rc-slot.FREE { background: #e8f7ee; cursor: pointer; }
.rc-slot.MINE { background: #13c2c2; }
.rc-slot.OTHERS { background: #4c8dff; }
.rc-slot.PAST { background: repeating-linear-gradient(45deg, #f0f0f0 0 3px, #dedede 3px 6px); }
.rc-slot.DISABLED, .rc-slot.UNSELECTABLE { background: #bfbfbf; }
.mobile-range { display: flex; align-items: center; gap: 8px; }
.mr-value {
  flex: 1 1 auto; min-width: 0; font-size: 13px; padding: 6px 8px;
  border: 1px solid var(--border, #e5e6eb); border-radius: 4px;
}

/* 弹层 teleport 到 body，scoped 样式必须走 :global 才能命中 */
:global(.meeting-picker-modal .ant-modal-body) { padding: 12px; }
.picker-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ph-name { display: flex; align-items: center; gap: 6px; min-width: 0; flex-wrap: wrap; }
.ph-links { display: flex; gap: 10px; flex: 0 0 auto; font-size: 12px; }
.picker-tip { font-size: 11px; color: var(--text-secondary, #8c8c8c); margin: 8px 0; }
.picker-list { max-height: 52vh; overflow-y: auto; border: 1px solid var(--border, #e5e6eb); border-radius: 6px; }
.picker-row {
  display: flex; align-items: center; gap: 8px; padding: 9px 10px;
  border-bottom: 1px solid var(--border-subtle, #f0f0f0); font-size: 13px;
}
.picker-row:last-child { border-bottom: 0; }
.picker-row.FREE { cursor: pointer; }
.pr-dot { width: 8px; height: 8px; border-radius: 50%; background: #bfbfbf; flex: 0 0 auto; }
.picker-row.FREE .pr-dot { background: #52c41a; }
.picker-row.MINE .pr-dot { background: #13c2c2; }
.picker-row.OTHERS .pr-dot { background: #4c8dff; }
.picker-row.chosen { background: rgba(82, 196, 26, 0.12); }
.picker-row.chosen .pr-time { color: #389e0d; font-weight: 600; }
.pr-time { width: 48px; flex: 0 0 auto; font-variant-numeric: tabular-nums; }
.pr-label {
  flex: 1 1 auto; min-width: 0; text-align: right; font-size: 12px;
  color: var(--text-secondary, #8c8c8c);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.picker-footer { display: flex; align-items: center; gap: 8px; margin-top: 12px; }
.pf-text { flex: 1 1 auto; min-width: 0; font-size: 12px; color: var(--text-secondary, #8c8c8c); }
.pf-text b { color: var(--primary, #2f54eb); }
.intro-image.intro-mobile { width: 100%; flex: 1 1 100%; min-height: 160px; }
</style>
