import request from './request'

/* ============================ 平台能力 ============================ */

export interface MyAppInfo { code: string; name: string }
export const fetchMyApps = () => request.get<never, MyAppInfo[]>('/api/accounts/me/apps')

/** SSO 权限树下发的菜单元信息：前端侧栏的名称、顺序、层级只认这里 */
export interface MenuMeta {
  code: string
  name: string
  sort: number
  parentCode?: string | null
}

/** 业务后台通用身份接口（finance 令牌由 /api/finance/sso/oauth/exchange 签发） */
export interface MeInfo {
  accountId: string
  username: string
  name: string
  nickname?: string
  city?: string
  superAdmin: boolean
  appCode: string
  permissions: string[]
  menus: MenuMeta[]
}
/** 带 app=finance 实时查库取本应用权限码与菜单，避免 JWT 快照过期导致菜单/按钮失配 */
export const fetchMe = () => request.get<never, MeInfo>('/api/auth/me', { params: { app: 'finance' } })

export const SSO_AUTHORIZE_URL = import.meta.env.VITE_SSO_ORIGIN ||
  (import.meta.env.DEV ? 'http://localhost:5173' : 'https://polaris-sso.laimengchao.site')

/** 注册的完整 redirect_uri（生产：https://polaris-finance.laimengchao.site/callback） */
export function callbackUrl(): string {
  return window.location.origin + '/callback'
}

export function gotoAuthorize(redirect: string) {
  const state = Math.random().toString(36).slice(2) + Date.now().toString(36)
  try {
    sessionStorage.setItem('finance_oauth_state', state)
    sessionStorage.setItem('finance_oauth_redirect', redirect)
  } catch { /* ignore */ }
  const url = `${SSO_AUTHORIZE_URL}/authorize?response_type=code` +
    `&client_id=finance&redirect_uri=${encodeURIComponent(callbackUrl())}` +
    `&state=${encodeURIComponent(state)}`
  window.location.replace(url)
}

export function consumeState(): string | null {
  try {
    const s = sessionStorage.getItem('finance_oauth_state')
    sessionStorage.removeItem('finance_oauth_state')
    return s
  } catch { return null }
}

export function takeRedirect(): string {
  let r = '/overview'
  try {
    r = sessionStorage.getItem('finance_oauth_redirect') || '/overview'
    sessionStorage.removeItem('finance_oauth_redirect')
  } catch { /* ignore */ }
  // 防 /callback 自循环
  return r.startsWith('/callback') || !r.startsWith('/') ? '/overview' : r
}

export interface TokenResult { accessToken: string; refreshToken: string }
export function exchangeCode(code: string, redirectUri: string) {
  return request.post<never, TokenResult>('/api/finance/sso/oauth/exchange', { code, redirectUri })
}

export function logoutSso(refreshToken?: string) {
  return request.post<never, void>('/api/auth/logout', { refreshToken })
}

/* ============================ 数据类型 ============================ */

export interface Page<T> { list: T[]; total: number }

export interface SubjectVO {
  id: string
  code: string
  name: string
  level: number
  balanceDirection: string
  category: string
  status: string
  orgCode?: string
  createdAt?: string
}

export interface BudgetSubjectVO {
  id: string
  code: string
  name: string
  year: number
  annualAmount: number
  accountSubjectCode?: string
  status: string
  orgCode?: string
  createdAt?: string
}

export interface ExpenseVO {
  id: string
  no: string
  applicant: string
  expenseType: string
  expenseTypeName?: string
  reason: string
  amount: number
  dept: string
  subject: string
  subjectCode?: string
  /** 发票类型字典 value */
  invoiceType?: string
  /** 税率（字典值为百分数，如 3 表示 3%） */
  taxRate?: number
  /** 税额（由金额 × 税率计算，客户端只读） */
  taxAmount?: number
  status: string
  voucherNo?: string
  date: string
  orgCode?: string
  /** 报销凭证 file_id 列表 */
  fileIds?: string[]
  /** 后端签发的临时预览 URL，与 fileIds 顺序一致 */
  attachmentUrls?: string[]
  createdAt?: string
}

/** 报销类型字典项：remark 承载 JSON {subjectCode, subjectName} 映射 */
export interface ExpenseTypeOption {
  value: string
  label: string
  remark?: string | null
}

/** 发起报销时的服务端默认申请人/归属部门（EHR 真相源） */
export interface ExpenseProfileVO {
  applicant: string
  dept: string
}

export interface VoucherVO {
  id: string
  no: string
  /** 凭证类型字典 value */
  voucherType?: string
  sourceType: string
  sourceNo?: string
  summary: string
  subject: string
  amount: number
  status: string
  date: string
  creator?: string
  /** 审核人 */
  reviewer?: string
  /** 过账人 */
  poster?: string
  dept?: string
  contractNo?: string
  orgCode?: string
  /** 凭证附件 file_id 列表 */
  fileIds?: string[]
  /** 凭证附件只读预览 URL */
  attachmentUrls?: string[]
  createdAt?: string
}

/** 薪资类往来且无 finance:salary:view 时 amount/balance 为 null（后端已脱敏） */
export interface ArApVO {
  id: string
  no: string
  party: string
  type: string
  amount: number | null
  balance: number | null
  ageDays: number
  contractNo?: string
  voucherNo?: string
  status: string
  date: string
  subject?: string
  summary?: string
  paymentStage?: string
  /** 发票号 */
  invoiceNo?: string
  /** 发票类型字典 value */
  invoiceType?: string
  /** 账期天数 */
  creditDays?: number
  /** 到期日 */
  dueDate?: string
  dept?: string
  orgCode?: string
  createdAt?: string
}

export interface BudgetVO {
  id: string
  dept: string
  year: number
  amount: number
  history: number
  current: number
  used: number
  available: number
  rate: number
  /** RED / YELLOW / 空，阈值来自后端埋点 finance.budget.alert_ratio */
  alertFlag: string
  contractCommitted: number
  contractPaid: number
  contractInFlight: number
  orgCode?: string
  createdAt?: string
}

export interface AgingBucketVO {
  label: string
  minDays: number
  maxDays: number
  value: number
  percent: number
}

export interface PaymentVO {
  id: string
  no: string
  contractNo?: string
  party: string
  /** 付款阶段字典 value */
  stage?: string
  amount: number
  /** 付款事由 */
  summary?: string
  /** 计划付款日期 */
  dueDate?: string
  /** 合同域来源单据号 */
  sourceDocNo?: string
  /** 过账凭证号 */
  voucherNo?: string
  /** 结算方式字典 value */
  settleMethod?: string
  status: string
  date: string
  orgCode?: string
  /** 合同与验收凭证 file_id 列表 */
  fileIds?: string[]
  /** 合同与验收凭证只读预览 URL */
  attachmentUrls?: string[]
  createdAt?: string
}

/* ============================ 请求体 ============================ */

export interface SubjectSave {
  code: string
  name: string
  level: number
  balanceDirection: string
  category: string
  status: string
  orgCode?: string
}
export interface BudgetSubjectSave {
  code: string
  name: string
  year: number
  annualAmount: number
  accountSubjectCode?: string
  status: string
  orgCode?: string
}
export interface ExpenseSave {
  applicant: string
  expenseType: string
  reason: string
  amount: number
  dept: string
  /** 兼容旧字段；新单科目由后端按报销类型映射带出 */
  subject?: string
  /** 发票类型字典 value */
  invoiceType?: string
  /** 税率（字典值为百分数，如 3 表示 3%） */
  taxRate?: number
  /** 税额（由金额 × 税率计算，客户端只读） */
  taxAmount?: number
  date: string
  orgCode?: string
  fileIds: string[]
}
export interface VoucherSave {
  /** 凭证类型字典 value */
  voucherType?: string
  sourceType: string
  sourceNo?: string
  summary: string
  subject: string
  amount: number
  date: string
  creator?: string
  dept?: string
  contractNo?: string
  fileIds?: string[]
  orgCode?: string
}
export interface ArApSave {
  party: string
  type: string
  amount: number
  contractNo?: string
  subject?: string
  summary?: string
  date: string
  dept?: string
  /** 发票号 */
  invoiceNo?: string
  /** 发票类型字典 value */
  invoiceType?: string
  /** 账期天数 */
  creditDays?: number
  /** 到期日 */
  dueDate?: string
  orgCode?: string
}
export interface BudgetSave {
  dept: string
  year: number
  amount: number
  history?: number
  contractCommitted?: number
  contractPaid?: number
  contractInFlight?: number
  orgCode?: string
}
export interface WriteOffRequest {
  amount: number
  targetNo?: string
  date?: string
}

/* ============================ 凭证 ============================ */

export interface VoucherQuery {
  sourceType?: string
  sourceNo?: string
  status?: string
  keyword?: string
  from?: string
  to?: string
  page: number
  size: number
}
export const listVouchers = (params: VoucherQuery) =>
  request.get<never, Page<VoucherVO>>('/api/finance/vouchers', { params })
export const getVoucher = (id: string) => request.get<never, VoucherVO>(`/api/finance/vouchers/${id}`)
export const createVoucher = (data: VoucherSave) => request.post<never, VoucherVO>('/api/finance/vouchers', data)
export const submitVoucherReview = (id: string) => request.post<never, void>(`/api/finance/vouchers/${id}/submit-review`)
export const reviewVoucher = (id: string) => request.post<never, void>(`/api/finance/vouchers/${id}/review`)
export const postVoucher = (id: string) => request.post<never, void>(`/api/finance/vouchers/${id}/post`)
export const deleteVoucher = (id: string) => request.delete<never, void>(`/api/finance/vouchers/${id}`)

/** 凭证附件 base64 上传代理，业务表只存 file_id */
export interface AttachmentUploadResult { fileId: string; url: string }
export const uploadVoucherAttachment = (file: File) =>
  new Promise<AttachmentUploadResult>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const content = String(reader.result || '').split(',')[1] || ''
      request.post<never, AttachmentUploadResult>('/api/finance/files/voucher/upload', {
        originalName: file.name, contentType: file.type, content
      }).then(resolve).catch(reject)
    }
    reader.onerror = () => reject(new Error('读取附件文件失败'))
    reader.readAsDataURL(file)
  })

/* ============================ 费用报销 ============================ */

export interface ExpenseQuery {
  status?: string
  keyword?: string
  dept?: string
  from?: string
  to?: string
  page: number
  size: number
}
export const listExpenses = (params: ExpenseQuery) =>
  request.get<never, Page<ExpenseVO>>('/api/finance/expenses', { params })
/** 申请人/归属部门由服务端从 EHR 反查，避免前端雪花 ID 精度丢失 */
export const fetchExpenseProfile = () =>
  request.get<never, ExpenseProfileVO>('/api/finance/expenses/my-profile')
/** 报销类型来自 settings 字典，remark 中维护「类型 → 会计科目」映射 */
export const fetchExpenseTypeOptions = () =>
  request.get<never, ExpenseTypeOption[]>('/api/settings/public/dicts/finance.expense_type')

/** 通用公共字典项（凭证类型/报销事由/发票类型/税率/结算方式/付款阶段等已在 settings 白名单放开） */
export interface DictOption { value: string; label: string; remark?: string | null }
export const fetchPublicDict = (typeCode: string) =>
  request.get<never, DictOption[]>(`/api/settings/public/dicts/${typeCode}`)
/** 报销凭证：base64 发到财务服务代理接口，由后端转交统一文件服务 */
export const uploadExpenseAttachment = (file: File) => new Promise<{ fileId: string; url: string }>((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => {
    const content = String(reader.result || '').split(',')[1] || ''
    request.post<never, { fileId: string; url: string }>('/api/finance/expense/files/upload', {
      originalName: file.name, contentType: file.type, content
    }).then(resolve).catch(reject)
  }
  reader.onerror = () => reject(new Error('读取凭证文件失败'))
  reader.readAsDataURL(file)
})
export const getExpense = (id: string) => request.get<never, ExpenseVO>(`/api/finance/expenses/${id}`)
export const createExpense = (data: ExpenseSave) => request.post<never, ExpenseVO>('/api/finance/expenses', data)
export const updateExpense = (id: string, data: ExpenseSave) => request.put<never, ExpenseVO>(`/api/finance/expenses/${id}`, data)
export const submitExpense = (id: string) => request.post<never, void>(`/api/finance/expenses/${id}/submit`)
export const approveExpense = (id: string) => request.post<never, void>(`/api/finance/expenses/${id}/approve`)
export const rejectExpense = (id: string) => request.post<never, void>(`/api/finance/expenses/${id}/reject`)
export const postExpense = (id: string) => request.post<never, void>(`/api/finance/expenses/${id}/post`)
export const payExpense = (id: string) => request.post<never, void>(`/api/finance/expenses/${id}/pay`)
export const deleteExpense = (id: string) => request.delete<never, void>(`/api/finance/expenses/${id}`)

/* ============================ 预算 ============================ */

export interface BudgetQuery { dept?: string; year?: number; page: number; size: number }
export const listBudgets = (params: BudgetQuery) =>
  request.get<never, Page<BudgetVO>>('/api/finance/budgets', { params })
export const getBudget = (id: string) => request.get<never, BudgetVO>(`/api/finance/budgets/${id}`)
export const budgetAvailable = (dept: string, year?: number) =>
  request.get<never, number>('/api/finance/budgets/available', { params: { dept, year } })
export const createBudget = (data: BudgetSave) => request.post<never, BudgetVO>('/api/finance/budgets', data)
export const updateBudget = (id: string, data: BudgetSave) => request.put<never, BudgetVO>(`/api/finance/budgets/${id}`, data)
export const deleteBudget = (id: string) => request.delete<never, void>(`/api/finance/budgets/${id}`)

/* ============================ 应收应付 ============================ */

export interface ArApQuery {
  type?: string
  status?: string
  keyword?: string
  contractNo?: string
  from?: string
  to?: string
  page: number
  size: number
}
export const listArAp = (params: ArApQuery) =>
  request.get<never, Page<ArApVO>>('/api/finance/ar-ap', { params })
export const listAging = () => request.get<never, AgingBucketVO[]>('/api/finance/ar-ap/aging')
export const getArAp = (id: string) => request.get<never, ArApVO>(`/api/finance/ar-ap/${id}`)
export const createArAp = (data: ArApSave) => request.post<never, ArApVO>('/api/finance/ar-ap', data)
export const approveArAp = (id: string) => request.post<never, void>(`/api/finance/ar-ap/${id}/approve`)
export const postArAp = (id: string) => request.post<never, void>(`/api/finance/ar-ap/${id}/post`)
export const writeOffArAp = (id: string, data: WriteOffRequest) =>
  request.post<never, void>(`/api/finance/ar-ap/${id}/write-off`, data)
export const deleteArAp = (id: string) => request.delete<never, void>(`/api/finance/ar-ap/${id}`)

/* ============================ 付款单 ============================ */

export interface PaymentQuery { contractNo?: string; status?: string; keyword?: string; page: number; size: number }
export const listPayments = (params: PaymentQuery) =>
  request.get<never, Page<PaymentVO>>('/api/finance/payments', { params })
export const getPayment = (id: string) => request.get<never, PaymentVO>(`/api/finance/payments/${id}`)
export const payPayment = (id: string) => request.post<never, void>(`/api/finance/payments/${id}/pay`)
export const cancelPayment = (id: string) => request.post<never, void>(`/api/finance/payments/${id}/cancel`)

/* ============================ 会计科目 ============================ */

export interface SubjectQuery { keyword?: string; status?: string; level?: number; category?: string; page: number; size: number }
export const listSubjects = (params: SubjectQuery) =>
  request.get<never, Page<SubjectVO>>('/api/finance/subjects', { params })
export const subjectOptions = () => request.get<never, SubjectVO[]>('/api/finance/subjects/options')
export const getSubject = (id: string) => request.get<never, SubjectVO>(`/api/finance/subjects/${id}`)
export const createSubject = (data: SubjectSave) => request.post<never, SubjectVO>('/api/finance/subjects', data)
export const updateSubject = (id: string, data: SubjectSave) => request.put<never, SubjectVO>(`/api/finance/subjects/${id}`, data)
export const toggleSubject = (id: string, status?: string) =>
  request.post<never, void>(`/api/finance/subjects/${id}/toggle`, null, { params: { status } })
export const deleteSubject = (id: string) => request.delete<never, void>(`/api/finance/subjects/${id}`)

/* ============================ 预算科目 ============================ */

export interface BudgetSubjectQuery { keyword?: string; year?: number; status?: string; page: number; size: number }
export const listBudgetSubjects = (params: BudgetSubjectQuery) =>
  request.get<never, Page<BudgetSubjectVO>>('/api/finance/budget-subjects', { params })
export const budgetSubjectOptions = (year?: number) =>
  request.get<never, BudgetSubjectVO[]>('/api/finance/budget-subjects/options', { params: { year } })
export const getBudgetSubject = (id: string) => request.get<never, BudgetSubjectVO>(`/api/finance/budget-subjects/${id}`)
export const createBudgetSubject = (data: BudgetSubjectSave) =>
  request.post<never, BudgetSubjectVO>('/api/finance/budget-subjects', data)
export const updateBudgetSubject = (id: string, data: BudgetSubjectSave) =>
  request.put<never, BudgetSubjectVO>(`/api/finance/budget-subjects/${id}`, data)
export const toggleBudgetSubject = (id: string, status?: string) =>
  request.post<never, void>(`/api/finance/budget-subjects/${id}/toggle`, null, { params: { status } })
export const deleteBudgetSubject = (id: string) => request.delete<never, void>(`/api/finance/budget-subjects/${id}`)

/* ==================== 组织 / 人员（EHR 只读引用，经财务服务内部令牌代理） ==================== */

/** EHR 组织节点（/internal/orgs/tree 扁平列表，id/parentId 已字符串化）；dept 字段存组织名称 */
export interface OrgNodeVO {
  id: string
  parentId: string | null
  type: string
  typeName: string
  code: string
  name: string
  status?: number
  employeeCount?: number
}

/** 在职员工候选（已开户、未离职；id 为字符串化账号 ID） */
export interface OrgEmployeeVO {
  id: string
  name: string
  empNo: string
  orgId?: string
}

/** 全量组织节点扁平列表，前端自行组树 */
export const fetchOrgTree = () => request.get<never, OrgNodeVO[]>('/api/finance/orgs/tree')

/** 全部已开户、未离职员工，人员下拉候选（姓名/工号） */
export const fetchActiveEmployees = () => request.get<never, OrgEmployeeVO[]>('/api/finance/orgs/employees/active')

/** 合同下拉候选（经财务服务代理合同域，只取编号与名称） */
export interface ContractOptionVO {
  no: string
  name: string
}
export const fetchContractOptions = () => request.get<never, ContractOptionVO[]>('/api/finance/contracts/options')

/** 往来单位下拉候选（经财务服务代理合同域，按角色取客户 / 供应商） */
export interface PartnerOptionVO {
  code: string
  name: string
  role?: string
}
export const fetchPartnerOptions = (role: 'CUSTOMER' | 'SUPPLIER') =>
  request.get<never, PartnerOptionVO[]>('/api/finance/partners/options', { params: { role } })
