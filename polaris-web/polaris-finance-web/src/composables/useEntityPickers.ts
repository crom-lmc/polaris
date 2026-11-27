import type { PickerColumn, PickerLoadParams, PickerResult, PickerRow } from '@shared/components/EntityPickerModal.vue'
import type { OrgNodeVO } from '@/api/finance'

/** 弹窗数据源通用本地分页（财务侧选项多为一次性加载的候选列表） */
export function paginatePicker(all: PickerRow[], params: PickerLoadParams): PickerResult {
  const kw = params.keyword.trim().toLowerCase()
  const filtered = !kw ? all : all.filter((r) =>
    Object.values(r).some((v) => typeof v === 'string' && v.toLowerCase().includes(kw)))
  const start = (params.page - 1) * params.size
  return { records: filtered.slice(start, start + params.size), total: filtered.length }
}

/** 归属部门弹窗列定义（value 存组织名称，与库中 dept 一致） */
export const financeDeptColumns: PickerColumn[] = [
  { title: '部门名称', dataIndex: 'name', ellipsis: true },
  { title: '编码', dataIndex: 'code', width: 140 },
  { title: '完整路径', dataIndex: 'path', ellipsis: true }
]

/** 把组织树节点映射为弹窗行（path 用 name 兜底，完整路径由调用方 pathOf 生成） */
export function orgRows(orgNodes: OrgNodeVO[], pathOf: (n: OrgNodeVO) => string): PickerRow[] {
  return orgNodes.map((n) => ({ name: n.name, code: n.code, path: pathOf(n), raw: n }))
}
