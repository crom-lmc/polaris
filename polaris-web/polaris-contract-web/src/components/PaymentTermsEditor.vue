<script setup lang="ts">
/**
 * 合同付款节点配置（内嵌在合同编辑抽屉，仅草稿可编辑）。
 * 比例合计必须 100%、必须含验收款；金额由合同清单合计 × 比例在服务端锁定。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  listPaymentTerms, savePaymentTerms, listItems, PAYMENT_STAGES, paymentStageLabel,
  type PaymentTermVO, type PaymentTermSave
} from '@/api/contract'
import { yuan } from '@/utils/format'

const props = defineProps<{ contractNo: string; contractStatus?: string; readonly?: boolean; itemsVersion?: number }>()
const emit = defineEmits<{ saved: [terms: PaymentTermVO[]] }>()

const rows = ref<PaymentTermVO[]>([])
const loading = ref(false)
const saving = ref(false)
const editable = computed(() => !props.readonly && props.contractStatus === 'DRAFT')

const columns = [
  { title: '付款阶段', dataIndex: 'stage', key: 'stage', width: 130 },
  { title: '比例', dataIndex: 'ratio', key: 'ratio', width: 110, align: 'right' as const },
  { title: '锁定金额', key: 'amount', width: 140, align: 'right' as const },
  { title: '计划付款日', dataIndex: 'dueDate', key: 'dueDate', width: 150 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  ...(editable.value ? [{ title: '操作', key: 'action', width: 80 }] : [])
]
const totalRatio = computed(() => Number(rows.value.reduce((s, r) => s + Number(r.ratio || 0), 0).toFixed(2)))
const hasAccept = computed(() => rows.value.some((r) => r.stage === 'ACCEPT'))
const usedStages = computed(() => new Set(rows.value.map((r) => r.stage)))
/** 合同清单合计：与服务端 contractAmount 同口径（清单行 amount 求和），用于编辑态实时预览锁定金额 */
const itemsTotal = ref(0)
/** 编辑态锁定金额实时预览 = 清单合计 × 比例（四舍五入两位），与服务端锁定口径一致；只读/未保存显示后端真实值 */
function previewAmount(row: PaymentTermVO): number {
  if (!editable.value) return Number(row.amount || 0)
  return Math.round(itemsTotal.value * Number(row.ratio || 0)) / 100
}

async function load() {
  loading.value = true
  try {
    const [terms, items] = await Promise.all([listPaymentTerms(props.contractNo), listItems(props.contractNo)])
    rows.value = terms || []
    itemsTotal.value = (items || []).reduce((s, i) => s + Number(i.amount || 0), 0)
  }
  catch (e: any) { message.error(e?.message || '付款节点加载失败') }
  finally { loading.value = false }
}
onMounted(() => { if (props.contractNo) load() })
watch(() => props.contractNo, (v) => { if (v) load() })
/** 清单增删后只重算合计，不覆盖用户正在编辑的比例/阶段 */
watch(() => props.itemsVersion, () => {
  if (props.contractNo) {
    listItems(props.contractNo).then((items) => {
      itemsTotal.value = (items || []).reduce((s, i) => s + Number(i.amount || 0), 0)
    }).catch(() => {})
  }
})

function addRow() {
  const next = PAYMENT_STAGES.find((s) => !usedStages.value.has(s.value))
  if (!next) { message.warning('四个付款阶段均已配置'); return }
  rows.value.push({
    id: '', contractNo: props.contractNo, stage: next.value, ratio: 0, amount: 0,
    dueDate: undefined, remark: ''
  })
}
function removeRow(index: number) { rows.value.splice(index, 1) }

/** 保存节点；silent=true 供提交审批自动调用，不重复弹成功提示。返回是否就绪（true=已保存/无需保存） */
async function save(silent = false): Promise<boolean> {
  if (!editable.value || props.readonly) return true
  if (!rows.value.length) { message.warning('请至少配置一个付款节点'); return false }
  if (totalRatio.value !== 100) { message.warning(`付款节点比例合计必须为 100%，当前 ${totalRatio.value}%`); return false }
  if (!hasAccept.value) { message.warning('付款节点必须包含验收款'); return false }
  if (rows.value.some((r) => !r.ratio || Number(r.ratio) <= 0)) { message.warning('每个付款节点比例必须大于 0'); return false }
  saving.value = true
  try {
    const payload: PaymentTermSave[] = rows.value.map((r) => ({
      id: r.id || undefined, stage: r.stage, ratio: Number(r.ratio),
      dueDate: r.dueDate || undefined, remark: r.remark || undefined
    }))
    const saved = await savePaymentTerms(props.contractNo, payload)
    rows.value = saved
    if (!silent) message.success('付款节点已保存，金额按当前清单合计锁定')
    emit('saved', saved)
    return true
  } catch (e: any) { message.error(e?.message || '付款节点保存失败'); return false }
  finally { saving.value = false }
}

/** 供父组件提交审批前调用：自动保存编辑态节点，未保存/校验失败返回 false 阻止提交 */
defineExpose({ save: () => save(true) })
</script>

<template>
  <div class="terms-editor">
    <div class="terms-toolbar">
      <span class="terms-title">付款节点</span>
      <span class="terms-sum" :class="{ invalid: !hasAccept || totalRatio !== 100 }">
        合计 {{ totalRatio }}%<template v-if="!hasAccept"> · 缺验收款</template>
      </span>
      <span class="grow" />
      <a-button v-if="editable" size="small" @click="addRow">＋ 添加节点</a-button>
      <a-button v-if="editable" size="small" type="primary" :loading="saving" @click="save()">保存节点</a-button>
    </div>
    <a-table class="ft-table" row-key="stage" size="small" :columns="columns" :data-source="rows"
             :loading="loading" :pagination="false" :scroll="rows.length ? { x: editable ? 720 : 640 } : undefined">
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'stage'">
          <a-select v-if="editable" v-model:value="record.stage" :options="PAYMENT_STAGES" style="width: 100%" />
          <span v-else>{{ paymentStageLabel(record.stage) }}</span>
        </template>
        <template v-else-if="column.key === 'ratio'">
          <a-input-number v-if="editable" v-model:value="record.ratio" :min="0" :max="100" :precision="2"
                          :controls="false" style="width: 100%" addon-after="%" />
          <span v-else>{{ record.ratio }}%</span>
        </template>
        <template v-else-if="column.key === 'amount'">
          <span>{{ yuan(previewAmount(record)) }}</span>
          <a-tooltip v-if="editable" title="按当前清单合计 × 比例预览，保存节点后锁定">
            <span class="terms-preview-tag">预览</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'dueDate'">
          <a-date-picker v-if="editable" v-model:value="record.dueDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
          <span v-else>{{ record.dueDate || '-' }}</span>
        </template>
        <template v-else-if="column.key === 'remark'">
          <a-input v-if="editable" v-model:value="record.remark" placeholder="选填" />
          <a-tooltip v-else-if="record.remark" :title="record.remark"><span class="cell-ellipsis">{{ record.remark }}</span></a-tooltip>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a @click="removeRow(index)">删除</a>
        </template>
      </template>
      <template #emptyText>
        <a-empty v-if="!loading" :description="editable ? '尚未配置付款节点，请添加' : '未配置付款节点（存量合同）'" />
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.terms-editor { margin-bottom: 16px; }
.terms-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.terms-title { font-weight: 600; font-size: 13px; }
.terms-sum { font-size: 12px; color: var(--muted); }
.terms-sum.invalid { color: #dc2626; }
.terms-preview-tag { margin-left: 6px; font-size: 11px; color: var(--muted); border: 1px dashed var(--border, #d9d9d9); border-radius: 3px; padding: 0 4px; }
</style>
