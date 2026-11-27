<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import {
  listSubjects, createSubject, updateSubject, toggleSubject, deleteSubject,
  listBudgetSubjects, createBudgetSubject, updateBudgetSubject, toggleBudgetSubject, deleteBudgetSubject,
  type SubjectVO, type BudgetSubjectVO, type SubjectSave, type BudgetSubjectSave
} from '@/api/finance'
import { useUserStore } from '@/stores/user'
import { categoryLabel, directionLabel, fmtDateTime, statusColor, statusLabel, yuan } from '@/utils/format'

const route = useRoute()
const store = useUserStore()
const canEdit = () => store.can(kind.value === 'subject' ? 'finance:account-subject:edit' : 'finance:budget-subject:edit')

type Kind = 'subject' | 'budget'
const kind = ref<Kind>(route.path === '/budget-subject' ? 'budget' : 'subject')
const currentYear = dayjs().year()
const yearOptions = [currentYear + 1, currentYear, currentYear - 1, currentYear - 2]
  .map((y) => ({ label: `${y} 年`, value: y }))
const directionOptions = [{ label: '借', value: 'DEBIT' }, { label: '贷', value: 'CREDIT' }]
const categoryOptions = [
  { label: '资产', value: 'ASSET' }, { label: '负债', value: 'LIABILITY' },
  { label: '权益', value: 'EQUITY' }, { label: '收入', value: 'INCOME' }, { label: '费用', value: 'EXPENSE' }
]
const statusOptions = [{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]

const loading = ref(false)
const keyword = ref('')
const status = ref<string>()
const level = ref<number>()
const category = ref<string>()
const year = ref<number>()
const rows = ref<Array<SubjectVO | BudgetSubjectVO>>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const title = computed(() => kind.value === 'subject' ? '会计科目' : '预算科目')

function columnsFor(k: Kind) {
  const common = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
    { title: '名称', key: 'name', width: 260 },
    { title: '状态', key: 'status', align: 'center' as const, width: 90 },
    { title: '创建时间', key: 'createdAt', width: 150 },
    { title: '操作', key: 'action', width: 190 }
  ]
  if (k === 'subject') {
    return [
      common[0], common[1],
      { title: '级次', dataIndex: 'level', key: 'level', align: 'center' as const, width: 80 },
      { title: '余额方向', key: 'direction', align: 'center' as const, width: 100 },
      { title: '科目类别', key: 'category', align: 'center' as const, width: 110 },
      common[2], common[3], common[4]
    ]
  }
  return [
    common[0], common[1],
    { title: '年度', dataIndex: 'year', key: 'year', align: 'center' as const, width: 80 },
    { title: '年度额度', key: 'annualAmount', align: 'right' as const, width: 140 },
    { title: '关联会计科目', dataIndex: 'accountSubjectCode', key: 'accountSubjectCode', width: 180 },
    common[2], common[3], common[4]
  ]
}
const columns = computed(() => columnsFor(kind.value))
const pagination = () => ({
  current: page.value, pageSize: size.value, total: total.value,
  showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条`
})

async function load() {
  loading.value = true
  try {
    if (kind.value === 'subject') {
      const res = await listSubjects({
        keyword: keyword.value || undefined, status: status.value,
        level: level.value, category: category.value, page: page.value, size: size.value
      })
      rows.value = res.list || []
      total.value = res.total
    } else {
      const res = await listBudgetSubjects({
        keyword: keyword.value || undefined, year: year.value, status: status.value,
        page: page.value, size: size.value
      })
      rows.value = res.list || []
      total.value = res.total
    }
  } catch (e: any) { message.error(e?.message || `加载${title.value}失败`) }
  finally { loading.value = false }
}
function onTableChange(p: any) {
  const changed = p.pageSize !== size.value
  page.value = changed ? 1 : p.current
  size.value = p.pageSize
  load()
}
function resetFilter() {
  keyword.value = ''; status.value = undefined; level.value = undefined
  category.value = undefined; year.value = undefined; page.value = 1; load()
}
watch([keyword, status, level, category, year], () => { page.value = 1; load() })
watch(() => route.path, (p) => {
  const next: Kind = p === '/budget-subject' ? 'budget' : 'subject'
  if (next !== kind.value) kind.value = next
})
watch(kind, () => { page.value = 1; resetFilter() })
onMounted(load)

async function onToggle(row: SubjectVO | BudgetSubjectVO) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    if (kind.value === 'subject') await toggleSubject((row as SubjectVO).id, next)
    else await toggleBudgetSubject((row as BudgetSubjectVO).id, next)
    message.success(`${row.name} 已${statusLabel(next)}`)
    load()
  } catch (e: any) { message.error(e?.message || '启停失败') }
}
function onDelete(row: SubjectVO | BudgetSubjectVO) {
  Modal.confirm({
    title: `删除${title.value}`, content: `确定删除 ${row.code} ${row.name} 吗？`, centered: true,
    onOk: async () => {
      try {
        if (kind.value === 'subject') await deleteSubject((row as SubjectVO).id)
        else await deleteBudgetSubject((row as BudgetSubjectVO).id)
        message.success('已删除'); load()
      } catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}

const drawerOpen = ref(false)
const editing = ref<string>()
const saving = ref(false)
const subjectForm = reactive<SubjectSave>({ code: '', name: '', level: 1, balanceDirection: 'DEBIT', category: 'ASSET', status: 'ENABLED' })
const budgetForm = reactive<BudgetSubjectSave>({ code: '', name: '', year: currentYear, annualAmount: 0, accountSubjectCode: '', status: 'ENABLED' })

function openCreate() {
  editing.value = undefined
  if (kind.value === 'subject') {
    Object.assign(subjectForm, { code: '', name: '', level: 1, balanceDirection: 'DEBIT', category: 'ASSET', status: 'ENABLED' })
  } else {
    Object.assign(budgetForm, { code: '', name: '', year: year.value || currentYear, annualAmount: 0, accountSubjectCode: '', status: 'ENABLED' })
  }
  drawerOpen.value = true
}
function openEdit(row: SubjectVO | BudgetSubjectVO) {
  editing.value = row.id
  if (kind.value === 'subject') {
    const s = row as SubjectVO
    Object.assign(subjectForm, { code: s.code, name: s.name, level: s.level, balanceDirection: s.balanceDirection, category: s.category, status: s.status })
  } else {
    const b = row as BudgetSubjectVO
    Object.assign(budgetForm, { code: b.code, name: b.name, year: b.year, annualAmount: Number(b.annualAmount || 0), accountSubjectCode: b.accountSubjectCode || '', status: b.status })
  }
  drawerOpen.value = true
}
async function submit() {
  if (kind.value === 'subject') {
    if (!subjectForm.code?.trim() || !subjectForm.name?.trim()) { message.warning('请填写科目编码与名称'); return }
    saving.value = true
    try {
      if (editing.value) await updateSubject(editing.value, { ...subjectForm })
      else await createSubject({ ...subjectForm })
    } catch (e: any) { message.error(e?.message || '保存失败'); return }
    finally { saving.value = false }
  } else {
    if (!budgetForm.code?.trim() || !budgetForm.name?.trim() || !budgetForm.year) { message.warning('请填写科目编码、名称与年度'); return }
    saving.value = true
    try {
      if (editing.value) await updateBudgetSubject(editing.value, { ...budgetForm })
      else await createBudgetSubject({ ...budgetForm })
    } catch (e: any) { message.error(e?.message || '保存失败'); return }
    finally { saving.value = false }
  }
  drawerOpen.value = false
  message.success('已保存')
  load()
}
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <a-input-search v-model:value="keyword" placeholder="编码 / 名称 / 关联科目" style="width: 220px" allow-clear />
      <a-select v-model:value="status" :options="statusOptions" placeholder="状态" style="width: 100px" allow-clear />
      <template v-if="kind === 'subject'">
        <a-select v-model:value="level" placeholder="级次" style="width: 90px" allow-clear
                  :options="[{ label: '1 级', value: 1 }, { label: '2 级', value: 2 }, { label: '3 级', value: 3 }]" />
        <a-select v-model:value="category" :options="categoryOptions" placeholder="科目类别" style="width: 120px" allow-clear />
      </template>
      <a-select v-else v-model:value="year" :options="yearOptions" placeholder="年度" style="width: 110px" allow-clear />
      <a-button @click="resetFilter">重置</a-button>
      <span class="grow" />
      <a-button v-if="canEdit()" type="primary" @click="openCreate">＋ 新增{{ title }}</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <a-alert type="info" show-icon class="drawer-alert"
               :message="title"
               :description="kind === 'subject'
                 ? '会计科目按编码与级次维护，余额方向与科目类别使用后端统一枚举；启停后不可再被新单据选用。'
                 : '预算科目按年度维护额度与关联会计科目，供预算占用与合同域只读引用。'" />
      <a-table class="ft-table" style="--ft-min-width: 1240px" row-key="id" :columns="columns" :data-source="rows"
               :loading="loading" :pagination="pagination()" @change="onTableChange"
               :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :scroll="rows.length ? { x: 1240 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-tooltip :title="record.name"><span class="cell-ellipsis">{{ record.name }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'direction'">{{ directionLabel(record.balanceDirection) }}</template>
          <template v-else-if="column.key === 'category'">{{ categoryLabel(record.category) }}</template>
          <template v-else-if="column.key === 'annualAmount'">{{ yuan(record.annualAmount) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'accountSubjectCode'">
            <a-tooltip :title="record.accountSubjectCode"><span class="cell-ellipsis">{{ record.accountSubjectCode || '-' }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ fmtDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <template v-if="canEdit()">
                <a @click="openEdit(record)">编辑</a>
                <a @click="onToggle(record)">{{ record.status === 'ENABLED' ? '停用' : '启用' }}</a>
                <a @click="onDelete(record)">删除</a>
              </template>
              <span v-else class="muted">只读</span>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" :description="`暂无${title}`" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="drawerOpen" :title="`${editing ? '编辑' : '新增'}${title}`" :width="520" placement="right">
      <a-form layout="vertical">
        <template v-if="kind === 'subject'">
          <a-form-item label="科目编码" required>
            <a-input v-model:value="subjectForm.code" :disabled="!!editing" placeholder="如 660201" />
          </a-form-item>
          <a-form-item label="科目名称" required><a-input v-model:value="subjectForm.name" /></a-form-item>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="级次">
                <a-select v-model:value="subjectForm.level" :options="[{ label: '1 级', value: 1 }, { label: '2 级', value: 2 }, { label: '3 级', value: 3 }]" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="余额方向">
                <a-select v-model:value="subjectForm.balanceDirection" :options="directionOptions" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="科目类别">
            <a-select v-model:value="subjectForm.category" :options="categoryOptions" />
          </a-form-item>
        </template>
        <template v-else>
          <a-form-item label="科目编码" required>
            <a-input v-model:value="budgetForm.code" :disabled="!!editing" placeholder="如 BS-2026-004" />
          </a-form-item>
          <a-form-item label="科目名称" required><a-input v-model:value="budgetForm.name" /></a-form-item>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="年度" required>
                <a-select v-model:value="budgetForm.year" :options="yearOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="年度额度">
                <a-input-number v-model:value="budgetForm.annualAmount" :min="0" :precision="2" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="关联会计科目编码">
            <a-input v-model:value="budgetForm.accountSubjectCode" placeholder="如 6602" />
          </a-form-item>
        </template>
        <a-form-item v-if="kind === 'subject'" label="状态">
          <a-select v-model:value="subjectForm.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item v-else label="状态">
          <a-select v-model:value="budgetForm.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="drawerOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
        </div>
      </template>
    </a-drawer>
  </section>
</template>
