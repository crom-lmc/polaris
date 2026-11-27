<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  listPaymentPlans, savePaymentPlan, applyPaymentPlan, deletePaymentPlan, uploadContractAttachment,
  type PaymentPlanVO
} from '@/api/contract'
import { yuan } from '@/utils/format'
import AttachmentUpload from '@shared/components/AttachmentUpload.vue'

const props = defineProps<{ contractNo: string }>()
const store = useUserStore()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 520))

const loading = ref(false)
const rows = ref<PaymentPlanVO[]>([])
const statusLabel: Record<string, string> = {
  WAITING: '待申请', PENDING: '待审批', APPROVED: '已审批待付', POSTED: '已入账', PAID: '已付款', REJECTED: '已驳回'
}
const statusColor: Record<string, string> = {
  WAITING: 'default', PENDING: 'orange', APPROVED: 'blue', POSTED: 'green', PAID: 'green', REJECTED: 'red'
}

const columns = [
  { title: '付款阶段', dataIndex: 'stage', key: 'stage', width: 140 },
  { title: '应付金额', key: 'amount', align: 'right' as const, width: 130 },
  { title: '应付日期', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
  { title: '付款事由', dataIndex: 'summary', key: 'summary', width: 180 },
  { title: '付款状态', key: 'status', align: 'center' as const, width: 120 },
  { title: '来源单据', dataIndex: 'sourceDocNo', key: 'sourceDocNo', width: 170 },
  { title: '关联凭证', dataIndex: 'voucherNo', key: 'voucherNo', width: 160 },
  { title: '附件', key: 'attachments', width: 110 },
  { title: '操作', key: 'action', width: 150 }
]

async function load() {
  loading.value = true
  try { rows.value = await listPaymentPlans(props.contractNo) }
  catch (e: any) { message.error(e?.message || '加载付款计划失败') }
  finally { loading.value = false }
}

const createOpen = ref(false)
const saving = ref(false)
const form = reactive({
  stage: '', amount: undefined as number | undefined, dueDate: undefined as string | undefined,
  summary: '', fileIds: [] as string[]
})
function openCreate() {
  form.stage = ''
  form.amount = undefined
  form.dueDate = undefined
  form.summary = ''
  form.fileIds = []
  createOpen.value = true
}
async function submit() {
  if (!form.amount || form.amount <= 0) { message.warning('付款金额必须大于 0'); return }
  saving.value = true
  try {
    await savePaymentPlan(props.contractNo, {
      stage: form.stage || undefined, amount: Number(form.amount), dueDate: form.dueDate,
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
    centered: true, title: '删除付款计划', content: `确定删除「${row.stage}」吗？仅待申请计划可删除。`,
    okButtonProps: { danger: true },
    onOk: async () => {
      try { await deletePaymentPlan(row.id); message.success('已删除'); await load() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}
onMounted(load)
</script>

<template>
  <div class="payment-plan-editor">
    <div class="items-toolbar">
      <span class="hint">付款计划从属于合同；发起申请仅推进状态，来源单据号由财务域建单后回写</span>
      <a-button v-if="store.can('contract:payment:edit')" type="primary" size="small" @click="openCreate">＋ 新增付款计划</a-button>
    </div>
    <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :loading="loading" :pagination="false"
             :class="{ 'tbl-empty-mode': !loading && !rows.length }" :scroll="rows.length ? { x: 1220 } : undefined">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
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
              <a v-if="record.status === 'WAITING' || record.status === 'REJECTED'" @click="apply(record)">发起申请</a>
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
        <a-form-item label="付款阶段"><a-input v-model:value="form.stage" placeholder="如：预付款 / 到货款 / 质保金" /></a-form-item>
        <a-form-item label="应付金额" required>
          <a-input-number v-model:value="form.amount" :min="0" :precision="2" style="width: 100%" addon-before="¥" />
        </a-form-item>
        <a-form-item label="应付日期">
          <a-date-picker v-model:value="form.dueDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="付款事由">
          <a-textarea v-model:value="form.summary" :rows="2" placeholder="填写本次付款事由" />
        </a-form-item>
        <a-form-item label="合同与验收凭证">
          <AttachmentUpload v-model="form.fileIds" label="付款凭证" button-text="上传付款凭证"
                            :upload="(file) => uploadContractAttachment(file, 'payment-plan')" />
        </a-form-item>
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
.items-toolbar .hint { flex: 1 1 auto; }
</style>
