<template>
  <section class="ft-page">
    <section class="ft-head">
      <div class="head-title">会议室使用率</div>
      <a-space>
        <span class="filter-label">地区</span>
        <a-select v-model:value="city" :options="cityOptions" placeholder="全部地区"
                  style="width: 130px" allow-clear @change="load" />
        <span class="filter-label">统计日期</span>
        <a-date-picker v-model:value="date" format="YYYY-MM-DD" :allow-clear="false" @change="load" />
        <a-button @click="load">刷新</a-button>
      </a-space>
    </section>
    <section class="ft-scroll usage-scroll">
      <a-card size="small" class="usage-card" :extra="cardExtra">
        <div v-if="loading" class="chart-state"><a-spin tip="加载中..." /></div>
        <a-empty v-else-if="!items.length" description="所选地区暂无会议室" class="chart-state" />
        <div v-else class="chart">
          <div class="axis-y">
            <span v-for="tick in ticks" :key="tick">{{ tick }}%</span>
          </div>
          <div class="plot">
            <div class="grid">
              <div v-for="tick in ticks" :key="tick" class="grid-line" />
              <div v-if="maxRate > 100" class="grid-line hundred" :style="{ top: hundredTop }" />
            </div>
            <div class="bars">
              <a-tooltip v-for="(item, index) in items" :key="item.roomId" :title="tooltip(item)">
                <div class="bar-col" :class="{ stagger: index % 2 === 1 }">
                  <div class="bar-area">
                    <div class="bar" :class="{ over: item.rate > 100 }" :style="{ height: barHeight(item) }">
                      <span class="bar-value">{{ item.rate }}%</span>
                    </div>
                  </div>
                  <div class="axis-x">{{ labelOf(item) }}</div>
                </div>
              </a-tooltip>
            </div>
          </div>
        </div>
      </a-card>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import { getDicts, getUsageStats, type DictItem, type UsageRoom, type UsageStats } from '@/api/meeting'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const city = ref<string | undefined>(store.city || undefined)
const cityOptions = ref<Array<{ label: string; value: string }>>([])
const date = ref<Dayjs>(dayjs())
const loading = ref(false)
const stats = ref<UsageStats | null>(null)
const items = computed(() => stats.value?.items || [])

const maxRate = computed(() => {
  const peak = items.value.reduce((max, item) => Math.max(max, item.rate), 0)
  return Math.max(100, Math.ceil(peak / 50) * 50)
})
const ticks = computed(() => {
  const max = maxRate.value
  return [max, Math.round(max * 75) / 100, max / 2, max / 4, 0]
})
const hundredTop = computed(() => `${100 - 100 / maxRate.value * 100}%`)
const cardExtra = computed(() => {
  const s = stats.value
  if (!s) return '仅统计 9:00–18:00'
  return `工作时段 ${s.workStart}–${s.workEnd} · 每间 ${s.workMinutesPerRoom} 分钟 · ${s.totalRooms} 间会议室`
})

function labelOf(item: UsageRoom) {
  return stats.value?.city ? item.roomName : `${item.city}·${item.roomName}`
}
function barHeight(item: UsageRoom) {
  const pct = maxRate.value > 0 ? item.rate / maxRate.value * 100 : 0
  return `${Math.max(pct, 0.8)}%`
}
function tooltip(item: UsageRoom) {
  const total = stats.value?.workMinutesPerRoom || 0
  return `${labelOf(item)}｜使用率 ${item.rate}%｜占用 ${item.usedMinutes}/${total} 分钟｜${item.bookings} 场预约`
}
async function load() {
  loading.value = true
  try {
    const day = date.value.format('YYYY-MM-DD')
    stats.value = await getUsageStats({
      from: day,
      to: day,
      city: city.value || undefined
    })
  } catch (e: any) {
    message.error(e?.message || '加载会议室使用率失败')
    stats.value = null
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  try {
    const dicts: Record<string, DictItem[]> = await getDicts()
    cityOptions.value = (dicts['ehr.job_city'] || []).map(d => ({ label: d.label, value: d.value }))
    if (city.value && !cityOptions.value.some(o => o.value === city.value)) {
      cityOptions.value.unshift({ label: city.value, value: city.value })
    }
  } catch { /* 字典失败不影响统计 */ }
  await load()
})
</script>

<style scoped>
.head-title { font-weight: 600; }
.filter-label { font-size: 12px; color: var(--muted, #8a9099); }
.usage-scroll { display: flex; }
.usage-card { flex: 1; min-width: 0; min-height: 0; display: flex; flex-direction: column; }
.usage-card :deep(.ant-card-body) { flex: 1; min-height: 0; display: flex; flex-direction: column; padding: 12px 16px 8px; }
.chart-state { flex: 1; display: flex; align-items: center; justify-content: center; }
.chart { flex: 1; min-height: 0; display: flex; gap: 8px; padding-top: 8px; }
.axis-y {
  flex: 0 0 48px; height: calc(100% - 78px); display: flex; flex-direction: column;
  justify-content: space-between; align-items: flex-end; font-size: 11px;
  color: var(--muted, #8a9099); padding-right: 2px;
}
.plot { flex: 1; min-width: 0; position: relative; display: flex; }
.grid { position: absolute; left: 0; right: 0; top: 0; bottom: 78px; pointer-events: none; }
.grid-line { position: relative; border-top: 1px dashed var(--border-subtle, rgba(0,0,0,0.08)); height: 0; }
.grid-line.hundred { position: absolute; left: 0; right: 0; border-top: 1px dashed #faad14; }
.bars {
  position: relative; z-index: 1; flex: 1; min-width: 0; display: flex; align-items: stretch;
  gap: 4px; overflow-x: auto; overflow-y: hidden; padding: 2px 16px 0;
}
.bar-col { flex: 0 0 84px; min-width: 84px; height: 100%; display: flex; flex-direction: column; align-items: center; }
.bar-area { flex: 1; min-height: 0; width: 100%; display: flex; align-items: flex-end; justify-content: center; }
.bar {
  width: 60%; max-width: 38px; min-height: 2px; border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, var(--primary, #2f54eb), rgba(47, 84, 235, 0.45));
  position: relative; transition: height 0.25s ease;
}
.bar.over { background: linear-gradient(180deg, #fa541c, rgba(250, 84, 28, 0.45)); }
.bar-value {
  position: absolute; left: 50%; top: -17px; transform: translateX(-50%);
  font-size: 10px; white-space: nowrap; color: var(--text-base, #1f2329);
}
/* 横轴名称横排不省略，奇偶两行错位，保证完整露出 */
.axis-x {
  flex: 0 0 62px; width: 100%; padding-top: 4px; text-align: center;
  display: flex; justify-content: center; align-items: flex-start;
  font-size: 10.5px; line-height: 14px; color: var(--muted, #8a9099);
  white-space: nowrap; overflow: visible;
}
.bar-col.stagger .axis-x { padding-top: 22px; }
</style>
