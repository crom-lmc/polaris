<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import {
  listBudgets, createBudget, updateBudget, deleteBudget, type BudgetVO, type BudgetSave
} from '@/api/finance'
import { useUserStore } from '@/stores/user'
import { useOrgDirectory, filterOption as orgFilterOption } from '@/composables/useOrgDirectory'
import { paginatePicker, financeDeptColumns, orgRows } from '@/composables/useEntityPickers'
import EntityPickerModal, { type PickerLoadParams, type PickerResult, type PickerRow } from '@shared/components/EntityPickerModal.vue'
import { yuan } from '@/utils/format'

const store = useUserStore()
const canEdit = () => store.can('finance:budget:edit')
const { orgNodes, pathOf, deptOptions, load: loadOrgDirectory } = useOrgDirectory()
const filterOption = orgFilterOption

/* 归属部门弹窗 */
const deptPickerOpen = ref(false)
async function loadDeptPicker(params: PickerLoadParams): Promise<PickerResult> {
  return paginatePicker(orgRows(orgNodes.value, pathOf), params)
}
function onDeptPicked(row: PickerRow) { form.dept = row.name }

const loading = ref(false)
const rows = ref<BudgetVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const year = ref<number | undefined>(dayjs().year())
const dept = ref<string>()

const currentYear = dayjs().year()
const yearOptions = [currentYear + 1, currentYear, currentYear - 1, currentYear - 2]
  .map((y) => ({ label: `${y} 年`, value: y }))

const columns = [
  { title: '部门', dataIndex: 'dept', key: 'dept', width: 120 },
  { title: '年度', dataIndex: 'year', key: 'year', align: 'center' as const, width: 80 },
  { title: '预算额度', key: 'amount', align: 'right' as const, width: 130 },
  { title: '历史归档', key: 'history', align: 'right' as const, width: 120 },
  { title: '本期发生', key: 'current', align: 'right' as const, width: 120 },
  { title: '已使用', key: 'used', align: 'right' as const, width: 120 },
  { title: '可用额度', key: 'available', align: 'right' as const, width: 120 },
  { title: '合同承诺', key: 'contractCommitted', align: 'right' as const, width: 120 },
  { title: '合同已付', key: 'contractPaid', align: 'right' as const, width: 120 },
  { title: '合同在途', key: 'contractInFlight', align: 'right' as const, width: 120 },
  { title: '执行率', key: 'rate', width: 200 },
  { title: '操作', key: 'action', width: 110 }
]
const pagination = () => ({
  current: page.value, pageSize: size.value, total: total.value,
  showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条`
})

async function load() {
  loading.value = true
  try {
    const res = await listBudgets({ year: year.value, dept: dept.value || undefined, page: page.value, size: size.value })
    rows.value = res.list || []
    total.value = res.total
  } catch (e: any) { message.error(e?.message || '加载预算失败') }
  finally { loading.value = false }
}
function onTableChange(p: any) {
  const changed = p.pageSize !== size.value
  page.value = changed ? 1 : p.current
  size.value = p.pageSize
  load()
}
function reset() { year.value = currentYear; dept.value = undefined; page.value = 1; load() }
watch([year, dept], () => { page.value = 1; load() })
onMounted(() => { load(); loadOrgDirectory() })

/** 前端硬底线 95%；后端 alertFlag=RED 同样变红（阈值来自埋点，默认 80） */
const RED_LINE = 95
function isRed(row: BudgetVO) { return row.alertFlag === 'RED' || row.rate >= RED_LINE }
function progressStatus(row: BudgetVO): 'exception' | 'warning' | 'active' {
  if (isRed(row)) return 'exception'
  if (row.alertFlag === 'YELLOW') return 'warning'
  return 'active'
}
function stroke(row: BudgetVO) {
  return isRed(row) ? '#ff4d4f' : row.alertFlag === 'YELLOW' ? '#faad14' : '#2f54eb'
}

const drawerOpen = ref(false)
const editing = ref<string>()
const saving = ref(false)
const form = reactive<BudgetSave>({
  dept: '', year: currentYear, amount: 0, history: 0,
  contractCommitted: 0, contractPaid: 0, contractInFlight: 0
})
function openCreate() {
  editing.value = undefined
  Object.assign(form, {
    dept: '', year: year.value || currentYear, amount: 0, history: 0,
    contractCommitted: 0, contractPaid: 0, contractInFlight: 0
  })
  drawerOpen.value = true
}
function openEdit(row: BudgetVO) {
  editing.value = row.id
  Object.assign(form, {
    dept: row.dept, year: row.year, amount: Number(row.amount || 0), history: Number(row.history || 0),
    contractCommitted: Number(row.contractCommitted || 0), contractPaid: Number(row.contractPaid || 0),
    contractInFlight: Number(row.contractInFlight || 0)
  })
  drawerOpen.value = true
}
async function submit() {
  if (!form.dept?.trim() || !form.year || form.amount == null || form.amount < 0) {
    message.warning('请填写部门、年度与预算额度')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateBudget(editing.value, { ...form })
    else await createBudget({ ...form })
    drawerOpen.value = false
    message.success(editing.value ? '预算已更新' : '预算已创建')
    load()
  } catch (e: any) { message.error(e?.message || '保存预算失败') }
  finally { saving.value = false }
}
function onDelete(row: BudgetVO) {
  Modal.confirm({
    title: '删除预算', content: `确定删除 ${row.dept} ${row.year} 年的预算吗？`, centered: true,
    onOk: async () => {
      try { await deleteBudget(row.id); message.success('已删除'); load() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <a-select v-model:value="year" :options="yearOptions" style="width: 110px" />
      <a-select v-model:value="dept" :options="deptOptions" placeholder="归属部门" style="width: 180px" allow-clear show-search :filter-option="filterOption" />
      <a-button @click="reset">重置</a-button>
      <span class="grow" />
      <a-button v-if="canEdit()" type="primary" @click="openCreate">＋ 新增预算</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <a-table class="ft-table" style="--ft-min-width: 1500px" row-key="id" :columns="columns" :data-source="rows"
               :loading="loading" :pagination="pagination()" @change="onTableChange"
               :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :scroll="rows.length ? { x: 1500 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'dept'">
            <a-tooltip :title="record.dept"><span class="cell-ellipsis">{{ record.dept || '-' }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'rate'">
            <div class="rate-cell">
              <a-progress :percent="record.rate" size="small" :show-info="false" :status="progressStatus(record)" :stroke-color="stroke(record)" />
              <span class="rate-text">{{ record.rate }}%</span>
            </div>
          </template>
          <template v-else-if="['amount', 'history', 'current', 'used', 'available', 'contractCommitted', 'contractPaid', 'contractInFlight'].includes(String(column.key))">
            {{ yuan(record[column.key as string]) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a v-if="canEdit()" @click="openEdit(record)">编辑</a>
              <a v-if="canEdit()" @click="onDelete(record)">删除</a>
              <span v-if="!canEdit()" class="muted">-</span>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无预算" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="drawerOpen" :title="editing ? '编辑预算' : '新增预算'" :width="520" placement="right">
      <a-form layout="vertical">
        <a-form-item label="归属部门" required>
          <a-input :value="form.dept" readonly placeholder="点击选择" style="width: 100%; cursor: pointer"
                   @click="deptPickerOpen = true" />
        </a-form-item>
        <a-form-item label="预算年度" required>
          <a-select v-model:value="form.year" :options="yearOptions" />
        </a-form-item>
        <a-form-item label="年度额度" required>
          <a-input-number v-model:value="form.amount" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item label="历史归档已用">
          <a-input-number v-model:value="form.history" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="合同承诺占用">
              <a-input-number v-model:value="form.contractCommitted" :min="0" :precision="2" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="合同已付">
              <a-input-number v-model:value="form.contractPaid" :min="0" :precision="2" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="合同在途">
          <a-input-number v-model:value="form.contractInFlight" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="drawerOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
        </div>
      </template>
    </a-drawer>

    <EntityPickerModal v-model:open="deptPickerOpen" title="选择归属部门" :columns="financeDeptColumns"
                       row-key="name" :initial-value="form.dept" placeholder="部门名称 / 编码"
                       :load="loadDeptPicker" @confirm="onDeptPicked" />
  </section>
</template>
