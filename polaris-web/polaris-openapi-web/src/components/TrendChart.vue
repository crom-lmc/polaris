<template>
  <div class="trend-chart" :style="{ height: height + 'px' }">
    <svg viewBox="0 0 560 180" preserveAspectRatio="none" role="img" aria-label="调用趋势折线图">
      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#2f54eb" stop-opacity="0.28" />
          <stop offset="100%" stop-color="#2f54eb" stop-opacity="0.02" />
        </linearGradient>
      </defs>
      <!-- 横向网格线与刻度（左边距加大，避免与 x 轴首标签重叠） -->
      <g v-for="i in 5" :key="'g' + i">
        <line x1="46" :y1="20 + (i - 1) * 34" x2="552" :y2="20 + (i - 1) * 34"
              stroke="var(--border-subtle, #eee)" stroke-width="1" />
        <text x="42" :y="24 + (i - 1) * 34" text-anchor="end" class="trend-axis-text">
          {{ trendMax - Math.round((i - 1) * trendMax / 4) }}
        </text>
      </g>
      <path :d="totalAreaPath" :fill="`url(#${gradientId})`" />
      <path :d="totalLinePath" fill="none" stroke="#2f54eb" stroke-width="2.5"
            stroke-linejoin="round" stroke-linecap="round" />
      <path :d="failedLinePath" fill="none" stroke="#fa541c" stroke-width="2"
            stroke-linejoin="round" stroke-linecap="round" stroke-dasharray="5 4" />
      <!-- x 轴日期（首点用 start 对齐，避免与 Y 轴 0 刻度重叠） -->
      <g v-for="(p, idx) in trendPoints" :key="'x' + idx">
        <text v-if="idx === 0 || idx % 2 === 0 || idx === trendPoints.length - 1" :x="p.x" y="174"
              :text-anchor="idx === 0 ? 'start' : (idx === trendPoints.length - 1 ? 'end' : 'middle')"
              class="trend-axis-text">{{ p.label }}</text>
      </g>
    </svg>
    <a-empty v-if="!hasData" class="trend-empty" description="暂无调用记录" />
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(defineProps<{
  rows: { day: string; total: number; failed: number }[]
  height?: number
}>(), { height: 210 })

// 卡片与弹窗可能同时渲染两个图表，渐变 id 必须唯一
const gradientId = `openapiTrendFill-${useId()}`

/* 折线图坐标：viewBox 560x180，绘图区 x∈[46,552]，y∈[20,156] */
const trendMax = computed(() => {
  const max = Math.max(1, ...props.rows.flatMap((p) => [p.total, p.failed]))
  return Math.ceil(max / 4) * 4 || 4
})
const hasData = computed(() => props.rows.some((p) => p.total > 0 || p.failed > 0))
const trendPoints = computed(() => {
  const rows = props.rows
  const n = rows.length
  const max = trendMax.value
  return rows.map((p, i) => ({
    x: n <= 1 ? 46 : 46 + i * (506 / (n - 1)),
    ty: 156 - (p.total / max) * 136,
    fy: 156 - (p.failed / max) * 136,
    label: (p.day || '').slice(5)
  }))
})
const totalLinePath = computed(() =>
  trendPoints.value
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.ty.toFixed(1)}`)
    .join(' ')
)
const failedLinePath = computed(() =>
  trendPoints.value
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.fy.toFixed(1)}`)
    .join(' ')
)
const totalAreaPath = computed(() => {
  const pts = trendPoints.value
  if (!pts.length) return ''
  const top = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.ty.toFixed(1)}`).join(' ')
  return `${top} L${pts[pts.length - 1].x.toFixed(1)},156 L${pts[0].x.toFixed(1)},156 Z`
})
</script>

<style scoped>
.trend-chart {
  position: relative;
  width: 100%;
}
.trend-chart svg {
  width: 100%;
  height: 100%;
  display: block;
}
.trend-axis-text {
  fill: var(--muted, #8c8c8c);
  font-size: 11px;
}
.trend-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
