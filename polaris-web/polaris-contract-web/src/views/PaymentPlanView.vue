<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  listContracts, listPaymentPlans, savePaymentPlan, applyPaymentPlan,
  type PaymentPlanVO, type ContractVO
} from '@/api/contract'
import { yuan } from '@/utils/format'

const store = useUserStore()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 560))

const loading = ref(false)
const allRows = ref<PaymentPlanVO[]>([])
const contracts = ref<ContractVO[]>([])
const query = reactive({ contractNo: undefined as string | undefined, status: undefined as string | undefined, keyword: '' })
const localPage = ref(1)
const localSize = ref(20)

const statusLabel: Record<string, string> = {
  WAITING: '待申请', PENDING: '待审批', APPROVED: '已审批待付', POSTED: '已入账', PAID: '已付款', REJECTED: '已驳回'
}
const statusColor: Record<string, string> = {
  WAITING: 'default', PENDING: 'orange', APPROVED: 'blue', POSTED: 'green', PAID: 'green', REJECTED: 'red'
}
const statusOptions = Object.entries(statusLabel).map(([value, label]) => ({ value, label }))
const contractOptions = computed(() => contracts.value.map((c) => ({ label: `${c.no} · ${c.name}`, value: c.no })))

const filtered = computed(() => {
  const kw = query.keyword.trim().toLowerCase()
  return allRows.value.filter((p) =>
    (!query.contractNo || p.contractNo === query.contractNo) &&
    (!query.status || p.status === query.status) &&
    (!kw || p.contractNo.toLowerCase().includes(kw) || (p.contractName || '').toLowerCase().includes(kw)
      || (p.stage || '').toLowerCase().includes(kw) || (p.sourceDocNo || '').toLowerCase().includes(kw)))
})
const paged = computed(() => filtered.value.slice((localPage.value - 1) * localSize.value, localPage.value * localSize.value))
const pagination = computed(() => ({
  current: localPage.value, pageSize: localSize.value, total: filtered.value.length, showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`, onChange: (p: number, size: number) => { localPage.value = p; localSize.value = size }
}))
const stats = computed(() => ({
  count: filtered.value.length,
  amount: filtered.value.reduce((s, p) => s + Number(p.amount || 0), 0),
  pending: filtered.value.filter((p) => p.status === 'PENDING').length,
  applied: filtered.value.filter((p) => p.status !== 'WAITING').length
}))

/**
 * 后端付款计划按合同查询，没有全局列表端点：先拉合同（仅取有付款计划的），
 * 再并发拉各合同付款计划聚合。筛选条件在前端按同一批数据应用，统计与列表口径一致。
 */
async function load() {
  loading.value = true
  try {
    const res = await listContracts({ page: 1, size: 200 })
    const list = res.records || []
    contracts.value = list
    const withPlans = list.filter((c) => Number(c.planCount || 0) > 0)
    const groups = await Promise.all(withPlans.map((c) =>
      listPaymentPlans(c.no).catch(() => [] as PaymentPlanVO[])))
    allRows.value = groups.flat().sort((a, b) => (a.dueDate || '').localeCompare(b.dueDate || ''))
    localPage.value = 1
  } catch (e: any) { message.error(e?.message || '加载付款计划失败') }
  finally { loading.value = false }
}
function resetPage() { localPage.value = 1 }

const columns = [
  { title: '合同编号', dataIndex: 'contractNo', key: 'contractNo', width: 170 },
  { title: '合同名称', key: 'contractName', width: 220 },
  { title: '付款阶段', dataIndex: 'stage', key: 'stage', width: 140 },
  { title: '应付金额', key: 'amount', align: 'right' as const, width: 130 },
  { title: '应付日期', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
  { title: '付款状态', key: 'status', align: 'center' as const, width: 120 },
  { title: '来源单据', dataIndex: 'sourceDocNo', key: 'sourceDocNo', width: 170 },
  { title: '操作', key: 'action', width: 130 }
]

async function apply(row: PaymentPlanVO) {
  try { await applyPaymentPlan(row.id); message.success('付款申请已发起，等待审批'); await load() }
  catch (e: any) { message.error(e?.message || '发起申请失败') }
}

/* 新增付款计划（选择合同） */
const createOpen = ref(false)
const saving = ref(false)
const form = reactive({ contractNo: undefined as string | undefined, stage: '', amount: undefined as number | undefined, dueDate: undefined as string | undefined })
function openCreate() {
  form.contractNo = undefined
  form.stage = ''
  form.amount = undefined
  form.dueDate = undefined
  createOpen.value = true
}
async function submitCreate() {
  if (!form.contractNo || !form.amount || form.amount <= 0) { message.warning('请选择合同并填写付款金额'); return }
  saving.value = true
  try {
    await savePaymentPlan(form.contractNo, { stage: form.stage || undefined, amount: Number(form.amount), dueDate: form.dueDate })
    createOpen.value = false
    message.success('付款计划已保存')
    await load()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <h2>合同付款计划</h2>
      <span class="divider" />
      <a-select v-model:value="query.contractNo" :options="contractOptions" placeholder="全部合同" style="width: 240px"
                allow-clear show-search @change="resetPage" />
      <a-select v-model:value="query.status" :options="statusOptions" placeholder="付款状态" style="width: 130px" allow-clear @change="resetPage" />
      <a-input-search v-model:value="query.keyword" placeholder="阶段 / 单据号" style="width: 200px" allow-clear @search="resetPage" />
      <span class="grow" />
      <a-button v-if="store.can('contract:payment:edit')" @click="openCreate">＋ 新增计划</a-button>
      <a-button v-if="store.can('contract:payment:edit')" type="primary" @click="openCreate">＋ 发起付款申请</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <div class="stat-grid">
        <div class="stat-card"><span class="st-label">付款计划</span><strong class="st-value">{{ stats.count }} 笔</strong><span class="st-foot"><b>按筛选条件</b></span></div>
        <div class="stat-card"><span class="st-label">应付金额</span><strong class="st-value">{{ yuan(stats.amount) }}</strong><span class="st-foot"><b>按筛选条件合计</b></span></div>
        <div class="stat-card"><span class="st-label">待审批</span><strong class="st-value">{{ stats.pending }} 笔</strong><span class="st-foot"><b>需财务确认</b></span></div>
        <div class="stat-card"><span class="st-label">已发起申请</span><strong class="st-value">{{ stats.applied }} 笔</strong><span class="st-foot"><b>含已入账 / 已付款</b></span></div>
      </div>
      <a-table class="ft-table" row-key="id" :columns="columns" :data-source="paged" :loading="loading"
               :pagination="pagination" :class="{ 'tbl-empty-mode': !loading && !paged.length }"
               :scroll="paged.length ? { x: 1200 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'contractName'">
            <a-tooltip :title="record.contractName"><span class="cell-ellipsis">{{ record.contractName || '-' }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
          <template v-else-if="column.key === 'dueDate'">
            <span>{{ record.dueDate || '-' }}</span>
            <a-tag v-if="record.overdue" color="red" style="margin-left:6px">已逾期</a-tag>
            <a-tooltip v-else-if="record.dueSoon" :title="`到期前 ${record.remindDays ?? 7} 天提醒（settings 埋点配置）`">
              <a-tag color="orange" style="margin-left:6px">临近到期</a-tag>
            </a-tooltip>
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
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a v-if="store.can('contract:payment:edit') && (record.status === 'WAITING' || record.status === 'REJECTED')"
                 @click="apply(record)">发起申请</a>
              <span v-else class="muted">-</span>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无付款计划" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="createOpen" title="新增付款计划 / 发起申请" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="所属合同" required>
          <a-select v-model:value="form.contractNo" :options="contractOptions" placeholder="请选择合同" show-search style="width: 100%" />
        </a-form-item>
        <a-form-item label="付款阶段"><a-input v-model:value="form.stage" placeholder="如：预付款 / 到货款 / 质保金" /></a-form-item>
        <a-form-item label="应付金额" required>
          <a-input-number v-model:value="form.amount" :min="0" :precision="2" style="width: 100%" addon-before="¥" />
        </a-form-item>
        <a-form-item label="应付日期">
          <a-date-picker v-model:value="form.dueDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="createOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submitCreate">保存并发起</a-button>
        </div>
      </template>
    </a-drawer>
  </section>
</template>
