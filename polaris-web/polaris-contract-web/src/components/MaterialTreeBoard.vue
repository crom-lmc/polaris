<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  getMaterialTree, saveCategory, saveMaterial, updateMaterial, deleteMaterial, type MaterialVO, type MaterialSave
} from '@/api/contract'
import { yuan } from '@/utils/format'

const props = defineProps<{ manage?: boolean }>()
const manage = computed(() => !!props.manage)
const store = useUserStore()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 560))

/** 新增分类挂「物料库」菜单，新增物料与编辑/删除挂「物料维护」菜单，各用独立权限码 */
const canAddCategoryPerm = computed(() => store.can('contract:material:category:add'))
const canAddMaterialPerm = computed(() => manage.value && store.can('contract:material:add'))
const canEditPerm = computed(() => manage.value && store.can('contract:material:edit'))

const loading = ref(false)
const flat = ref<MaterialVO[]>([])
const treeData = computed(() => toTreeData(flat.value))
const keyword = ref('')
const selectedKeys = ref<string[]>([])
const expandedKeys = ref<string[]>([])

function toTreeData(nodes: MaterialVO[]): any[] {
  return nodes.map((n) => ({
    key: n.id,
    title: n.name,
    children: n.children?.length ? toTreeData(n.children) : undefined
  }))
}
function flatten(nodes: MaterialVO[], out: MaterialVO[] = []): MaterialVO[] {
  nodes.forEach((n) => { out.push(n); if (n.children?.length) flatten(n.children, out) })
  return out
}
async function load() {
  loading.value = true
  try {
    const tree = await getMaterialTree()
    flat.value = flatten(tree || [])
    if (!selectedKeys.value.length && flat.value.length) selectedKeys.value = [flat.value[0].id]
    expandedKeys.value = flat.value.filter((n) => n.kind === 'CATEGORY').map((n) => n.id)
  } catch (e: any) { message.error(e?.message || '加载物料树失败') }
  finally { loading.value = false }
}

const currentNode = computed(() => flat.value.find((n) => n.id === selectedKeys.value[0]) || null)
/** 选中节点的分类层级：1/2/3 级分类，0=未选中分类（根节点或物料） */
const selectedLevel = computed(() => {
  const n = currentNode.value
  if (!n || n.kind !== 'CATEGORY') return 0
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
  if (selectedLevel.value === 3) return '当前三级分类 → 可新增物料'
  return '请选中三级分类后新增物料'
})
const rows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const node = currentNode.value
  const list = node ? (node.kind === 'MATERIAL' ? [node] : (node.children || [])) : flat.value.filter((n) => !n.parentId)
  return list.filter((n) => !kw || n.code.toLowerCase().includes(kw) || n.name.toLowerCase().includes(kw)
    || (n.spec || '').toLowerCase().includes(kw))
})

const baseColumns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 170 },
  { title: '名称', key: 'name', width: 190 },
  { title: '节点类型', key: 'kind', align: 'center' as const, width: 100 },
  { title: '规格型号', dataIndex: 'spec', key: 'spec', width: 170 },
  { title: '单位', dataIndex: 'unit', key: 'unit', align: 'center' as const, width: 80 },
  { title: '参考单价', key: 'referencePrice', align: 'right' as const, width: 130 },
  { title: '操作', key: 'action', width: 110 }
]
const columns = computed(() => (manage.value ? baseColumns : baseColumns.filter((c) => c.key !== 'action')))

const editOpen = ref(false)
const saving = ref(false)
const form = reactive<MaterialSave & { id?: string }>({
  id: undefined, parentId: null, code: '', name: '', kind: 'CATEGORY', spec: '', unit: '', referencePrice: undefined
})
const parentName = computed(() => {
  const p = flat.value.find((n) => n.id === form.parentId)
  return p ? `${p.name}（${p.code}）` : '根节点（新增一级分类）'
})
function resetForm() {
  form.id = undefined
  form.parentId = null
  form.code = ''
  form.name = ''
  form.kind = 'CATEGORY'
  form.spec = ''
  form.unit = ''
  form.referencePrice = undefined
}
/** 新增类型由所属菜单的按钮固定，上级节点固定为当前选中项，不在抽屉里切换 */
function openCreate(kind: 'CATEGORY' | 'MATERIAL') {
  if (kind === 'CATEGORY') {
    if (!canAddCategoryPerm.value || !categoryLevelOk.value) {
      message.warning('请选中一级或二级分类后新增分类；三级分类下不允许继续新增分类')
      return
    }
  } else {
    if (!canAddMaterialPerm.value || !materialLevelOk.value) {
      message.warning('新增物料请先选中三级分类')
      return
    }
  }
  resetForm()
  form.kind = kind
  form.parentId = currentNode.value?.id || null
  editOpen.value = true
}
function openEdit(row: MaterialVO) {
  if (!canEditPerm.value) return
  resetForm()
  form.id = row.id
  form.parentId = row.parentId || null
  form.code = row.code
  form.name = row.name
  form.kind = row.kind
  form.spec = row.spec || ''
  form.unit = row.unit || ''
  form.referencePrice = row.referencePrice ?? undefined
  editOpen.value = true
}
async function submit() {
  if (!form.name?.trim()) { message.warning('请填写节点名称'); return }
  saving.value = true
  try {
    const payload: MaterialSave = {
      parentId: form.parentId || null, name: form.name.trim(), kind: form.kind,
      spec: form.spec || '', unit: form.unit || '', referencePrice: form.referencePrice ?? null
    }
    if (form.id) await updateMaterial(form.id, payload)
    else if (form.kind === 'CATEGORY') await saveCategory(payload)
    else await saveMaterial(payload)
    editOpen.value = false
    message.success('节点已保存，编码由服务端按层级自动生成')
    await load()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
function remove(row: MaterialVO) {
  if (!canEditPerm.value) return
  Modal.confirm({
    centered: true, title: '删除节点',
    content: `确定删除「${row.name}」吗？有子节点或已被合同清单 / 采购计划引用的节点不可删除。`,
    okButtonProps: { danger: true },
    onOk: async () => {
      try { await deleteMaterial(row.id); message.success('已删除'); await load() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}
function onSelect(keys: any[]) { if (keys.length) selectedKeys.value = [String(keys[0])] }
function onExpand(keys: any[]) { expandedKeys.value = keys.map(String) }
onMounted(load)
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <h2>{{ manage ? '物料维护' : '物料库' }}</h2>
      <span class="divider" />
      <a-input-search v-model:value="keyword" placeholder="编码 / 名称 / 规格" style="width: 240px" allow-clear />
      <span class="grow" />
      <span class="hint">{{ manage ? '三级分类 + 叶子物料 · 编码由服务端自动生成' : '三级分类 + 叶子物料 · 采购计划与合同清单统一从这里选料' }}</span>
      <template v-if="!manage">
        <template v-if="canAddCategoryPerm">
          <span class="hint" :title="addHint">{{ addHint }}</span>
          <a-button :disabled="!categoryLevelOk" :title="categoryLevelOk ? '' : '请选中一级或二级分类'"
                    @click="openCreate('CATEGORY')">＋ 新增分类</a-button>
        </template>
      </template>
      <template v-else>
        <span class="hint" :title="addHint">{{ addHint }}</span>
        <a-button v-if="canAddMaterialPerm" type="primary" :disabled="!materialLevelOk"
                  :title="materialLevelOk ? '' : '请选中三级分类'"
                  @click="openCreate('MATERIAL')">＋ 新增物料</a-button>
      </template>
    </section>
    <section class="ft-scroll">
      <div class="panel-tree-layout">
        <div class="panel-tree-side">
          <div class="panel-hint" style="margin: 0 0 8px 4px">物料分类树（共 {{ flat.length }} 个节点）</div>
          <a-spin :spinning="loading">
            <a-tree v-model:selected-keys="selectedKeys" :tree-data="treeData" :expanded-keys="expandedKeys"
                    block-node @select="onSelect" @expand="onExpand">
              <template #title="node"><span class="panel-tree-node">{{ node.title }}</span></template>
            </a-tree>
          </a-spin>
        </div>
        <div class="panel-tree-main">
          <a-card size="small" class="panel-card"
                  :title="`当前节点：${currentNode?.name || '根节点'}（${currentNode?.kind === 'MATERIAL' ? '物料' : '分类'}）`">
            <a-table class="ft-table" row-key="id" :columns="columns" :data-source="rows" :pagination="false"
                     :class="{ 'panel-empty-mode': !rows.length }" :scroll="rows.length ? { x: 900 } : undefined">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'name'">
                  <a-tooltip :title="`${record.name} ${record.spec || ''}`"><span class="cell-ellipsis">{{ record.name }}</span></a-tooltip>
                </template>
                <template v-else-if="column.key === 'kind'">
                  <a-tag :color="record.kind === 'CATEGORY' ? 'blue' : 'green'">{{ record.kind === 'CATEGORY' ? '分类' : '物料' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'referencePrice'">
                  {{ record.referencePrice != null ? yuan(record.referencePrice) : '-' }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space v-if="canEditPerm" :size="4">
                    <a @click="openEdit(record)">编辑</a>
                    <a @click="remove(record)">删除</a>
                  </a-space>
                  <span v-else class="muted">只读</span>
                </template>
              </template>
              <template #emptyText><div class="panel-empty"><a-empty description="该节点下暂无内容" /></div></template>
            </a-table>
          </a-card>
          <a-alert v-if="manage" type="info" show-icon message="维护口径"
                   description="选中三级分类可新增物料，节点编辑与删除也在本页；新增分类请到「物料库」页。分类最多三级，不允许在三级分类下继续新增分类。编码由服务端自动生成，已被合同清单或采购计划引用的物料不可删除。" />
          <a-alert v-else type="info" show-icon message="查看口径"
                   description="选中一级 / 二级分类可在本页新增分类；新增物料、编辑与删除请到「物料维护」页。" />
        </div>
      </div>
    </section>

    <a-drawer v-model:open="editOpen"
              :title="form.id ? '编辑节点' : (form.kind === 'CATEGORY' ? `新增${selectedLevel + 1}级分类` : '新增物料')"
              :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-form-item v-if="form.id" label="节点类型">
          <a-input :value="form.kind === 'CATEGORY' ? '分类' : '物料'" readonly />
        </a-form-item>
        <a-form-item label="上级节点">
          <a-input :value="parentName" readonly placeholder="根节点" />
        </a-form-item>
        <a-form-item label="编码">
          <a-input :value="form.id ? form.code : ''" readonly :placeholder="form.id ? '' : '保存时由服务端自动生成'" />
          <div class="hint" style="margin-top: 4px">{{ form.id ? '编辑时编码保持不变，避免引用断裂' : '按层级自动续位，无需手工填写' }}</div>
        </a-form-item>
        <a-form-item label="名称" required><a-input v-model:value="form.name" /></a-form-item>
        <template v-if="form.kind === 'MATERIAL'">
          <a-form-item label="规格型号"><a-input v-model:value="form.spec" /></a-form-item>
          <a-row :gutter="12">
            <a-col :span="12"><a-form-item label="计量单位"><a-input v-model:value="form.unit" placeholder="台 / 套 / 项" /></a-form-item></a-col>
            <a-col :span="12">
              <a-form-item label="参考单价">
                <a-input-number v-model:value="form.referencePrice" :min="0" :precision="2" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </template>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="editOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存节点</a-button>
        </div>
      </template>
    </a-drawer>
  </section>
</template>
