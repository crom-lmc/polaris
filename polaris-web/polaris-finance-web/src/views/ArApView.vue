<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import {
  listArAp, listAging, createArAp, approveArAp, postArAp, writeOffArAp, deleteArAp,
  fetchPublicDict, fetchPartnerOptions, fetchContractOptions, subjectOptions,
  type ArApVO, type ArApSave, type AgingBucketVO, type WriteOffRequest, type DictOption
} from '@/api/finance'
import { useUserStore } from '@/stores/user'
import { useOrgDirectory } from '@/composables/useOrgDirectory'
import { paginatePicker, financeDeptColumns, orgRows } from '@/composables/useEntityPickers'
import EntityPickerModal, { type PickerColumn, type PickerLoadParams, type PickerResult, type PickerRow } from '@shared/components/EntityPickerModal.vue'
import { arApTypeColor, arApTypeLabel, dateOf, fmtDate, statusColor, statusLabel, yuan, yuanOrMasked } from '@/utils/format'

const store = useUserStore()
const canWrite = () => store.can('finance:ar-ap:write')
const { orgNodes, pathOf, load: loadOrgDirectory } = useOrgDirectory()

const loading = ref(false)
const rows = ref<ArApVO[]>([])
const statRows = ref<ArApVO[]>([])
const aging = ref<AgingBucketVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const type = ref<string>()
const status = ref<string>()
const keyword = ref('')
const contractNo = ref<string | undefined>()
const ageFilter = ref<AgingBucketVO | null>(null)
const range = ref<[Dayjs, Dayjs] | null>(null)

const typeOptions = [{ label: '应收', value: 'AR' }, { label: '应付', value: 'AP' }]
const statusOptions = [
  { label: '草稿', value: 'DRAFT' }, { label: '待审批', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' }, { label: '已驳回', value: 'REJECTED' },
  { label: '已入账', value: 'POSTED' }, { label: '已付款', value: 'PAID' }
]

const columns = [
  { title: '单号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '往来单位', key: 'party', width: 220 },
  { title: '类型', key: 'type', align: 'center' as const, width: 80 },
  { title: '金额', key: 'amount', align: 'right' as const, width: 120 },
  { title: '未核销余额', key: 'balance', align: 'right' as const, width: 130 },
  { title: '账龄', key: 'ageDays', align: 'right' as const, width: 90 },
  { title: '关联合同', key: 'contract', width: 160 },
  { title: '关联凭证', key: 'voucher', width: 160 },
  { title: '发票号', dataIndex: 'invoiceNo', key: 'invoiceNo', width: 150 },
  { title: '到期日', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
  { title: '状态', key: 'status', align: 'center' as const, width: 100 },
  { title: '日期', dataIndex: 'date', key: 'date', width: 110 },
  { title: '操作', key: 'action', width: 180 }
]
const pagination = () => ({
  current: page.value, pageSize: size.value, total: total.value,
  showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条`
})

const query = (p: number, s: number) => ({
  type: type.value || undefined, status: status.value || undefined,
  keyword: keyword.value || undefined, contractNo: contractNo.value || undefined,
  from: dateOf(range.value?.[0]), to: dateOf(range.value?.[1]), page: p, size: s
})

async function load() {
  loading.value = true
  try {
    const res = await listArAp(query(page.value, size.value))
    rows.value = res.list || []
    total.value = res.total
  } catch (e: any) { message.error(e?.message || '加载应收应付失败') }
  finally { loading.value = false }
}
async function loadStats() {
  try {
    const [stat, buckets] = await Promise.all([listArAp(query(1, 500)), listAging()])
    statRows.value = stat.list || []
    aging.value = buckets || []
  } catch { /* 统计失败不影响列表 */ }
}
function onTableChange(p: any) {
  const changed = p.pageSize !== size.value
  page.value = changed ? 1 : p.current
  size.value = p.pageSize
  load()
}
function reset() {
  type.value = undefined; status.value = undefined; keyword.value = ''
  contractNo.value = undefined; range.value = null; ageFilter.value = null
  page.value = 1; load(); loadStats()
}
watch([type, status, keyword, contractNo, range], () => { page.value = 1; load(); loadStats() })

const invoiceTypeOptions = ref<{ label: string; value: string }[]>([])
const creditPeriodOptions = ref<{ label: string; value: number }[]>([])
const partnerOptions = ref<{ label: string; value: string }[]>([])
const contractOptions = ref<{ label: string; value: string }[]>([])
const accountSubjectOptions = ref<{ label: string; value: string }[]>([])

async function loadPartners() {
  const role = form.type === 'AR' ? 'CUSTOMER' : 'SUPPLIER'
  try {
    partnerOptions.value = (await fetchPartnerOptions(role) || [])
      .map((p) => ({ label: `${p.code} · ${p.name}`, value: p.name }))
  } catch { partnerOptions.value = [] }
}
async function loadFormOptions() {
  await loadPartners()
  try {
    contractOptions.value = (await fetchContractOptions() || [])
      .map((c) => ({ label: `${c.no} · ${c.name}`, value: c.no }))
  } catch { contractOptions.value = [] }
  try {
    accountSubjectOptions.value = (await subjectOptions() || [])
      .map((s) => ({ label: `${s.code} · ${s.name}`, value: s.name }))
  } catch { accountSubjectOptions.value = [] }
  try {
    const periods: DictOption[] = await fetchPublicDict('finance.credit_period') || []
    creditPeriodOptions.value = periods.map((d) => ({ label: d.label, value: Number(d.value) }))
  } catch { creditPeriodOptions.value = [] }
}
onMounted(async () => {
  try {
    const dicts: DictOption[] = await fetchPublicDict('finance.invoice_type') || []
    invoiceTypeOptions.value = dicts.map((d) => ({ label: d.label, value: d.value }))
  } catch { /* 字典可空，不阻断列表 */ }
  await loadFormOptions()
  load(); loadStats(); loadOrgDirectory()
})

const unsettled = (a: ArApVO) => a.status === 'APPROVED' || a.status === 'POSTED'
const arBalance = computed(() => statRows.value.filter((a) => a.type === 'AR' && unsettled(a)).reduce((s, a) => s + Number(a.balance || 0), 0))
const apBalance = computed(() => statRows.value.filter((a) => a.type === 'AP' && unsettled(a)).reduce((s, a) => s + Number(a.balance || 0), 0))
const overdue = computed(() => statRows.value.filter((a) => a.type === 'AR' && a.ageDays > 30 && unsettled(a)).reduce((s, a) => s + Number(a.balance || 0), 0))

function pickAging(b: AgingBucketVO) {
  ageFilter.value = ageFilter.value?.label === b.label ? null : b
  // 后端没有账龄筛选参数：按当前页数据的 ageDays 过滤并提示口径
  if (ageFilter.value) message.info(`已按账龄区间「${b.label}」筛选（当前页数据）`)
}
const displayRows = computed(() => {
  if (!ageFilter.value) return rows.value
  return rows.value.filter((a) => a.ageDays >= ageFilter.value!.minDays && a.ageDays <= ageFilter.value!.maxDays)
})

async function act(row: ArApVO, fn: (id: string) => Promise<unknown>, ok: string) {
  try { await fn(row.id); message.success(ok); load(); loadStats() }
  catch (e: any) { message.error(e?.message || '操作失败') }
}
function onDelete(row: ArApVO) {
  Modal.confirm({
    title: '删除应收应付单', content: `确定删除 ${row.no} 吗？`, centered: true,
    onOk: async () => {
      try { await deleteArAp(row.id); message.success('已删除'); load(); loadStats() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}

const writeOffOpen = ref(false)
const writeOffTarget = ref<ArApVO | null>(null)
const writeOffForm = reactive<WriteOffRequest>({ amount: 0, targetNo: '', date: dayjs().format('YYYY-MM-DD') })
function openWriteOff(row: ArApVO) {
  writeOffTarget.value = row
  Object.assign(writeOffForm, { amount: Number(row.balance || 0), targetNo: '', date: dayjs().format('YYYY-MM-DD') })
  writeOffOpen.value = true
}
async function submitWriteOff() {
  if (!writeOffTarget.value) return
  if (!writeOffForm.amount || writeOffForm.amount <= 0) { message.warning('请填写核销金额'); return }
  try {
    await writeOffArAp(writeOffTarget.value.id, { ...writeOffForm })
    writeOffOpen.value = false
    message.success(`单据 ${writeOffTarget.value.no} 已核销`)
    load(); loadStats()
  } catch (e: any) { message.error(e?.message || '核销失败') }
}

const createOpen = ref(false)
const saving = ref(false)
const form = reactive<ArApSave>({
  party: '', type: 'AR', amount: 0, contractNo: '', subject: '', summary: '',
  invoiceNo: '', invoiceType: '', creditDays: undefined, dueDate: undefined,
  date: dayjs().format('YYYY-MM-DD'), dept: ''
})
/** 类型切换后往来单位候选跟着切客户 / 供应商，已选单位清空避免串类型 */
watch(() => form.type, () => { form.party = '' })

/* ===== 主数据弹窗选择：往来单位 / 归属部门 ===== */
const partnerPickerOpen = ref(false)
const deptPickerOpen = ref(false)
const partnerLabel = computed(() => form.type === 'AR' ? '客户' : '供应商')
const partnerColumns: PickerColumn[] = [
  { title: '编码', dataIndex: 'code', width: 150 },
  { title: '名称', dataIndex: 'name', ellipsis: true }
]

async function loadPartnerPicker(params: PickerLoadParams): Promise<PickerResult> {
  const role = form.type === 'AR' ? 'CUSTOMER' : 'SUPPLIER'
  try {
    const opts = await fetchPartnerOptions(role) || []
    return paginatePicker(opts.map((p) => ({ code: p.code, name: p.name, raw: p })), params)
  } catch { return { records: [], total: 0 } }
}
async function loadDeptPicker(params: PickerLoadParams): Promise<PickerResult> {
  return paginatePicker(orgRows(orgNodes.value, pathOf), params)
}
function onPartnerPicked(row: PickerRow) { form.party = row.name }
function onDeptPicked(row: PickerRow) { form.dept = row.name }
/** 账期或发生日期变化即推算到期日；账期清空时到期日一并清空，由后端再做最终派生 */
watch([() => form.creditDays, () => form.date], () => {
  form.dueDate = form.creditDays == null || !form.date
    ? undefined
    : dayjs(form.date).add(Number(form.creditDays), 'day').format('YYYY-MM-DD')
})
function openCreate() {
  Object.assign(form, {
    party: '', type: type.value || 'AR', amount: 0, contractNo: '', subject: '', summary: '',
    invoiceNo: '', invoiceType: '', creditDays: undefined, dueDate: undefined,
    date: dayjs().format('YYYY-MM-DD'), dept: ''
  })
  createOpen.value = true
}
async function submitCreate() {
  if (!form.party?.trim() || !form.type || !form.amount || form.amount <= 0 || !form.date) {
    message.warning('请填写往来单位、类型、金额与发生日期')
    return
  }
  saving.value = true
  try {
    await createArAp({ ...form, amount: Number(form.amount) })
    createOpen.value = false
    message.success('应收应付单已创建')
    page.value = 1; load(); loadStats()
  } catch (e: any) { message.error(e?.message || '创建失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <h2>应收应付</h2>
      <span class="divider" />
      <a-select v-model:value="type" :options="typeOptions" placeholder="全部往来" style="width: 110px" allow-clear />
      <a-select v-model:value="status" :options="statusOptions" placeholder="状态" style="width: 110px" allow-clear />
      <a-input-search v-model:value="keyword" placeholder="单号 / 往来单位 / 摘要" style="width: 220px" allow-clear />
      <a-input-search v-model:value="contractNo" placeholder="合同号" style="width: 150px" allow-clear />
      <a-range-picker v-model:value="range" format="YYYY-MM-DD" />
      <a-button @click="reset">重置</a-button>
      <span class="grow" />
      <a-button v-if="canWrite()" type="primary" @click="openCreate">＋ 新增单据</a-button>
    </section>
    <section class="ft-scroll">
      <div class="stat-grid two">
        <div class="stat-card clickable" @click="type = 'AR'">
          <span class="st-label">应收余额（未核销）</span>
          <strong class="st-value">{{ yuanOrMasked(arBalance) }}</strong>
          <span class="st-foot"><b class="trend-up">逾期 {{ yuanOrMasked(overdue) }}</b><span class="muted">账龄 &gt; 30 天</span></span>
        </div>
        <div class="stat-card clickable" @click="type = 'AP'">
          <span class="st-label">应付余额（未付款）</span>
          <strong class="st-value">{{ yuanOrMasked(apBalance) }}</strong>
          <span class="st-foot"><b class="trend-down">{{ statRows.filter((a) => a.type === 'AP').length }} 笔</b><span class="muted">含合同应付</span></span>
        </div>
      </div>
      <a-card class="panel-card" title="应收账龄分布（区间来自后端埋点，点击筛选当前页明细）" size="small">
        <div class="aging">
          <div v-for="a in aging" :key="a.label" class="aging-row clickable"
               :class="{ active: ageFilter?.label === a.label }" @click="pickAging(a)">
            <span class="ag-label">{{ a.label }}</span>
            <div class="ag-track"><i :style="{ width: `${a.percent}%` }" /></div>
            <span class="ag-value">{{ yuan(a.value) }}</span>
            <span class="ag-percent">{{ a.percent }}%</span>
          </div>
          <div v-if="!aging.length" class="hint">暂无账龄数据</div>
        </div>
      </a-card>
      <a-card class="panel-card" title="往来明细（合同 → 应收应付单 → 会计凭证）" size="small">
        <a-table class="ft-table" style="--ft-min-width: 1800px" row-key="id" :columns="columns" :data-source="displayRows"
                 :loading="loading" :pagination="pagination()" @change="onTableChange"
                 :class="{ 'tbl-empty-mode': !loading && !displayRows.length }"
                 :scroll="displayRows.length ? { x: 1800 } : undefined">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'party'">
              <a-tooltip :title="record.party"><span class="cell-ellipsis">{{ record.party }}</span></a-tooltip>
            </template>
            <template v-else-if="column.key === 'type'">
              <a-tag :color="arApTypeColor(record.type)">{{ arApTypeLabel(record.type) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'amount'">
              <a-tooltip :title="record.amount === null ? '薪资类往来金额已按 finance:salary:view 脱敏' : yuan(record.amount)">
                <span>{{ yuanOrMasked(record.amount) }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'balance'">
              <a-tooltip :title="record.balance === null ? '薪资类往来余额已按 finance:salary:view 脱敏' : yuan(record.balance)">
                <span>{{ yuanOrMasked(record.balance) }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'ageDays'">{{ record.ageDays }} 天</template>
            <template v-else-if="column.key === 'contract'">
              <a-tooltip v-if="record.contractNo" :title="record.contractNo">
                <a class="cell-ellipsis" @click="message.info(`合同号 ${record.contractNo}`)">{{ record.contractNo }}</a>
              </a-tooltip>
              <span v-else class="muted">-</span>
            </template>
            <template v-else-if="column.key === 'voucher'">
              <a-tooltip v-if="record.voucherNo" :title="record.voucherNo">
                <a class="cell-ellipsis" @click="message.info(`凭证号 ${record.voucherNo}`)">{{ record.voucherNo }}</a>
              </a-tooltip>
              <span v-else class="muted">未生成</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'date'">{{ fmtDate(record.date) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-space :size="4">
                <a v-if="canWrite() && record.status === 'PENDING'" @click="act(record, approveArAp, `单据 ${record.no} 已审批通过`)">确认</a>
                <a v-if="canWrite() && record.status === 'APPROVED'" @click="act(record, postArAp, `单据 ${record.no} 已过账并生成凭证`)">过账</a>
                <a v-if="canWrite() && (record.status === 'APPROVED' || record.status === 'POSTED')" @click="openWriteOff(record)">核销</a>
                <a v-if="canWrite() && ['DRAFT', 'REJECTED'].includes(record.status)" @click="onDelete(record)">删除</a>
                <span v-if="!canWrite()" class="muted">-</span>
              </a-space>
            </template>
          </template>
          <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="该筛选条件下暂无往来" /></div></template>
        </a-table>
      </a-card>
    </section>

    <a-modal v-model:open="writeOffOpen" title="核销应收应付单" :centered="true" @ok="submitWriteOff" ok-text="确定核销">
      <a-alert v-if="writeOffTarget" type="info" show-icon class="drawer-alert"
               :message="`单据 ${writeOffTarget.no} · 余额 ${yuanOrMasked(writeOffTarget.balance)}`" />
      <a-form layout="vertical">
        <a-form-item label="核销金额" required>
          <a-input-number v-model:value="writeOffForm.amount" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item label="目标单号（MANUAL 模式必填）">
          <a-input v-model:value="writeOffForm.targetNo" placeholder="核销模式由后端埋点决定" />
        </a-form-item>
        <a-form-item label="核销日期">
          <a-date-picker v-model:value="writeOffForm.date" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:open="createOpen" title="新增应收应付单" :width="520" placement="right">
      <a-form layout="vertical">
        <a-form-item :label="partnerLabel" required>
          <a-input :value="form.party" readonly placeholder="点击选择" style="width: 100%; cursor: pointer"
                   @click="partnerPickerOpen = true" />
        </a-form-item>
        <a-form-item label="类型" required><a-select v-model:value="form.type" :options="typeOptions" /></a-form-item>
        <a-form-item label="金额" required>
          <a-input-number v-model:value="form.amount" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item label="关联合同号">
          <a-select v-model:value="form.contractNo" :options="contractOptions" placeholder="从合同主数据选择（选填）"
                    allow-clear show-search option-filter-prop="label" style="width: 100%" />
        </a-form-item>
        <a-form-item label="会计科目">
          <a-select v-model:value="form.subject" :options="accountSubjectOptions" placeholder="从财务会计科目选择（选填）"
                    allow-clear show-search option-filter-prop="label" style="width: 100%" />
        </a-form-item>
        <a-form-item label="摘要"><a-textarea v-model:value="form.summary" :rows="3" /></a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="发票号"><a-input v-model:value="form.invoiceNo" placeholder="选填" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发票类型">
              <a-select v-model:value="form.invoiceType" :options="invoiceTypeOptions" placeholder="从字典选择（选填）"
                        allow-clear show-search option-filter-prop="label" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="账期天数">
              <a-select v-model:value="form.creditDays" :options="creditPeriodOptions" placeholder="从字典选择（选填）"
                        allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="到期日">
              <a-date-picker v-model:value="form.dueDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="发生日期" required>
              <a-date-picker v-model:value="form.date" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="归属部门">
              <a-input :value="form.dept" readonly placeholder="点击选择（选填）" style="width: 100%; cursor: pointer"
                       @click="deptPickerOpen = true" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="createOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submitCreate">保存</a-button>
        </div>
      </template>
    </a-drawer>

    <EntityPickerModal v-model:open="partnerPickerOpen" :title="`选择${partnerLabel}`" :columns="partnerColumns"
                       row-key="code" :initial-value="form.party" placeholder="编码 / 名称"
                       :load="loadPartnerPicker" @confirm="onPartnerPicked" />
    <EntityPickerModal v-model:open="deptPickerOpen" title="选择归属部门" :columns="financeDeptColumns"
                       row-key="name" :initial-value="form.dept" placeholder="部门名称 / 编码"
                       :load="loadDeptPicker" @confirm="onDeptPicked" />
  </section>
</template>
