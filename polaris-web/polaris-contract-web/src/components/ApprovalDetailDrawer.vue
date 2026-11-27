<script setup lang="ts">
/**
 * 审批单详情抽屉（只读）——与审批中心「我发起的 / 待我审批」点开的详情是同一张页面。
 * 数据走统一账号中心 /api/accounts/approvals/{id} + /context，渲染口径与 sso-web 的
 * ApprovalDetailDrawer 完全一致：表单快照 / 流程进度 / 每个步骤的审批人。
 * 合同侧只查看、不审批：不渲染通过/驳回/转办/加签/撤回操作区。
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { fetchApprovalContext, fetchApprovalDetail, type ApprovalContext, type ApprovalInstance, type ApprovalPageField, type ApprovalPageSection, type ApprovalTask } from '@/api/approvals'

const props = withDefaults(
  defineProps<{ open: boolean; instanceId: string | null; width?: number | string }>(),
  { width: 860 }
)
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()

const loading = ref(false)
const detail = ref<ApprovalInstance | null>(null)
const ctx = ref<ApprovalContext | null>(null)

const values = computed<Record<string, unknown>>(() => ctx.value?.values || detail.value?.formData || {})
const tableModes = computed<Record<string, string>>(() => ctx.value?.tableModes || {})

const title = computed(() => {
  const tpl = ctx.value?.page?.titleTpl
  const inst = detail.value
  if (!inst) return '审批详情'
  if (tpl) {
    return tpl
      .replace('{bizName}', inst.bizName || '')
      .replace('{applicantName}', inst.applicantName || '')
      .replace('{bizNo}', inst.bizNo || '')
      .replace('{title}', inst.title || '')
  }
  return inst.title || inst.bizName || '审批详情'
})

/** 未配置版式时退回默认：把表单快照整体铺开，字段名做兜底翻译 */
const DEFAULT_LABELS: Record<string, string> = {
  orgPath: '所属组织', submitter: '提交人', applicantName: '发起人', bizNo: '审批单号',
  title: '标题', amount: '金额', contractNo: '合同编号', contractName: '合同名称',
  dept: '部门', partnerCode: '往来单位编码', payment: '付款阶段', stage: '付款阶段'
}
const sections = computed<ApprovalPageSection[]>(() => {
  const configured = ctx.value?.page?.sections
  if (configured && configured.length) return configured
  const keys = Object.keys(values.value).filter(
    (k) => !['employeeId', 'empId', 'orgId', 'exemptProbation', 'probationMonths', 'newLeaderEmployeeId'].includes(k)
  )
  if (!keys.length) return []
  return [{
    key: 'default', title: '', columns: 3,
    fields: keys.map<ApprovalPageField>((k) => ({ key: k, label: DEFAULT_LABELS[k] || k, format: 'text' }))
  }]
})

const applicantLabel = computed(() => {
  const fromSnapshot = values.value.submitter
  return detail.value?.applicantName ||
    (typeof fromSnapshot === 'string' ? fromSnapshot : '') || '—'
})

interface FlowStep { key: string; name: string; state: string; description: string }
/** 流程条 = 发起申请（发起人提交）+ 后端返回的审批步骤 */
const flowSteps = computed<FlowStep[]>(() => {
  const start: FlowStep = { key: '__applicant__', name: '发起申请', state: 'DONE', description: '' }
  const rest = (detail.value?.steps || []).map((s) => ({
    key: String(s.seq), name: s.name, state: s.state,
    description: s.state === 'SKIPPED' ? '条件不满足，已跳过' : ''
  }))
  return [start, ...rest]
})
const currentStepIndex = computed(() => {
  const list = flowSteps.value
  const idx = list.findIndex((s) => s.state === 'CURRENT')
  if (idx >= 0) return idx
  return detail.value?.status === 1 ? list.length : 1
})

/** 按步骤把任务分组，同一分支多人放一起 */
const taskGroups = computed(() => {
  const tasks = detail.value?.tasks || []
  const map = new Map<number, { seq: number; name: string; mode?: string; tasks: ApprovalTask[] }>()
  for (const t of tasks) {
    const g = map.get(t.stepSeq) || { seq: t.stepSeq, name: t.stepName, mode: t.approveMode, tasks: [] }
    g.tasks.push(t)
    if (t.approveMode) g.mode = t.approveMode
    map.set(t.stepSeq, g)
  }
  return [...map.values()].sort((a, b) => a.seq - b.seq)
})

function isEmpty(v: unknown) { return v === null || v === undefined || v === '' }
function cellText(v: unknown): string {
  if (isEmpty(v)) return '—'
  if (typeof v === 'object') {
    try { return JSON.stringify(v) } catch { return String(v) }
  }
  return String(v)
}
function cellOf(column: { key?: unknown; dataIndex?: unknown }, record: Record<string, unknown>) {
  const k = String(column.dataIndex ?? column.key ?? '')
  const v = record[k]
  return { text: cellText(v), empty: isEmpty(v) }
}
function descFields(sec: ApprovalPageSection) { return (sec.fields || []).filter((f) => f.format !== 'table') }
function tableFields(sec: ApprovalPageSection) { return (sec.fields || []).filter((f) => f.format === 'table') }
function asRows(v: unknown): Record<string, unknown>[] { return Array.isArray(v) ? (v as Record<string, unknown>[]) : [] }
function subColumns(rows: Record<string, unknown>[]) {
  const keys = new Set<string>()
  for (const r of rows) for (const k of Object.keys(r)) keys.add(k)
  return [...keys].map((k) => ({ title: k, dataIndex: k, key: k }))
}
function tableColumnsOf(f: ApprovalPageField, rows: Record<string, unknown>[]) {
  const cfg = f.tableColumns || []
  if (cfg.length) return cfg.map((c) => ({ title: c.label || c.key, dataIndex: c.key, key: c.key }))
  return subColumns(rows)
}
/** MAIN_ROW / BIZ_ROW 用服务端铺平的「表名.列名」键，不降级取首行；仅老单无 tableModes 时兼容 */
function valueOf(f: { key: string }): unknown {
  const direct = values.value[f.key]
  if (!isEmpty(direct)) return direct
  const dot = f.key.lastIndexOf('.')
  if (dot < 0) return direct
  const tableName = f.key.slice(0, dot)
  const column = f.key.slice(dot + 1)
  if (tableModes.value[tableName] === 'MAIN_ROW' || tableModes.value[tableName] === 'BIZ_ROW') return direct
  const rows = asRows(values.value[tableName])
  return rows.length ? rows[0][column] : direct
}
function display(f: ApprovalPageField | { key: string; label: string; format?: string }) {
  const v = valueOf(f)
  if (isEmpty(v)) return '—'
  if (f.format === 'money') {
    const n = Number(v)
    return Number.isFinite(n) ? n.toFixed(2) : String(v)
  }
  if (typeof v === 'boolean') return v ? '是' : '否'
  if (Array.isArray(v)) return v.join('、')
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function statusText(s?: number) {
  return ({ 0: '进行中', 1: '已通过', 2: '已驳回', 3: '已撤回', 4: '已终止' } as Record<number, string>)[s ?? -1] || '未知'
}
function statusColor(s?: number) {
  return ({ 0: 'processing', 1: 'green', 2: 'red', 3: 'default', 4: 'default' } as Record<number, string>)[s ?? -1] || 'default'
}
function taskText(s?: number) {
  return ({ 0: '待处理', 1: '已通过', 2: '已驳回', 3: '已跳过', 4: '已取消', 9: '已转办' } as Record<number, string>)[s ?? -1] || '未知'
}
function taskColor(s?: number) {
  return ({ 0: 'processing', 1: 'green', 2: 'red', 3: 'default', 4: 'default', 9: 'blue' } as Record<number, string>)[s ?? -1] || 'default'
}
function stepStatus(state: string) {
  return ({ DONE: 'finish', CURRENT: 'process', TODO: 'wait', SKIPPED: 'wait' } as Record<string, 'finish' | 'process' | 'wait'>)[state] || 'wait'
}
function modeText(mode?: string) {
  return ({ ANY: '或签（一人通过即可）', ALL: '会签（全部通过）', SEQ: '依次审批' } as Record<string, string>)[mode || ''] || ''
}
function fmtTime(v?: string | null): string {
  if (!v) return ''
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(v)) return v.replace('T', ' ').slice(0, 19)
  return v
}

async function load() {
  if (!props.instanceId) return
  loading.value = true
  try {
    const [d, c] = await Promise.all([
      fetchApprovalDetail(props.instanceId),
      fetchApprovalContext(props.instanceId).catch(() => null)
    ])
    detail.value = d || null
    ctx.value = c || null
  } catch (e: any) {
    detail.value = null
    ctx.value = null
    message.error(e?.message || '审批详情加载失败')
  } finally {
    loading.value = false
  }
}

watch(() => [props.open, props.instanceId], ([o, id]) => { if (o && id) load() })
</script>

<template>
  <a-drawer
    :open="open"
    :width="width"
    :body-style="{ padding: '0' }"
    :mask-closable="true"
    @close="emit('update:open', false)"
  >
    <template #title>
      <div class="dt-title">
        <span class="tt">{{ title }}</span>
        <a-tag color="default">只读查看</a-tag>
        <a-tag v-if="detail" :color="statusColor(detail.status)">{{ detail.statusName || statusText(detail.status) }}</a-tag>
      </div>
    </template>

    <a-spin :spinning="loading">
      <div v-if="detail" class="dt-body">
        <section v-for="sec in sections" :key="sec.key" class="card form-card" :class="{ emphasis: sec.emphasis }">
          <div v-if="sec.title" class="card-title">{{ sec.title }}</div>
          <a-descriptions v-if="descFields(sec).length" :column="sec.columns || 3" size="small" :colon="false" bordered class="desc">
            <a-descriptions-item v-for="f in descFields(sec)" :key="f.key" :label="f.label" :span="f.span || 1">
              <span :class="{ muted: isEmpty(valueOf(f)) }">{{ display(f) }}</span>
            </a-descriptions-item>
          </a-descriptions>
          <template v-for="f in tableFields(sec)" :key="f.key">
            <div class="sub-table-title">{{ f.label }}</div>
            <a-table size="small" bordered table-layout="fixed" row-key="__rk" :pagination="false"
                     :locale="{ emptyText: '暂无数据' }"
                     :columns="tableColumnsOf(f, asRows(values[f.key]))"
                     :data-source="asRows(values[f.key]).map((r, i) => ({ ...r, __rk: i }))" class="sub-table">
              <template #bodyCell="{ column, record }">
                <span class="cell-val" :class="{ 'cell-empty': cellOf(column, record).empty }" :title="cellOf(column, record).text">
                  {{ cellOf(column, record).text }}
                </span>
              </template>
            </a-table>
          </template>
        </section>

        <section class="card">
          <div class="card-title">流程进度</div>
          <a-steps :current="currentStepIndex" size="small" label-placement="vertical" class="steps">
            <a-step v-for="s in flowSteps" :key="s.key" :title="s.name" :status="stepStatus(s.state)" :description="s.description" />
          </a-steps>
          <div class="applicant-row">
            <a-avatar :size="26" class="av">{{ applicantLabel.slice(0, 1) }}</a-avatar>
            <div class="who-main">
              <div class="name">{{ applicantLabel }}<a-tag color="green" class="mini-tag">已提交</a-tag></div>
              <div class="meta muted">{{ fmtTime(detail.createdAt) || '—' }}</div>
            </div>
            <span class="applicant-role">发起人</span>
          </div>
        </section>

        <section v-for="grp in taskGroups" :key="grp.seq" class="card">
          <div class="card-title">
            <span>{{ grp.seq }}. {{ grp.name }}</span>
            <a-tag v-if="grp.mode" class="mode-tag">{{ modeText(grp.mode) }}</a-tag>
          </div>
          <div class="task-list">
            <div v-for="t in grp.tasks" :key="t.id" class="task">
              <div class="who">
                <a-avatar :size="26" class="av">{{ (t.approverName || '?').slice(0, 1) }}</a-avatar>
                <div class="who-main">
                  <div class="name">
                    {{ t.approverName }}
                    <a-tag v-if="t.approverSource === 'DELEGATE'" class="mini-tag" color="purple">代 {{ t.delegateOwnerName }}</a-tag>
                  </div>
                  <div class="meta">
                    <a-tag :color="taskColor(t.status)" class="mini-tag">{{ t.statusName || taskText(t.status) }}</a-tag>
                    <span v-if="t.actedAt" class="muted">{{ fmtTime(t.actedAt) }}</span>
                  </div>
                </div>
              </div>
              <div v-if="t.opinion" class="opinion">{{ t.opinion }}</div>
            </div>
          </div>
        </section>
      </div>
      <a-empty v-else-if="!loading" description="审批单不存在或无权查看" />
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.dt-title { display: flex; align-items: center; gap: 10px; padding-right: 24px; }
.dt-title .tt { font-size: 15px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dt-body { padding: 16px; }
.card { background: var(--panel-bg); border: 1px solid var(--border-subtle); border-radius: 10px; padding: 14px 16px; margin-bottom: 12px; }
.card.emphasis { background: linear-gradient(180deg, rgba(99, 102, 241, 0.06), transparent); border-color: rgba(99, 102, 241, 0.25); }
.card-title { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; margin-bottom: 10px; color: var(--text-base); }
.mode-tag { font-size: 11px; line-height: 18px; margin: 0; background: rgba(99, 102, 241, 0.12); border-color: transparent; color: #6366f1; }
.desc :deep(.ant-descriptions-item-label) { width: 96px; color: var(--muted); font-size: 13px; }
.desc :deep(.ant-descriptions-item-content) { color: var(--text-base); font-size: 13px; }
.form-card { background: transparent; border: none; border-radius: 0; padding: 0; margin-bottom: 16px; }
.form-card.emphasis { background: transparent; border: none; }
.form-card > .card-title { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.steps { padding-top: 4px; }
.applicant-row { display: flex; align-items: center; gap: 10px; margin-top: 14px; padding: 10px 12px; border-radius: 8px; background: var(--bg-page); }
.applicant-row .who-main { flex: 1; min-width: 0; }
.applicant-row .name { font-size: 13px; display: flex; align-items: center; gap: 6px; }
.applicant-row .meta { margin-top: 2px; font-size: 12px; }
.applicant-role { font-size: 12px; color: var(--muted); }
.task-list { display: flex; flex-direction: column; gap: 10px; }
.task { padding: 10px 12px; border-radius: 8px; background: var(--bg-page); }
.who { display: flex; align-items: center; gap: 10px; }
.av { background: rgba(99, 102, 241, 0.16); color: #6366f1; font-size: 12px; flex: none; }
.who-main { min-width: 0; }
.who-main .name { font-size: 13px; display: flex; align-items: center; gap: 6px; }
.who-main .meta { display: flex; align-items: center; gap: 8px; margin-top: 2px; font-size: 12px; }
.mini-tag { font-size: 11px; line-height: 17px; margin: 0; }
.opinion { margin-top: 8px; margin-left: 36px; font-size: 12.5px; color: var(--text-base); opacity: 0.86; white-space: pre-wrap; word-break: break-word; }
.muted { color: var(--muted); }
.sub-table-title { font-size: 13px; font-weight: 600; color: var(--text-base); margin: 14px 0 8px; }
.sub-table { margin-bottom: 10px; }
.sub-table :deep(.ant-table) { font-size: 13px; }
.sub-table :deep(.ant-table-container) { border-radius: 8px; overflow: hidden; }
.sub-table :deep(.ant-table table) { table-layout: fixed; }
.sub-table :deep(.ant-table-thead > tr > th) { background: var(--bg-page); color: var(--text-base); font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sub-table :deep(.ant-table-tbody > tr > td) { font-size: 13px; }
.sub-table .cell-val { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sub-table .cell-empty { color: var(--muted); }
</style>
