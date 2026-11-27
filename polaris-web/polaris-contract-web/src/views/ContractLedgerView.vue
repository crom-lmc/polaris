<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import { useOrgDirectory } from '@/composables/useOrgDirectory'
import {
  listContracts, getContractStats, getContract, saveContract, updateContract, deleteContract, changeContractStatus,
  listContractChanges, getProjectOptions, listPartners, uploadContractAttachment, fetchActiveEmployees,
  type ContractVO, type ContractSave, type ContractStats, type ProjectOptionVO, type ChangeLogVO, type PartnerVO, type OrgEmployeeVO
} from '@/api/contract'
import { yuan } from '@/utils/format'
import ContractItemsEditor from '@/components/ContractItemsEditor.vue'
import PaymentPlanEditor from '@/components/PaymentPlanEditor.vue'
import AttachmentUpload from '@shared/components/AttachmentUpload.vue'
import EntityPickerModal, { type PickerColumn, type PickerRow, type PickerLoadParams, type PickerResult } from '@shared/components/EntityPickerModal.vue'

const store = useUserStore()
const isMobile = useIsMobile()
const { orgNodes, load: loadOrgDirectory } = useOrgDirectory()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 900))
const descColumn = computed(() => (isMobile.value ? 1 : 2))

const loading = ref(false)
const rows = ref<ContractVO[]>([])
const total = ref(0)
const stats = ref<ContractStats>({ total: 0, amount: 0, paidAmount: 0, inFlightAmount: 0, pendingAmount: 0 })
const query = reactive({
  type: undefined as string | undefined, status: undefined as string | undefined,
  keyword: '', projectNo: undefined as string | undefined, partnerCode: undefined as string | undefined,
  page: 1, size: 20
})

const typeLabel: Record<string, string> = { PURCHASE: '采购合同', SALES: '销售合同' }
const statusLabel: Record<string, string> = {
  DRAFT: '草稿', PENDING: '审批中', EFFECTIVE: '已生效', EXECUTING: '执行中', COMPLETED: '已完成', TERMINATED: '已终止'
}
const statusColor: Record<string, string> = {
  DRAFT: 'default', PENDING: 'orange', EFFECTIVE: 'blue', EXECUTING: 'geekblue', COMPLETED: 'green', TERMINATED: 'red'
}
const typeOptions = [{ label: '采购合同', value: 'PURCHASE' }, { label: '销售合同', value: 'SALES' }]
const statusOptions = Object.entries(statusLabel).map(([value, label]) => ({ value, label }))

const projectOptions = ref<{ label: string; value: string }[]>([])
const partnerOptions = ref<{ label: string; value: string }[]>([])
const sourceLabel: Record<string, string> = { DIRECT: '直接新建', FROM_PLAN: '计划转入' }
const changeReason = ref('')

/** 合同执行闭环步骤条（与设计稿 contractFlowSteps / salesFlowSteps 对齐） */
const flowSteps = computed(() => detail.value?.type === 'SALES'
  ? ['销售合同', '合同清单', '验收', '应收单', '凭证', '账表']
  : ['采购计划', '合同与清单', '付款计划', '应付单', '凭证', '账表'])
const flowCurrent = computed(() => {
  const s = detail.value?.status
  if (!s) return 0
  if (s === 'DRAFT') return 1
  if (s === 'PENDING' || s === 'EFFECTIVE') return 2
  if (s === 'EXECUTING') return 3
  return 4
})

const pagination = computed(() => ({
  current: query.page, pageSize: query.size, total: total.value, showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`, onChange: (p: number, size: number) => {
    query.page = p; query.size = size; load()
  }
}))

async function load() {
  loading.value = true
  try {
    const [res, st] = await Promise.all([
      listContracts({
        type: query.type, status: query.status, keyword: query.keyword || undefined,
        projectNo: query.projectNo, partnerCode: query.partnerCode, page: query.page, size: query.size
      }),
      getContractStats(query.type)
    ])
    rows.value = res.records || []
    total.value = res.total || 0
    stats.value = st
  } catch (e: any) { message.error(e?.message || '加载合同台账失败') }
  finally { loading.value = false }
}
function resetAndLoad() { query.page = 1; load() }
async function loadOptions() {
  try {
    const ps: ProjectOptionVO[] = await getProjectOptions()
    projectOptions.value = (ps || []).map((p) => ({ label: `${p.code} · ${p.name}`, value: p.code }))
  } catch { projectOptions.value = [] }
  try {
    const res = await listPartners({ page: 1, size: 200 })
    partnerOptions.value = (res.records || []).map((p) => ({ label: `${p.code} · ${p.name}`, value: p.code }))
  } catch { partnerOptions.value = [] }
}

const columns = [
  { title: '合同编号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '类型', key: 'type', align: 'center' as const, width: 100 },
  { title: '来源', key: 'contractSource', align: 'center' as const, width: 90 },
  { title: '合同名称', key: 'name', width: 230 },
  { title: '供应商/客户', key: 'partnerName', width: 180 },
  { title: '所属项目', key: 'projectName', width: 180 },
  { title: '归属部门', dataIndex: 'dept', key: 'dept', width: 100 },
  { title: '合同金额', key: 'amount', align: 'right' as const, width: 130 },
  { title: '已入账', key: 'paidAmount', align: 'right' as const, width: 120 },
  { title: '在途应付', key: 'inFlightAmount', align: 'right' as const, width: 120 },
  { title: '待支付', key: 'pendingAmount', align: 'right' as const, width: 120 },
  { title: '执行进度', key: 'progress', width: 160 },
  { title: '状态', key: 'status', align: 'center' as const, width: 100 },
  { title: '到期日期', dataIndex: 'endDate', key: 'endDate', width: 110 },
  { title: '附件', key: 'attachments', align: 'center' as const, width: 110 },
  { title: '操作', key: 'action', width: 130 }
]

/* ========== 主数据弹窗选择（往来单位 / 所属项目 / 负责人） ========== */
const partnerPickerOpen = ref(false)
const projectPickerOpen = ref(false)
const ownerPickerOpen = ref(false)

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
const roleLabel: Record<string, string> = { SUPPLIER: '供应商', CUSTOMER: '客户', BOTH: '供应商/客户' }
const projectStatusLabel: Record<string, string> = { ACTIVE: '启用', DRAFT: '草稿', PAUSED: '暂停', CLOSED: '关闭', CANCELLED: '取消' }

/** 往来单位按合同类型过滤角色：采购合同只给供应商/两者，销售合同只给客户/两者 */
async function loadPartnerPicker(params: PickerLoadParams): Promise<PickerResult> {
  const role = createForm.type === 'SALES' ? 'CUSTOMER' : 'SUPPLIER'
  try {
    const res = await listPartners({ role, status: 'COOPERATING', keyword: params.keyword || undefined, page: params.page, size: params.size })
    const records = (res.records || []).map((p: PartnerVO) => ({
      code: p.code, name: p.name, roleLabel: roleLabel[p.role] || p.role,
      statusLabel: '合作中', raw: p
    }))
    return { records, total: res.total || 0 }
  } catch { return { records: [], total: 0 } }
}

/** 所属项目：可用项目（启用状态）本地过滤 + 分页 */
async function loadProjectPicker(params: PickerLoadParams): Promise<PickerResult> {
  try {
    const all = await getProjectOptions() || []
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

/** 负责人：在职员工本地过滤 + 分页，带所属部门供自动带出 */
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

function onPartnerPicked(row: PickerRow) {
  createForm.partnerCode = row.code
  createForm.partnerName = row.name
}
function onProjectPicked(row: PickerRow) {
  createForm.projectNo = row.code
  createForm.projectName = row.name
}
function onOwnerPicked(row: PickerRow) {
  createForm.owner = row.name
  // 选负责人自动带出所属部门，且不允许修改
  createForm.dept = row.orgName || ''
}

/** 合同类型切换：往来单位角色随类型变化，清空已选避免角色错配 */
watch(() => createForm.type, () => {
  createForm.partnerCode = ''
  createForm.partnerName = ''
})
const partnerLabel = computed(() => createForm.type === 'SALES' ? '客户' : '供应商')

/* 新增合同 */
const createOpen = ref(false)
const saving = ref(false)
const createForm = reactive<ContractSave & { partnerName?: string; projectName?: string }>({
  type: 'PURCHASE', name: '', partnerCode: '', partnerName: '', projectNo: undefined, projectName: '', dept: '', owner: '',
  signedDate: undefined, effectiveDate: undefined, endDate: undefined, paymentTerms: '', subject: '', note: '',
  fileIds: []
})
function resetCreate() {
  Object.assign(createForm, {
    type: 'PURCHASE', name: '', partnerCode: '', partnerName: '', projectNo: undefined, projectName: '', dept: '', owner: '',
    signedDate: undefined, effectiveDate: undefined, endDate: undefined, paymentTerms: '', subject: '', note: '',
    fileIds: []
  })
}
function openCreate() { resetCreate(); createOpen.value = true }
async function submitCreate() {
  if (!createForm.name?.trim()) { message.warning('请填写合同名称'); return }
  if (!createForm.partnerCode) { message.warning(`请选择${partnerLabel.value}`); return }
  if (!createForm.projectNo) { message.warning('请选择所属项目'); return }
  if (!createForm.owner) { message.warning('请选择负责人'); return }
  if (!createForm.dept) { message.warning('负责人未维护所属部门，无法带出归属部门'); return }
  if (!createForm.signedDate || !createForm.effectiveDate || !createForm.endDate) {
    message.warning('请选择签订日期、生效日期与到期日期'); return
  }
  if (createForm.note && createForm.note.length > 200) { message.warning('备注最多 200 字'); return }
  saving.value = true
  try {
    await saveContract({ ...createForm, name: createForm.name.trim() })
    createOpen.value = false
    message.success('合同已创建，可在详情中维护清单与付款计划')
    resetAndLoad()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}

/* 详情 */
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<ContractVO | null>(null)
async function openDetail(id: string) {
  detailOpen.value = true
  detailLoading.value = true
  try { detail.value = await getContract(id) }
  catch (e: any) { message.error(e?.message || '加载详情失败') }
  finally { detailLoading.value = false }
}
/* 变更记录 */
const changeOpen = ref(false)
const changeLoading = ref(false)
const changes = ref<ChangeLogVO[]>([])
async function openChanges() {
  if (!detail.value) return
  changeOpen.value = true
  changeLoading.value = true
  try { changes.value = await listContractChanges(detail.value.id) }
  catch (e: any) { message.error(e?.message || '加载变更记录失败') }
  finally { changeLoading.value = false }
}
async function changeProject(projectNo: string | undefined) {
  if (!detail.value) return
  try {
    await updateContract(detail.value.id, {
      projectNo: projectNo || undefined, name: detail.value.name, partnerCode: detail.value.partnerCode,
      remark: changeReason.value || undefined
    })
    detail.value = await getContract(detail.value.id)
    changeReason.value = ''
    message.success('所属项目已更新')
    load()
  } catch (e: any) { message.error(e?.message || '更新所属项目失败') }
}
async function doStatus(status: string) {
  if (!detail.value) return
  try {
    await changeContractStatus(detail.value.id, status)
    detail.value = await getContract(detail.value.id)
    message.success('合同状态已更新')
    load()
  } catch (e: any) { message.error(e?.message || '状态更新失败') }
}
function remove(row: ContractVO) {
  Modal.confirm({
    centered: true, title: '删除合同', content: `确定删除「${row.name}」吗？已有清单或付款计划的合同不可删除。`,
    okButtonProps: { danger: true },
    onOk: async () => { try { await deleteContract(row.id); message.success('已删除'); load() } catch (e: any) { message.error(e?.message || '删除失败') } }
  })
}
onMounted(() => { load(); loadOptions(); loadOrgDirectory() })
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <a-select v-model:value="query.type" :options="typeOptions" placeholder="全部类型" style="width: 140px" allow-clear @change="resetAndLoad" />
      <a-input-search v-model:value="query.keyword" placeholder="编号 / 名称 / 供应商 / 客户" style="width: 240px" allow-clear @search="resetAndLoad" />
      <a-select v-model:value="query.status" :options="statusOptions" placeholder="合同状态" style="width: 120px" allow-clear @change="resetAndLoad" />
      <a-select v-model:value="query.projectNo" :options="projectOptions" placeholder="所属项目" style="width: 180px" allow-clear show-search @change="resetAndLoad" />
      <a-select v-model:value="query.partnerCode" :options="partnerOptions" placeholder="往来单位" style="width: 170px" allow-clear show-search @change="resetAndLoad" />
      <span class="grow" />
      <a-button v-if="store.can('contract:ledger:edit')" type="primary" @click="openCreate">＋ 新增合同</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <div class="stat-grid">
        <div class="stat-card"><span class="st-label">合同总额</span><strong class="st-value">{{ yuan(stats.amount) }}</strong><span class="st-foot"><b>{{ stats.total }} 份合同</b><span class="muted">按清单合计</span></span></div>
        <div class="stat-card"><span class="st-label">已入账付款</span><strong class="st-value">{{ yuan(stats.paidAmount) }}</strong><span class="st-foot"><b>{{ yuan(stats.pendingAmount) }} 待支付</b></span></div>
        <div class="stat-card"><span class="st-label">在途应付</span><strong class="st-value">{{ yuan(stats.inFlightAmount) }}</strong><span class="st-foot"><b>审批中 / 已审批</b></span></div>
        <div class="stat-card"><span class="st-label">待支付</span><strong class="st-value">{{ yuan(stats.pendingAmount) }}</strong><span class="st-foot"><b>合同金额减已付与在途</b></span></div>
      </div>
      <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :loading="loading"
               :pagination="pagination" :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :scroll="rows.length ? { x: 2150 } : undefined" style="--ft-min-width: 2150px">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-tooltip :title="record.name"><span class="cell-ellipsis">{{ record.name }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'partnerName'">
            <a-tooltip :title="record.partnerName"><span class="cell-ellipsis">{{ record.partnerName || '-' }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'projectName'">
            <a-tooltip v-if="record.projectName" :title="`${record.projectNo} · ${record.projectName}`">
              <span class="cell-ellipsis">{{ record.projectName }}</span>
            </a-tooltip>
            <span v-else class="muted">未关联</span>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag :color="record.type === 'PURCHASE' ? 'geekblue' : 'purple'">{{ typeLabel[record.type] || record.type }}</a-tag>
          </template>
          <template v-else-if="column.key === 'contractSource'">
            <a-tag :color="record.contractSource === 'FROM_PLAN' ? 'blue' : 'default'">
              {{ sourceLabel[record.contractSource] || record.contractSource || '直接新建' }}
            </a-tag>
          </template>
          <template v-else-if="['amount', 'paidAmount', 'inFlightAmount', 'pendingAmount'].includes(String(column.key))">
            {{ yuan(record[column.key as 'amount' | 'paidAmount' | 'inFlightAmount' | 'pendingAmount']) }}
          </template>
          <template v-else-if="column.key === 'progress'">
            <div class="rate-cell">
              <a-progress :percent="Math.min(100, Number(record.progress || 0))" size="small"
                          :stroke-color="Number(record.progress || 0) >= 100 ? '#52a563' : '#2f54eb'" />
              <span class="rate-text">{{ record.progress }}%</span>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor[record.status] || 'default'">{{ statusLabel[record.status] || record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'attachments'">
            <a v-if="record.fileIds?.length && record.attachmentUrls?.[0]" :href="record.attachmentUrls[0]" target="_blank">附件（{{ record.fileIds.length }}）</a>
            <span v-else-if="record.fileIds?.length" class="muted">{{ record.fileIds.length }} 个</span>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a @click="openDetail(record.id)">详情 / 清单</a>
              <a v-if="store.can('contract:ledger:edit')" @click="remove(record)">删除</a>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无合同" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="detailOpen" title="合同详情 · 清单 · 付款计划" :width="drawerWidth" placement="right">
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-steps :current="flowCurrent" size="small" style="margin-bottom: 16px"
                   :items="flowSteps.map((title) => ({ title }))" />
          <a-card class="panel-card" size="small" title="合同信息">
            <a-descriptions :column="descColumn" bordered size="small">
              <a-descriptions-item label="合同编号">{{ detail.no }}</a-descriptions-item>
              <a-descriptions-item label="合同名称">{{ detail.name }}</a-descriptions-item>
              <a-descriptions-item label="类型">{{ typeLabel[detail.type] || detail.type }}</a-descriptions-item>
              <a-descriptions-item label="供应商/客户">{{ detail.partnerName || detail.partnerCode }}</a-descriptions-item>
              <a-descriptions-item label="归属部门">{{ detail.dept || '-' }} · {{ detail.owner || '-' }}</a-descriptions-item>
              <a-descriptions-item label="签订 / 生效 / 到期">{{ detail.signedDate || '-' }} / {{ detail.effectiveDate || '-' }} / {{ detail.endDate || '-' }}</a-descriptions-item>
              <a-descriptions-item label="付款条款">{{ detail.paymentTerms || '-' }}</a-descriptions-item>
              <a-descriptions-item label="合同标的">{{ detail.subject || '-' }}</a-descriptions-item>
              <a-descriptions-item label="备注">{{ detail.note || '-' }}</a-descriptions-item>
              <a-descriptions-item label="来源">
                <a-tag :color="detail.contractSource === 'FROM_PLAN' ? 'blue' : 'default'">
                  {{ sourceLabel[detail.contractSource] || detail.contractSource || '直接新建' }}
                </a-tag>
                <span v-if="detail.planNo" class="muted" style="margin-left: 8px">计划号 {{ detail.planNo }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="合同金额">{{ yuan(detail.amount) }}</a-descriptions-item>
              <a-descriptions-item label="已入账 / 在途 / 待支付">{{ yuan(detail.paidAmount) }} / {{ yuan(detail.inFlightAmount) }} / {{ yuan(detail.pendingAmount) }}</a-descriptions-item>
              <a-descriptions-item label="执行进度">{{ detail.progress }}%</a-descriptions-item>
              <a-descriptions-item label="变更次数">
                {{ detail.changeCount || 0 }} 次
                <a v-if="detail.changeCount" @click="openChanges">查看记录</a>
              </a-descriptions-item>
              <a-descriptions-item label="状态">
                <a-select v-if="store.can('contract:ledger:edit')" :value="detail.status" :options="statusOptions" size="small"
                          style="width: 120px" @change="doStatus" />
                <a-tag v-else :color="statusColor[detail.status] || 'default'">{{ statusLabel[detail.status] || detail.status }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="合同附件">
                <template v-if="detail.fileIds?.length">
                  <a v-for="(url, idx) in detail.attachmentUrls || []" :key="url" :href="url" target="_blank" style="margin-right: 12px">附件 {{ idx + 1 }}</a>
                  <span v-if="!detail.attachmentUrls?.length" class="muted">{{ detail.fileIds.length }} 个（签名链接不可用）</span>
                </template>
                <span v-else class="muted">无附件</span>
              </a-descriptions-item>
            </a-descriptions>
            <a-form-item label="所属项目" style="margin-top: 12px; margin-bottom: 0">
              <a-select v-if="store.can('contract:ledger:edit')" :value="detail.projectNo || undefined" :options="projectOptions"
                        placeholder="请选择所属项目" allow-clear show-search style="width: 100%" @change="changeProject" />
              <span v-else>{{ detail.projectName || '未关联' }}</span>
            </a-form-item>
            <a-form-item v-if="store.can('contract:ledger:edit')" label="变更原因" style="margin-bottom: 0">
              <a-input v-model:value="changeReason" placeholder="变更所属项目时填写，用于留痕（选填）" allow-clear />
            </a-form-item>
          </a-card>

          <div class="drawer-section-title">合同清单</div>
          <ContractItemsEditor v-if="detail" :contract-no="detail.no" />

          <div class="drawer-section-title">付款计划</div>
          <PaymentPlanEditor v-if="detail" :contract-no="detail.no" :remaining-amount="detail.pendingAmount" />
        </template>
      </a-spin>
      <template #footer>
        <div class="drawer-footer"><a-button @click="detailOpen = false">关闭</a-button></div>
      </template>
    </a-drawer>

    <a-modal v-model:open="changeOpen" title="合同变更记录" :footer="null" :width="720">
      <a-spin :spinning="changeLoading">
        <a-table :data-source="changes" row-key="id" size="small" :pagination="false" :scroll="{ x: 760, y: 420 }">
          <a-table-column title="变更单号" data-index="no" key="no" width="170" />
          <a-table-column title="变更类型" data-index="changeType" key="changeType" width="100" />
          <a-table-column title="变更原因" data-index="reason" key="reason">
            <template #default="{ record }">
              <a-tooltip :title="record.reason"><span class="cell-ellipsis">{{ record.reason || '-' }}</span></a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="操作人" data-index="operator" key="operator" width="120" />
          <a-table-column title="时间" data-index="createdAt" key="createdAt" width="160" />
        </a-table>
        <div v-if="!changes.length && !changeLoading" class="panel-hint" style="padding: 16px 0; text-align: center">暂无变更记录</div>
      </a-spin>
    </a-modal>

    <a-drawer v-model:open="createOpen" title="新增合同" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-card class="panel-card" size="small" title="合同主信息">
          <a-row :gutter="12">
            <a-col :span="12"><a-form-item label="合同类型"><a-select v-model:value="createForm.type" :options="typeOptions" /></a-form-item></a-col>
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
          <a-form-item label="付款条款"><a-input v-model:value="createForm.paymentTerms" /></a-form-item>
          <a-form-item label="合同标的"><a-input v-model:value="createForm.subject" /></a-form-item>
          <a-form-item label="备注"><a-input v-model:value="createForm.note" placeholder="选填，最多 200 字" :maxlength="200" show-count /></a-form-item>
          <AttachmentUpload v-if="store.can('contract:ledger:attachment:upload')" v-model="createForm.fileIds" label="合同附件" button-text="上传合同附件"
                            :upload="(file) => uploadContractAttachment(file, 'contract')" @error="message.error" />
        </a-card>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="createOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submitCreate">保存合同</a-button>
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
  </section>
</template>
