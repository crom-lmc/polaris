<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  listItems, saveItem, deleteItem, acceptItem, getMaterialTree,
  type ItemVO, type MaterialVO, type ItemSave
} from '@/api/contract'
import { yuan } from '@/utils/format'

const props = defineProps<{ contractNo: string }>()
const store = useUserStore()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 620))
const smallDrawerWidth = computed(() => (isMobile.value ? '100%' : 480))

const loading = ref(false)
const rows = ref<ItemVO[]>([])
const materials = ref<{ label: string; value: string; spec: string; unit: string; price: number }[]>([])

const columns = [
  { title: '行号', dataIndex: 'lineNo', key: 'lineNo', align: 'center' as const, width: 60 },
  { title: '物料', key: 'materialName', width: 150 },
  { title: '规格型号', dataIndex: 'spec', key: 'spec', width: 140 },
  { title: '单位', dataIndex: 'unit', key: 'unit', align: 'center' as const, width: 60 },
  { title: '数量', dataIndex: 'qty', key: 'qty', align: 'right' as const, width: 80 },
  { title: '单价', key: 'price', align: 'right' as const, width: 100 },
  { title: '金额', key: 'amount', align: 'right' as const, width: 110 },
  { title: '交付进度', key: 'delivered', width: 130 },
  { title: '操作', key: 'action', width: 140 }
]

async function load() {
  loading.value = true
  try { rows.value = await listItems(props.contractNo) }
  catch (e: any) { message.error(e?.message || '加载清单失败') }
  finally { loading.value = false }
}
async function loadMaterials() {
  try {
    const tree = await getMaterialTree()
    const flat: MaterialVO[] = []
    const walk = (ns: MaterialVO[]) => ns.forEach((n) => { flat.push(n); if (n.children?.length) walk(n.children) })
    walk(tree || [])
    materials.value = flat.filter((n) => n.kind === 'MATERIAL').map((n) => ({
      label: `${n.code} · ${n.name}`, value: n.code, spec: n.spec || '', unit: n.unit || '', price: Number(n.referencePrice || 0)
    }))
  } catch { /* 物料库不可用保持空 */ }
}
function filterOption(input: string, option: any) {
  return String(option?.label || '').toLowerCase().includes(input.toLowerCase())
}
function onMaterialChange(code: string) {
  const m = materials.value.find((x) => x.value === code)
  if (m) {
    form.materialName = m.label.split(' · ')[1] || ''
    form.spec = m.spec
    form.unit = m.unit
    if (!form.price) form.price = m.price
  }
}

const editOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({
  materialCode: '', materialName: '', spec: '', unit: '', qty: 1, price: 0, remark: ''
})
const amount = computed(() => Number(((Number(form.qty) || 0) * (Number(form.price) || 0)).toFixed(2)))
function openEdit(row?: ItemVO) {
  editingId.value = row?.id || null
  form.materialCode = row?.materialCode || ''
  form.materialName = row?.materialName || ''
  form.spec = row?.spec || ''
  form.unit = row?.unit || ''
  form.qty = row ? Number(row.qty || 0) : 1
  form.price = row ? Number(row.price || 0) : 0
  form.remark = row?.remark || ''
  editOpen.value = true
}
async function submit() {
  if (!form.materialCode || !form.qty || form.price < 0) { message.warning('请选择物料并填写数量与单价'); return }
  saving.value = true
  try {
    const payload: ItemSave = {
      id: editingId.value || undefined, materialCode: form.materialCode, materialName: form.materialName || undefined,
      spec: form.spec || undefined, unit: form.unit || undefined,
      qty: Number(form.qty), price: Number(form.price), amount: amount.value, remark: form.remark || undefined
    }
    await saveItem(props.contractNo, payload)
    editOpen.value = false
    message.success('清单行已保存')
    await load()
  } catch (e: any) { message.error(e?.message || '保存失败：' + (e?.message || '')) }
  finally { saving.value = false }
}
function remove(row: ItemVO) {
  Modal.confirm({
    centered: true, title: '删除清单行', content: `确定删除「${row.materialName}」吗？`,
    okButtonProps: { danger: true },
    onOk: async () => {
      try { await deleteItem(props.contractNo, row.id); message.success('已删除'); await load() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}

/* 到货验收 */
const acceptOpen = ref(false)
const acceptSaving = ref(false)
const acceptId = ref('')
const acceptForm = reactive({ qty: 1, date: undefined as string | undefined, result: 'PASS', remark: '' })
const acceptRow = computed(() => rows.value.find((r) => r.id === acceptId.value) || null)
const acceptRemain = computed(() => acceptRow.value ? Math.max(0, Number(acceptRow.value.qty || 0) - Number(acceptRow.value.deliveredQty || 0)) : 0)
function openAccept(row: ItemVO) {
  acceptId.value = row.id
  acceptForm.qty = Math.max(1, acceptRemain.value || 1)
  acceptForm.date = undefined
  acceptForm.result = 'PASS'
  acceptForm.remark = ''
  acceptOpen.value = true
}
async function submitAccept() {
  if (!acceptForm.qty || acceptForm.qty < 1 || acceptForm.qty > acceptRemain.value) {
    message.warning(`验收数量须在 1 ~ ${acceptRemain.value} 之间`); return
  }
  acceptSaving.value = true
  try {
    await acceptItem(props.contractNo, acceptId.value, {
      qty: Number(acceptForm.qty), date: acceptForm.date, result: acceptForm.result, remark: acceptForm.remark || undefined
    })
    acceptOpen.value = false
    message.success('验收已登记')
    await load()
  } catch (e: any) { message.error(e?.message || '验收失败') }
  finally { acceptSaving.value = false }
}

onMounted(() => { load(); loadMaterials() })
</script>

<template>
  <div class="contract-items-editor">
    <div class="items-toolbar">
      <span class="hint">合同金额由清单合计派生；超项目预算由服务端校验</span>
      <a-tag color="blue">清单 {{ rows.length }} 行</a-tag>
      <a-button v-if="store.can('contract:item:edit')" type="primary" size="small" @click="openEdit()">＋ 新增清单行</a-button>
    </div>
    <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :loading="loading" :pagination="false"
             :class="{ 'tbl-empty-mode': !loading && !rows.length }" :scroll="rows.length ? { x: 930 } : undefined">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'materialName'">
          <a-tooltip :title="`${record.materialName} ${record.spec || ''}`"><span class="cell-ellipsis">{{ record.materialName }}</span></a-tooltip>
        </template>
        <template v-else-if="['price', 'amount'].includes(String(column.key))">{{ yuan(record[column.key as 'price' | 'amount']) }}</template>
        <template v-else-if="column.key === 'delivered'">
          <a-progress :percent="Number(record.qty) ? Math.min(100, Math.round(Number(record.deliveredQty || 0) / Number(record.qty) * 100)) : 0" size="small" />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <template v-if="store.can('contract:item:edit')">
              <a v-if="Number(record.deliveredQty || 0) < Number(record.qty || 0)" @click="openAccept(record)">验收</a>
              <a @click="openEdit(record)">编辑</a>
              <a @click="remove(record)">删除</a>
            </template>
            <span v-else class="muted">只读</span>
          </a-space>
        </template>
      </template>
      <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="该合同暂无清单明细" /></div></template>
    </a-table>

    <a-drawer v-model:open="editOpen" :title="editingId ? '编辑清单行' : '新增清单行'" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="所属合同"><a-input :value="contractNo" readonly /></a-form-item>
        <a-form-item label="物料" required>
          <a-select v-model:value="form.materialCode" :options="materials" placeholder="从物料库选择物料" show-search
                    :filter-option="filterOption" @change="onMaterialChange" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="物料名称"><a-input v-model:value="form.materialName" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="规格型号"><a-input v-model:value="form.spec" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="8"><a-form-item label="单位"><a-input v-model:value="form.unit" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="数量" required><a-input-number v-model:value="form.qty" :min="0" style="width: 100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="单价" required><a-input-number v-model:value="form.price" :min="0" :precision="2" style="width: 100%" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="行金额"><a-input :value="yuan(amount)" readonly /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="editOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存清单行</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="acceptOpen" title="到货验收" :width="smallDrawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item label="物料"><a-input :value="acceptRow ? `${acceptRow.materialName} · ${acceptRow.spec}` : ''" readonly /></a-form-item>
        <a-form-item label="合同数量"><a-input :value="acceptRow ? `${acceptRow.qty} ${acceptRow.unit || ''}` : ''" readonly /></a-form-item>
        <a-form-item label="已交付"><a-input :value="acceptRow ? String(acceptRow.deliveredQty || 0) : ''" readonly /></a-form-item>
        <a-form-item label="本次验收数量" required>
          <a-input-number v-model:value="acceptForm.qty" :min="1" :max="acceptRemain" style="width: 100%" />
        </a-form-item>
        <a-form-item label="验收日期"><a-date-picker v-model:value="acceptForm.date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" /></a-form-item>
        <a-form-item label="验收结果">
          <a-select v-model:value="acceptForm.result" :options="[{ label: '合格', value: 'PASS' }, { label: '不合格', value: 'FAIL' }]" />
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="acceptForm.remark" :rows="2" /></a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="acceptOpen = false">取消</a-button>
          <a-button type="primary" :loading="acceptSaving" @click="submitAccept">确认验收</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.contract-items-editor { margin-top: 4px; }
.items-toolbar { display: flex; align-items: center; gap: 12px; margin: 4px 0 8px; }
.items-toolbar .hint { flex: 1 1 auto; }
</style>
