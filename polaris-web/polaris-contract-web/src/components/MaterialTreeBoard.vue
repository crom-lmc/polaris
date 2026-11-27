<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  getCategoryTree, getCategoryList, getMaterials,
  saveCategory, updateCategory, deleteCategory,
  saveMaterial, updateMaterial, deleteMaterial,
  type MaterialCategoryVO, type MaterialVO,
  type MaterialCategorySave, type MaterialSave
} from '@/api/contract'
import { yuan } from '@/utils/format'

const props = defineProps<{ manage?: boolean }>()
const manage = computed(() => !!props.manage)
const store = useUserStore()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 560))

/** 物料库维护分类，物料维护维护物料，权限各自独立不复用 */
const canAddCategoryPerm = computed(() => !manage.value && store.can('contract:material:category:add'))
const canEditCategoryPerm = computed(() => !manage.value && store.can('contract:material:category:edit'))
const canAddMaterialPerm = computed(() => manage.value && store.can('contract:material:add'))
const canEditMaterialPerm = computed(() => manage.value && store.can('contract:material:edit'))

const loading = ref(false)
const tree = ref<MaterialCategoryVO[]>([])
const flatCategories = ref<MaterialCategoryVO[]>([])
const keyword = ref('')
const selectedKeys = ref<string[]>([])
const expandedKeys = ref<string[]>([])

function flatten(nodes: MaterialCategoryVO[], out: MaterialCategoryVO[] = []): MaterialCategoryVO[] {
  nodes.forEach((n) => { out.push(n); if (n.children?.length) flatten(n.children, out) })
  return out
}
async function loadTree() {
  loading.value = true
  try {
    const data = await getCategoryTree()
    tree.value = data || []
    flatCategories.value = flatten(tree.value)
    if (!selectedKeys.value.length && flatCategories.value.length) selectedKeys.value = [flatCategories.value[0].id]
    expandedKeys.value = flatCategories.value.map((n) => n.id)
  } catch (e: any) { message.error(e?.message || '加载分类树失败') }
  finally { loading.value = false }
}

/** 自绘树节点：缩进由 level 内联 padding-left 决定 */
type FlatNode = { key: string; title: string; level: number; hasChildren: boolean; parentKey: string | null }
function buildFlatNodes(nodes: MaterialCategoryVO[], out: FlatNode[] = [], level = 0, parentKey: string | null = null): FlatNode[] {
  for (const n of nodes) {
    out.push({ key: n.id, title: n.name, level, hasChildren: !!n.children?.length, parentKey })
    if (n.children?.length) buildFlatNodes(n.children, out, level + 1, n.id)
  }
  return out
}
const flatNodes = computed(() => buildFlatNodes(tree.value))
const visibleNodes = computed(() => {
  const byKey = new Map(flatNodes.value.map((n) => [n.key, n]))
  return flatNodes.value.filter((n) => {
    let p = n.parentKey
    while (p) {
      if (!expandedKeys.value.includes(p)) return false
      p = byKey.get(p)?.parentKey || null
    }
    return true
  })
})
function selectNode(key: string) { selectedKeys.value = [key] }
function isExpanded(key: string) { return expandedKeys.value.includes(key) }
function toggleExpand(key: string) {
  expandedKeys.value = isExpanded(key)
    ? expandedKeys.value.filter((k) => k !== key)
    : [...expandedKeys.value, key]
}

const currentNode = computed(() => flatCategories.value.find((n) => n.id === selectedKeys.value[0]) || null)
/** 选中分类层级：1/2/3 级；0=未选中 */
const selectedLevel = computed(() => {
  const n = currentNode.value
  if (!n) return 0
  const len = (n.code || '').length
  return len === 2 ? 1 : (len === 4 ? 2 : (len === 6 ? 3 : 0))
})
const categoryLevelOk = computed(() => selectedLevel.value === 1 || selectedLevel.value === 2)
const materialLevelOk = computed(() => selectedLevel.value === 3)
const addHint = computed(() => {
  if (!manage.value) {
    if (selectedLevel.value === 1) return '当前一级分类 → 可新增二级分类'
    if (selectedLevel.value === 2) return '当前二级分类 → 可新增三级分类'
    return '请选中一级或二级分类后新增分类'
  }
  return selectedLevel.value === 3 ? '当前三级分类 → 可新增物料' : '请选中三级分类后新增物料'
})

/* ============ 右侧列表数据 ============ */
const categoryRows = ref<MaterialCategoryVO[]>([])
const materialRows = ref<MaterialVO[]>([])
const rowsLoading = ref(false)
async function loadRows() {
  rowsLoading.value = true
  try {
    if (!manage.value) {
      categoryRows.value = await getCategoryList(currentNode.value?.id || null)
    } else {
      materialRows.value = selectedLevel.value === 3
        ? await getMaterials(currentNode.value?.id)
        : []
    }
  } catch (e: any) { message.error(e?.message || '加载列表失败') }
  finally { rowsLoading.value = false }
}
const keywordFiltered = <T extends { code?: string; name?: string }>(list: T[]) => {
  const kw = keyword.value.trim().toLowerCase()
  return list.filter((n) => !kw || (n.code || '').toLowerCase().includes(kw) || (n.name || '').toLowerCase().includes(kw))
}
const rows = computed(() => (manage.value ? keywordFiltered(materialRows.value) : keywordFiltered(categoryRows.value)))

/* ============ 表格列 ============ */
const categoryColumns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 170 },
  { title: '分类名称', key: 'name', width: 220 },
  { title: '操作', key: 'action', width: 140 }
]
const materialColumns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 170 },
  { title: '物料名称', key: 'name', width: 190 },
  { title: '规格型号', dataIndex: 'spec', key: 'spec', width: 170 },
  { title: '单位', dataIndex: 'unit', key: 'unit', align: 'center' as const, width: 80 },
  { title: '参考单价', key: 'referencePrice', align: 'right' as const, width: 130 },
  { title: '操作', key: 'action', width: 140 }
]
const columns = computed(() => {
  const base = manage.value ? materialColumns : categoryColumns
  const canEdit = manage.value ? canEditMaterialPerm.value : canEditCategoryPerm.value
  return canEdit ? base : base.filter((c) => c.key !== 'action')
})

/* ============ 抽屉表单 ============ */
const editOpen = ref(false)
const saving = ref(false)
const catForm = reactive<MaterialCategorySave & { id?: string }>({
  id: undefined, parentId: null, code: '', name: '', orgCode: ''
})
const matForm = reactive<MaterialSave & { id?: string }>({
  id: undefined, categoryId: null, parentId: null, code: '', name: '',
  spec: '', unit: '', referencePrice: undefined, orgCode: ''
})
const parentName = computed(() => {
  const id = manage.value ? matForm.categoryId : catForm.parentId
  const p = flatCategories.value.find((n) => n.id === id)
  return p ? `${p.name}（${p.code}）` : (manage.value ? '请选择三级分类' : '根节点（新增一级分类）')
})
function resetForms() {
  Object.assign(catForm, { id: undefined, parentId: null, code: '', name: '', orgCode: '' })
  Object.assign(matForm, { id: undefined, categoryId: null, parentId: null, code: '', name: '', spec: '', unit: '', referencePrice: undefined, orgCode: '' })
}
/** 新增类型由所属菜单按钮固定；上级固定为当前选中项，不在抽屉切换 */
function openCreate() {
  if (!manage.value) {
    if (!canAddCategoryPerm.value || !categoryLevelOk.value) {
      message.warning('请选中一级或二级分类后新增分类；三级分类下不允许继续新增分类')
      return
    }
    resetForms()
    catForm.parentId = currentNode.value?.id || null
  } else {
    if (!canAddMaterialPerm.value || !materialLevelOk.value) {
      message.warning('新增物料请先选中三级分类')
      return
    }
    resetForms()
    matForm.categoryId = currentNode.value?.id || null
  }
  editOpen.value = true
}
function openEdit(row: MaterialCategoryVO | MaterialVO) {
  resetForms()
  if (!manage.value) {
    if (!canEditCategoryPerm.value) return
    catForm.id = row.id
    catForm.parentId = (row as MaterialCategoryVO).parentId || null
    catForm.code = row.code
    catForm.name = row.name
    catForm.orgCode = (row as MaterialCategoryVO).orgCode || ''
  } else {
    if (!canEditMaterialPerm.value) return
    const m = row as MaterialVO
    matForm.id = row.id
    matForm.categoryId = m.categoryId || m.parentId || null
    matForm.code = row.code
    matForm.name = row.name
    matForm.spec = m.spec || ''
    matForm.unit = m.unit || ''
    matForm.referencePrice = m.referencePrice ?? undefined
    matForm.orgCode = m.orgCode || ''
  }
  editOpen.value = true
}
async function submit() {
  const form = manage.value ? matForm : catForm
  if (!form.name?.trim()) { message.warning(manage.value ? '请填写物料名称' : '请填写分类名称'); return }
  saving.value = true
  try {
    if (!manage.value) {
      const payload: MaterialCategorySave = {
        parentId: catForm.parentId || null, name: catForm.name.trim(), orgCode: catForm.orgCode || undefined
      }
      if (catForm.id) await updateCategory(catForm.id, payload)
      else await saveCategory(payload)
    } else {
      const payload: MaterialSave = {
        categoryId: matForm.categoryId || null, name: matForm.name.trim(),
        spec: matForm.spec || '', unit: matForm.unit || '', referencePrice: matForm.referencePrice ?? null,
        orgCode: matForm.orgCode || undefined
      }
      if (matForm.id) await updateMaterial(matForm.id, payload)
      else await saveMaterial(payload)
    }
    editOpen.value = false
    message.success(manage.value ? '物料已保存，编码由服务端续位' : '分类已保存，编码由服务端按层级续位')
    await loadTree()
    await loadRows()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
function remove(row: MaterialCategoryVO | MaterialVO) {
  const isCat = !manage.value
  if (isCat ? !canEditCategoryPerm.value : !canEditMaterialPerm.value) return
  Modal.confirm({
    centered: true, title: isCat ? '删除分类' : '删除物料',
    content: isCat
      ? `确定删除分类「${row.name}」吗？有子分类或已挂物料的分类不可删除。`
      : `确定删除物料「${row.name}」吗？已被合同清单或采购计划引用的物料不可删除。`,
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        if (isCat) await deleteCategory(row.id); else await deleteMaterial(row.id)
        message.success('已删除')
        await loadTree()
        await loadRows()
      } catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}
const drawerTitle = computed(() => {
  if (editOpen.value && (manage.value ? matForm.id : catForm.id)) return manage.value ? '编辑物料' : '编辑分类'
  if (!manage.value) return `新增${selectedLevel.value + 1}级分类`
  return '新增物料'
})
const editCode = computed(() => (manage.value ? matForm.code : catForm.code))

onMounted(async () => { await loadTree(); await loadRows() })
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <h2>{{ manage ? '物料维护' : '物料库' }}</h2>
      <span class="divider" />
      <a-input-search v-model:value="keyword" placeholder="编码 / 名称 / 规格" style="width: 240px" allow-clear />
      <span class="grow" />
      <span class="hint">{{ manage ? '三级分类 + 物料 · 编码由服务端自动生成' : '三级分类维护 · 编码由服务端自动生成' }}</span>
      <template v-if="!manage">
        <span v-if="canAddCategoryPerm" class="hint" :title="addHint">{{ addHint }}</span>
        <a-button v-if="canAddCategoryPerm" :disabled="!categoryLevelOk"
                  :title="categoryLevelOk ? '' : '请选中一级或二级分类'"
                  @click="openCreate">＋ 新增分类</a-button>
      </template>
      <template v-else>
        <span class="hint" :title="addHint">{{ addHint }}</span>
        <a-button v-if="canAddMaterialPerm" type="primary" :disabled="!materialLevelOk"
                  :title="materialLevelOk ? '' : '请选中三级分类'"
                  @click="openCreate">＋ 新增物料</a-button>
      </template>
    </section>
    <section class="ft-scroll">
      <div class="panel-tree-layout">
        <div class="panel-tree-side">
          <div class="panel-hint" style="margin: 0 0 8px 4px">物料分类树（共 {{ flatCategories.length }} 个分类）</div>
          <a-spin :spinning="loading">
            <div class="mt-tree">
              <div v-for="node in visibleNodes" :key="node.key" class="mt-node"
                   :class="{ 'is-selected': selectedKeys[0] === node.key }"
                   :style="{ paddingLeft: (node.level * 22 + 6) + 'px' }"
                   @click="selectNode(node.key); loadRows()">
                <span class="mt-switcher" @click.stop="node.hasChildren && toggleExpand(node.key)">
                  <span v-if="node.hasChildren" class="mt-arrow">{{ isExpanded(node.key) ? '▾' : '▸' }}</span>
                </span>
                <span class="mt-title">{{ node.title }}</span>
              </div>
              <div v-if="!visibleNodes.length && !loading" class="mt-empty">暂无分类</div>
            </div>
          </a-spin>
        </div>
        <div class="panel-tree-main">
          <a-card size="small" class="panel-card"
                  :title="manage
                    ? `物料清单：${currentNode?.name || '未选择分类'}`
                    : `子分类：${currentNode?.name || '根节点'}`">
            <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :loading="rowsLoading"
                     :pagination="false" :class="{ 'panel-empty-mode': !rows.length }"
                     :scroll="rows.length ? { x: manage ? 900 : 500 } : undefined">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'name'">
                  <a-tooltip :title="`${record.name}`"><span class="cell-ellipsis">{{ record.name }}</span></a-tooltip>
                </template>
                <template v-else-if="column.key === 'referencePrice'">
                  {{ record.referencePrice != null ? yuan(record.referencePrice) : '-' }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space v-if="manage ? canEditMaterialPerm : canEditCategoryPerm" :size="4">
                    <a @click="openEdit(record)">编辑</a>
                    <a @click="remove(record)">删除</a>
                  </a-space>
                  <span v-else class="muted">只读</span>
                </template>
              </template>
              <template #emptyText>
                <div class="panel-empty"><a-empty :description="manage
                  ? (selectedLevel === 3 ? '该分类下暂无物料' : '请先在左侧选中三级分类')
                  : (selectedLevel === 3 ? '三级分类下不能再新增分类' : '该分类下暂无子分类')" /></div>
              </template>
            </a-table>
          </a-card>
          <a-alert v-if="manage" type="info" show-icon message="维护口径"
                   description="左侧只展示三级分类树。选中三级分类后在右侧维护物料；物料编码由服务端按分类码续位，已被合同清单或采购计划引用的物料不可删除。分类的新增与编辑请到「物料库」页。" />
          <a-alert v-else type="info" show-icon message="维护口径"
                   description="这里只维护物料分类（最多三级）：选中一级/二级分类可新增下级分类，右侧列表显示当前节点的子分类并支持编辑删除。三级分类下不再新增分类，物料请到「物料维护」页。" />
        </div>
      </div>
    </section>

    <a-drawer v-model:open="editOpen" :title="drawerTitle" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item v-if="editCode" label="编码">
          <a-input :value="editCode" readonly />
          <div class="hint" style="margin-top: 4px">编码保持不变，避免引用断裂</div>
        </a-form-item>
        <a-form-item :label="manage ? '所属三级分类' : '上级分类'">
          <a-input :value="parentName" readonly />
        </a-form-item>
        <a-form-item :label="manage ? '物料名称' : '分类名称'" required>
          <a-input v-model:value="(manage ? matForm : catForm).name" />
        </a-form-item>
        <template v-if="manage">
          <a-form-item label="规格型号"><a-input v-model:value="matForm.spec" /></a-form-item>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="计量单位"><a-input v-model:value="matForm.unit" placeholder="台 / 套 / 项" /></a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="参考单价">
                <a-input-number v-model:value="matForm.referencePrice" :min="0" :precision="2" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </template>
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
