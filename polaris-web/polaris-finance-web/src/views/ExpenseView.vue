<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import dayjs, { type Dayjs } from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import {
  listExpenses, createExpense, submitExpense, approveExpense, rejectExpense,
  postExpense, payExpense, deleteExpense,
  fetchExpenseProfile, fetchExpenseTypeOptions, fetchPublicDict, uploadExpenseAttachment,
  type ExpenseVO, type ExpenseSave, type ExpenseTypeOption, type DictOption
} from '@/api/finance'
import { useUserStore } from '@/stores/user'
import { useOrgDirectory, filterOption as orgFilterOption } from '@/composables/useOrgDirectory'
import { dateOf, fmtDate, statusColor, statusLabel, yuan } from '@/utils/format'

const router = useRouter()
const store = useUserStore()
const { orgNodes, employees, deptOptions, load: loadOrgDirectory } = useOrgDirectory()
const filterOption = orgFilterOption
const canCreate = () => store.can('finance:expense:create')
const canAudit = () => store.can('finance:expense:audit')

const loading = ref(false)
const rows = ref<ExpenseVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const status = ref<string>()
const dept = ref<string>()
const range = ref<[Dayjs, Dayjs] | null>(null)

/** 报销类型来自 settings 字典；remark 承载「类型 → 会计科目」映射 */
const expenseTypes = ref<ExpenseTypeOption[]>([])
const invoiceTypeOptions = ref<{ label: string; value: string }[]>([])
const taxRateOptions = ref<{ label: string; value: number }[]>([])
const statusOptions = [
  { label: '草稿', value: 'DRAFT' }, { label: '待审批', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' }, { label: '已驳回', value: 'REJECTED' },
  { label: '已入账', value: 'POSTED' }, { label: '已付款', value: 'PAID' }
]

const columns = [
  { title: '单号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 100 },
  { title: '报销类型', key: 'expenseType', width: 110 },
  { title: '事由', key: 'reason', width: 240 },
  { title: '金额', key: 'amount', align: 'right' as const, width: 120 },
  { title: '会计科目', key: 'subject', width: 140 },
  { title: '归属部门', dataIndex: 'dept', key: 'dept', width: 110 },
  { title: '报销凭证', key: 'attachments', width: 110 },
  { title: '关联凭证', key: 'voucher', width: 170 },
  { title: '状态', key: 'status', align: 'center' as const, width: 100 },
  { title: '报销日期', dataIndex: 'date', key: 'date', width: 110 },
  { title: '操作', key: 'action', width: 230 }
]
const pagination = () => ({
  current: page.value, pageSize: size.value, total: total.value,
  showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条`
})

async function load() {
  loading.value = true
  try {
    const res = await listExpenses({
      keyword: keyword.value || undefined, status: status.value, dept: dept.value,
      from: dateOf(range.value?.[0]), to: dateOf(range.value?.[1]),
      page: page.value, size: size.value
    })
    rows.value = res.list || []
    total.value = res.total
  } catch (e: any) { message.error(e?.message || '加载报销单失败') }
  finally { loading.value = false }
}
function onTableChange(p: any) {
  const changed = p.pageSize !== size.value
  page.value = changed ? 1 : p.current
  size.value = p.pageSize
  load()
}
function reset() {
  keyword.value = ''; status.value = undefined; dept.value = undefined; range.value = null
  page.value = 1; load()
}
watch([keyword, status, dept, range], () => { page.value = 1; load() })

onMounted(async () => {
  try { expenseTypes.value = await fetchExpenseTypeOptions() } catch { /* 字典可空，后端仍会校验报销类型 */ }
  try {
    const invoices: DictOption[] = await fetchPublicDict('finance.invoice_type') || []
    invoiceTypeOptions.value = invoices.map((d) => ({ label: d.label, value: d.value }))
  } catch { /* 字典可空，不阻断列表 */ }
  try {
    const rates: DictOption[] = await fetchPublicDict('finance.tax_rate') || []
    taxRateOptions.value = rates.map((d) => ({ label: d.label, value: Number(d.value) }))
  } catch { /* 字典可空，不阻断列表 */ }
  await loadOrgDirectory()
  load()
})

async function act(row: ExpenseVO, fn: (id: string) => Promise<unknown>, ok: string) {
  try { await fn(row.id); message.success(ok); load() }
  catch (e: any) { message.error(e?.message || '操作失败') }
}
function onReject(row: ExpenseVO) {
  Modal.confirm({
    title: '驳回报销单', content: `确定驳回 ${row.no} 吗？`, centered: true,
    onOk: () => act(row, rejectExpense, `报销单 ${row.no} 已驳回`)
  })
}
function onDelete(row: ExpenseVO) {
  Modal.confirm({
    title: '删除报销单', content: `确定删除草稿报销单 ${row.no} 吗？`, centered: true,
    onOk: async () => {
      try { await deleteExpense(row.id); message.success('已删除'); load() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}

const drawerOpen = ref(false)
const saving = ref(false)
const form = reactive<ExpenseSave>({
  applicant: '', expenseType: '', reason: '', amount: 0, dept: '',
  invoiceType: '', taxRate: undefined, taxAmount: 0,
  date: dayjs().format('YYYY-MM-DD'), fileIds: []
})
/** 税额 = 金额 × 税率（字典值为百分数，如 3 表示 3%），金额或税率变化即重算；税额不允许手填 */
function calcTaxAmount(): number {
  const amount = Number(form.amount) || 0
  const rate = Number(form.taxRate) || 0
  return Number((amount * rate / 100).toFixed(2))
}
watch([() => form.amount, () => form.taxRate], () => {
  form.taxAmount = calcTaxAmount()
}, { immediate: true })
/** 上传态：只保留 file_id 与签名预览 URL，文件本体不落业务库 */
interface Attachment { uid: string; fileId: string; url: string; name: string }
const attachments = ref<Attachment[]>([])
const uploadFileList = ref<any[]>([])

/** 旧逻辑兜底：EHR 未返回默认部门时，仍尽量用本地组织目录推导 */
const currentApplicant = computed(() => {
  const me = store.me
  const id = me?.accountId == null ? '' : String(me.accountId)
  return employees.value.find((e) => e.id === id)?.name || store.displayName || ''
})
const currentDept = computed(() => {
  const me = store.me
  const id = me?.accountId == null ? '' : String(me.accountId)
  const emp = employees.value.find((e) => e.id === id)
  return emp?.orgId ? (orgNodes.value.find((n) => n.id === emp.orgId)?.name || '') : ''
})

function subjectOf(option?: ExpenseTypeOption): { code: string; name: string } | null {
  if (!option?.remark) return null
  try {
    const parsed = JSON.parse(option.remark)
    if (parsed && typeof parsed === 'object') {
      return { code: String(parsed.subjectCode || ''), name: String(parsed.subjectName || '') }
    }
  } catch { /* 兼容 remark 直接写科目编码的历史配置 */ }
  return { code: option.remark, name: '' }
}
const selectedType = computed(() => expenseTypes.value.find((t) => t.value === form.expenseType))
const derivedSubject = computed(() => subjectOf(selectedType.value))
const subjectText = computed(() => {
  const s = derivedSubject.value
  return s ? `${s.code} ${s.name}`.trim() : ''
})
function typeName(row: ExpenseVO) {
  return row.expenseTypeName || row.expenseType || '-'
}

async function openCreate() {
  let profile: { applicant: string; dept: string } | null = null
  try { profile = await fetchExpenseProfile() } catch { /* EHR 不可用时回退本地目录 */ }
  Object.assign(form, {
    applicant: profile?.applicant || currentApplicant.value,
    expenseType: '', reason: '', amount: 0,
    invoiceType: '', taxRate: undefined, taxAmount: 0,
    dept: profile?.dept || currentDept.value,
    date: dayjs().format('YYYY-MM-DD'),
    fileIds: []
  })
  attachments.value = []
  uploadFileList.value = []
  drawerOpen.value = true
}

async function onUpload(option: any) {
  try {
    const file = option.file as File
    const res = await uploadExpenseAttachment(file)
    attachments.value.push({
      uid: String((option.file as any).uid || `${Date.now()}-${Math.random()}`),
      fileId: res.fileId, url: res.url, name: file.name
    })
    option.onSuccess?.({} as never)
  } catch (e: any) {
    option.onError?.(e)
    message.error(e?.message || '凭证上传失败')
  }
}
function onRemove(file: any) {
  attachments.value = attachments.value.filter((a) => a.uid !== String(file.uid))
  return true
}

async function submit() {
  if (!form.applicant?.trim() || !form.reason?.trim() || !form.amount || form.amount <= 0 || !form.date) {
    message.warning('请填写申请人、费用事由、金额与报销日期')
    return
  }
  if (!form.expenseType) { message.warning('请选择报销类型'); return }
  if (!attachments.value.length) { message.warning('请上传报销凭证附件'); return }
  saving.value = true
  try {
    await createExpense({
      ...form,
      applicant: form.applicant || currentApplicant.value,
      dept: form.dept || currentDept.value,
      amount: Number(form.amount),
      taxAmount: calcTaxAmount(),
      fileIds: attachments.value.map((a) => a.fileId)
    })
    drawerOpen.value = false
    message.success('报销单已创建，可在列表提交审批')
    page.value = 1; load()
  } catch (e: any) { message.error(e?.message || '发起报销失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <h2>费用报销</h2>
      <span class="divider" />
      <a-input-search v-model:value="keyword" placeholder="单号 / 申请人 / 事由" style="width: 220px" allow-clear />
      <a-select v-model:value="status" :options="statusOptions" placeholder="状态" style="width: 110px" allow-clear />
      <a-select v-model:value="dept" :options="deptOptions" placeholder="归属部门" style="width: 160px" allow-clear show-search :filter-option="filterOption" />
      <a-range-picker v-model:value="range" format="YYYY-MM-DD" />
      <a-button @click="reset">重置</a-button>
      <span class="grow" />
      <a-button v-if="canCreate()" type="primary" @click="openCreate">＋ 发起报销</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <a-table class="ft-table" style="--ft-min-width: 1730px" row-key="id" :columns="columns" :data-source="rows"
               :loading="loading" :pagination="pagination()" @change="onTableChange"
               :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :scroll="rows.length ? { x: 1730 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'expenseType'">{{ typeName(record) }}</template>
          <template v-else-if="column.key === 'reason'">
            <a-tooltip :title="record.reason"><span class="cell-ellipsis">{{ record.reason }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
          <template v-else-if="column.key === 'subject'">
            <a-tooltip v-if="record.subject" :title="`${record.subjectCode || ''} ${record.subject}`.trim()">
              <span class="cell-ellipsis">{{ record.subject }}</span>
            </a-tooltip>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'attachments'">
            <a v-if="record.attachmentUrls?.length" :href="record.attachmentUrls[0]" target="_blank" rel="noopener">
              凭证（{{ record.attachmentUrls.length }}）
            </a>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'voucher'">
            <a v-if="record.voucherNo" @click="router.push({ path: '/voucher', query: { keyword: record.voucherNo } })">{{ record.voucherNo }}</a>
            <span v-else class="muted">未生成</span>
          </template>
          <template v-else-if="column.key === 'date'">{{ fmtDate(record.date) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a v-if="canCreate() && record.status === 'DRAFT'" @click="act(record, submitExpense, `报销单 ${record.no} 已提交`)">提交</a>
              <template v-if="canAudit()">
                <a v-if="record.status === 'PENDING'" @click="act(record, approveExpense, `报销单 ${record.no} 已审批通过`)">审批通过</a>
                <a v-if="record.status === 'PENDING'" @click="onReject(record)">驳回</a>
                <a v-if="record.status === 'APPROVED'" @click="act(record, postExpense, `已为 ${record.no} 生成凭证并过账`)">生成凭证并过账</a>
                <a v-if="record.status === 'POSTED'" @click="act(record, payExpense, `报销单 ${record.no} 已付款`)">付款</a>
              </template>
              <a v-if="canCreate() && record.status === 'DRAFT'" @click="onDelete(record)">删除</a>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无报销单" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="drawerOpen" title="发起报销申请" :width="620" placement="right">
      <a-form layout="vertical">
        <a-form-item label="申请人" required>
          <a-input :value="form.applicant" readonly placeholder="自动取当前登录人" />
        </a-form-item>
        <a-form-item label="报销类型" required>
          <a-select v-model:value="form.expenseType"
                    :options="expenseTypes.map((t) => ({ label: t.label, value: t.value }))"
                    show-search option-filter-prop="label" placeholder="请选择报销类型" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="金额" required>
              <a-input-number v-model:value="form.amount" :min="0" :precision="2" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="归属部门" required>
              <a-input :value="form.dept" readonly placeholder="自动取当前登录人所在部门" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="会计科目">
          <a-input :value="subjectText" readonly placeholder="选择报销类型后自动带出" />
          <template #extra>科目由报销类型映射带出，非财务人员无需选择；如需调整请联系财务在配置中心维护。</template>
        </a-form-item>
        <a-form-item label="费用事由" required>
          <a-textarea v-model:value="form.reason" :rows="3" placeholder="请输入报销事由" />
        </a-form-item>
        <a-form-item label="发票类型">
          <a-select v-model:value="form.invoiceType" :options="invoiceTypeOptions" placeholder="从字典选择（选填）"
                    allow-clear show-search option-filter-prop="label" style="width: 100%" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="税率">
              <a-select v-model:value="form.taxRate" :options="taxRateOptions" placeholder="从字典选择（选填）"
                        allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="税额">
              <a-input-number :value="form.taxAmount" readonly :precision="2" style="width: 100%" addon-before="¥" />
              <template #extra>由金额 × 税率自动计算，不允许手输</template>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="报销日期" required>
          <a-date-picker v-model:value="form.date" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="报销凭证" required>
          <a-upload v-model:file-list="uploadFileList" :custom-request="onUpload" :on-remove="onRemove"
                    accept=".jpg,.jpeg,.png,.pdf,.ofd,.doc,.docx,.xls,.xlsx,.zip" multiple>
            <a-button>上传凭证</a-button>
          </a-upload>
          <template #extra>支持 jpg/png/pdf/doc/xls/zip，单文件不超过 20MB；附件由统一文件服务托管。</template>
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="drawerOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">提交报销单</a-button>
        </div>
      </template>
    </a-drawer>
  </section>
</template>
