<script lang="ts">
/** 物料选择弹窗的公共契约：组件不绑定任何应用的 API，调用方按需注入加载函数。 */
export interface MaterialPick {
  code: string
  name: string
  spec?: string
  unit?: string
  price?: number | null
  categoryId?: string | null
  categoryName?: string
}

export interface MaterialCategoryNode {
  id: string
  code: string
  name: string
  parentId: string | null
  childCount?: number
  status?: string
}

export interface MaterialSearchParams {
  categoryId: string | null
  keyword: string
  page: number
  size: number
}

export interface MaterialSearchResult {
  records: MaterialPick[]
  total: number
}
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  open: boolean
  title?: string
  multiple?: boolean
  /** 编辑态回填；按 code 识别 */
  initialMaterials?: MaterialPick[]
  /** 只返回一层直接子分类，parentId 为空表示根节点 */
  loadChildren: (parentId: string | null) => Promise<MaterialCategoryNode[]>
  /** 分页搜索物料；调用方负责按自身响应壳解包 */
  loadMaterials: (params: MaterialSearchParams) => Promise<MaterialSearchResult>
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'confirm', materials: MaterialPick[]): void
}>()

interface TreeNode extends MaterialCategoryNode {
  children: TreeNode[]
  loaded: boolean
  loading: boolean
  expanded: boolean
}

const toNode = (c: MaterialCategoryNode): TreeNode => ({
  ...c, children: [], loaded: false, loading: false, expanded: false
})

const treeLoading = ref(false)
const roots = ref<TreeNode[]>([])
const activeCategoryId = ref<string | null>(null)

const nodeIndex = computed(() => {
  const map = new Map<string, TreeNode>()
  const walk = (nodes: TreeNode[]) => nodes.forEach((n) => {
    map.set(n.id, n)
    walk(n.children)
  })
  walk(roots.value)
  return map
})

const activeCategoryName = computed(() =>
  activeCategoryId.value ? (nodeIndex.value.get(activeCategoryId.value)?.name || '') : '全部分类')

const flatNodes = computed(() => {
  const out: Array<TreeNode & { level: number }> = []
  const walk = (nodes: TreeNode[], level: number) => {
    for (const n of nodes) {
      out.push({ ...n, level })
      if (n.expanded && n.children.length) walk(n.children, level + 1)
    }
  }
  walk(roots.value, 0)
  return out
})

const rowsLoading = ref(false)
const rows = ref<MaterialPick[]>([])
const total = ref(0)
const keyword = ref('')
const searched = ref('')
const page = ref(1)
const size = 10

const columns = [
  { title: '物料编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '物料名称', dataIndex: 'name', key: 'name', width: 150 },
  { title: '规格', dataIndex: 'spec', key: 'spec', width: 120 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 60 },
  { title: '参考价', dataIndex: 'price', key: 'price', width: 90 },
  { title: '所属分类', dataIndex: 'categoryName', key: 'categoryName', width: 120 }
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: size,
  total: total.value,
  showSizeChanger: false,
  showTotal: (t: number) => `共 ${t} 条`,
  onChange: (p: number) => { page.value = p; void loadRows() }
}))

const selected = ref<Map<string, MaterialPick>>(new Map())
const selectedRowKeys = computed(() => [...selected.value.keys()])
const rowSelection = computed(() => ({
  type: props.multiple ? 'checkbox' : 'radio',
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[], chosen: MaterialPick[]) => onSelectChange(keys, chosen)
}))

function onSelectChange(_keys: (string | number)[], chosen: MaterialPick[]) {
  if (!props.multiple) {
    selected.value = chosen.length ? new Map([[chosen[0].code, chosen[0]]]) : new Map()
    return
  }
  // 跨页保留已选：先清掉本页全部 key，再把本页新勾上的补回来
  const next = new Map(selected.value)
  rows.value.forEach((r) => next.delete(r.code))
  chosen.forEach((r) => next.set(r.code, r))
  selected.value = next
}

async function loadRoot() {
  treeLoading.value = true
  try {
    roots.value = ((await props.loadChildren(null)) || []).map(toNode)
  } catch {
    roots.value = []
  } finally {
    treeLoading.value = false
  }
}

async function toggleExpand(id: string) {
  const node = nodeIndex.value.get(id)
  if (!node) return
  node.expanded = !node.expanded
  if (!node.expanded || node.loaded || node.loading) return
  node.loading = true
  try {
    node.children = ((await props.loadChildren(node.id)) || []).map(toNode)
    node.loaded = true
  } finally {
    node.loading = false
  }
}

async function selectCategory(id: string | null) {
  if (activeCategoryId.value === id) return
  activeCategoryId.value = id
  page.value = 1
  searched.value = ''
  keyword.value = ''
  await loadRows()
}

async function loadRows() {
  rowsLoading.value = true
  try {
    const res = await props.loadMaterials({
      categoryId: activeCategoryId.value,
      keyword: searched.value,
      page: page.value,
      size
    })
    rows.value = res?.records || []
    total.value = res?.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    rowsLoading.value = false
  }
}

function doSearch() {
  searched.value = keyword.value.trim()
  page.value = 1
  void loadRows()
}

function resetSearch() {
  keyword.value = ''
  searched.value = ''
  page.value = 1
  void loadRows()
}

function close() { emit('update:open', false) }
function confirm() { emit('confirm', [...selected.value.values()]); close() }

watch(() => props.open, async (open) => {
  if (!open) return
  selected.value = new Map((props.initialMaterials || []).map((m) => [m.code, m]))
  keyword.value = ''
  searched.value = ''
  page.value = 1
  await loadRoot()
  activeCategoryId.value = roots.value[0]?.id ?? null
  await loadRows()
})

defineExpose({ reload: loadRows })
</script>

<template>
  <a-modal :open="open" :title="title || '选择物料'" width="960" style="max-width: 94vw"
           :footer="null" :mask-closable="false" @cancel="close">
    <div class="mp-wrap">
      <div class="mp-left">
        <div class="mp-head">物料分类<span class="mp-hint">按需展开</span></div>
        <div class="mp-tree">
          <div v-if="treeLoading" class="mp-empty">分类加载中…</div>
          <div v-else-if="!flatNodes.length" class="mp-empty">暂无可用分类</div>
          <div v-for="n in flatNodes" v-else :key="n.id" class="mp-node"
               :class="{ 'is-active': activeCategoryId === n.id, 'is-disabled': n.status === 'DISABLED' }"
               :style="{ paddingLeft: (n.level * 18 + 6) + 'px' }" @click="selectCategory(n.id)">
            <span class="mp-switcher" @click.stop="toggleExpand(n.id)">
              <span v-if="(n.childCount || 0) > 0 || n.children.length" class="mp-arrow">{{ n.expanded ? '▾' : '▸' }}</span>
            </span>
            <span class="mp-title">{{ n.name }}</span>
            <span v-if="n.status === 'DISABLED'" class="mp-badge">停用</span>
          </div>
        </div>
      </div>
      <div class="mp-right">
        <div class="mp-search">
          <a-input v-model:value="keyword" placeholder="编码 / 名称 / 规格" allow-clear @pressEnter="doSearch" />
          <a-button type="primary" @click="doSearch">搜索</a-button>
          <a-button @click="resetSearch">重置</a-button>
        </div>
        <div class="mp-caption">当前分类：{{ activeCategoryName }} · 共 {{ total }} 条物料</div>
        <a-table class="mp-table" row-key="code" size="small" :columns="columns" :data-source="rows"
                 :loading="rowsLoading" :pagination="pagination" :row-selection="rowSelection" :scroll="{ x: 680, y: 340 }">
          <template #bodyCell="{ column, record }">
            <template v-if="['name', 'spec', 'categoryName'].includes(String(column.key))">
              <a-tooltip :title="record[column.key as string]">
                <span class="cell-ellipsis">{{ record[column.key as string] || '-' }}</span>
              </a-tooltip>
            </template>
          </template>
          <template #emptyText><div class="mp-empty">该分类下暂无物料</div></template>
        </a-table>
      </div>
    </div>
    <div class="mp-footer">
      <div class="mp-picked">
        <span>已选 {{ selected.size }} 项</span>
        <a-tag v-for="m in [...selected.values()].slice(0, 4)" :key="m.code" closable
               @close="selected.delete(m.code)">{{ m.code }} · {{ m.name }}</a-tag>
        <span v-if="selected.size > 4" class="mp-hint">…</span>
      </div>
      <a-space :size="8">
        <a-button @click="close">取消</a-button>
        <a-button type="primary" :disabled="!selected.size" @click="confirm">确定</a-button>
      </a-space>
    </div>
  </a-modal>
</template>

<style scoped>
.mp-wrap { display: flex; gap: 12px; min-height: 420px; }
.mp-left {
  width: 240px; flex: none; display: flex; flex-direction: column; gap: 8px;
  border-right: 1px solid var(--border-subtle, #eef0f4); padding-right: 10px;
}
.mp-head { font-weight: 600; font-size: 13px; display: flex; align-items: center; gap: 8px; }
.mp-hint { color: var(--muted, #8a9099); font-size: 11px; font-weight: 400; }
.mp-tree {
  flex: 1 1 auto; min-height: 360px; max-height: 420px; overflow: auto;
  border: 1px solid var(--border-subtle, #eef0f4); border-radius: 8px; padding: 6px 2px;
}
.mp-node {
  display: flex; align-items: center; gap: 4px; min-height: 28px; padding-right: 8px;
  border-radius: 6px; cursor: pointer; font-size: 13px;
  color: var(--text-base, #1f2329);
}
.mp-node:hover { background: var(--panel-bg, #f5f6f8); }
.mp-node.is-active { background: rgba(47, 84, 235, 0.1); color: var(--primary, #2f54eb); font-weight: 600; }
.mp-node.is-disabled .mp-title { text-decoration: line-through; color: var(--muted, #8a9099); }
.mp-switcher { width: 18px; flex: none; display: inline-flex; justify-content: center; }
.mp-arrow { font-size: 11px; color: var(--muted, #8a9099); }
.mp-title { flex: 1 1 auto; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mp-badge { font-size: 10px; color: var(--muted, #8a9099); }
.mp-right { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.mp-search { display: flex; gap: 8px; }
.mp-search .ant-input { flex: 1 1 auto; }
.mp-caption { font-size: 12px; color: var(--muted, #8a9099); }
.mp-table { flex: 1 1 auto; min-height: 0; }
.mp-table :deep(.ant-table-wrapper) { min-height: 0; }
.mp-empty { padding: 24px 8px; text-align: center; color: var(--muted, #8a9099); font-size: 12px; }
.mp-footer {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border-subtle, #eef0f4);
}
.mp-picked { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; min-width: 0; font-size: 12px; color: var(--muted, #8a9099); }
@media (max-width: 700px) {
  .mp-wrap { flex-direction: column; }
  .mp-left { width: 100%; border-right: 0; padding-right: 0; }
  .mp-tree { min-height: 140px; max-height: 180px; }
  .mp-footer { flex-direction: column; align-items: flex-start; }
}
</style>
