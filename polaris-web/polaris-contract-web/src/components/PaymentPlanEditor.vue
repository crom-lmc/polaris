<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  listPaymentPlans, savePaymentPlan, applyPaymentPlan, deletePaymentPlan, uploadContractAttachment,
  listPaymentTerms, listItems, paymentStageLabel,
  type PaymentPlanVO, type PaymentTermVO, type ItemVO
} from '@/api/contract'
import { yuan } from '@/utils/format'
import AttachmentUpload from '@shared/components/AttachmentUpload.vue'

const props = defineProps<{ contractNo: string; remainingAmount?: number; readonly?: boolean; contractStatus?: string; warrantyDueDate?: string }>()
const store = useUserStore()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 520))
/** 付款申请仅已生效/执行中可发起，与后端一致；草稿/审批中/完成/终止隐藏入口 */
const canApply = computed(() => ['EFFECTIVE', 'EXECUTING'].includes(props.contractStatus || ''))

const loading = ref(false)
const rows = ref<PaymentPlanVO[]>([])
const statusLabel: Record<string, string> = {
  WAITING: '待申请', PENDING: '待审批', APPROVED: '已审批待付', POSTED: '已入账', PAID: '已付款', REJECTED: '已驳回'
}
const statusColor: Record<string, string> = {
  WAITING: 'default', PENDING: 'orange', APPROVED: 'blue', POSTED: 'green', PAID: 'green', REJECTED: 'red'
}
/** 付款节点：有节点时申请必须引用节点，阶段由节点决定；存量无节点仍可自由选阶段 */
const terms = ref<PaymentTermVO[]>([])
const items = ref<ItemVO[]>([])
const stageOptions = [
  { value: 'PREPAY', label: '预付款' }, { value: 'GOODS', label: '到货款' },
  { value: 'ACCEPT', label: '验收款' }, { value: 'WARRANTY', label: '质保金' }
]
function stageText(value?: string) { return paymentStageLabel(value) }
/** 节点下拉：质保未到期时置灰（禁用但仍展示，说明原因） */
const termOptions = computed(() => terms.value.map((t) => ({
  value: t.id,
  label: `${paymentStageLabel(t.stage)} ${t.ratio}% · 可申请 ${yuan(t.availableAmount ?? 0)}`,
  disabled: t.stage === 'WARRANTY' && (!props.warrantyDueDate || props.warrantyDueDate > new Date().toISOString().slice(0, 10))
})))

const columns = computed(() => {
  const base = [
    { title: '付款阶段', dataIndex: 'stage', key: 'stage', width: 140 },
    { title: '本期付款金额', key: 'amount', align: 'right' as const, width: 140 },
    { title: '应付日期', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
    { title: '付款事由', dataIndex: 'summary', key: 'summary', width: 180 },
    { title: '付款状态', key: 'status', align: 'center' as const, width: 120 },
    { title: '来源单据', dataIndex: 'sourceDocNo', key: 'sourceDocNo', width: 170 },
    { title: '关联凭证', dataIndex: 'voucherNo', key: 'voucherNo', width: 160 },
    { title: '附件', key: 'attachments', width: 110 },
    { title: '操作', key: 'action', width: 150 }
  ]
  return props.readonly ? base.filter((c) => c.key !== 'action') : base
})

async function load() {
  loading.value = true
  try { rows.value = await listPaymentPlans(props.contractNo) }
  catch (e: any) { message.error(e?.message || '加载付款计划失败') }
  finally { loading.value = false }
}

const createOpen = ref(false)
const saving = ref(false)
const form = reactive({
  termId: undefined as string | undefined,
  stage: undefined as string | undefined,
  itemIds: [] as string[],
  remainingAmount: undefined as number | undefined,
  amount: undefined as number | undefined,
  dueDate: undefined as string | undefined,
  summary: '', fileIds: [] as string[]
})
/** 当前选中的付款节点 */
const selectedTerm = computed(() => terms.value.find((t) => t.id === form.termId))
/** 节点模式：有节点时必须选节点；存量无节点时回退自由选阶段 */
const termMode = computed(() => terms.value.length > 0)
/** 本节点可申请金额：有节点取节点余额，否则取合同剩余 */
const appliableAmount = computed(() => selectedTerm.value
  ? Number(selectedTerm.value.availableAmount ?? 0)
  : Number(form.remainingAmount ?? 0))
/** GOODS 阶段可验收的清单行（已交付 > 0） */
const goodsRows = computed(() => items.value.filter((i) => Number(i.deliveredQty || 0) > 0))
/** 剩余未付金额 = 合同剩余金额 - 本期付款金额，纯展示派生字段，不落库 */
const unpaidAmount = computed(() => {
  const base = selectedTerm.value ? Number(selectedTerm.value.availableAmount ?? 0) : form.remainingAmount
  if (base == null) return undefined
  return Math.round((Number(base) - Number(form.amount ?? 0)) * 100) / 100
})
async function openCreate() {
  form.termId = undefined
  form.stage = undefined
  form.itemIds = []
  form.remainingAmount = props.remainingAmount
  form.amount = undefined
  form.dueDate = undefined
  form.summary = ''
  form.fileIds = []
  // 加载节点与清单行（节点模式由节点带出阶段与金额）
  try {
    const [t, i] = await Promise.all([
      listPaymentTerms(props.contractNo), listItems(props.contractNo)
    ])
    terms.value = t || []
    items.value = i || []
  } catch (e: any) { message.error(e?.message || '付款节点/清单加载失败') }
  createOpen.value = true
}
/** 选节点后带出阶段与可申请金额 */
function onTermChange() {
  const term = selectedTerm.value
  form.stage = term?.stage
  form.amount = undefined
  form.itemIds = []
}
async function submit() {
  const amount = form.amount
  const missing: string[] = []
  if (termMode.value && !form.termId) missing.push('付款节点')
  if (!termMode.value && !form.stage) missing.push('付款阶段')
  if (!amount || amount <= 0) missing.push('本期付款金额')
  if (!form.dueDate) missing.push('应付日期')
  if (form.stage === 'GOODS' && !form.itemIds.length) missing.push('到货清单行')
  if (missing.length) { message.warning(`请填写必填项：${missing.join('、')}`); return }
  if (amount == null) return
  if (amount > appliableAmount.value) {
    message.warning(`本期付款金额不能超过${selectedTerm.value ? '该节点可申请金额' : '合同剩余金额'} ${yuan(appliableAmount.value)}`)
    return
  }
  saving.value = true
  try {
    await savePaymentPlan(props.contractNo, {
      termId: form.termId, stage: form.stage, itemIds: form.itemIds.length ? form.itemIds.join(',') : undefined,
      amount: Number(amount), dueDate: form.dueDate,
      summary: form.summary || undefined, fileIds: form.fileIds
    })
    createOpen.value = false
    message.success('付款计划已保存')
    await load()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
async function apply(row: PaymentPlanVO) {
  try { await applyPaymentPlan(row.id); message.success('付款申请已发起，等待审批'); await load() }
  catch (e: any) { message.error(e?.message || '发起申请失败') }
}
function remove(row: PaymentPlanVO) {
  Modal.confirm({
    centered: true, title: '删除付款计划', content: `确定删除「${stageText(row.stage)}」吗？仅待申请计划可删除。`,
    okButtonProps: { danger: true },
    onOk: async () => {
      try { await deletePaymentPlan(row.id); message.success('已删除'); await load() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}
onMounted(() => {
  load()
  // 节点/清单在打开新增抽屉时按需加载（openCreate）
})
</script>

<template>
  <div class="payment-plan-editor">
    <div v-if="!readonly" class="items-toolbar">
      <span class="grow" />
      <a-button v-if="store.can('contract:payment:edit')" type="primary" size="small" @click="openCreate">＋ 新增付款计划</a-button>
    </div>
    <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :loading="loading" :pagination="false"
             :class="{ 'tbl-empty-mode': !loading && !rows.length }" :scroll="rows.length ? { x: 1220 } : undefined">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'stage'">
          {{ record.termName || stageText(record.stage) }}
          <a-tag v-if="record.termId" color="blue" style="margin-left: 4px">节点</a-tag>
        </template>
        <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
        <template v-else-if="column.key === 'summary'">
          <a-tooltip v-if="record.summary" :title="record.summary"><span class="cell-ellipsis">{{ record.summary }}</span></a-tooltip>
          <span v-else class="muted">-</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor[record.status] || 'default'">{{ statusLabel[record.status] || record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'sourceDocNo'">
          <a-tooltip v-if="record.sourceDocNo" :title="record.sourceDocNo">
            <span class="cell-ellipsis">{{ record.sourceDocNo }}</span>
          </a-tooltip>
          <span v-else class="muted">待申请</span>
        </template>
        <template v-else-if="column.key === 'voucherNo'">
          <a-tooltip v-if="record.voucherNo" :title="record.voucherNo"><span class="cell-ellipsis">{{ record.voucherNo }}</span></a-tooltip>
          <span v-else class="muted">未过账</span>
        </template>
        <template v-else-if="column.key === 'attachments'">
          <template v-if="record.fileIds?.length">
            <a v-for="(url, idx) in record.attachmentUrls || []" :key="url" :href="url" target="_blank" style="margin-right: 8px">凭证 {{ idx + 1 }}</a>
            <span v-if="!record.attachmentUrls?.length" class="muted">{{ record.fileIds.length }} 个</span>
          </template>
          <span v-else class="muted">无</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <template v-if="store.can('contract:payment:edit')">
              <a v-if="canApply && (record.status === 'WAITING' || record.status === 'REJECTED')" @click="apply(record)">发起申请</a>
              <a v-if="record.status === 'WAITING'" @click="remove(record)">删除</a>
            </template>
            <span v-else class="muted">只读</span>
          </a-space>
        </template>
      </template>
      <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="该合同暂无付款计划" /></div></template>
    </a-table>

    <a-drawer v-model:open="createOpen" title="新增付款计划" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="所属合同"><a-input :value="contractNo" readonly /></a-form-item>
        <a-form-item v-if="termMode" label="付款节点" required>
          <a-select v-model:value="form.termId" :options="termOptions" placeholder="请选择付款节点" show-search
                    style="width: 100%" @change="onTermChange" />
        </a-form-item>
        <a-form-item v-else label="付款阶段" required>
          <a-select v-model:value="form.stage" :options="stageOptions" placeholder="请选择付款阶段" show-search style="width: 100%" />
        </a-form-item>
        <a-form-item v-if="form.stage === 'GOODS'" label="到货清单行" required>
          <a-select v-model:value="form.itemIds" mode="multiple" placeholder="选择已验收的清单行"
                    style="width: 100%" :max-tag-count="3">
            <a-select-option v-for="i in goodsRows" :key="i.id" :value="i.id">
              {{ i.materialName }} · 已交付 {{ i.deliveredQty }}/{{ i.qty }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="selectedTerm ? '本节点可申请金额' : '合同剩余金额'" required>
          <a-input-number :value="appliableAmount" :controls="false" :precision="2" readonly
                          style="width: 100%" addon-before="¥" />
        </a-form-item>
        <a-form-item label="本期付款金额" required>
          <a-input-number v-model:value="form.amount" :min="0" :max="appliableAmount || undefined" :precision="2"
                          style="width: 100%" addon-before="¥" />
        </a-form-item>
        <a-form-item label="剩余未付金额">
          <a-input-number :value="unpaidAmount" :controls="false" :precision="2" readonly
                          placeholder="自动计算：可申请金额 - 本期付款金额" style="width: 100%" addon-before="¥" />
        </a-form-item>
        <a-form-item label="应付日期" required>
          <a-date-picker v-model:value="form.dueDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="付款事由">
          <a-textarea v-model:value="form.summary" :rows="2" placeholder="填写本次付款事由" />
        </a-form-item>
        <AttachmentUpload v-model="form.fileIds" label="付款凭证" button-text="上传付款凭证"
                          :upload="(file) => uploadContractAttachment(file, 'payment-plan')" />
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="createOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.payment-plan-editor { margin-top: 4px; }
.items-toolbar { display: flex; align-items: center; gap: 12px; margin: 4px 0 8px; }
</style>
