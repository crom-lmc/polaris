<template>
  <div class="ft-page">
    <a-alert
      class="overview-alert"
      type="info"
      show-icon
      style="margin-bottom: 14px"
      message="平台统一审计：网关对所有登录后动作（含 GET）全量兜底，业务层补语义，登录事件合并，内部调用记为 SYSTEM。"
    />

    <div class="stats">
      <div class="stat"><div class="label"><span class="dot" style="background:#7c5cff"></span>今日事件</div><div class="num">{{ fmt(data.today) }}</div><div class="sub">含 GET 查询 · 全部登录动作</div></div>
      <div class="stat"><div class="label"><span class="dot" style="background:#22c55e"></span>近 7 天</div><div class="num">{{ fmt(data.week) }}</div><div class="sub">覆盖 7 天 · 网关全量</div></div>
      <div class="stat"><div class="label"><span class="dot" style="background:#f59e0b"></span>累计记录</div><div class="num">{{ fmt(data.total) }}</div><div class="sub">保留期 180 天 · 按天清理</div></div>
      <div class="stat"><div class="label"><span class="dot" style="background:#ef4444"></span>登录失败</div><div class="num">{{ fmt(data.loginFail) }}</div><div class="sub">含撞库尝试 · 记尝试用户名</div></div>
    </div>

    <div class="grid">
      <section class="card">
        <div class="card-title">近 7 天事件趋势</div>
        <div v-if="trendRows.length" class="bars">
          <div v-for="row in trendRows" :key="row.day" class="bar-row">
            <span class="bar-label">{{ row.day.slice(5) }}</span>
            <div class="bar-track"><div class="bar-fill" :style="{ width: barWidth(row.cnt) }"></div></div>
            <span class="bar-val">{{ fmt(row.cnt) }}</span>
          </div>
        </div>
        <a-empty v-else description="暂无趋势数据" />
      </section>

      <section class="card">
        <div class="card-title">按应用分布（近 30 天）</div>
        <div v-if="data.byApp?.length" class="bars">
          <div v-for="row in data.byApp" :key="row.app" class="bar-row">
            <span class="bar-label">{{ appLabel(row.app) }}</span>
            <div class="bar-track"><div class="bar-fill alt" :style="{ width: ratioWidth(row.cnt, maxApp) }"></div></div>
            <span class="bar-val">{{ fmt(row.cnt) }}</span>
          </div>
        </div>
        <a-empty v-else description="暂无分布数据" />
      </section>

      <section class="card">
        <div class="card-title">按事件类别（近 30 天）</div>
        <div v-if="data.byCategory?.length" class="bars">
          <div v-for="row in data.byCategory" :key="row.category" class="bar-row">
            <span class="bar-label">{{ categoryLabel(row.category) }}</span>
            <div class="bar-track"><div class="bar-fill cat" :style="{ width: ratioWidth(row.cnt, maxCat) }"></div></div>
            <span class="bar-val">{{ fmt(row.cnt) }}</span>
          </div>
        </div>
        <a-empty v-else description="暂无类别数据" />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { fetchAuditOverview, type AuditOverview } from '@/api/audit'
import { appLabel, ensureAppNames } from '@/utils/appLabels'

const data = reactive<AuditOverview>({
  today: 0, week: 0, total: 0, loginFail: 0, trend: [], byApp: [], byCategory: []
})

const trendRows = computed(() => data.trend || [])
const maxTrend = computed(() => Math.max(1, ...data.trend.map(r => Number(r.cnt) || 0)))
const maxApp = computed(() => Math.max(1, ...(data.byApp || []).map(r => Number(r.cnt) || 0)))
const maxCat = computed(() => Math.max(1, ...(data.byCategory || []).map(r => Number(r.cnt) || 0)))

onMounted(async () => {
  void ensureAppNames()
  try {
    const res = await fetchAuditOverview()
    Object.assign(data, res.data)
  } catch (e: any) {
    message.error(e?.message || '加载概览失败')
  }
})

function fmt(n: number | string | undefined) { return Number(n || 0).toLocaleString() }
function barWidth(cnt: number) { return `${Math.max(2, (Number(cnt) / maxTrend.value) * 100)}%` }
function ratioWidth(cnt: number, max: number) { return `${Math.max(2, (Number(cnt) / max) * 100)}%` }
function categoryLabel(c: string) { return { ACCESS: '访问', LOGIN: '登录', BUSINESS: '业务', SENSITIVE: '敏感操作' }[c] || c }
</script>

<style scoped>
/* 整页已是固定高度 flex 列：alert/stats 固定，图表区吃掉剩余高度并三等分铺满 */
.overview-alert { flex: 0 0 auto; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); grid-auto-rows: 1fr; gap: 14px; margin-bottom: 16px; flex: 0 0 auto; }
.stat { background: var(--bg-page); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 16px 18px; }
.stat .label { font-size: 12px; color: var(--muted); display: flex; align-items: center; gap: 7px; }
.stat .num { font-size: 26px; font-weight: 600; margin-top: 8px; }
.stat .sub { font-size: 11px; color: var(--muted); margin-top: 4px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.grid { flex: 1 1 0; min-height: 0; display: grid; grid-template-columns: 1fr; grid-template-rows: repeat(3, minmax(0, 1fr)); gap: 14px; }
.card { background: var(--bg-page); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 16px 18px; display: flex; flex-direction: column; min-height: 0; }
.card-title { font-weight: 600; margin-bottom: 14px; flex: 0 0 auto; }
.bars { flex: 1 1 0; min-height: 0; display: flex; flex-direction: column; justify-content: space-evenly; gap: 6px; }
.bar-row { display: flex; align-items: center; gap: 10px; font-size: 12px; }
.bar-label { width: 160px; flex-shrink: 0; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bar-track { flex: 1; height: 12px; background: var(--border-subtle); border-radius: 4px; overflow: hidden; }
.bar-fill { height: 100%; background: linear-gradient(90deg, #7c5cff, #22d3ee); border-radius: 4px; }
.bar-fill.alt { background: linear-gradient(90deg, #22c55e, #22d3ee); }
.bar-fill.cat { background: linear-gradient(90deg, #f59e0b, #ef4444); }
.bar-val { width: 56px; text-align: right; flex-shrink: 0; font-variant-numeric: tabular-nums; }
</style>
