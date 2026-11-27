import request from './request'

/**
 * 审批中心详情接口（与 sso-web 审批中心同一套）。
 * 合同侧只做「查看审批过程」：按业务域薄接口反查到 instanceId 后，
 * 用这里取详情 + 渲染上下文，展示表单快照 / 流程进度 / 审批人，全部只读。
 * 雪花 ID 均为字符串，前端不做精度转换。
 */

/** 0 进行中 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 已终止 */
export type ApprovalStatus = 0 | 1 | 2 | 3 | 4

export interface ApprovalStep {
  seq: number
  name: string
  state: 'DONE' | 'CURRENT' | 'TODO' | 'SKIPPED'
  conditionDesc?: string
}

export interface ApprovalTask {
  id: string
  instanceId: string
  stepSeq: number
  stepName: string
  nodeSeq?: number
  nodeName?: string
  approverId: string
  approverName: string
  approverSource?: string
  delegateOwnerName?: string
  taskKind?: string
  status: number
  statusName?: string
  approveMode?: string
  opinion?: string
  actedAt?: string
  createdAt?: string
}

export interface ApprovalInstance {
  id: string
  bizType: string
  bizName?: string
  bizId: string
  bizNo?: string
  title: string
  applicantId?: string | number
  applicantName?: string
  orgPath?: string
  status: ApprovalStatus
  statusName?: string
  currentStep?: number
  totalStep?: number
  createdAt?: string
  finishedAt?: string
  formData?: Record<string, unknown>
  steps?: ApprovalStep[]
  tasks?: ApprovalTask[]
}

export interface ApprovalPageField {
  key: string
  label: string
  source?: string
  format?: 'text' | 'date' | 'datetime' | 'money' | 'enum' | 'tag' | 'tags' | 'progress' | 'duration' | 'link' | 'richText' | 'json' | 'table'
  tableColumns?: { key: string; label: string; code?: string }[]
  path?: string
  span?: number
}

export interface ApprovalPageSection {
  key: string
  title?: string
  columns?: number
  emphasis?: boolean
  fields: ApprovalPageField[]
}

export interface ApprovalContext {
  instanceId: string
  bizType: string
  bizName?: string
  title?: string
  applicantName?: string
  status: ApprovalStatus
  statusName?: string
  values: Record<string, unknown>
  tableModes?: Record<string, string>
  page?: { titleTpl?: string; sections?: ApprovalPageSection[] } | null
}

/** 单据详情：步骤条 + 审批人 + 表单快照 */
export function fetchApprovalDetail(id: string) {
  return request.get<never, ApprovalInstance>(`/api/accounts/approvals/${id}`)
}

/** 渲染上下文：版式 + 已算好的字段值（未配置版式时 page 为 null，走默认铺开） */
export function fetchApprovalContext(id: string) {
  return request.get<never, ApprovalContext>(`/api/accounts/approvals/${id}/context`, {
    params: { view: 'APPROVAL' }
  })
}
