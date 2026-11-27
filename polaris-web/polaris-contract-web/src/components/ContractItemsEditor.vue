<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  listItems, saveItem, deleteItem, acceptItem, listAcceptances, pushAcceptanceAr, uploadContractAttachment,
  type ItemVO, type ItemSave, type AcceptanceVO
} from '@/api/contract'
import { loadMaterialChildren, loadMaterialPage } from '@/composables/useMaterialPicker'
import MaterialPickerModal, { type MaterialPick } from '@shared/components/MaterialPickerModal.vue'
import AttachmentUpload from '@shared/components/AttachmentUpload.vue'
import { yuan } from '@/utils/format'

const props = defineProps<{ contractNo: string; readonly?: boolean; contractStatus?: string; contractType?: string }>()
/** 销售合同客户验收：通过/不通过，通过才累加交付并推应收；采购仍为到货验收合格/不合格 */
const isSales = computed(() => props.contractType === 'SALES')
const store = useUserStore()
/** 采购计划转入行由后端写入该备注前缀，作为来源标记：禁止删除，可编辑/验收 */
const PLAN_ITEM_REMARK_PREFIX = '由采购计划 '
const isFromPlan = (row: ItemVO) => !!row.remark?.startsWith(PLAN_ITEM_REMARK_PREFIX)
/** 清单增删改仅草稿；验收仅已生效/执行中，与后端状态机一致（前端按钮只是可见入口） */
const canEditItems = computed(() => !props.readonly && props.contractStatus === 'DRAFT')
const canAccept = computed(() => !props.readonly && ['EFFECTIVE', 'EXECUTING'].includes(props.contractStatus || ''))
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 620))
const smallDrawerWidth = computed(() => (isMobile.value ? '100%' : 620))

const loading = ref(false)
const rows = ref<ItemVO[]>([])

const columns = computed(() => {
  const base = [
    { title: '行号', dataIndex: 'lineNo', key: 'lineNo', align: 'center' as const, width: 60 },
    { title: '物料', key: 'materialName', width: 150 },
    { title: '规格型号', dataIndex: 'spec', key: 'spec', width: 140 },
    { title: '单位', dataIndex: 'unit', key: 'unit', align: 'center' as const, width: 60 },
    { title: '数量', dataIndex: 'qty', key: 'qty', align: 'right' as const, width: 80 },
    { title: '单价', key: 'price', align: 'right' as const, width: 100 },
    { title: '金额', key: 'amount', align: 'right' as const, width: 110 },
    { title: '交付进度', key: 'delivered', width: 130 },
    { title: '操作', key: 'action', width: 140 }
  ]
  return props.readonly ? base.filter((c) => c.key !== 'action') : base
})

async function load() {
  loading.value = true
  try { rows.value = await listItems(props.contractNo) }
  catch (e: any) { message.error(e?.message || '加载清单失败') }
  finally { loading.value = false }
}
const editOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const editingFromPlan = ref(false)
const form = reactive({
  materialCode: '', materialName: '', spec: '', unit: '', qty: 1, price: 0, remark: ''
})
/** 清单行选料统一走公共弹窗：新增直接弹窗选料并写入清单行，编辑时在抽屉内回填 */
const pickerOpen = ref(false)
const pickerMode = ref<'add' | 'edit'>('edit')
const pickerInitial = ref<MaterialPick[]>([])

function openAddPicker() {
  pickerMode.value = 'add'
  pickerInitial.value = []
  pickerOpen.value = true
}
function openEditPicker() {
  pickerMode.value = 'edit'
  pickerInitial.value = form.materialCode ? [{
    code: form.materialCode,
    name: form.materialName || form.materialCode,
    spec: form.spec,
    unit: form.unit,
    price: form.price
  }] : []
  pickerOpen.value = true
}

async function onMaterialConfirm(picks: MaterialPick[]) {
  if (!picks.length) return
  if (pickerMode.value === 'add') {
    await addFromMaterials(picks)
    return
  }
  const m = picks[0]
  form.materialCode = m.code
  form.materialName = m.name
  form.spec = m.spec || ''
  form.unit = m.unit || ''
  if (!form.price && m.price) form.price = m.price
}

/** 直接由物料生成清单行：数量默认 1、单价取参考价，后续可在表格「编辑」调整 */
async function addFromMaterials(picks: MaterialPick[]) {
  saving.value = true
  let ok = 0
  try {
    for (const m of picks) {
      const price = Number(m.price || 0)
      const payload: ItemSave = {
        materialCode: m.code, materialName: m.name, spec: m.spec || undefined, unit: m.unit || undefined,
        qty: 1, price, amount: Number(price.toFixed(2))
      }
      await saveItem(props.contractNo, payload)
      ok++
    }
    if (ok) message.success(`已添加 ${ok} 行清单，可在表格「编辑」调整数量与单价`)
    await load()
  } catch (e: any) { message.error(e?.message || '添加清单行失败：' + (e?.message || '')) }
  finally { saving.value = false }
}
const amount = computed(() => Number(((Number(form.qty) || 0) * (Number(form.price) || 0)).toFixed(2)))
function openEdit(row?: ItemVO) {
  editingId.value = row?.id || null
  editingFromPlan.value = !!row && isFromPlan(row)
  form.materialCode = row?.materialCode || ''
  form.materialName = row?.materialName || ''
  form.spec = row?.spec || ''
  form.unit = row?.unit || ''
  form.qty = row ? Number(row.qty || 0) : 1
  form.price = row ? Number(row.price || 0) : 0
  form.remark = row?.remark || ''
  editOpen.value = true
}
async function submit() {
  if (!form.materialCode || !form.qty || form.price < 0) { message.warning('请选择物料并填写数量与单价'); return }
  saving.value = true
  try {
    const payload: ItemSave = {
      id: editingId.value || undefined, materialCode: form.materialCode, materialName: form.materialName || undefined,
      spec: form.spec || undefined, unit: form.unit || undefined,
      qty: Number(form.qty), price: Number(form.price), amount: amount.value, remark: form.remark || undefined
    }
    await saveItem(props.contractNo, payload)
    editOpen.value = false
    message.success('清单行已保存')
    await load()
  } catch (e: any) { message.error(e?.message || '保存失败：' + (e?.message || '')) }
  finally { saving.value = false }
}
function remove(row: ItemVO) {
  Modal.confirm({
    centered: true, title: '删除清单行', content: `确定删除「${row.materialName}」吗？`,
    okButtonProps: { danger: true },
    onOk: async () => {
      try { await deleteItem(props.contractNo, row.id); message.success('已删除'); await load() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}

/* 到货验收 */
const acceptOpen = ref(false)
const acceptSaving = ref(false)
const acceptId = ref('')
const acceptForm = reactive({
  qty: 1, date: undefined as string | undefined, result: '合格', customerNo: '', remark: '', fileIds: [] as string[]
})
const resultOptions = computed(() => isSales.value
  ? [{ value: '通过', label: '通过' }, { value: '不通过', label: '不通过' }]
  : [{ value: '合格', label: '合格' }, { value: '不合格', label: '不合格' }])
const acceptRow = computed(() => rows.value.find((r) => r.id === acceptId.value) || null)
const acceptRemain = computed(() => acceptRow.value ? Math.max(0, Number(acceptRow.value.qty || 0) - Number(acceptRow.value.deliveredQty || 0)) : 0)
function openAccept(row: ItemVO) {
  acceptId.value = row.id
  acceptForm.qty = Math.max(1, acceptRemain.value || 1)
  acceptForm.date = undefined
  acceptForm.result = isSales.value ? '通过' : '合格'
  acceptForm.customerNo = ''
  acceptForm.remark = ''
  acceptForm.fileIds = []
  acceptOpen.value = true
}
async function submitAccept() {
  if (!acceptForm.qty || acceptForm.qty < 1 || acceptForm.qty > acceptRemain.value) {
    message.warning(`验收数量须在 1 ~ ${acceptRemain.value} 之间`); return
  }
  if (!acceptForm.date) {
    message.warning('请选择验收日期'); return
  }
  acceptSaving.value = true
  try {
    await acceptItem(props.contractNo, acceptId.value, {
      qty: Number(acceptForm.qty), date: acceptForm.date, result: acceptForm.result,
      customerNo: isSales.value ? acceptForm.customerNo || undefined : undefined,
      remark: acceptForm.remark || undefined, fileIds: acceptForm.fileIds
    })
    acceptOpen.value = false
    message.success(isSales.value
      ? (acceptForm.result === '通过' ? '客户验收已登记，应收将自动推送' : '客户验收已登记（不通过，不推应收）')
      : '验收已登记')
    await load()
    if (acceptRecordsOpen.value) await loadAcceptances()
  } catch (e: any) { message.error(e?.message || '验收失败') }
  finally { acceptSaving.value = false }
}

/* 验收记录：集中查看验收单与验收凭证 */
const acceptRecordsOpen = ref(false)
const acceptRecordsLoading = ref(false)
const acceptRecords = ref<AcceptanceVO[]>([])
const acceptanceColumns = computed(() => {
  const base = [
    { title: '验收单号', dataIndex: 'no', key: 'no', width: 170 },
    { title: '物料', dataIndex: 'materialCode', key: 'materialCode', width: 140 },
    { title: '数量', dataIndex: 'qty', key: 'qty', align: 'right' as const, width: 90 },
    { title: '日期', dataIndex: 'date', key: 'date', width: 110 },
    { title: '结果', dataIndex: 'result', key: 'result', width: 90 }
  ] as any[]
  if (isSales.value) {
    base.push({ title: '客户验收单号', dataIndex: 'customerNo', key: 'customerNo', width: 150 })
    base.push({ title: '应收单号', dataIndex: 'arNo', key: 'arNo', width: 150 })
  }
  base.push({ title: '过账凭证', dataIndex: 'voucherNo', key: 'voucherNo', width: 150 })
  base.push({ title: '验收凭证', key: 'attachments', width: 150 })
  if (isSales.value && !props.readonly) base.push({ title: '操作', key: 'arAction', width: 110 })
  return base
})
const acceptanceScrollX = computed(() => acceptanceColumns.value.reduce((sum, c) => sum + Number(c.width || 100), 0))
async function retryAr(row: AcceptanceVO) {
  try {
    await pushAcceptanceAr(props.contractNo, row.id)
    message.success('已重新推送应收')
    await loadAcceptances()
  } catch (e: any) { message.error(e?.message || '推送应收失败') }
}
async function loadAcceptances() {
  acceptRecordsLoading.value = true
  try { acceptRecords.value = await listAcceptances(props.contractNo) }
  catch (e: any) { message.error(e?.message || '加载验收记录失败') }
  finally { acceptRecordsLoading.value = false }
}
function openAcceptRecords() {
  acceptRecordsOpen.value = true
  loadAcceptances()
}

onMounted(() => { load() })
</script>

<template>
  <div class="contract-items-editor">
    <div class="items-toolbar">
      <span class="grow" />
      <a-tag color="blue">清单 {{ rows.length }} 行</a-tag>
      <a-button v-if="store.can('contract:ledger:list')" size="small" @click="openAcceptRecords">验收记录</a-button>
      <template v-if="!readonly">
        <a-button v-if="canEditItems && store.can('contract:item:edit')" type="primary" size="small" :loading="saving" @click="openAddPicker">＋ 新增清单行</a-button>
      </template>
    </div>
    <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :loading="loading" :pagination="false"
             :class="{ 'tbl-empty-mode': !loading && !rows.length }" :scroll="rows.length ? { x: 980 } : undefined">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'materialName'">
          <a-tooltip :title="`${record.materialName} ${record.spec || ''}`"><span class="cell-ellipsis">{{ record.materialName }}</span></a-tooltip>
        </template>
        <template v-else-if="column.key === 'spec'">
          <a-tooltip :title="record.spec"><span class="cell-ellipsis">{{ record.spec || '-' }}</span></a-tooltip>
        </template>
        <template v-else-if="['price', 'amount'].includes(String(column.key))">{{ yuan(record[column.key as 'price' | 'amount']) }}</template>
        <template v-else-if="column.key === 'delivered'">
          <a-progress :percent="Number(record.qty) ? Math.min(100, Math.round(Number(record.deliveredQty || 0) / Number(record.qty) * 100)) : 0" size="small" />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <template v-if="store.can('contract:item:edit')">
              <a v-if="canAccept && Number(record.deliveredQty || 0) < Number(record.qty || 0)" @click="openAccept(record)">验收</a>
              <a v-if="canEditItems" @click="openEdit(record)">编辑</a>
              <a-tooltip v-if="canEditItems && isFromPlan(record)" title="由采购计划转入的清单行不允许删除，可编辑数量与单价">
                <span class="link-disabled">删除</span>
              </a-tooltip>
              <a v-else-if="canEditItems" @click="remove(record)">删除</a>
            </template>
            <span v-else class="muted">只读</span>
          </a-space>
        </template>
      </template>
      <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="该合同暂无清单明细" /></div></template>
    </a-table>

    <a-drawer v-model:open="editOpen" :title="editingId ? '编辑清单行' : '新增清单行'" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="所属合同"><a-input :value="contractNo" readonly /></a-form-item>
        <a-form-item label="物料" required>
          <a-button style="text-align: left" @click="openEditPicker">
            {{ form.materialCode ? `${form.materialCode} · ${form.materialName}` : '从物料库选择物料' }}
          </a-button>
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="物料名称"><a-input v-model:value="form.materialName" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="规格型号"><a-input v-model:value="form.spec" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="8"><a-form-item label="单位"><a-input v-model:value="form.unit" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="数量" required><a-input-number v-model:value="form.qty" :min="0" style="width: 100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="单价" required><a-input-number v-model:value="form.price" :min="0" :precision="2" style="width: 100%" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="行金额"><a-input :value="yuan(amount)" readonly /></a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="2" :disabled="editingFromPlan" />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="editOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存清单行</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="acceptOpen" :title="isSales ? '客户验收' : '到货验收'" :width="smallDrawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="物料"><a-input :value="acceptRow ? `${acceptRow.materialName} · ${acceptRow.spec}` : ''" readonly /></a-form-item>
        <a-form-item label="合同数量"><a-input :value="acceptRow ? `${acceptRow.qty} ${acceptRow.unit || ''}` : ''" readonly /></a-form-item>
        <a-form-item :label="isSales ? '客户已验收' : '已交付'"><a-input :value="acceptRow ? String(acceptRow.deliveredQty || 0) : ''" readonly /></a-form-item>
        <a-form-item :label="isSales ? '本次验收数量' : '本次验收数量'" required>
          <a-input-number v-model:value="acceptForm.qty" :min="1" :max="acceptRemain" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="isSales ? '客户验收日期' : '验收日期'" required><a-date-picker v-model:value="acceptForm.date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" placeholder="请选择验收日期" /></a-form-item>
        <a-form-item v-if="isSales" label="客户验收单号">
          <a-input v-model:value="acceptForm.customerNo" placeholder="选填，客户侧单据号" />
        </a-form-item>
        <a-form-item :label="isSales ? '验收结论' : '验收结果'">
          <a-select v-model:value="acceptForm.result" :options="resultOptions" />
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="acceptForm.remark" :rows="2" /></a-form-item>
        <AttachmentUpload v-if="store.can('contract:item:attachment:upload')" v-model="acceptForm.fileIds" label="验收凭证" button-text="上传验收凭证"
                          :upload="(file) => uploadContractAttachment(file, 'acceptance')" @error="message.error" />
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="acceptOpen = false">取消</a-button>
          <a-button type="primary" :loading="acceptSaving" @click="submitAccept">确认验收</a-button>
        </div>
      </template>
    </a-drawer>

    <a-modal v-model:open="acceptRecordsOpen" title="验收记录 · 验收凭证" :footer="null" :width="820">
      <a-spin :spinning="acceptRecordsLoading">
        <a-table class="ft-table" row-key="id" size="small" :columns="acceptanceColumns" :data-source="acceptRecords"
                 :pagination="false" :scroll="{ x: acceptanceScrollX }" :style="{ '--ft-min-width': `${acceptanceScrollX}px` }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'customerNo'">
              <a-tooltip v-if="record.customerNo" :title="record.customerNo"><span class="cell-ellipsis">{{ record.customerNo }}</span></a-tooltip>
              <span v-else class="muted">-</span>
            </template>
            <template v-else-if="column.key === 'arNo'">
              <a-tooltip v-if="record.arNo" :title="record.arNo"><span class="cell-ellipsis">{{ record.arNo }}</span></a-tooltip>
              <a-tooltip v-else-if="record.arError" :title="record.arError"><span class="text-danger">推送失败</span></a-tooltip>
              <span v-else class="muted">待推送</span>
            </template>
            <template v-else-if="column.key === 'voucherNo'">
              <a-tooltip v-if="record.voucherNo" :title="record.voucherNo"><span class="cell-ellipsis">{{ record.voucherNo }}</span></a-tooltip>
              <span v-else class="muted">未过账</span>
            </template>
            <template v-else-if="column.key === 'attachments'">
              <a v-if="record.fileIds?.length && record.attachmentUrls?.[0]" :href="record.attachmentUrls[0]" target="_blank">凭证（{{ record.fileIds.length }}）</a>
              <span v-else-if="record.fileIds?.length" class="muted">{{ record.fileIds.length }} 个</span>
              <span v-else class="muted">-</span>
            </template>
            <template v-else-if="column.key === 'arAction'">
              <a v-if="record.arError && store.can('contract:item:edit')" @click="retryAr(record)">重试推应收</a>
              <span v-else class="muted">-</span>
            </template>
          </template>
          <template #emptyText><div class="tbl-empty"><a-empty v-if="!acceptRecordsLoading" description="该合同暂无验收记录" /></div></template>
        </a-table>
      </a-spin>
    </a-modal>

    <MaterialPickerModal v-model:open="pickerOpen" :title="pickerMode === 'add' ? '选择物料（可多选）' : '选择物料'"
                         :multiple="pickerMode === 'add'" :initial-materials="pickerInitial"
                         :load-children="loadMaterialChildren" :load-materials="loadMaterialPage"
                         @confirm="onMaterialConfirm" />
  </div>
</template>

<style scoped>
.contract-items-editor { margin-top: 4px; }
.items-toolbar { display: flex; align-items: center; gap: 12px; margin: 4px 0 8px; }
.text-danger { color: #dc2626; }
</style>
