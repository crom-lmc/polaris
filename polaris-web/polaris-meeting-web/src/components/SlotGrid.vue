<template>
  <div class="slot-wrap" :class="{ split }">
    <!-- split：移动端 48 格放不下，拆成 00:00–12:00 / 12:00–24:00 两行，每行独立小时标尺 -->
    <div v-for="(row, ri) in gridRows" :key="ri" class="slot-row">
      <div v-if="hourScale || split" class="hour-scale" :style="scaleStyle(row)">
        <b v-for="h in row.length / 2" :key="h">{{ String(startHourOf(row) + h - 1).padStart(2, '0') }}</b>
      </div>
      <div class="slot-grid" :class="{ compact }" :style="gridStyle(row)">
        <div v-for="(cell, i) in row" :key="i"
             class="slot-cell" :class="[cell.state, { chosen: isChosen(cell), pickable: isPickable(cell) }]"
             :title="cell.state === 'FREE' || cell.state === 'MINE' || cell.state === 'OTHERS'
               ? slotTitle(cell) : stateLabel(cell)"
             @click="onClick(cell)">
          <span v-if="!compact && !hourScale && i % 2 === 0">{{ cell.start }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Slot } from '@/api/meeting'

type Range = { start: string; end: string } | undefined

const props = withDefaults(defineProps<{
  slots: Slot[]
  selectable?: boolean
  value?: Range
  /** 显示与外层时间轴一致的小时标尺（整点落在两格之间），用于新建预定弹窗 */
  hourScale?: boolean
  /** 修改预定时允许选择本人已占用的格子（当前预定的时段） */
  allowMine?: boolean
  /** 仅该预定单号对应的 MINE 格子可选，避免误选本人其他预定 */
  mineBookingId?: string
  /** 移动端分两行：12:00 前一行、12:00 后一行，避免 48 格横向放不下 */
  split?: boolean
}>(), { selectable: false, value: undefined, hourScale: false, allowMine: false, mineBookingId: undefined, split: false })

const emit = defineEmits<{ (e: 'pick', slot: Slot): void }>()
const compact = computed(() => !props.selectable)
/** 分行后每行仍保持「每两格一个整点」的对齐口径 */
const gridRows = computed<Slot[][]>(() => {
  if (!props.split || !props.slots.length) return [props.slots]
  const noon = props.slots.findIndex(s => s.start >= '12:00')
  if (noon <= 0) return [props.slots]
  return [props.slots.slice(0, noon), props.slots.slice(noon)]
})
function scaleStyle(row: Slot[]) { return { gridTemplateColumns: `repeat(${row.length / 2}, minmax(0, 1fr))` } }
function gridStyle(row: Slot[]) { return { gridTemplateColumns: `repeat(${row.length}, minmax(0, 1fr))` } }
function startHourOf(row: Slot[]) { return Number((row[0]?.start || '00:00').slice(0, 2)) || 0 }

const labels: Record<string, string> = {
  FREE: '空闲', MINE: '我的预约', OTHERS: '他人预约', PAST: '已过去',
  DISABLED: '房间不可用', UNSELECTABLE: '结束最晚 23:30'
}

function stateLabel(cell: Slot): string {
  return labels[cell.state] || cell.state
}

/** 悬停提示：时段 + 预约人预约的主题（如「王浩然预约的需求评审会议」） */
function slotTitle(cell: Slot): string {
  const base = `${cell.start}-${cell.end}`
  if (!cell.title) return base
  // 后端返回「姓名（工号）」，悬停只展示姓名，贴合「王浩然预约的需求评审会议」口径
  const who = (cell.creatorName || '未知预约人').replace(/（[^）]*）$/, '')
  return `${base} ${who}预约的${cell.title}`
}

function isChosen(cell: Slot): boolean {
  const v = props.value
  if (!v) return false
  return cell.start >= v.start && cell.start < v.end
}

function isPickable(cell: Slot): boolean {
  if (!props.selectable) return false
  if (cell.state === 'FREE') return true
  return props.allowMine && cell.state === 'MINE'
    && (!props.mineBookingId || cell.bookingId === props.mineBookingId)
}

function onClick(cell: Slot) {
  if (!isPickable(cell)) return
  emit('pick', cell)
}
</script>

<style scoped>
.slot-wrap { flex: 1 1 auto; min-width: 720px; }
.slot-wrap.split { min-width: 0; width: 100%; }
.slot-wrap.split .slot-row + .slot-row { margin-top: 8px; }
.slot-wrap.split .hour-scale, .slot-wrap.split .slot-grid { min-width: 0; }
.slot-wrap.split .slot-grid { gap: 1px; }
.slot-wrap.split .slot-cell { height: 28px; }
.slot-row > .slot-grid { width: 100%; }
.hour-scale {
  display: grid; grid-template-columns: repeat(24, 1fr);
  min-width: 720px; height: 18px; font-size: 10px;
  color: var(--text-secondary, #8c8c8c);
}
.hour-scale b { font-weight: 400; text-align: center; white-space: nowrap; border-left: 1px solid var(--border, #eee); }
.slot-grid {
  display: grid; grid-template-columns: repeat(48, minmax(14px, 1fr));
  gap: 1px; min-width: 720px; background: var(--border, #e5e6eb); padding: 1px;
}
.slot-grid.compact { min-width: 720px; height: 22px; }
.slot-cell {
  position: relative; height: 34px; cursor: default; overflow: visible;
  background: var(--panel-bg, #fff); font-size: 9px; color: var(--text-secondary, #8c8c8c);
  display: flex; align-items: flex-start; justify-content: flex-start;
}
.slot-cell.FREE { background: #e8f7ee; }
.slot-grid.selectable .slot-cell.FREE, .slot-grid.selectable .slot-cell.MINE.pickable { cursor: pointer; }
.slot-cell.MINE { background: var(--mine-color, #13c2c2); }
.slot-cell.OTHERS { background: var(--other-color, #4c8dff); }
.slot-cell.PAST {
  background: repeating-linear-gradient(45deg, var(--past-a, #f0f0f0) 0 3px, var(--past-b, #dedede) 3px 6px);
}
.slot-cell.DISABLED, .slot-cell.UNSELECTABLE { background: var(--disabled-bg, #bfbfbf); }
.slot-cell.chosen { background: #52c41a !important; box-shadow: inset 0 0 0 1px #389e0d; }
/* 悬停格提升层级，否则后面兄弟格子的背景会盖住提示右侧 */
.slot-cell:hover { z-index: 2; }
.slot-cell:hover::after {
  content: attr(title); position: absolute; bottom: calc(100% + 4px); left: 0; z-index: 5;
  white-space: nowrap; background: var(--header-bg, #fff); color: var(--header-text, #333);
  border: 1px solid var(--border, #ddd); border-radius: 4px; padding: 2px 6px;
  font-size: 11px; line-height: 16px; pointer-events: none;
}
</style>
