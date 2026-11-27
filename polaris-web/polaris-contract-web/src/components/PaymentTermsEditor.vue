<script setup lang="ts">
/**
 * 合同付款节点配置（内嵌在合同编辑抽屉，仅草稿可编辑）。
 * 比例合计必须 100%、必须含验收款；金额由合同清单合计 × 比例在服务端锁定。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  listPaymentTerms, savePaymentTerms, PAYMENT_STAGES, paymentStageLabel,
  type PaymentTermVO, type PaymentTermSave
} from '@/api/contract'
import { yuan } from '@/utils/format'

const props = defineProps<{ contractNo: string; contractStatus?: string; readonly?: boolean }>()
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

async function load() {
  loading.value = true
  try { rows.value = await listPaymentTerms(props.contractNo) }
  catch (e: any) { message.error(e?.message || '付款节点加载失败') }
  finally { loading.value = false }
}
onMounted(() => { if (props.contractNo) load() })
watch(() => props.contractNo, (v) => { if (v) load() })

function addRow() {
  const next = PAYMENT_STAGES.find((s) => !usedStages.value.has(s.value))
  if (!next) { message.warning('四个付款阶段均已配置'); return }
  rows.value.push({
    id: '', contractNo: props.contractNo, stage: next.value, ratio: 0, amount: 0,
    dueDate: undefined, remark: ''
  })
}
function removeRow(index: number) { rows.value.splice(index, 1) }

async function save() {
  if (!editable.value) return
  if (!rows.value.length) { message.warning('请至少配置一个付款节点'); return }
  if (totalRatio.value !== 100) { message.warning(`付款节点比例合计必须为 100%，当前 ${totalRatio.value}%`); return }
  if (!hasAccept.value) { message.warning('付款节点必须包含验收款'); return }
  if (rows.value.some((r) => !r.ratio || Number(r.ratio) <= 0)) { message.warning('每个付款节点比例必须大于 0'); return }
  saving.value = true
  try {
    const payload: PaymentTermSave[] = rows.value.map((r) => ({
      id: r.id || undefined, stage: r.stage, ratio: Number(r.ratio),
      dueDate: r.dueDate || undefined, remark: r.remark || undefined
    }))
    const saved = await savePaymentTerms(props.contractNo, payload)
    rows.value = saved
    message.success('付款节点已保存，金额按当前清单合计锁定')
    emit('saved', saved)
  } catch (e: any) { message.error(e?.message || '付款节点保存失败') }
  finally { saving.value = false }
}
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
      <a-button v-if="editable" size="small" type="primary" :loading="saving" @click="save">保存节点</a-button>
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
        <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
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
</style>
