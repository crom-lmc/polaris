import request from './request'

/** 统一分页壳（后端 PageResult：records/total/page/size）。 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/* ==================== 公共字典（settings 白名单） ==================== */

export interface DictOption { value: string; label: string; remark?: string | null }
export const fetchPublicDict = (typeCode: string) =>
  request.get<never, DictOption[]>(`/api/settings/public/dicts/${typeCode}`)

/* ==================== 认证与应用信息 ==================== */

export interface MyAppInfo { code: string; name: string }
export const fetchMyApps = () => request.get<never, MyAppInfo[]>('/api/accounts/me/apps')

export const SSO_AUTHORIZE_URL = import.meta.env.VITE_SSO_ORIGIN ||
  (import.meta.env.DEV ? 'http://localhost:5173' : 'https://polaris-sso.laimengchao.site')

/** 注册的完整 redirect_uri：签码与换票必须使用同一个地址。 */
export function callbackUrl(): string {
  return window.location.origin + '/callback'
}

export function gotoAuthorize(redirect: string) {
  const state = Math.random().toString(36).slice(2) + Date.now().toString(36)
  try {
    sessionStorage.setItem('contract_oauth_state', state)
    sessionStorage.setItem('contract_oauth_redirect', redirect)
  } catch { /* ignore */ }
  const url = `${SSO_AUTHORIZE_URL}/authorize?response_type=code` +
    `&client_id=contract&redirect_uri=${encodeURIComponent(callbackUrl())}` +
    `&state=${encodeURIComponent(state)}`
  window.location.replace(url)
}

export function consumeState(): string | null {
  try {
    const s = sessionStorage.getItem('contract_oauth_state')
    sessionStorage.removeItem('contract_oauth_state')
    return s
  } catch { return null }
}

export function takeRedirect(): string {
  try {
    const r = sessionStorage.getItem('contract_oauth_redirect') || '/purchase-plan'
    sessionStorage.removeItem('contract_oauth_redirect')
    return r.startsWith('/callback') ? '/purchase-plan' : r
  } catch { return '/purchase-plan' }
}

/** 授权码换 contract 用户令牌（appSecret 在后端）。 */
export function exchangeCode(code: string, redirectUri: string) {
  return request.post<never, { accessToken: string; refreshToken: string }>(
    '/api/contract/sso/oauth/exchange', { code, redirectUri })
}

/** 凭中心会话 cookie 静默换票（无本地 token 时的回退路径）。 */
export function sessionToken() {
  return request.post<never, { accessToken: string; refreshToken: string }>(
    '/api/auth/session/token', {}, { skipAuthRedirect: true })
}

export function logoutSso(refreshToken?: string) {
  return request.post<never, void>('/api/auth/logout', { refreshToken })
}

/** SSO 权限树下发的菜单元信息：前端侧栏的名称、顺序、层级只认这里 */
export interface MenuMeta {
  code: string
  name: string
  sort: number
  parentCode?: string | null
}

export interface MeInfo {
  accountId?: string | number
  username: string
  name?: string
  nickname?: string
  superAdmin: boolean
  appCode?: string
  permissions: string[]
  menus: MenuMeta[]
}
/** 带 app=contract 实时查库取权限码与菜单，改完授权立即生效，不必等令牌过期。 */
export const fetchMe = () => request.get<never, MeInfo>('/api/auth/me', { params: { app: 'contract' } })

/* ==================== 往来单位 ==================== */

export interface PartnerVO {
  id: string
  code: string
  name: string
  subjectType: string
  role: string
  type: string
  creditCode: string
  taxNo?: string
  legalPerson: string
  registeredCapital: string
  establishDate: string
  taxpayerType: string
  address: string
  remark: string
  contact: string
  phone: string
  terms: string
  rating: string
  /** 授信额度；0=不限制（销售信用校验埋点开启时生效） */
  creditLimit: number
  /** 已占用额度（手工维护） */
  creditUsed: number
  status: string
  bank: string
  accountName: string
  /** 后端已按 contract:partner:bank:view 脱敏 */
  bankAccount: string
  bankMasked: boolean
  orgCode: string
}

export interface PartnerSave {
  code: string
  name: string
  subjectType?: string
  role?: string
  type?: string
  creditCode?: string
  legalPerson?: string
  registeredCapital?: string
  establishDate?: string
  taxpayerType?: string
  address?: string
  remark?: string
  contact?: string
  phone?: string
  terms?: string
  rating?: string
  creditLimit?: number
  creditUsed?: number
  status?: string
  bank?: string
  accountName?: string
  bankAccount?: string
  orgCode?: string
}

export const listPartners = (params: {
  code?: string; name?: string; role?: string; status?: string; keyword?: string; page: number; size: number
}) => request.get<never, PageResult<PartnerVO>>('/api/contract/partners', { params })
export const getPartner = (id: string) => request.get<never, PartnerVO>(`/api/contract/partners/${id}`)
export const savePartner = (data: PartnerSave) => request.post<never, PartnerVO>('/api/contract/partners', data)
export const updatePartner = (id: string, data: PartnerSave) =>
  request.put<never, void>(`/api/contract/partners/${id}`, data)
export const togglePartner = (id: string, status: string) =>
  request.post<never, void>(`/api/contract/partners/${id}/status`, { status })
export const deletePartner = (id: string) => request.delete<never, void>(`/api/contract/partners/${id}`)

/* ==================== 物料分类（物料库）与物料（物料维护）分表 ==================== */

/** 分类树节点：树里只有分类，不挂物料 */
export interface MaterialCategoryVO {
  id: string
  parentId: string | null
  code: string
  name: string
  status: string
  orgCode: string
  /** 直接子分类数 / 直接物料数，后端给出以便禁用删除按钮 */
  childCount?: number
  materialCount?: number
  canDelete?: boolean
  children?: MaterialCategoryVO[]
}

export interface MaterialCategorySave {
  parentId?: string | null
  code?: string
  name: string
  orgCode?: string
}

/** 物料：categoryId 指向三级分类 */
export interface MaterialVO {
  id: string
  categoryId: string
  parentId: string | null
  code: string
  name: string
  kind: string
  spec: string
  unit: string
  referencePrice: number | null
  categoryName?: string
  orgCode: string
  children?: MaterialVO[]
}

export interface MaterialSave {
  categoryId?: string | null
  parentId?: string | null
  code?: string
  name: string
  kind?: string
  spec?: string
  unit?: string
  referencePrice?: number | null
  orgCode?: string
}

/* 分类：物料库 */
export const getCategoryTree = () => request.get<never, MaterialCategoryVO[]>('/api/contract/material-categories/tree')
export const getCategoryList = (parentId?: string | null) =>
  request.get<never, MaterialCategoryVO[]>('/api/contract/material-categories', { params: { parentId } })
export const getCategory = (id: string) => request.get<never, MaterialCategoryVO>(`/api/contract/material-categories/${id}`)
export const saveCategory = (data: MaterialCategorySave) =>
  request.post<never, MaterialCategoryVO>('/api/contract/material-categories', data)
export const updateCategory = (id: string, data: MaterialCategorySave) =>
  request.put<never, void>(`/api/contract/material-categories/${id}`, data)
export const toggleCategoryStatus = (id: string, status: string) =>
  request.post<never, void>(`/api/contract/material-categories/${id}/status`, { status })
export const deleteCategory = (id: string) => request.delete<never, void>(`/api/contract/material-categories/${id}`)

/* 物料：物料维护 */
/** 选料弹窗左树：只取一层直接子分类，按需展开 */
export const getMaterialCategoryChildren = (parentId?: string | null) =>
  request.get<never, MaterialCategoryVO[]>('/api/contract/material-categories/children', { params: { parentId } })
/** 选料弹窗右侧：分页 + 关键字搜索，避免一次性拉全量物料 */
export const searchMaterials = (params: { categoryId?: string | null; keyword?: string; page: number; size: number }) =>
  request.get<never, PageResult<MaterialVO>>('/api/contract/materials/search', { params })
/** 选料下拉用：分类 + 物料拼合树（物料库/维护页不用） */
export const getMaterialTree = () => request.get<never, MaterialVO[]>('/api/contract/materials/tree')
export const getMaterials = (categoryId?: string | null) =>
  request.get<never, MaterialVO[]>('/api/contract/materials', { params: { categoryId } })
export const getMaterial = (id: string) => request.get<never, MaterialVO>(`/api/contract/materials/${id}`)
export const saveMaterial = (data: MaterialSave) => request.post<never, MaterialVO>('/api/contract/materials', data)
export const updateMaterial = (id: string, data: MaterialSave) =>
  request.put<never, void>(`/api/contract/materials/${id}`, data)
export const deleteMaterial = (id: string) => request.delete<never, void>(`/api/contract/materials/${id}`)

/* ==================== 项目维护 ==================== */

export interface ProjectVO {
  id: string
  code: string
  name: string
  type: string
  orgCode: string
  teamCode: string
  costCenterCode: string
  owner: string
  budgetAmount: number
  startDate: string
  endDate: string
  status: string
  remark: string
  contractCount: number
  contractAmount: number
  usedAmount: number
}

export interface ProjectOptionVO {
  code: string
  name: string
  type: string
  orgCode: string
  status: string
  budgetAmount: number
}

export interface ProjectSave {
  name: string
  type?: string
  orgCode?: string
  teamCode?: string
  costCenterCode?: string
  owner?: string
  budgetAmount?: number
  startDate?: string
  endDate?: string
  status?: string
  remark?: string
}

export const listProjects = (params: {
  code?: string; name?: string; type?: string; status?: string; page: number; size: number
}) => request.get<never, PageResult<ProjectVO>>('/api/contract/projects', { params })
export const getProjectOptions = () => request.get<never, ProjectOptionVO[]>('/api/contract/projects/options')
export const getProject = (id: string) => request.get<never, ProjectVO>(`/api/contract/projects/${id}`)
export const saveProject = (data: ProjectSave) => request.post<never, ProjectVO>('/api/contract/projects', data)
export const updateProject = (id: string, data: ProjectSave) =>
  request.put<never, void>(`/api/contract/projects/${id}`, data)
export const changeProjectStatus = (id: string, status: string) =>
  request.post<never, void>(`/api/contract/projects/${id}/status`, { status })
export const deleteProject = (id: string) => request.delete<never, void>(`/api/contract/projects/${id}`)

/* ==================== 合同台账 ==================== */

export interface ContractVO {
  id: string
  no: string
  type: string
  name: string
  partnerCode: string
  partnerName: string
  projectNo: string
  projectName: string
  dept: string
  owner: string
  signedDate: string
  effectiveDate: string
  endDate: string
  status: string
  paymentTerms: string
  /** 合同标的 */
  subject: string
  /** 备注（非必填，200 字） */
  note?: string
  /** 来源 DIRECT 直接新建 / FROM_PLAN 采购计划转入 */
  contractSource: string
  /** 来源采购计划号（FROM_PLAN 时带入） */
  planNo?: string
  orgCode: string
  amount: number
  paidAmount: number
  inFlightAmount: number
  pendingAmount: number
  progress: number
  itemCount: number
  planCount: number
  /** 变更次数（来自 contract_change_log） */
  changeCount: number
  /** 附件 file_id 列表，顺序与 attachmentUrls 一致 */
  fileIds?: string[]
  /** 附件只读预览 URL（统一文件服务临时签名） */
  attachmentUrls?: string[]
}

export interface ContractSave {
  type?: string
  name: string
  partnerCode: string
  projectNo?: string
  dept?: string
  owner?: string
  signedDate?: string
  effectiveDate?: string
  endDate?: string
  status?: string
  paymentTerms?: string
  /** 合同标的 */
  subject?: string
  /** 备注（非必填，200 字） */
  note?: string
  orgCode?: string
  /** 变更原因（仅用于变更留痕） */
  remark?: string
  /** 附件 file_id 列表；不传表示保留原附件 */
  fileIds?: string[]
}

export interface ChangeLogVO {
  id: string
  no: string
  changeType: string
  beforeValue: string
  afterValue: string
  reason: string
  operator: string
  createdAt: string
}

export interface ContractStats {
  total: number
  amount: number
  paidAmount: number
  inFlightAmount: number
  pendingAmount: number
}

export const listContracts = (params: {
  type?: string; status?: string; keyword?: string; projectNo?: string; partnerCode?: string; page: number; size: number
}) => request.get<never, PageResult<ContractVO>>('/api/contract/contracts', { params })
export const getContractStats = (type?: string) =>
  request.get<never, ContractStats>('/api/contract/contracts/stats', { params: { type } })
export const getContract = (id: string) => request.get<never, ContractVO>(`/api/contract/contracts/${id}`)
export const getContractByNo = (no: string) => request.get<never, ContractVO>(`/api/contract/contracts/by-no/${encodeURIComponent(no)}`)
export const saveContract = (data: ContractSave) => request.post<never, ContractVO>('/api/contract/contracts', data)
export const updateContract = (id: string, data: ContractSave) =>
  request.put<never, void>(`/api/contract/contracts/${id}`, data)
export const changeContractStatus = (id: string, status: string) =>
  request.post<never, void>(`/api/contract/contracts/${id}/status`, { status })
export const listContractChanges = (id: string) =>
  request.get<never, ChangeLogVO[]>(`/api/contract/contracts/${id}/changes`)
export const deleteContract = (id: string) => request.delete<never, void>(`/api/contract/contracts/${id}`)

/* ==================== 合同清单与验收 ==================== */

export interface ItemVO {
  id: string
  contractNo: string
  lineNo: number
  code: string
  materialCode: string
  materialName: string
  spec: string
  unit: string
  qty: number
  price: number
  amount: number
  deliveredQty: number
  remark: string
  orgCode: string
}

export interface ItemSave {
  id?: string
  lineNo?: number
  materialCode: string
  materialName?: string
  spec?: string
  unit?: string
  qty: number
  price: number
  amount?: number
  remark?: string
}

export interface AcceptRequest {
  qty: number
  result?: string
  date?: string
  remark?: string
  /** 验收凭证 file_id 列表 */
  fileIds?: string[]
}

export interface AcceptanceVO {
  id: string
  no: string
  contractNo: string
  itemId: string
  materialCode: string
  qty: number
  date: string
  result: string
  status: string
  /** 过账凭证号（财务域回写） */
  voucherNo?: string
  orgCode: string
  fileIds?: string[]
  attachmentUrls?: string[]
}

export const listItems = (contractNo: string) =>
  request.get<never, ItemVO[]>(`/api/contract/contracts/${contractNo}/items`)
export const saveItem = (contractNo: string, data: ItemSave) =>
  request.post<never, ItemVO>(`/api/contract/contracts/${contractNo}/items`, data)
export const deleteItem = (contractNo: string, id: string) =>
  request.delete<never, void>(`/api/contract/contracts/${contractNo}/items/${id}`)
export const listAcceptances = (contractNo: string) =>
  request.get<never, AcceptanceVO[]>(`/api/contract/contracts/${contractNo}/items/acceptances`)
export const acceptItem = (contractNo: string, id: string, data: AcceptRequest) =>
  request.post<never, AcceptanceVO>(`/api/contract/contracts/${contractNo}/items/${id}/accept`, data)

/* ==================== 附件上传（统一文件服务代理） ==================== */

export type ContractAttachmentOwner = 'purchase-plan' | 'contract' | 'acceptance' | 'payment-plan'

export interface AttachmentUploadResult { fileId: string; url: string }

const attachmentPaths: Record<ContractAttachmentOwner, string> = {
  'purchase-plan': '/api/contract/files/purchase-plan/upload',
  contract: '/api/contract/files/contract/upload',
  acceptance: '/api/contract/files/acceptance/upload',
  'payment-plan': '/api/contract/files/payment-plan/upload'
}

/** 附件 base64 发到合同服务代理接口，业务表只存 file_id；预览 URL 由后端签名后返回 */
export const uploadContractAttachment = (file: File, owner: ContractAttachmentOwner) =>
  new Promise<AttachmentUploadResult>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const content = String(reader.result || '').split(',')[1] || ''
      request.post<never, AttachmentUploadResult>(attachmentPaths[owner], {
        originalName: file.name, contentType: file.type, content
      }).then(resolve).catch(reject)
    }
    reader.onerror = () => reject(new Error('读取附件文件失败'))
    reader.readAsDataURL(file)
  })

/* ==================== 付款计划 ==================== */

export interface PaymentPlanVO {
  id: string
  /** 付款申请单号 FK-yyyy-XXXXXX */
  no?: string
  contractNo: string
  contractName: string
  stage: string
  dueDate: string
  amount: number
  /** 付款事由 */
  summary?: string
  sourceDocNo: string
  /** 关联凭证号（财务域过账回写） */
  voucherNo?: string
  status: string
  orgCode: string
  /** 合同与验收凭证 file_id 列表 */
  fileIds?: string[]
  /** 合同与验收凭证只读预览 URL */
  attachmentUrls?: string[]
  /** 临近到期（埋点 contract.payment.remind-days 天数内） */
  dueSoon?: boolean
  /** 已逾期未付款 */
  overdue?: boolean
  /** 提醒天数 */
  remindDays?: number
}

export interface PaymentPlanSave {
  stage?: string
  dueDate?: string
  amount: number
  /** 付款事由 */
  summary?: string
  status?: string
  /** 合同与验收凭证 file_id 列表；不传表示保留原值 */
  fileIds?: string[]
}

export const listPaymentPlans = (contractNo: string) =>
  request.get<never, PaymentPlanVO[]>(`/api/contract/payment-plans/contract/${contractNo}`)
export const savePaymentPlan = (contractNo: string, data: PaymentPlanSave) =>
  request.post<never, PaymentPlanVO>(`/api/contract/payment-plans/contract/${contractNo}`, data)
export const applyPaymentPlan = (id: string, sourceDocNo?: string) =>
  request.post<never, PaymentPlanVO>(`/api/contract/payment-plans/${id}/apply`,
    sourceDocNo ? { sourceDocNo } : {})
export const deletePaymentPlan = (id: string) =>
  request.delete<never, void>(`/api/contract/payment-plans/${id}`)

/* ==================== 采购计划 ==================== */

export interface PurchasePlanItemVO {
  id: string
  planNo: string
  materialCode: string
  materialName: string
  spec: string
  unit: string
  qty: number
  price: number
  amount: number
}

export interface PurchasePlanVO {
  id: string
  no: string
  title: string
  dept: string
  applicant: string
  /** 计划日期（设计稿 date） */
  date?: string
  needDate: string
  status: string
  contractNo: string
  orgCode: string
  amount: number
  itemCount: number
  items: PurchasePlanItemVO[]
  /** 附件 file_id 列表，顺序与 attachmentUrls 一致 */
  fileIds?: string[]
  /** 附件只读预览 URL（统一文件服务临时签名） */
  attachmentUrls?: string[]
}

export interface PurchasePlanItemSave {
  materialCode: string
  spec?: string
  unit?: string
  qty: number
  price: number
}

export interface PurchasePlanSave {
  title: string
  dept?: string
  applicant?: string
  /** 计划日期（设计稿 date） */
  date?: string
  needDate?: string
  orgCode?: string
  items?: PurchasePlanItemSave[]
  /** 附件 file_id 列表；不传表示保留原附件 */
  fileIds?: string[]
}

export interface PlanConvertRequest {
  name?: string
  partnerCode: string
  projectNo?: string
  dept?: string
  owner?: string
  signedDate?: string
  effectiveDate?: string
  endDate?: string
  note?: string
  fileIds?: string[]
  paymentTerms?: string
  subject?: string
  orgCode?: string
}

/** 发起采购计划默认申请人/需求部门：服务端按账号从 EHR 反查，避免前端雪花 ID 丢精度 */
export interface PurchasePlanProfileVO {
  applicant: string
  dept: string
}
export const fetchPurchasePlanProfile = () =>
  request.get<never, PurchasePlanProfileVO>('/api/contract/purchase-plans/my-profile')

export const listPurchasePlans = (params: {
  no?: string; status?: string; keyword?: string; page: number; size: number
}) => request.get<never, PageResult<PurchasePlanVO>>('/api/contract/purchase-plans', { params })
export const getPurchasePlan = (id: string) =>
  request.get<never, PurchasePlanVO>(`/api/contract/purchase-plans/${id}`)
export const savePurchasePlan = (data: PurchasePlanSave) =>
  request.post<never, PurchasePlanVO>('/api/contract/purchase-plans', data)
export const updatePurchasePlan = (id: string, data: PurchasePlanSave) =>
  request.put<never, void>(`/api/contract/purchase-plans/${id}`, data)
export const submitPurchasePlan = (id: string) =>
  request.post<never, void>(`/api/contract/purchase-plans/${id}/submit`)
export const approvePurchasePlan = (id: string) =>
  request.post<never, void>(`/api/contract/purchase-plans/${id}/approve`)
export const rejectPurchasePlan = (id: string) =>
  request.post<never, void>(`/api/contract/purchase-plans/${id}/reject`)
export const convertPurchasePlan = (id: string, data: PlanConvertRequest) =>
  request.post<never, ContractVO>(`/api/contract/purchase-plans/${id}/convert`, data)
export const deletePurchasePlan = (id: string) =>
  request.delete<never, void>(`/api/contract/purchase-plans/${id}`)

/* ==================== 组织 / 人员（EHR 只读引用，经合同服务内部令牌代理） ==================== */

/**
 * EHR 组织节点（/internal/orgs/tree 扁平列表，id/parentId 已字符串化）。
 * dept 等字段存的是组织「名称」（审批按名称取负责人），code 仅用于成本中心/项目组级联。
 */
export interface OrgNodeVO {
  id: string
  parentId: string | null
  type: string
  typeName: string
  code: string
  name: string
  status?: number
  employeeCount?: number
  costCenter?: string
  children?: OrgNodeVO[]
}

/** 在职员工候选（已开户、未离职；id 为字符串化账号 ID） */
export interface OrgEmployeeVO {
  id: string
  name: string
  empNo: string
  orgId?: string
}

/** 成本中心（公司主体）与项目组从 EHR 组织树只读引用，不在本应用维护。 */
export const fetchOrgTree = () => request.get<never, OrgNodeVO[]>('/api/contract/orgs/tree')

/** 全部已开户、未离职员工，人员下拉候选（姓名/工号）。 */
export const fetchActiveEmployees = () => request.get<never, OrgEmployeeVO[]>('/api/contract/orgs/employees/active')
