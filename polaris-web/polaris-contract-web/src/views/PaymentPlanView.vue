<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  listContracts, listPaymentPlans, savePaymentPlan, updatePaymentPlan, deletePaymentPlan, applyPaymentPlan, fetchPublicDict,
  type PaymentPlanVO, type ContractVO, type DictOption
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
/** 待审批由谁审：与 settings 审批模板 contract.payment / contract.payment.senior 保持一致 */
const approvalHint = '审批中：合同归口部门负责人 → 财务中心负责人；本期金额超过升级线时加总经理终审（settings 埋点 contract.payment.approval-amount）'
const contractOptions = computed(() => contracts.value.map((c) => ({ label: `${c.no} · ${c.name}`, value: c.no })))
const dictStages = ref<DictOption[]>([])
const stageOptions = computed(() => dictStages.value.map((d) => ({ value: d.value, label: d.label })))
function stageText(value?: string) {
  if (!value) return '-'
  return dictStages.value.find((d) => d.value === value)?.label || value
}

const filtered = computed(() => {
  const kw = query.keyword.trim().toLowerCase()
  return allRows.value.filter((p) =>
    (!query.contractNo || p.contractNo === query.contractNo) &&
    (!query.status || p.status === query.status) &&
    (!kw || p.contractNo.toLowerCase().includes(kw) || (p.contractName || '').toLowerCase().includes(kw)
      || stageText(p.stage).toLowerCase().includes(kw) || (p.no || '').toLowerCase().includes(kw)
      || (p.sourceDocNo || '').toLowerCase().includes(kw)))
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
  { title: '付款申请单号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '合同编号', dataIndex: 'contractNo', key: 'contractNo', width: 170 },
  { title: '合同名称', key: 'contractName', width: 220 },
  { title: '付款阶段', dataIndex: 'stage', key: 'stage', width: 140 },
  { title: '本期付款金额', key: 'amount', align: 'right' as const, width: 140 },
  { title: '应付日期', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
  { title: '付款状态', key: 'status', align: 'center' as const, width: 120 },
  { title: '操作', key: 'action', width: 280 }
]

async function apply(row: PaymentPlanVO) {
  try { await applyPaymentPlan(row.id); message.success('付款申请已发起，等待审批'); await load() }
  catch (e: any) { message.error(e?.message || '发起申请失败') }
}

/* 查看：复用列表行数据（VO 已含单号/财务单据号/凭证号/附件），无需额外接口 */
const viewOpen = ref(false)
const viewRow = ref<PaymentPlanVO | null>(null)
function openView(row: PaymentPlanVO) { viewRow.value = row; viewOpen.value = true }

/* 发起付款申请：一步完成「新建计划 + 发起申请」。仅生成待申请计划的入口在合同详情的付款计划编辑器。 */
const applyOpen = ref(false)
const saving = ref(false)
/** 编辑待申请计划：复用申请抽屉的表单，editingId 非空表示编辑态（只保存、不发起） */
const editingId = ref<string | null>(null)
const form = reactive({
  contractNo: undefined as string | undefined,
  stage: undefined as string | undefined,
  remainingAmount: undefined as number | undefined,
  amount: undefined as number | undefined,
  dueDate: undefined as string | undefined
})
/** 剩余未付金额 = 合同剩余金额 - 本期付款金额，纯展示派生字段，不落库 */
const unpaidAmount = computed(() => {
  if (form.remainingAmount == null) return undefined
  return Math.round((Number(form.remainingAmount) - Number(form.amount ?? 0)) * 100) / 100
})
/** 合同剩余金额取合同 VO 的待付款金额（合同金额 - 已付 - 在途），选择合同后自动带出 */
function onContractChange(no?: string) {
  form.remainingAmount = contracts.value.find((c) => c.no === no)?.pendingAmount
  form.amount = undefined
}
function resetForm() {
  form.contractNo = undefined
  form.stage = undefined
  form.remainingAmount = undefined
  form.amount = undefined
  form.dueDate = undefined
}
function openApply() {
  editingId.value = null
  resetForm()
  applyOpen.value = true
}
/** 编辑待申请计划：回填当前值，合同锁定不可改（金额校验基准仍取该合同） */
function openEdit(row: PaymentPlanVO) {
  editingId.value = row.id
  form.contractNo = row.contractNo
  form.stage = row.stage
  form.remainingAmount = contracts.value.find((c) => c.no === row.contractNo)?.pendingAmount
  form.amount = Number(row.amount || 0)
  form.dueDate = row.dueDate
  applyOpen.value = true
}
/** 删除待申请计划（服务端再次校验仅 WAITING 可删） */
async function remove(row: PaymentPlanVO) {
  try {
    await deletePaymentPlan(row.id)
    message.success('付款计划已删除')
    await load()
  } catch (e: any) { message.error(e?.message || '删除失败') }
}
async function submitApply() {
  const amount = form.amount
  const missing: string[] = []
  if (!form.contractNo) missing.push('所属合同')
  if (!form.stage) missing.push('付款阶段')
  if (form.remainingAmount == null) missing.push('合同剩余金额')
  if (!amount || amount <= 0) missing.push('本期付款金额')
  if (!form.dueDate) missing.push('应付日期')
  if (missing.length) { message.warning(`请填写必填项：${missing.join('、')}`); return }
  if (amount == null) return
  if (amount > Number(form.remainingAmount)) {
    message.warning(`本期付款金额不能超过合同剩余金额 ${yuan(Number(form.remainingAmount))}`)
    return
  }
  saving.value = true
  try {
    const payload = { stage: form.stage, amount: Number(amount), dueDate: form.dueDate }
    if (editingId.value) {
      // 编辑待申请计划：只保存，不发起
      await updatePaymentPlan(editingId.value, payload)
      message.success('付款计划已保存')
    } else {
      const saved = await savePaymentPlan(form.contractNo!, payload)
      try {
        await applyPaymentPlan(saved.id)
        message.success('付款计划已保存并发起申请，等待审批')
      } catch (e: any) {
        // 计划已落库为待申请：验收未过账等条件下发起失败时保留计划，供列表行「发起申请」重试
        message.warning(`付款计划已保存，但发起申请失败：${e?.message || '请在列表中重试'}`)
      }
    }
    applyOpen.value = false
    await load()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
onMounted(() => {
  load()
  fetchPublicDict('contract.payment_stage').then((list) => { dictStages.value = list || [] })
    .catch(() => { message.warning('付款阶段字典加载失败，请检查配置中心') })
})
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <a-select v-model:value="query.contractNo" :options="contractOptions" placeholder="全部合同" style="width: 240px"
                allow-clear show-search @change="resetPage" />
      <a-select v-model:value="query.status" :options="statusOptions" placeholder="付款状态" style="width: 130px" allow-clear @change="resetPage" />
      <a-input-search v-model:value="query.keyword" placeholder="阶段 / 单据号" style="width: 200px" allow-clear @search="resetPage" />
      <span class="grow" />
      <a-button v-if="store.can('contract:payment:edit')" type="primary" @click="openApply">＋ 发起付款申请</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <div class="stat-grid">
        <div class="stat-card"><span class="st-label">付款计划</span><strong class="st-value">{{ stats.count }} 笔</strong><span class="st-foot"><b>按筛选条件</b></span></div>
        <div class="stat-card"><span class="st-label">本期付款金额</span><strong class="st-value">{{ yuan(stats.amount) }}</strong><span class="st-foot"><b>按筛选条件合计</b></span></div>
        <div class="stat-card"><span class="st-label">待审批</span><strong class="st-value">{{ stats.pending }} 笔</strong><span class="st-foot"><b>需财务确认</b></span></div>
        <div class="stat-card"><span class="st-label">已发起申请</span><strong class="st-value">{{ stats.applied }} 笔</strong><span class="st-foot"><b>含已入账 / 已付款</b></span></div>
      </div>
      <a-table class="ft-table" row-key="id" :columns="columns" :data-source="paged" :loading="loading"
               :pagination="pagination" :class="{ 'tbl-empty-mode': !loading && !paged.length }"
               :scroll="paged.length ? { x: 1350 } : undefined">
          <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'no'">
            <a-tooltip v-if="record.no" :title="record.no"><span class="cell-ellipsis">{{ record.no }}</span></a-tooltip>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'contractName'">
            <a-tooltip :title="record.contractName"><span class="cell-ellipsis">{{ record.contractName || '-' }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'stage'">{{ stageText(record.stage) }}</template>
          <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
          <template v-else-if="column.key === 'dueDate'">
            <span>{{ record.dueDate || '-' }}</span>
            <a-tag v-if="record.overdue" color="red" style="margin-left:6px">已逾期</a-tag>
            <a-tooltip v-else-if="record.dueSoon" :title="`到期前 ${record.remindDays ?? 7} 天提醒（settings 埋点配置）`">
              <a-tag color="orange" style="margin-left:6px">临近到期</a-tag>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tooltip v-if="record.status === 'PENDING'" :title="approvalHint">
              <a-tag color="orange">{{ statusLabel[record.status] }}</a-tag>
            </a-tooltip>
            <a-tag v-else :color="statusColor[record.status] || 'default'">{{ statusLabel[record.status] || record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4" class="action-links">
              <a @click="openView(record)">查看</a>
              <template v-if="store.can('contract:payment:edit') && record.status === 'WAITING'">
                <a @click="openEdit(record)">编辑</a>
                <a-popconfirm title="确认删除该待申请付款计划？" ok-text="删除" cancel-text="取消" @confirm="remove(record)">
                  <a class="link-danger">删除</a>
                </a-popconfirm>
              </template>
              <a v-if="store.can('contract:payment:edit') && (record.status === 'WAITING' || record.status === 'REJECTED')"
                 @click="apply(record)">发起申请</a>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无付款计划" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="applyOpen" :title="editingId ? '编辑付款计划' : '发起付款申请'" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="所属合同" required>
          <a-select v-model:value="form.contractNo" :options="contractOptions" placeholder="请选择合同" show-search
                    :disabled="!!editingId" style="width: 100%" @change="onContractChange" />
        </a-form-item>
        <a-form-item label="付款阶段" required>
          <a-select v-model:value="form.stage" :options="stageOptions" placeholder="请选择付款阶段" show-search style="width: 100%" />
        </a-form-item>
        <a-form-item label="合同剩余金额" required>
          <a-input-number :value="form.remainingAmount" :controls="false" :precision="2" readonly
                          placeholder="选择合同后自动带出" style="width: 100%" addon-before="¥" />
        </a-form-item>
        <a-form-item label="本期付款金额" required>
          <a-input-number v-model:value="form.amount" :min="0" :max="form.remainingAmount ?? undefined" :precision="2"
                          style="width: 100%" addon-before="¥" />
        </a-form-item>
        <a-form-item label="剩余未付金额">
          <a-input-number :value="unpaidAmount" :controls="false" :precision="2" readonly
                          placeholder="自动计算：合同剩余金额 - 本期付款金额" style="width: 100%" addon-before="¥" />
        </a-form-item>
        <a-form-item label="应付日期" required>
          <a-date-picker v-model:value="form.dueDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="applyOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submitApply">{{ editingId ? '保存修改' : '保存并发起' }}</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="viewOpen" title="付款申请详情" :width="drawerWidth" placement="right">
      <template v-if="viewRow">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="付款申请单号"><b>{{ viewRow.no || '-' }}</b></a-descriptions-item>
          <a-descriptions-item label="合同编号">{{ viewRow.contractNo }}</a-descriptions-item>
          <a-descriptions-item label="合同名称">{{ viewRow.contractName || '-' }}</a-descriptions-item>
          <a-descriptions-item label="付款阶段">{{ stageText(viewRow.stage) }}</a-descriptions-item>
          <a-descriptions-item label="本期付款金额">{{ yuan(viewRow.amount) }}</a-descriptions-item>
          <a-descriptions-item label="应付日期">{{ viewRow.dueDate || '-' }}</a-descriptions-item>
          <a-descriptions-item label="付款状态">
            <a-tag :color="statusColor[viewRow.status] || 'default'">{{ statusLabel[viewRow.status] || viewRow.status }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="付款事由">{{ viewRow.summary || '-' }}</a-descriptions-item>
          <a-descriptions-item label="财务单据号">{{ viewRow.sourceDocNo || '尚未生成（审批通过后由财务域建单回写）' }}</a-descriptions-item>
          <a-descriptions-item label="凭证号">{{ viewRow.voucherNo || '尚未生成' }}</a-descriptions-item>
        </a-descriptions>
        <div v-if="viewRow.attachmentUrls?.length" class="view-attachments">
          <div class="view-attachments-title">合同与验收凭证</div>
          <div class="view-attachments-list">
            <a v-for="(u, i) in viewRow.attachmentUrls" :key="i" :href="u" target="_blank" rel="noopener">附件 {{ i + 1 }}</a>
          </div>
        </div>
      </template>
      <template #footer>
        <div class="drawer-footer"><a-button @click="viewOpen = false">关闭</a-button></div>
      </template>
    </a-drawer>
  </section>
</template>

<style scoped>
.link-danger { color: var(--danger, #dc2626); }
.view-attachments { margin-top: 16px; }
.view-attachments-title { font-weight: 600; margin-bottom: 8px; }
.view-attachments-list { display: flex; flex-wrap: wrap; gap: 8px 16px; }
</style>
