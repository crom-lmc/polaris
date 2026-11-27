import { getMaterialCategoryChildren, searchMaterials, type MaterialVO } from '@/api/contract'
import type {
  MaterialCategoryNode, MaterialPick, MaterialSearchParams, MaterialSearchResult
} from '@shared/components/MaterialPickerModal.vue'

/** 把物料 VO 转成公共弹窗的回传结构；name 独立字段，不再从 label 字符串里切 */
export function materialPickFrom(vo: MaterialVO): MaterialPick {
  return {
    code: vo.code,
    name: vo.name,
    spec: vo.spec || '',
    unit: vo.unit || '',
    price: vo.referencePrice ?? 0,
    categoryId: vo.categoryId,
    categoryName: vo.categoryName
  }
}

/** 公共弹窗左树数据源：只拉一层，按需展开 */
export async function loadMaterialChildren(parentId: string | null): Promise<MaterialCategoryNode[]> {
  const list = await getMaterialCategoryChildren(parentId)
  return (list || []).map((c) => ({
    id: c.id,
    code: c.code || '',
    name: c.name,
    parentId: c.parentId || null,
    childCount: c.childCount || 0,
    status: c.status
  }))
}

/** 公共弹窗右侧数据源：分页搜索 */
export async function loadMaterialPage(params: MaterialSearchParams): Promise<MaterialSearchResult> {
  const res = await searchMaterials(params)
  return { records: (res.records || []).map(materialPickFrom), total: res.total || 0 }
}
