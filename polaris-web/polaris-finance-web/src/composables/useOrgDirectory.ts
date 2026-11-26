import { computed, ref } from 'vue'
import {
  fetchOrgTree, fetchActiveEmployees,
  type OrgNodeVO, type OrgEmployeeVO
} from '@/api/finance'

export interface Option { label: string; value: string }

/**
 * 组织 / 人员选择数据源：真相源在 EHR，经财务服务本域代理读取。
 * 部门、申请人一律走这里，不允许手输——
 * select 的 value 存「名称」（与库里 dept/applicant 及审批按名称取负责人一致），
 * label 展示完整层级路径 / 工号，避免同名歧义。
 */
export function useOrgDirectory() {
  const orgNodes = ref<OrgNodeVO[]>([])
  const employees = ref<OrgEmployeeVO[]>([])
  const loading = ref(false)

  const byId = computed(() => new Map(orgNodes.value.map((n) => [n.id, n])))

  /** 组织完整层级路径（带环保护，脏数据不死循环） */
  function pathOf(node: OrgNodeVO): string {
    const names: string[] = []
    const seen = new Set<string>()
    let cur: OrgNodeVO | undefined = node
    while (cur && !seen.has(cur.id)) {
      seen.add(cur.id)
      names.unshift(cur.name)
      cur = cur.parentId ? byId.value.get(cur.parentId) : undefined
    }
    return names.join(' / ')
  }

  /** 部门下拉：value=组织名称，label=完整路径（同名可区分） */
  const deptOptions = computed<Option[]>(() =>
    orgNodes.value.map((n) => ({ label: pathOf(n), value: n.name })))

  /** 人员下拉：value=姓名，label=姓名（工号），已按工号排序 */
  const employeeOptions = computed<Option[]>(() =>
    employees.value.map((e) => ({ label: e.empNo ? `${e.name}（${e.empNo}）` : e.name, value: e.name })))

  async function load() {
    loading.value = true
    const [orgs, emps] = await Promise.all([
      fetchOrgTree().catch(() => [] as OrgNodeVO[]),
      fetchActiveEmployees().catch(() => [] as OrgEmployeeVO[])
    ])
    orgNodes.value = orgs || []
    employees.value = emps || []
    loading.value = false
  }

  return { orgNodes, employees, loading, byId, pathOf, deptOptions, employeeOptions, load }
}

/** a-select 通用本地搜索：按 label 模糊匹配（大小写不敏感） */
export function filterOption(input: string, option: any): boolean {
  return String(option?.label || '').toLowerCase().includes(input.toLowerCase())
}
