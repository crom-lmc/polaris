<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  listContracts, getContractStats, getContract, deleteContract, changeContractStatus,
  listContractChanges, getProjectOptions, listPartners,
  type ContractVO, type ContractStats, type ProjectOptionVO, type ChangeLogVO
} from '@/api/contract'
import { yuan } from '@/utils/format'
import ContractItemsEditor from '@/components/ContractItemsEditor.vue'
import PaymentPlanEditor from '@/components/PaymentPlanEditor.vue'
import ContractFormDrawer from '@/components/ContractFormDrawer.vue'

const store = useUserStore()
const isMobile = useIsMobile()
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

interface StatusAction { label: string; target: string; kind?: 'primary' | 'danger' }
/** 状态流转确认文案；危险动作先确认，再调状态机接口 */
const statusActionTips: Record<string, string> = {
  PENDING: '提交后将按合同金额与类型进入审批流程（未达审批线的采购合同直接生效），确认吗？',
  EFFECTIVE: '确认审批通过并将合同置为已生效？',
  DRAFT: '退回后合同变为草稿，可继续编辑，确认吗？',
  EXECUTING: '确认合同开始执行？',
  COMPLETED: '确认合同已完成？完成后不可再变更状态。',
  TERMINATED: '确认终止合同？终止后不可再恢复。'
}

const projectOptions = ref<{ label: string; value: string }[]>([])
const partnerOptions = ref<{ label: string; value: string }[]>([])
const sourceLabel: Record<string, string> = { DIRECT: '直接新建', FROM_PLAN: '计划转入' }

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

/** 状态是流程结果，详情只读；流转按钮收敛到列表操作行，按当前状态给出明确动作 */
function statusActionsOf(row: ContractVO): StatusAction[] {
  if (row.status === 'DRAFT') return [{ label: '提交审批', target: 'PENDING', kind: 'primary' }]
  if (row.status === 'PENDING') {
    return row.type === 'SALES'
      ? [{ label: '审批通过', target: 'EFFECTIVE', kind: 'primary' }, { label: '退回草稿', target: 'DRAFT' }]
      : []
  }
  if (row.status === 'EFFECTIVE') {
    return [{ label: '开始执行', target: 'EXECUTING', kind: 'primary' }, { label: '终止合同', target: 'TERMINATED', kind: 'danger' }]
  }
  if (row.status === 'EXECUTING') {
    return [{ label: '完成合同', target: 'COMPLETED', kind: 'primary' }, { label: '终止合同', target: 'TERMINATED', kind: 'danger' }]
  }
  return []
}

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
  { title: '操作', key: 'action', width: 270 }
]

/* 新增 / 编辑合同：公共表单抽屉 */
const createOpen = ref(false)
const editingContract = ref<ContractVO | null>(null)
function openCreate() { editingContract.value = null; createOpen.value = true }
/** 仅草稿可编辑主信息；类型不允许改，避免与往来单位角色错配 */
function openEdit(row: ContractVO) { editingContract.value = row; createOpen.value = true }
function onFormSaved(saved: ContractVO) {
  resetAndLoad()
  // 新建成功后自动进入编辑态维护清单与付款计划
  if (!editingContract.value) openEdit(saved)
}

/* 详情：纯查看，无任何写操作按钮 */
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
async function doStatus(id: string, status: string) {
  try {
    await changeContractStatus(id, status)
    message.success('合同状态已更新')
    load()
    if (detailOpen.value && detail.value?.id === id) detail.value = await getContract(id)
  } catch (e: any) { message.error(e?.message || '状态更新失败') }
}
function requestStatusAction(action: StatusAction, row: ContractVO) {
  // 提交审批必须有清单（合同金额来源），付款计划可后建不强制
  if (action.target === 'PENDING' && !Number(row.itemCount || 0)) {
    message.warning('请先维护合同清单，再提交审批')
    return
  }
  Modal.confirm({
    centered: true,
    title: action.label,
    content: statusActionTips[action.target] || '确认更新合同状态？',
    okButtonProps: action.kind === 'danger' ? { danger: true } : undefined,
    onOk: () => doStatus(row.id, action.target)
  })
}
function remove(row: ContractVO) {
  Modal.confirm({
    centered: true, title: '删除合同', content: `确定删除「${row.name}」吗？已有清单或付款计划的合同不可删除。`,
    okButtonProps: { danger: true },
    onOk: async () => { try { await deleteContract(row.id); message.success('已删除'); load() } catch (e: any) { message.error(e?.message || '删除失败') } }
  })
}
onMounted(() => { load(); loadOptions() })
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
               :scroll="rows.length ? { x: 2290 } : undefined" style="--ft-min-width: 2290px">
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
              <a-progress :percent="Math.min(100, Number(record.progress || 0))" size="small" :show-info="false"
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
              <a @click="openDetail(record.id)">查看</a>
              <a v-if="store.can('contract:ledger:edit') && record.status === 'DRAFT'" @click="openEdit(record)">编辑</a>
              <template v-if="store.can('contract:ledger:edit')">
                <a v-for="action in statusActionsOf(record)" :key="action.target" @click="requestStatusAction(action, record)">{{ action.label }}</a>
              </template>
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
              <a-descriptions-item label="所属项目">{{ detail.projectName || '未关联' }}</a-descriptions-item>
              <a-descriptions-item label="签订 / 生效 / 到期">{{ detail.signedDate || '-' }} / {{ detail.effectiveDate || '-' }} / {{ detail.endDate || '-' }}</a-descriptions-item>
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
                <a-tag :color="statusColor[detail.status] || 'default'">{{ statusLabel[detail.status] || detail.status }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="合同附件">
                <template v-if="detail.fileIds?.length">
                  <a v-for="(url, idx) in detail.attachmentUrls || []" :key="url" :href="url" target="_blank" style="margin-right: 12px">附件 {{ idx + 1 }}</a>
                  <span v-if="!detail.attachmentUrls?.length" class="muted">{{ detail.fileIds.length }} 个（签名链接不可用）</span>
                </template>
                <span v-else class="muted">无附件</span>
              </a-descriptions-item>
            </a-descriptions>
          </a-card>

          <div class="drawer-section-title">合同清单</div>
          <ContractItemsEditor v-if="detail" :contract-no="detail.no" readonly />

          <div class="drawer-section-title">付款计划</div>
          <PaymentPlanEditor v-if="detail" :contract-no="detail.no" :remaining-amount="detail.pendingAmount" readonly />
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

    <ContractFormDrawer v-model:open="createOpen" :contract="editingContract" @saved="onFormSaved" />
  </section>
</template>
