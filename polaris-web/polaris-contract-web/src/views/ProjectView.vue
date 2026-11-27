<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import { useOrgDirectory, filterOption as orgFilterOption } from '@/composables/useOrgDirectory'
import {
  listProjects, getProject, saveProject, updateProject, changeProjectStatus, deleteProject,
  listContracts,
  type ProjectVO, type ProjectSave, type OrgNodeVO, type ContractVO
} from '@/api/contract'
import { yuan } from '@/utils/format'

const store = useUserStore()
const isMobile = useIsMobile()
const { orgNodes, employeeOptions, load: loadOrgDirectory } = useOrgDirectory()
const filterOption = orgFilterOption
const drawerWidth = computed(() => (isMobile.value ? '100%' : 760))
const descColumn = computed(() => (isMobile.value ? 1 : 2))

const loading = ref(false)
const rows = ref<ProjectVO[]>([])
const total = ref(0)
const query = reactive({
  code: '', keyword: '', type: undefined as string | undefined,
  status: undefined as string | undefined, teamCode: undefined as string | undefined,
  page: 1, size: 20
})

const typeLabel: Record<string, string> = { SALES: '销售项目', PURCHASE: '采购项目', INTERNAL: '内部项目' }
const statusLabel: Record<string, string> = { DRAFT: '立项中', ACTIVE: '执行中', PAUSED: '已暂停', CLOSED: '已结项', CANCELLED: '已取消' }
const statusColor: Record<string, string> = { DRAFT: 'blue', ACTIVE: 'green', PAUSED: 'orange', CLOSED: 'default', CANCELLED: 'red' }
const typeOptions = Object.entries(typeLabel).map(([value, label]) => ({ value, label }))
const statusOptions = Object.entries(statusLabel).map(([value, label]) => ({ value, label }))

const pagination = computed(() => ({
  current: query.page, pageSize: query.size, total: total.value, showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`, onChange: (p: number, size: number) => {
    query.page = p; query.size = size; load()
  }
}))
const stats = computed(() => ({
  total: total.value,
  active: rows.value.filter((r) => r.status === 'ACTIVE').length,
  closed: rows.value.filter((r) => r.status === 'CLOSED').length,
  budget: rows.value.reduce((s, r) => s + Number(r.budgetAmount || 0), 0),
  used: rows.value.reduce((s, r) => s + Number(r.usedAmount || 0), 0)
}))
const budgetUsage = (r: ProjectVO) => Number(r.budgetAmount || 0) > 0
  ? Number(((Number(r.usedAmount || 0) / Number(r.budgetAmount)) * 100).toFixed(1)) : 0

async function load() {
  loading.value = true
  try {
    const res = await listProjects({
      code: query.code || undefined, name: query.keyword || undefined,
      type: query.type, status: query.status, page: query.page, size: query.size
    })
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) { message.error(e?.message || '加载项目失败') }
  finally { loading.value = false }
}
function resetAndLoad() { query.page = 1; load() }

const columns = [
  { title: '项目编码', dataIndex: 'code', key: 'code', width: 175 },
  { title: '项目名称', key: 'name', width: 220 },
  { title: '类型', key: 'type', align: 'center' as const, width: 100 },
  { title: '归属组织', dataIndex: 'orgCode', key: 'orgCode', width: 130 },
  { title: '项目组编码', dataIndex: 'teamCode', key: 'teamCode', width: 130 },
  { title: '负责人', dataIndex: 'owner', key: 'owner', width: 90 },
  { title: '归集合同', key: 'contractCount', align: 'center' as const, width: 100 },
  { title: '项目预算', key: 'budgetAmount', align: 'right' as const, width: 130 },
  { title: '已入账', key: 'usedAmount', align: 'right' as const, width: 120 },
  { title: '执行率', key: 'usage', width: 140 },
  { title: '状态', key: 'status', align: 'center' as const, width: 100 },
  { title: '操作', key: 'action', width: 200 }
]
const contractColumns = [
  { title: '合同编号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '类型', key: 'type', align: 'center' as const, width: 80 },
  { title: '合同名称', key: 'name', width: 220 },
  { title: '金额', key: 'amount', align: 'right' as const, width: 130 }
]

/* 组织级联：真相源 EHR 组织树（本域代理只读引用），失败则从已有项目数据派生，不写死常量 */
const COMPANY_TYPES = ['GROUP', 'COMPANY', 'SUB_COMPANY']
const TEAM_TYPES = ['PROJECT', 'DEPARTMENT']
async function loadOrg() {
  await loadOrgDirectory()
}
function companyOf(node: OrgNodeVO): string {
  const byId = new Map(orgNodes.value.map((n) => [n.id, n]))
  let cur: OrgNodeVO | undefined = node
  while (cur?.parentId) {
    cur = byId.get(cur.parentId)
    if (cur && COMPANY_TYPES.includes(cur.type)) return cur.code
  }
  return ''
}
const costCenterOptions = computed(() => {
  const fromOrg = orgNodes.value.filter((n) => COMPANY_TYPES.includes(n.type))
    .map((n) => ({ label: n.name, value: n.code }))
  if (fromOrg.length) return fromOrg
  return [...new Set(rows.value.map((r) => r.costCenterCode).filter(Boolean))].map((v) => ({ label: v, value: v }))
})
const teamOptionsAll = computed(() => {
  const fromOrg = orgNodes.value.filter((n) => TEAM_TYPES.includes(n.type))
    .map((n) => ({ label: n.name, value: n.code, orgCode: n.code, companyCode: companyOf(n) }))
  if (fromOrg.length) return fromOrg
  return [...new Map(rows.value.filter((r) => r.teamCode).map((r) =>
    [r.teamCode, { label: r.teamCode, value: r.teamCode, orgCode: r.orgCode, companyCode: r.costCenterCode }])).values()]
})
const editableTeamOptions = computed(() => teamOptionsAll.value.filter((t) =>
  !form.costCenterCode || !t.companyCode || t.companyCode === form.costCenterCode))
const teamFilterOptions = computed(() => [...new Map(teamOptionsAll.value.map((t) => [t.value, t])).values()]
  .map((t) => ({ label: t.label, value: t.value })))

function onCostCenterChange() {
  if (!editableTeamOptions.value.some((o) => o.value === form.teamCode)) {
    form.teamCode = ''
    form.orgCode = ''
  }
}
function onTeamChange() {
  form.orgCode = editableTeamOptions.value.find((t) => t.value === form.teamCode)?.orgCode || ''
}

/* 详情 + 只读反查合同 */
const detailOpen = ref(false)
const detail = ref<ProjectVO | null>(null)
const detailContracts = ref<ContractVO[]>([])
async function openDetail(id: string) {
  try {
    detail.value = await getProject(id)
    const res = await listContracts({ projectNo: detail.value.code, page: 1, size: 50 })
    detailContracts.value = res.records || []
    detailOpen.value = true
  } catch (e: any) { message.error(e?.message || '加载详情失败') }
}

/* 新增 / 编辑 */
const editOpen = ref(false)
const editing = ref<string | null>(null)
const saving = ref(false)
const form = reactive<ProjectSave>({
  name: '', type: 'INTERNAL', orgCode: '', teamCode: '', costCenterCode: '', owner: '',
  budgetAmount: 0, startDate: undefined, endDate: undefined, status: 'DRAFT', remark: ''
})
function resetForm() {
  Object.assign(form, {
    name: '', type: 'INTERNAL', orgCode: '', teamCode: '', costCenterCode: '', owner: '',
    budgetAmount: 0, startDate: undefined, endDate: undefined, status: 'DRAFT', remark: ''
  })
}
function openEdit(row?: ProjectVO) {
  detailOpen.value = false
  editing.value = row?.id || null
  resetForm()
  if (row) {
    Object.assign(form, {
      name: row.name, type: row.type, orgCode: row.orgCode, teamCode: row.teamCode,
      costCenterCode: row.costCenterCode, owner: row.owner, budgetAmount: Number(row.budgetAmount || 0),
      startDate: row.startDate || undefined, endDate: row.endDate || undefined,
      status: row.status, remark: row.remark || ''
    })
  }
  editOpen.value = true
}
async function submit() {
  const missing: string[] = []
  if (!form.name?.trim()) missing.push('项目名称')
  if (!form.costCenterCode) missing.push('成本中心（公司主体）')
  if (!form.teamCode) missing.push('关联组织项目组')
  if (!form.orgCode) missing.push('归属组织（项目组带出）')
  if (!form.startDate) missing.push('开始日期')
  if (!form.endDate) missing.push('结束日期')
  if (missing.length) { message.warning(`请填写必填项：${missing.join('、')}`); return }
  if (form.endDate! < form.startDate!) { message.warning('结束日期不能早于开始日期'); return }
  saving.value = true
  try {
    const payload: ProjectSave = { ...form, name: form.name.trim(), budgetAmount: Number(form.budgetAmount || 0) }
    if (editing.value) await updateProject(editing.value, payload)
    else await saveProject(payload)
    editOpen.value = false
    message.success('项目档案已保存，编码由服务端按流水自动生成')
    resetAndLoad()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
function nextStatus(s: string): { label: string; value: string } | undefined {
  if (s === 'DRAFT') return { label: '启动', value: 'ACTIVE' }
  if (s === 'ACTIVE') return { label: '暂停', value: 'PAUSED' }
  if (s === 'PAUSED') return { label: '恢复', value: 'ACTIVE' }
  return undefined
}
async function applyStatus(row: ProjectVO) {
  const next = nextStatus(row.status)
  if (!next) return
  try { await changeProjectStatus(row.id, next.value); message.success(`${row.code} 已${next.label}`); load() }
  catch (e: any) { message.error(e?.message || '状态流转失败') }
}
function remove(row: ProjectVO) {
  if (!store.can('contract:project:edit')) return
  const doDelete = async () => {
    try { await deleteProject(row.id); message.success('已删除'); load() }
    catch (e: any) { message.error(e?.message || '删除失败') }
  }
  Modal.confirm({
    centered: true, title: '删除项目', content: `确定删除「${row.name}」吗？已被合同引用的项目不可删除。`,
    okButtonProps: { danger: true }, onOk: doDelete
  })
}

onMounted(() => { load(); loadOrg() })
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <a-input-search v-model:value="query.code" placeholder="项目编码" style="width: 160px" allow-clear @search="resetAndLoad" />
      <a-input-search v-model:value="query.keyword" placeholder="项目名称" style="width: 180px" allow-clear @search="resetAndLoad" />
      <a-select v-model:value="query.type" :options="typeOptions" placeholder="全部类型" style="width: 130px" allow-clear @change="resetAndLoad" />
      <a-select v-model:value="query.status" :options="statusOptions" placeholder="全部状态" style="width: 120px" allow-clear @change="resetAndLoad" />
      <a-select v-model:value="query.teamCode" :options="teamFilterOptions" placeholder="全部项目组" style="width: 170px" allow-clear @change="resetAndLoad" />
      <span class="grow" />
      <a-button v-if="store.can('contract:project:edit')" type="primary" @click="openEdit()">＋ 新增项目</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <div class="stat-grid">
        <div class="stat-card"><span class="st-label">项目档案</span><strong class="st-value">{{ stats.total }} 个</strong><span class="st-foot"><b>独立编码，不挂组织树</b></span></div>
        <div class="stat-card"><span class="st-label">执行中</span><strong class="st-value">{{ stats.active }} 个</strong><span class="st-foot"><b>本页已结项 {{ stats.closed }} 个</b></span></div>
        <div class="stat-card"><span class="st-label">项目预算合计</span><strong class="st-value">{{ yuan(stats.budget) }}</strong><span class="st-foot"><b>本页合计</b></span></div>
        <div class="stat-card"><span class="st-label">预算执行率</span><strong class="st-value">{{ stats.budget ? (stats.used / stats.budget * 100).toFixed(1) : '0' }}%</strong><span class="st-foot"><b>已入账 {{ yuan(stats.used) }}</b></span></div>
      </div>
      <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :loading="loading"
               :pagination="pagination" :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :scroll="rows.length ? { x: 1780 } : undefined" style="--ft-min-width: 1780px">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-tooltip :title="record.name"><span class="cell-ellipsis">{{ record.name }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag :color="record.type === 'SALES' ? 'green' : record.type === 'PURCHASE' ? 'blue' : 'purple'">{{ typeLabel[record.type] || record.type }}</a-tag>
          </template>
          <template v-else-if="['budgetAmount', 'usedAmount'].includes(String(column.key))">{{ yuan(record[column.key as 'budgetAmount' | 'usedAmount']) }}</template>
          <template v-else-if="column.key === 'usage'">
            <div class="usage-cell">
              <a-progress :percent="Math.min(100, budgetUsage(record))" :status="budgetUsage(record) > 100 ? 'exception' : 'active'" size="small" :show-info="false" />
              <span :class="budgetUsage(record) > 100 ? 'usage-over' : 'muted'">{{ budgetUsage(record) }}%</span>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor[record.status] || 'default'">{{ statusLabel[record.status] || record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a @click="openDetail(record.id)">详情</a>
              <template v-if="store.can('contract:project:edit')">
                <a @click="openEdit(record)">编辑</a>
                <a v-if="nextStatus(record.status)" @click="applyStatus(record)">{{ nextStatus(record.status)?.label }}</a>
                <a @click="remove(record)">删除</a>
              </template>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无项目档案" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="detailOpen" title="项目档案详情" :width="drawerWidth" placement="right">
      <template v-if="detail">
        <a-card class="panel-card" size="small" title="基本信息">
          <a-descriptions :column="descColumn" bordered size="small">
            <a-descriptions-item label="项目编码">{{ detail.code }}</a-descriptions-item>
            <a-descriptions-item label="项目名称">{{ detail.name }}</a-descriptions-item>
            <a-descriptions-item label="项目类型">{{ typeLabel[detail.type] || detail.type }}</a-descriptions-item>
            <a-descriptions-item label="项目状态">{{ statusLabel[detail.status] || detail.status }}</a-descriptions-item>
            <a-descriptions-item label="负责人">{{ detail.owner || '-' }}</a-descriptions-item>
            <a-descriptions-item label="项目周期">{{ detail.startDate || '-' }} ~ {{ detail.endDate || '-' }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ detail.remark || '-' }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
        <a-card class="panel-card" size="small" title="组织与核算口径">
          <a-descriptions :column="descColumn" bordered size="small">
            <a-descriptions-item label="成本中心（公司主体）">{{ detail.costCenterCode || '-' }}</a-descriptions-item>
            <a-descriptions-item label="关联组织项目组">{{ detail.teamCode || '-' }}</a-descriptions-item>
            <a-descriptions-item label="归属组织 orgCode">{{ detail.orgCode || '-' }}</a-descriptions-item>
          </a-descriptions>
          <div class="hint org-note">成本中心引用公司主体，项目组引用组织节点；归属组织由项目组带出，决定单据数据范围。</div>
        </a-card>
        <a-card class="panel-card" size="small" title="预算与执行">
          <a-descriptions :column="descColumn" bordered size="small">
            <a-descriptions-item label="项目预算">{{ yuan(detail.budgetAmount) }}</a-descriptions-item>
            <a-descriptions-item label="已入账金额">{{ yuan(detail.usedAmount) }}</a-descriptions-item>
            <a-descriptions-item label="预算执行率">{{ budgetUsage(detail) }}%</a-descriptions-item>
            <a-descriptions-item label="关联合同">{{ detail.contractCount }} 份 / {{ yuan(detail.contractAmount) }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
        <div class="panel-section-title">归集合同（{{ detailContracts.length }} 份 · 在合同台账维护「所属项目」，此处只读反查）</div>
        <a-table class="ft-table" row-key="no" :columns="contractColumns" :data-source="detailContracts" :pagination="false"
                 :scroll="detailContracts.length ? { x: 620 } : undefined">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <a-tooltip :title="record.name"><span class="cell-ellipsis">{{ record.name }}</span></a-tooltip>
            </template>
            <template v-else-if="column.key === 'type'">{{ record.type === 'SALES' ? '销售' : '采购' }}</template>
            <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
          </template>
          <template #emptyText><div class="tbl-empty"><a-empty description="暂无归集合同" /></div></template>
        </a-table>
      </template>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="detailOpen = false">关闭</a-button>
          <a-button v-if="store.can('contract:project:edit') && detail" type="primary" @click="openEdit(detail)">编辑档案</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="editOpen" :title="`${editing ? '编辑' : '新增'}项目档案`" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-card class="panel-card" size="small" title="基本信息">
          <a-row :gutter="12">
            <a-col :span="12"><a-form-item label="项目编码"><a-input :value="editing && detail ? detail.code : ''" disabled :placeholder="editing ? '' : '保存时自动生成'" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="项目名称" required><a-input v-model:value="form.name" /></a-form-item></a-col>
          </a-row>
          <a-row :gutter="12">
            <a-col :span="12"><a-form-item label="项目类型"><a-select v-model:value="form.type" :options="typeOptions" /></a-form-item></a-col>
            <a-col :span="12">
              <a-form-item label="负责人">
                <a-select v-model:value="form.owner" :options="employeeOptions" placeholder="从组织架构选择" show-search
                          :filter-option="filterOption" allow-clear style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </a-card>
        <a-card class="panel-card" size="small" title="组织与核算口径">
          <a-form-item label="成本中心（公司主体）" required>
            <a-select v-model:value="form.costCenterCode" :options="costCenterOptions" placeholder="请选择公司主体" @change="onCostCenterChange" />
          </a-form-item>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="关联组织项目组" required>
                <a-select v-model:value="form.teamCode" :options="editableTeamOptions" placeholder="请选择项目组" @change="onTeamChange" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="归属组织 orgCode（自动带出）">
                <a-input :value="form.orgCode" disabled placeholder="选择项目组后自动带出" />
              </a-form-item>
            </a-col>
          </a-row>
          <div class="hint org-note">先选成本中心，再选该公司主体下的项目组；归属组织由项目组带出，不单独手填。</div>
        </a-card>
        <a-card class="panel-card" size="small" title="预算与周期">
          <a-form-item label="项目预算"><a-input-number v-model:value="form.budgetAmount" :min="0" :precision="2" style="width: 100%" addon-before="¥" /></a-form-item>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="开始日期" required>
                <a-date-picker v-model:value="form.startDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="结束日期" required>
                <a-date-picker v-model:value="form.endDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="项目状态"><a-select v-model:value="form.status" :options="statusOptions" :disabled="!!editing" /></a-form-item>
          <a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item>
        </a-card>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="editOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
        </div>
      </template>
    </a-drawer>
  </section>
</template>
