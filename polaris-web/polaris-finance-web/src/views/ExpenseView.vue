<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import dayjs, { type Dayjs } from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import {
  listExpenses, createExpense, submitExpense, approveExpense, rejectExpense,
  postExpense, payExpense, deleteExpense, subjectOptions, listBudgets,
  type ExpenseVO, type ExpenseSave, type SubjectVO, type BudgetVO
} from '@/api/finance'
import { useUserStore } from '@/stores/user'
import { dateOf, fmtDate, statusColor, statusLabel, yuan } from '@/utils/format'

const router = useRouter()
const store = useUserStore()
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

const subjects = ref<SubjectVO[]>([])
const depts = ref<string[]>([])
const statusOptions = [
  { label: '草稿', value: 'DRAFT' }, { label: '待审批', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' }, { label: '已驳回', value: 'REJECTED' },
  { label: '已入账', value: 'POSTED' }, { label: '已付款', value: 'PAID' }
]

const columns = [
  { title: '单号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 100 },
  { title: '事由', key: 'reason', width: 260 },
  { title: '金额', key: 'amount', align: 'right' as const, width: 120 },
  { title: '归属部门', dataIndex: 'dept', key: 'dept', width: 110 },
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
  try {
    const [ss, bs] = await Promise.all([subjectOptions(), listBudgets({ page: 1, size: 500 })])
    subjects.value = ss
    depts.value = [...new Set((bs.list || []).map((b: BudgetVO) => b.dept).filter(Boolean))]
  } catch { /* 下拉可空，不阻断列表 */ }
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
  applicant: '', reason: '', amount: 0, dept: '', subject: '', date: dayjs().format('YYYY-MM-DD')
})
function openCreate() {
  Object.assign(form, { applicant: '', reason: '', amount: 0, dept: depts.value[0] || '', subject: '', date: dayjs().format('YYYY-MM-DD') })
  drawerOpen.value = true
}
async function submit() {
  if (!form.applicant?.trim() || !form.reason?.trim() || !form.amount || form.amount <= 0 || !form.date) {
    message.warning('请填写申请人、费用事由、金额与报销日期')
    return
  }
  saving.value = true
  try {
    await createExpense({ ...form, amount: Number(form.amount) })
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
      <a-select v-model:value="dept" :options="depts.map((d) => ({ label: d, value: d }))" placeholder="归属部门" style="width: 130px" allow-clear />
      <a-range-picker v-model:value="range" format="YYYY-MM-DD" />
      <a-button @click="reset">重置</a-button>
      <span class="grow" />
      <a-button v-if="canCreate()" type="primary" @click="openCreate">＋ 发起报销</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <a-table class="ft-table" style="--ft-min-width: 1380px" row-key="id" :columns="columns" :data-source="rows"
               :loading="loading" :pagination="pagination()" @change="onTableChange"
               :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :scroll="rows.length ? { x: 1380 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'reason'">
            <a-tooltip :title="record.reason"><span class="cell-ellipsis">{{ record.reason }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
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

    <a-drawer v-model:open="drawerOpen" title="发起报销申请" :width="520" placement="right">
      <a-form layout="vertical">
        <a-form-item label="申请人" required><a-input v-model:value="form.applicant" placeholder="请输入申请人" /></a-form-item>
        <a-form-item label="费用事由" required>
          <a-textarea v-model:value="form.reason" :rows="3" placeholder="请输入报销事由" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="金额" required>
              <a-input-number v-model:value="form.amount" :min="0" :precision="2" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="归属部门">
              <a-select v-model:value="form.dept" :options="depts.map((d) => ({ label: d, value: d }))" allow-clear placeholder="请选择部门" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="会计科目">
          <a-select v-model:value="form.subject" :options="subjects.map((s) => ({ label: `${s.code} ${s.name}`, value: s.name }))"
                    show-search option-filter-prop="label" allow-clear placeholder="请选择科目" />
        </a-form-item>
        <a-form-item label="报销日期" required>
          <a-date-picker v-model:value="form.date" value-format="YYYY-MM-DD" style="width: 100%" />
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
