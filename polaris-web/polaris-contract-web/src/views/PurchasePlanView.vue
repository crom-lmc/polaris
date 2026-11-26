<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import { useOrgDirectory, filterOption as orgFilterOption } from '@/composables/useOrgDirectory'
import {
  listPurchasePlans, getPurchasePlan, savePurchasePlan, approvePurchasePlan, rejectPurchasePlan,
  convertPurchasePlan, listPartners, getMaterialTree, fetchBudgetSubjectOptions,
  type PurchasePlanVO, type MaterialVO, type BudgetSubjectVO
} from '@/api/contract'
import { yuan } from '@/utils/format'

const store = useUserStore()
const isMobile = useIsMobile()
const { deptOptions, employeeOptions, load: loadOrgDirectory } = useOrgDirectory()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 760))

const loading = ref(false)
const rows = ref<PurchasePlanVO[]>([])
const total = ref(0)
const query = reactive({ no: '', status: undefined as string | undefined, dept: undefined as string | undefined, keyword: '', page: 1, size: 20 })

const statusLabel: Record<string, string> = {
  DRAFT: '草稿', PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', CONVERTED: '已转合同'
}
const statusColor: Record<string, string> = {
  DRAFT: 'default', PENDING: 'orange', APPROVED: 'blue', REJECTED: 'red', CONVERTED: 'green'
}
const statusOptions = Object.entries(statusLabel).map(([value, label]) => ({ value, label }))
const queryDeptOptions = computed(() => [...new Set(rows.value.map((r) => r.dept).filter(Boolean))].map((v) => ({ label: v, value: v })))

const pagination = computed(() => ({
  current: query.page, pageSize: query.size, total: total.value, showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`, onChange: (p: number, size: number) => {
    query.page = p
    query.size = size
    load()
  }
}))

const stats = computed(() => ({
  total: total.value,
  amount: rows.value.reduce((s, r) => s + Number(r.amount || 0), 0),
  pending: rows.value.filter((r) => r.status === 'PENDING').length,
  converted: rows.value.filter((r) => r.status === 'CONVERTED').length
}))

async function load() {
  loading.value = true
  try {
    const res = await listPurchasePlans({
      no: query.no || undefined, status: query.status, keyword: query.keyword || undefined,
      page: query.page, size: query.size
    })
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载采购计划失败')
  } finally {
    loading.value = false
  }
}
function resetAndLoad() { query.page = 1; load() }

const columns = [
  { title: '计划编号', dataIndex: 'no', key: 'no', width: 160 },
  { title: '计划名称', key: 'title', width: 220 },
  { title: '需求部门', dataIndex: 'dept', key: 'dept', width: 110 },
  { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 100 },
  { title: '需求日期', dataIndex: 'needDate', key: 'needDate', width: 110 },
  { title: '预算科目', dataIndex: 'budgetSubject', key: 'budgetSubject', width: 170 },
  { title: '明细数', dataIndex: 'itemCount', key: 'itemCount', align: 'center' as const, width: 90 },
  { title: '计划金额', key: 'amount', align: 'right' as const, width: 130 },
  { title: '状态', key: 'status', align: 'center' as const, width: 110 },
  { title: '关联合同', key: 'contract', width: 170 },
  { title: '操作', key: 'action', width: 160 }
]
const itemColumns = [
  { title: '物料编码', dataIndex: 'materialCode', key: 'materialCode', width: 130 },
  { title: '物料名称', key: 'materialName', width: 150 },
  { title: '规格', dataIndex: 'spec', key: 'spec', width: 130 },
  { title: '单位', dataIndex: 'unit', key: 'unit', align: 'center' as const, width: 70 },
  { title: '数量', dataIndex: 'qty', key: 'qty', align: 'right' as const, width: 80 },
  { title: '单价', key: 'price', align: 'right' as const, width: 100 },
  { title: '金额', key: 'amount', align: 'right' as const, width: 110 }
]

/* 详情 */
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<PurchasePlanVO | null>(null)
async function openDetail(id: string) {
  detailOpen.value = true
  detailLoading.value = true
  try { detail.value = await getPurchasePlan(id) } catch (e: any) { message.error(e?.message || '加载详情失败') }
  finally { detailLoading.value = false }
}

/* 物料与往来单位（真实数据派生，不写死常量） */
interface MaterialPick { label: string; value: string; spec: string; unit: string; price: number }
const materialPicks = ref<MaterialPick[]>([])
const partnerPicks = ref<{ label: string; value: string }[]>([])
async function loadOptions() {
  try {
    const tree = await getMaterialTree()
    const flat: MaterialVO[] = []
    const walk = (nodes: MaterialVO[]) => nodes.forEach((n) => { flat.push(n); if (n.children?.length) walk(n.children) })
    walk(tree || [])
    materialPicks.value = flat.filter((n) => n.kind === 'MATERIAL').map((n) => ({
      label: `${n.code} · ${n.name}`, value: n.code, spec: n.spec || '', unit: n.unit || '', price: Number(n.referencePrice || 0)
    }))
  } catch { /* 物料库不可用时保持空 */ }
  try {
    const res = await listPartners({ role: 'SUPPLIER', page: 1, size: 200 })
    partnerPicks.value = (res.records || []).map((p) => ({ label: `${p.code} · ${p.name}`, value: p.code }))
  } catch { /* 往来单位不可用时保持空 */ }
}
function filterOption(input: string, option: any) {
  return orgFilterOption(input, option)
}

/* 新建 */
const createOpen = ref(false)
const createSaving = ref(false)
type DraftItem = { materialCode: string; qty: number; price: number }
const createForm = reactive({
  title: '', dept: '', applicant: '', needDate: undefined as string | undefined,
  budgetSubject: '', items: [] as DraftItem[]
})
const createTotal = computed(() => createForm.items.reduce((s, i) => s + (Number(i.qty) || 0) * (Number(i.price) || 0), 0))
/** 预算科目只能从财务主数据选，不允许手输或从已有单据派生 */
const budgetSubjectOptions = ref<{ label: string; value: string }[]>([])
async function loadBudgetSubjects() {
  try {
    const list = await fetchBudgetSubjectOptions() || []
    // 同一科目可能多年份重复，按 name 去重；label 带编码便于识别
    const seen = new Map<string, string>()
    list.forEach((s: BudgetSubjectVO) => {
      if (s.name && !seen.has(s.name)) seen.set(s.name, s.code)
    })
    budgetSubjectOptions.value = [...seen.entries()].map(([name, code]) => ({ label: `${code} · ${name}`, value: name }))
  } catch { budgetSubjectOptions.value = [] }
}
function openCreate() {
  createForm.title = ''
  createForm.dept = ''
  createForm.applicant = ''
  createForm.needDate = undefined
  createForm.budgetSubject = ''
  createForm.items = []
  createOpen.value = true
}
function addItem() { createForm.items.push({ materialCode: '', qty: 1, price: 0 }) }
function removeItem(idx: number) { createForm.items.splice(idx, 1) }
function onMaterialPick(idx: number, code: string) {
  const m = materialPicks.value.find((x) => x.value === code)
  if (m && !createForm.items[idx].price) createForm.items[idx].price = m.price
}
async function submitCreate() {
  if (!createForm.title.trim()) { message.warning('请填写计划名称'); return }
  if (!createForm.items.length || createForm.items.some((i) => !i.materialCode || !i.qty || i.price < 0)) {
    message.warning('请至少添加一条明细，并填写物料、数量与单价')
    return
  }
  createSaving.value = true
  try {
    await savePurchasePlan({
      title: createForm.title.trim(), dept: createForm.dept || undefined, applicant: createForm.applicant || undefined,
      needDate: createForm.needDate, budgetSubject: createForm.budgetSubject || undefined,
      items: createForm.items.map((i) => ({ materialCode: i.materialCode, qty: Number(i.qty), price: Number(i.price) }))
    })
    createOpen.value = false
    message.success('采购计划已创建，可提交审批')
    resetAndLoad()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { createSaving.value = false }
}

/* 审批 / 驳回 / 转合同 */
async function doApprove(id: string) {
  try { await approvePurchasePlan(id); message.success('已审批通过'); detailOpen.value = false; load() }
  catch (e: any) { message.error(e?.message || '审批失败') }
}
async function doReject(id: string) {
  try { await rejectPurchasePlan(id); message.success('已驳回'); detailOpen.value = false; load() }
  catch (e: any) { message.error(e?.message || '驳回失败') }
}
const convertOpen = ref(false)
const convertSaving = ref(false)
const convertId = ref('')
const convertForm = reactive({ partnerCode: undefined as string | undefined, projectNo: '', dept: '', owner: '', paymentTerms: '', subject: '' })
const convertBudgetSubject = ref('')
function openConvert(id: string) {
  convertId.value = id
  convertBudgetSubject.value = rows.value.find((r) => r.id === id)?.budgetSubject || ''
  convertForm.partnerCode = undefined
  convertForm.projectNo = ''
  convertForm.dept = ''
  convertForm.owner = ''
  convertForm.paymentTerms = ''
  convertForm.subject = ''
  detailOpen.value = false
  convertOpen.value = true
}
async function submitConvert() {
  if (!convertForm.partnerCode) { message.warning('请选择往来单位'); return }
  convertSaving.value = true
  try {
    const contract = await convertPurchasePlan(convertId.value, {
      partnerCode: convertForm.partnerCode, projectNo: convertForm.projectNo || undefined,
      dept: convertForm.dept || undefined, owner: convertForm.owner || undefined,
      paymentTerms: convertForm.paymentTerms || undefined, subject: convertForm.subject || undefined
    })
    convertOpen.value = false
    message.success(`已生成采购合同 ${contract.no}，明细已带入合同清单`)
    resetAndLoad()
  } catch (e: any) { message.error(e?.message || '转合同失败') }
  finally { convertSaving.value = false }
}

onMounted(() => { load(); loadOptions(); loadOrgDirectory(); loadBudgetSubjects() })
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <h2>采购计划</h2>
      <span class="divider" />
      <a-input-search v-model:value="query.no" placeholder="计划编号" style="width: 170px" allow-clear @search="resetAndLoad" />
      <a-input-search v-model:value="query.keyword" placeholder="名称 / 申请人" style="width: 200px" allow-clear @search="resetAndLoad" />
      <a-select v-model:value="query.status" :options="statusOptions" placeholder="计划状态" style="width: 120px" allow-clear @change="resetAndLoad" />
      <a-select v-model:value="query.dept" :options="queryDeptOptions" placeholder="需求部门" style="width: 130px" allow-clear @change="resetAndLoad" />
      <span class="grow" />
      <span class="hint">审批通过后转采购合同并带入清单</span>
      <a-button v-if="store.can('contract:plan:edit')" type="primary" @click="openCreate">＋ 新建计划</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <div class="stat-grid">
        <div class="stat-card"><span class="st-label">采购计划</span><strong class="st-value">{{ stats.total }} 份</strong><span class="st-foot"><b>按筛选条件</b></span></div>
        <div class="stat-card"><span class="st-label">计划金额</span><strong class="st-value">{{ yuan(stats.amount) }}</strong><span class="st-foot"><b>本页合计</b></span></div>
        <div class="stat-card"><span class="st-label">待审批</span><strong class="st-value">{{ stats.pending }} 份</strong><span class="st-foot"><b>本页</b></span></div>
        <div class="stat-card"><span class="st-label">已转合同</span><strong class="st-value">{{ stats.converted }} 份</strong><span class="st-foot"><b>本页</b></span></div>
      </div>
      <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :loading="loading"
               :pagination="pagination" :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :scroll="rows.length ? { x: 1500 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <a-tooltip :title="record.title"><span class="cell-ellipsis">{{ record.title }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor[record.status] || 'default'">{{ statusLabel[record.status] || record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'contract'">
            <a v-if="record.contractNo" @click="openDetail(record.id)">{{ record.contractNo }}</a>
            <span v-else class="muted">未转合同</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a @click="openDetail(record.id)">详情</a>
              <template v-if="store.can('contract:plan:edit')">
                <a v-if="record.status === 'PENDING' || record.status === 'DRAFT'" @click="doApprove(record.id)">审批</a>
                <a v-if="record.status === 'PENDING'" @click="doReject(record.id)">驳回</a>
                <a v-if="record.status === 'APPROVED'" @click="openConvert(record.id)">转合同</a>
              </template>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无采购计划" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="detailOpen" title="采购计划详情" :width="drawerWidth" placement="right">
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-card class="panel-card" size="small" title="基本信息">
            <a-descriptions :column="1" bordered size="small">
              <a-descriptions-item label="计划编号">{{ detail.no }}</a-descriptions-item>
              <a-descriptions-item label="计划名称">{{ detail.title }}</a-descriptions-item>
              <a-descriptions-item label="需求部门 / 申请人">{{ detail.dept || '-' }} · {{ detail.applicant || '-' }}</a-descriptions-item>
              <a-descriptions-item label="需求日期">{{ detail.needDate || '-' }}</a-descriptions-item>
              <a-descriptions-item label="预算科目">{{ detail.budgetSubject || '-' }}</a-descriptions-item>
              <a-descriptions-item label="计划金额">{{ yuan(detail.amount) }}（{{ detail.itemCount }} 条明细）</a-descriptions-item>
              <a-descriptions-item label="状态">{{ statusLabel[detail.status] || detail.status }}</a-descriptions-item>
              <a-descriptions-item label="关联采购合同">{{ detail.contractNo || '未转合同' }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
          <div class="panel-section-title">计划明细（来自物料库）</div>
          <a-table class="ft-table" row-key="id" :columns="itemColumns" :data-source="detail.items || []"
                   :pagination="false" :scroll="(detail.items || []).length ? { x: 700 } : undefined">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'materialName'">
                <a-tooltip :title="`${record.materialName} ${record.spec || ''}`"><span class="cell-ellipsis">{{ record.materialName }}</span></a-tooltip>
              </template>
              <template v-else-if="['price', 'amount'].includes(String(column.key))">{{ yuan(record[column.key as 'price' | 'amount']) }}</template>
            </template>
            <template #emptyText><div class="tbl-empty"><a-empty description="暂无明细" /></div></template>
          </a-table>
        </template>
      </a-spin>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="detailOpen = false">关闭</a-button>
          <template v-if="store.can('contract:plan:edit') && detail">
            <a-button v-if="detail.status === 'PENDING' || detail.status === 'DRAFT'" type="primary" @click="doApprove(detail.id)">审批通过</a-button>
            <a-button v-if="detail.status === 'APPROVED'" type="primary" @click="openConvert(detail.id)">转采购合同</a-button>
          </template>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="convertOpen" title="采购计划转合同" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="往来单位" required>
          <a-select v-model:value="convertForm.partnerCode" :options="partnerPicks" placeholder="请选择供应商" show-search
                    :filter-option="filterOption" style="width: 100%" />
        </a-form-item>
        <a-form-item label="所属项目编码"><a-input v-model:value="convertForm.projectNo" placeholder="选填，项目编码" /></a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="需求部门">
              <a-select v-model:value="convertForm.dept" :options="deptOptions" placeholder="从组织架构选择" show-search
                        :filter-option="filterOption" allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="负责人">
              <a-select v-model:value="convertForm.owner" :options="employeeOptions" placeholder="从组织架构选择" show-search
                        :filter-option="filterOption" allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="付款条款"><a-input v-model:value="convertForm.paymentTerms" /></a-form-item>
        <a-form-item label="合同标的"><a-input v-model:value="convertForm.subject" /></a-form-item>
        <a-form-item label="预算科目">
          <a-input :value="convertBudgetSubject || '未设置'" readonly placeholder="自动取采购计划的预算科目" />
          <span class="panel-hint">预算科目固定取采购计划值，转入后不可在此改写</span>
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="convertOpen = false">取消</a-button>
          <a-button type="primary" :loading="convertSaving" @click="submitConvert">生成采购合同</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="createOpen" title="新建采购计划" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="计划名称" required><a-input v-model:value="createForm.title" placeholder="如：研发服务器扩容计划" /></a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="需求部门">
              <a-select v-model:value="createForm.dept" :options="deptOptions" placeholder="从组织架构选择" show-search
                        :filter-option="filterOption" allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="申请人">
              <a-select v-model:value="createForm.applicant" :options="employeeOptions" placeholder="从组织架构选择" show-search
                        :filter-option="filterOption" allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="需求日期">
              <a-date-picker v-model:value="createForm.needDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="预算科目">
              <a-select v-model:value="createForm.budgetSubject" :options="budgetSubjectOptions" placeholder="从主数据选择" show-search
                        :filter-option="filterOption" allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <div class="panel-section-title">计划明细（从物料库选料）</div>
        <div v-for="(item, idx) in createForm.items" :key="idx" class="plan-item-row">
          <a-select v-model:value="item.materialCode" :options="materialPicks" placeholder="选择物料" show-search
                    :filter-option="filterOption" style="flex: 2 1 220px" @change="(v: string) => onMaterialPick(idx, v)" />
          <a-input-number v-model:value="item.qty" :min="1" placeholder="数量" style="width: 90px" />
          <a-input-number v-model:value="item.price" :min="0" :precision="2" placeholder="单价" style="width: 110px" />
          <span class="plan-item-amt">{{ yuan(item.qty * item.price) }}</span>
          <a @click="removeItem(idx)">删除</a>
        </div>
        <a-button type="dashed" block @click="addItem">＋ 添加明细行</a-button>
        <div class="plan-total">计划金额：<b>{{ yuan(createTotal) }}</b></div>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="createOpen = false">取消</a-button>
          <a-button type="primary" :loading="createSaving" @click="submitCreate">提交计划</a-button>
        </div>
      </template>
    </a-drawer>
  </section>
</template>
