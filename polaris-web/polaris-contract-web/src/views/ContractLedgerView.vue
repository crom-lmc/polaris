<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import {
  listContracts, getContractStats, deleteContract, changeContractStatus,
  getProjectOptions, listPartners,
  type ContractVO, type ContractStats, type ProjectOptionVO
} from '@/api/contract'
import { yuan } from '@/utils/format'
import ContractDetailDrawer from '@/components/ContractDetailDrawer.vue'
import ContractFormDrawer from '@/components/ContractFormDrawer.vue'
import ContractItemsEditor from '@/components/ContractItemsEditor.vue'
import { useIsMobile } from '@/composables/useIsMobile'

const store = useUserStore()
const route = useRoute()

/** 合同台账按菜单拆分为采购/销售两个入口，类型由当前路由固定，不再由用户切换 */
const fixedType = computed(() => route.meta.contractType as 'PURCHASE' | 'SALES' | undefined)
const partnerLabel = computed(() => fixedType.value === 'SALES' ? '客户' : '供应商')

const loading = ref(false)
const rows = ref<ContractVO[]>([])
const total = ref(0)
const stats = ref<ContractStats>({ total: 0, amount: 0, paidAmount: 0, inFlightAmount: 0, pendingAmount: 0 })
const query = reactive({
  type: fixedType.value, status: undefined as string | undefined,
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
const statusOptions = Object.entries(statusLabel).map(([value, label]) => ({ value, label }))

interface StatusAction { label: string; target: string; kind?: 'primary' | 'danger' }
/** 状态流转确认文案；危险动作先确认，再调状态机接口 */
const statusActionTips: Record<string, string> = {
  PENDING: '提交后将按合同金额与类型进入审批流程（未达审批线的采购合同直接生效），确认吗？',
  EFFECTIVE: '确认审批通过并将合同置为已生效？',
  DRAFT: '退回后合同变为草稿，可继续编辑，确认吗？',
  TERMINATED: '确认终止合同？终止后不可再恢复。'
}

const projectOptions = ref<{ label: string; value: string }[]>([])
const partnerOptions = ref<{ label: string; value: string }[]>([])
const sourceLabel: Record<string, string> = { DIRECT: '直接新建', FROM_PLAN: '计划转入' }

/** 状态是流程结果，详情只读；流转按钮收敛到列表操作行，按当前状态给出明确动作 */
function statusActionsOf(row: ContractVO): StatusAction[] {
  if (row.status === 'DRAFT') return [{ label: '提交审批', target: 'PENDING', kind: 'primary' }]
  if (row.status === 'PENDING') {
    return row.type === 'SALES'
      ? [{ label: '审批通过', target: 'EFFECTIVE', kind: 'primary' }, { label: '退回草稿', target: 'DRAFT' }]
      : []
  }
  // 执行中 / 已完成由业务动作自动推进：首次验收 → 执行中，付款结清 → 完成；此处只保留终止
  if (row.status === 'EFFECTIVE' || row.status === 'EXECUTING') {
    return [{ label: '终止合同', target: 'TERMINATED', kind: 'danger' }]
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
    const res = await listPartners({
      role: fixedType.value === 'SALES' ? 'CUSTOMER' : 'SUPPLIER', page: 1, size: 200
    })
    partnerOptions.value = (res.records || []).map((p) => ({ label: `${p.code} · ${p.name}`, value: p.code }))
  } catch { partnerOptions.value = [] }
}

const columns = computed(() => [
  { title: '合同编号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '类型', key: 'type', align: 'center' as const, width: 100 },
  { title: '来源', key: 'contractSource', align: 'center' as const, width: 90 },
  { title: '合同名称', key: 'name', width: 230 },
  { title: partnerLabel.value, key: 'partnerName', width: 180 },
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
])

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

/* 详情：纯查看，复用公共合同详情抽屉 */
const detailOpen = ref(false)
const detailId = ref<string | null>(null)
function openDetail(id: string) { detailId.value = id; detailOpen.value = true }

/* 验收：已生效/执行中的合同通过独立抽屉办验收（复用清单编辑器，仅开放验收按钮） */
const isMobile = useIsMobile()
const acceptDrawerWidth = computed(() => (isMobile.value ? '100%' : 900))
const acceptOpen = ref(false)
const acceptContract = ref<ContractVO | null>(null)
function openAccept(row: ContractVO) { acceptContract.value = row; acceptOpen.value = true }
async function doStatus(id: string, status: string) {
  try {
    await changeContractStatus(id, status)
    message.success('合同状态已更新')
    load()
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
// 两个菜单复用同一个组件实例，切换路由时按新类型重新查询
watch(() => route.path, () => {
  query.type = fixedType.value
  query.page = 1
  query.partnerCode = undefined
  load()
  loadOptions()
})

onMounted(() => { load(); loadOptions() })
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <a-input-search v-model:value="query.keyword" :placeholder="`编号 / 名称 / ${partnerLabel}`" style="width: 240px" allow-clear @search="resetAndLoad" />
      <a-select v-model:value="query.status" :options="statusOptions" placeholder="合同状态" style="width: 120px" allow-clear @change="resetAndLoad" />
      <a-select v-model:value="query.projectNo" :options="projectOptions" placeholder="所属项目" style="width: 180px" allow-clear show-search @change="resetAndLoad" />
      <a-select v-model:value="query.partnerCode" :options="partnerOptions" :placeholder="partnerLabel" style="width: 170px" allow-clear show-search @change="resetAndLoad" />
      <span class="grow" />
      <a-button v-if="store.can('contract:ledger:edit')" type="primary" @click="openCreate">＋ 新增{{ fixedType === 'SALES' ? '销售合同' : '采购合同' }}</a-button>
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
              <a v-if="store.can('contract:item:edit') && ['EFFECTIVE', 'EXECUTING'].includes(record.status) && !record.acceptanceDone" @click="openAccept(record)">验收</a>
              <template v-if="store.can('contract:ledger:edit')">
                <a v-for="action in statusActionsOf(record)" :key="action.target" @click="requestStatusAction(action, record)">{{ action.label }}</a>
              </template>
              <a v-if="store.can('contract:ledger:edit') && record.status === 'DRAFT'" @click="remove(record)">删除</a>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无合同" /></div></template>
      </a-table>
    </section>

    <ContractDetailDrawer v-model:open="detailOpen" :contract-id="detailId" />

    <a-drawer v-model:open="acceptOpen" title="合同验收 · 清单交付" :width="acceptDrawerWidth" placement="right">
      <template v-if="acceptContract">
        <a-alert type="info" show-icon style="margin-bottom: 12px"
                 :message="`${acceptContract.no} · ${acceptContract.name}`"
                 description="已生效/执行中的合同可登记到货验收；清单已锁定，如需变更请走合同变更流程。" />
        <ContractItemsEditor :contract-no="acceptContract.no" :contract-status="acceptContract.status" />
      </template>
      <template #footer><div class="drawer-footer"><a-button @click="acceptOpen = false">关闭</a-button></div></template>
    </a-drawer>

    <ContractFormDrawer v-model:open="createOpen" :contract="editingContract" :default-type="fixedType" @saved="onFormSaved" />
  </section>
</template>
