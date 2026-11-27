<script lang="ts">
/**
 * 通用实体选择弹窗的公共契约：组件不绑定任何应用的 API，调用方按需注入加载函数。
 * 用于往来单位 / 所属项目 / 负责人 / 归属部门等主数据选择，替代下拉框。
 */
export interface PickerColumn {
  title: string
  dataIndex: string
  key?: string
  width?: number
  /** 长文本列用省略号 + tooltip 展示完整值 */
  ellipsis?: boolean
}

export interface PickerRow {
  [key: string]: any
}

export interface PickerLoadParams {
  keyword: string
  page: number
  size: number
}

export interface PickerResult {
  records: PickerRow[]
  total: number
}
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  open: boolean
  title?: string
  /** 表格列定义 */
  columns: PickerColumn[]
  /** 行唯一标识字段名（如 code / id / name） */
  rowKey: string
  /** 当前选中值（按 rowKey 匹配），用于打开时回填高亮 */
  initialValue?: string | null
  /** 搜索框占位文案 */
  placeholder?: string
  /** 单选；返回完整行对象 */
  load: (params: PickerLoadParams) => Promise<PickerResult>
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'confirm', row: PickerRow): void
}>()

const keyword = ref('')
const searched = ref('')
const page = ref(1)
const size = 10
const loading = ref(false)
const rows = ref<PickerRow[]>([])
const total = ref(0)
const selected = ref<PickerRow | null>(null)

function rowKeyOf(record: PickerRow): string { return String(record[props.rowKey] ?? '') }

const selectedKey = computed(() => selected.value ? rowKeyOf(selected.value) : '')

const pagination = computed(() => ({
  current: page.value,
  pageSize: size,
  total: total.value,
  showSizeChanger: false,
  showTotal: (t: number) => `共 ${t} 条`,
  onChange: (p: number) => { page.value = p; void loadRows() }
}))

const rowSelection = computed(() => ({
  type: 'radio' as const,
  selectedRowKeys: selectedKey.value ? [selectedKey.value] : [],
  onChange: (_keys: (string | number)[], chosen: PickerRow[]) => {
    selected.value = chosen.length ? chosen[0] : null
  }
}))

async function loadRows() {
  loading.value = true
  try {
    const res = await props.load({ keyword: searched.value, page: page.value, size })
    rows.value = res?.records || []
    total.value = res?.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
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
function confirm() {
  if (selected.value) emit('confirm', selected.value)
  close()
}

watch(() => props.open, async (open) => {
  if (!open) return
  keyword.value = ''
  searched.value = ''
  page.value = 1
  // 回显当前值：注入一个按 rowKey 精确匹配的加载器结果不一定在当前页，
  // 这里先置空，由调用方保证加载器在关键词为空时能覆盖已选记录（首屏）。
  selected.value = null
  await loadRows()
  if (props.initialValue) {
    const hit = rows.value.find((r) => String(r[props.rowKey] ?? '') === String(props.initialValue))
    if (hit) selected.value = hit
  }
})
</script>

<template>
  <a-modal :open="open" :title="title || '选择'" :width="800" style="max-width: 94vw"
           :footer="null" :mask-closable="false" @cancel="close">
    <div class="ep-wrap">
      <div class="ep-search">
        <a-input v-model:value="keyword" :placeholder="placeholder || '编码 / 名称'" allow-clear @pressEnter="doSearch" />
        <a-button type="primary" @click="doSearch">搜索</a-button>
        <a-button @click="resetSearch">重置</a-button>
      </div>
      <a-table class="ep-table" :row-key="rowKeyOf" :columns="columns" :data-source="rows"
               :loading="loading" :pagination="pagination" :row-selection="rowSelection"
               :scroll="{ x: 'max-content', y: 300 }" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.ellipsis">
            <a-tooltip :title="record[column.dataIndex as string]">
              <span class="cell-ellipsis">{{ record[column.dataIndex as string] || '-' }}</span>
            </a-tooltip>
          </template>
        </template>
        <template #emptyText><div class="ep-empty">暂无可选数据</div></template>
      </a-table>
    </div>
    <div class="ep-footer">
      <div class="ep-picked">
        <span v-if="selected">已选：<b>{{ selected[columns[0]?.dataIndex || rowKey] }}</b></span>
        <span v-else>未选择</span>
      </div>
      <a-space :size="8">
        <a-button @click="close">取消</a-button>
        <a-button type="primary" :disabled="!selected" @click="confirm">确定</a-button>
      </a-space>
    </div>
  </a-modal>
</template>

<style scoped>
.ep-wrap { min-height: 360px; }
.ep-search { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; }
.ep-search .ant-input { flex: 1 1 auto; max-width: 260px; }
.ep-table { min-height: 0; }
.ep-empty { padding: 24px 8px; text-align: center; color: var(--muted, #8a9099); font-size: 12px; }
.ep-footer {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border-subtle, #eef0f4);
}
.ep-picked { font-size: 12px; color: var(--muted, #8a9099); }
.ep-picked b { color: var(--text-base, #1f2329); }
@media (max-width: 700px) {
  .ep-search .ant-input { max-width: none; }
}
</style>
