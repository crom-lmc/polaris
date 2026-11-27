<script setup lang="ts">
/**
 * 合同表单抽屉（新增 / 编辑 / 采购计划转入共用）
 * 合同台账与采购计划页复用同一套主信息表单、主数据弹窗、附件与清单/付款计划编辑。
 * 编辑态（传 contract）才显示清单与付款计划；计划转入保存成功后由父页面回传 contract 进入编辑态。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import { useOrgDirectory } from '@/composables/useOrgDirectory'
import {
  saveContract, updateContract, changeContractStatus, convertPurchasePlan,
  getProjectOptions, listPartners, uploadContractAttachment, fetchActiveEmployees, listItems, listPaymentTerms,
  type ContractVO, type ContractSave, type ProjectOptionVO, type PartnerVO, type OrgEmployeeVO,
  type PurchasePlanVO, type PurchasePlanItemVO
} from '@/api/contract'
import ContractItemsEditor from '@/components/ContractItemsEditor.vue'
import PaymentTermsEditor from '@/components/PaymentTermsEditor.vue'
import AttachmentUpload from '@shared/components/AttachmentUpload.vue'
import { yuan } from '@/utils/format'
import EntityPickerModal, { type PickerColumn, type PickerRow, type PickerLoadParams, type PickerResult } from '@shared/components/EntityPickerModal.vue'

const props = defineProps<{
  open: boolean
  contract?: ContractVO | null
  convertPlan?: PurchasePlanVO | null
  /** 台账入口菜单已指定合同类型时，新增表单锁定该类型 */
  defaultType?: 'PURCHASE' | 'SALES'
}>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: [contract: ContractVO]
}>()

const store = useUserStore()
const isMobile = useIsMobile()
const { orgNodes, load: loadOrgDirectory } = useOrgDirectory()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 900))

const typeOptions = [{ label: '采购合同', value: 'PURCHASE' }, { label: '销售合同', value: 'SALES' }]
const roleLabel: Record<string, string> = { SUPPLIER: '供应商', CUSTOMER: '客户', BOTH: '供应商/客户' }
const projectStatusLabel: Record<string, string> = { ACTIVE: '启用', DRAFT: '草稿', PAUSED: '暂停', CLOSED: '关闭', CANCELLED: '取消' }
const partnerColumns: PickerColumn[] = [
  { title: '编码', dataIndex: 'code', width: 150 },
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '角色', dataIndex: 'roleLabel', width: 90 },
  { title: '状态', dataIndex: 'statusLabel', width: 90 }
]
const projectColumns: PickerColumn[] = [
  { title: '项目编号', dataIndex: 'code', width: 160 },
  { title: '项目名称', dataIndex: 'name', ellipsis: true },
  { title: '状态', dataIndex: 'statusLabel', width: 90 }
]
const ownerColumns: PickerColumn[] = [
  { title: '姓名', dataIndex: 'name', width: 110 },
  { title: '工号', dataIndex: 'empNo', width: 130 },
  { title: '所属部门', dataIndex: 'orgName', ellipsis: true }
]

const partnerPickerOpen = ref(false)
const projectPickerOpen = ref(false)
const ownerPickerOpen = ref(false)
const saving = ref(false)
/** 清单变更版本号：通知付款节点组件重算清单合计（锁定金额预览） */
const itemsVersion = ref(0)

const isEdit = computed(() => !!props.contract)
const isConvert = computed(() => !props.contract && !!props.convertPlan)
const drawerTitle = computed(() => isEdit.value ? '编辑合同' : isConvert.value ? '采购计划转合同' : '新增合同')
const partnerLabel = computed(() => createForm.type === 'SALES' ? '客户' : '供应商')

/** 转合同模式：预览即将带入的计划明细（合同号未生成，不渲染依赖合同号的编辑器） */
const planItems = computed<PurchasePlanItemVO[]>(() => props.convertPlan?.items || [])
const planItemsTotal = computed(() => Number(planItems.value.reduce((s, i) => s + Number(i.amount || 0), 0).toFixed(2)))
const planItemColumns = [
  { title: '行号', key: 'lineNo', width: 60, align: 'center' as const },
  { title: '物料', dataIndex: 'materialName', key: 'materialName', ellipsis: true },
  { title: '规格型号', dataIndex: 'spec', key: 'spec', width: 140 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 70, align: 'center' as const },
  { title: '数量', dataIndex: 'qty', key: 'qty', width: 90, align: 'right' as const },
  { title: '单价', key: 'price', width: 100, align: 'right' as const },
  { title: '金额', key: 'amount', width: 110, align: 'right' as const }
]

const createForm = reactive<ContractSave & { partnerName?: string; projectName?: string }>({
  type: 'PURCHASE', name: '', partnerCode: '', partnerName: '', projectNo: undefined, projectName: '', dept: '', owner: '',
  signedDate: undefined, effectiveDate: undefined, endDate: undefined, warrantyDueDate: undefined, note: '', fileIds: []
})

function resetCreate() {
  Object.assign(createForm, {
    type: props.defaultType || 'PURCHASE', name: '', partnerCode: '', partnerName: '', projectNo: undefined, projectName: '', dept: '', owner: '',
    signedDate: undefined, effectiveDate: undefined, endDate: undefined, warrantyDueDate: undefined, note: '', fileIds: []
  })
}

/** 采购计划数据带出：名称=计划名称，部门/负责人=计划需求部门/申请人，日期=计划日期与需求日期，附件与 orgCode 沿用 */
function fillFromPlan(plan: PurchasePlanVO) {
  Object.assign(createForm, {
    type: 'PURCHASE', name: plan.title || '', partnerCode: '', partnerName: '',
    projectNo: undefined, projectName: '', dept: plan.dept || '', owner: plan.applicant || '',
    signedDate: plan.date || undefined, effectiveDate: plan.date || undefined, endDate: plan.needDate || undefined,
    warrantyDueDate: undefined,
    note: '', fileIds: plan.fileIds ? [...plan.fileIds] : []
  })
}

function fillFromContract(row: ContractVO) {
  Object.assign(createForm, {
    type: row.type, name: row.name, partnerCode: row.partnerCode, partnerName: row.partnerName,
    projectNo: row.projectNo, projectName: row.projectName, dept: row.dept, owner: row.owner,
    signedDate: row.signedDate, effectiveDate: row.effectiveDate, endDate: row.endDate,
    warrantyDueDate: row.warrantyDueDate || undefined,
    note: row.note || '', fileIds: row.fileIds ? [...row.fileIds] : []
  })
}

watch(() => [props.open, props.contract?.id, props.convertPlan?.id], () => {
  if (!props.open) return
  if (props.contract) fillFromContract(props.contract)
  else if (props.convertPlan) fillFromPlan(props.convertPlan)
  else resetCreate()
}, { immediate: true })

/** 切换合同类型时清空往来单位，避免角色与类型错配（编辑态类型禁用，不会触发） */
watch(() => createForm.type, () => {
  createForm.partnerCode = ''
  createForm.partnerName = ''
})

async function loadPartnerPicker(params: PickerLoadParams): Promise<PickerResult> {
  const role = createForm.type === 'SALES' ? 'CUSTOMER' : 'SUPPLIER'
  try {
    const res = await listPartners({ role, status: 'COOPERATING', keyword: params.keyword || undefined, page: params.page, size: params.size })
    const records = (res.records || []).map((p: PartnerVO) => ({
      code: p.code, name: p.name, roleLabel: roleLabel[p.role] || p.role, statusLabel: '合作中', raw: p
    }))
    return { records, total: res.total || 0 }
  } catch { return { records: [], total: 0 } }
}

async function loadProjectPicker(params: PickerLoadParams): Promise<PickerResult> {
  try {
    const all: ProjectOptionVO[] = await getProjectOptions() || []
    const kw = params.keyword.trim().toLowerCase()
    const filtered = all.filter((p) => p.status === 'ACTIVE' &&
      (!kw || p.code.toLowerCase().includes(kw) || p.name.toLowerCase().includes(kw)))
    const start = (params.page - 1) * params.size
    return {
      records: filtered.slice(start, start + params.size).map((p) => ({
        code: p.code, name: p.name, statusLabel: projectStatusLabel[p.status] || p.status, raw: p
      })),
      total: filtered.length
    }
  } catch { return { records: [], total: 0 } }
}

async function loadOwnerPicker(params: PickerLoadParams): Promise<PickerResult> {
  try {
    const all = await fetchActiveEmployees() || []
    const kw = params.keyword.trim().toLowerCase()
    const orgNameOf = (orgId?: string) => orgId ? (orgNodes.value.find((n) => n.id === orgId)?.name || '') : ''
    const filtered = all.filter((e) => !kw || e.name.toLowerCase().includes(kw) || (e.empNo || '').toLowerCase().includes(kw))
    const start = (params.page - 1) * params.size
    return {
      records: filtered.slice(start, start + params.size).map((e: OrgEmployeeVO) => ({
        name: e.name, empNo: e.empNo, orgName: orgNameOf(e.orgId), orgId: e.orgId, raw: e
      })),
      total: filtered.length
    }
  } catch { return { records: [], total: 0 } }
}

function onPartnerPicked(row: PickerRow) { createForm.partnerCode = row.code; createForm.partnerName = row.name }
function onProjectPicked(row: PickerRow) { createForm.projectNo = row.code; createForm.projectName = row.name }
function onOwnerPicked(row: PickerRow) {
  createForm.owner = row.name
  createForm.dept = row.orgName || ''
}

function validate(): boolean {
  if (!createForm.name?.trim()) { message.warning('请填写合同名称'); return false }
  if (!createForm.partnerCode) { message.warning(`请选择${partnerLabel.value}`); return false }
  if (!createForm.projectNo) { message.warning('请选择所属项目'); return false }
  if (!createForm.owner) { message.warning('请选择负责人'); return false }
  if (!createForm.dept) { message.warning('负责人未维护所属部门，无法带出归属部门'); return false }
  if (!createForm.signedDate || !createForm.effectiveDate || !createForm.endDate) {
    message.warning('请选择签订日期、生效日期与到期日期'); return false
  }
  if (createForm.note && createForm.note.length > 200) { message.warning('备注最多 200 字'); return false }
  return true
}

function close() { emit('update:open', false) }

async function submit() {
  if (!validate()) return
  saving.value = true
  try {
    const payload = { ...createForm, name: createForm.name.trim() }
    if (props.contract) {
      await updateContract(props.contract.id, payload)
      close()
      message.success('合同主信息已保存')
      emit('saved', props.contract)
    } else if (props.convertPlan) {
      const saved = await convertPurchasePlan(props.convertPlan.id, payload)
      close()
      message.success(`已生成采购合同 ${saved.no}，明细已带入合同清单，请继续维护`)
      emit('saved', saved)
    } else {
      const saved = await saveContract(payload)
      close()
      message.success('合同已创建，请在编辑页维护清单与付款计划')
      emit('saved', saved)
    }
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}

/** 编辑页提交审批：必须有清单与付款节点，先保存主信息再走状态机；所有合同一律进审批中心等回调生效 */
async function submitAndApprove() {
  if (!props.contract || !validate()) return
  // 清单是合同金额来源，空清单不允许提交；付款计划非必须
  try {
    const items = await listItems(props.contract.no)
    if (!items?.length) { message.warning('请先维护合同清单，再提交审批'); return }
    const terms = await listPaymentTerms(props.contract.no)
    if (!terms?.length) { message.warning('请先配置付款节点，再提交审批（比例合计 100%，须含验收款）'); return }
  } catch (e: any) { message.error(e?.message || '检查合同清单/付款节点失败'); return }
  Modal.confirm({
    centered: true,
    title: '提交审批',
    content: '提交后将进入审批流程，由审批中心审批通过后合同才生效，确认吗？',
    onOk: async () => {
      saving.value = true
      try {
        const id = props.contract!.id
        await updateContract(id, { ...createForm, name: createForm.name.trim() })
        await changeContractStatus(id, 'PENDING')
        close()
        message.success('合同已提交审批')
        emit('saved', props.contract!)
      } catch (e: any) { message.error(e?.message || '提交审批失败') }
      finally { saving.value = false }
    }
  })
}

onMounted(() => { loadOrgDirectory() })
</script>

<template>
  <a-drawer :open="open" :title="drawerTitle" :width="drawerWidth" placement="right" @update:open="emit('update:open', $event)">
    <a-form layout="vertical">
      <a-card class="panel-card" size="small" title="合同主信息">
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="合同类型"><a-select v-model:value="createForm.type" :options="typeOptions" :disabled="isEdit || isConvert || !!defaultType" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="合同名称" required><a-input v-model:value="createForm.name" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item :label="partnerLabel" required>
              <a-input :value="createForm.partnerName" readonly placeholder="点击选择"
                       style="width: 100%; cursor: pointer" @click="partnerPickerOpen = true" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="所属项目" required>
              <a-input :value="createForm.projectName" readonly placeholder="点击选择"
                       style="width: 100%; cursor: pointer" @click="projectPickerOpen = true" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="归属部门" required>
              <a-input v-model:value="createForm.dept" readonly placeholder="选择负责人后自动带出，不可修改" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="负责人" required>
              <a-input :value="createForm.owner" readonly placeholder="点击选择"
                       style="width: 100%; cursor: pointer" @click="ownerPickerOpen = true" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="签订日期" required>
              <a-date-picker v-model:value="createForm.signedDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="生效日期" required>
              <a-date-picker v-model:value="createForm.effectiveDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="到期日期" required>
              <a-date-picker v-model:value="createForm.endDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="质保到期日">
              <a-date-picker v-model:value="createForm.warrantyDueDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD"
                             placeholder="质保金节点需填，到期后可申请" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注"><a-input v-model:value="createForm.note" placeholder="选填，最多 200 字" :maxlength="200" show-count /></a-form-item>
        <AttachmentUpload v-if="store.can('contract:ledger:attachment:upload')" v-model="createForm.fileIds" label="合同附件" button-text="上传合同附件"
                          :upload="(file) => uploadContractAttachment(file, 'contract')" @error="message.error" />
      </a-card>

      <template v-if="isEdit && contract">
        <div class="drawer-section-title">合同清单</div>
        <ContractItemsEditor :contract-no="contract.no" :contract-status="contract.status" :contract-type="contract.type"
                             @changed="itemsVersion++" />

        <PaymentTermsEditor :contract-no="contract.no" :contract-status="contract.status" :items-version="itemsVersion" />
      </template>

      <template v-else-if="isConvert">
        <div class="drawer-section-title">合同清单</div>
        <div class="plan-items-preview">
          <a-table class="ft-table" row-key="id" size="small" :columns="planItemColumns" :data-source="planItems"
                   :pagination="false" :scroll="planItems.length ? { x: 720 } : undefined">
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'lineNo'">{{ index + 1 }}</template>
              <template v-else-if="column.key === 'materialName'">
                <a-tooltip :title="`${record.materialName} ${record.spec || ''}`"><span class="cell-ellipsis">{{ record.materialName }}</span></a-tooltip>
              </template>
              <template v-else-if="column.key === 'price' || column.key === 'amount'">{{ yuan(record[column.key]) }}</template>
            </template>
            <template #emptyText><a-empty description="该采购计划暂无明细" /></template>
          </a-table>
          <div class="plan-items-total">清单合计：<b>{{ yuan(planItemsTotal) }}</b></div>
        </div>
      </template>
    </a-form>
    <template #footer>
      <div class="drawer-footer">
        <a-button @click="close">取消</a-button>
        <a-button v-if="isEdit" :loading="saving" @click="submitAndApprove">提交审批</a-button>
        <a-button type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存修改' : isConvert ? '生成采购合同' : '保存合同' }}</a-button>
      </div>
    </template>
  </a-drawer>

  <EntityPickerModal v-model:open="partnerPickerOpen" :title="`选择${partnerLabel}`" :columns="partnerColumns"
                     row-key="code" :initial-value="createForm.partnerCode" placeholder="编码 / 名称"
                     :load="loadPartnerPicker" @confirm="onPartnerPicked" />
  <EntityPickerModal v-model:open="projectPickerOpen" title="选择所属项目" :columns="projectColumns"
                     row-key="code" :initial-value="createForm.projectNo" placeholder="项目编号 / 名称"
                     :load="loadProjectPicker" @confirm="onProjectPicked" />
  <EntityPickerModal v-model:open="ownerPickerOpen" title="选择负责人" :columns="ownerColumns"
                     row-key="name" :initial-value="createForm.owner" placeholder="姓名 / 工号（自动带出部门）"
                     :load="loadOwnerPicker" @confirm="onOwnerPicked" />
</template>

<style scoped>
.plan-items-preview { margin-top: 4px; }
.plan-items-total { text-align: right; padding: 8px 0; font-size: 13px; color: var(--text-color, var(--color-text-primary)); }
</style>
