/** 后端统一响应外壳：code=0 成功，其余为业务错误（拦截器会脱壳） */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页结果，字段与后端 PageResult 对齐 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 账户身份快照（来自 /api/auth/me 或登录响应） */
export interface AuthUser {
  accountId: number
  username: string
  nickname?: string
  superAdmin: boolean
  /** 按钮级权限码 */
  permissions: string[]
  /** 可访问的应用编码 */
  apps: string[]
}

/** 登录/刷新下发的一对令牌 */
export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

/** 账户详情（管理端列表/详情） */
export interface AccountVO {
  /** 雪花ID，后端序列化为字符串（19 位数字超出 JS 安全整数） */
  id: string
  username: string
  nickname?: string
  email?: string
  phone?: string
  status: number
  statusDesc?: string
  source?: string
  pwdResetRequired?: number
  lastLoginAt?: string
  createdAt?: string
  roles?: string[]
  /** 数据权限根组织编码；为空表示未配置 */
  dataScopeOrgCode?: string
}

/** 应用（SSO 保护的下游系统） */
export interface ApplicationVO {
  /** 雪花ID，后端序列化为字符串 */
  id: string
  code: string
  name: string
  /** 应用类型，取值来自配置中心字典 sso.application_type */
  appType?: string
  baseUrl?: string
  /** OAuth2 回调地址（授权码回跳点），「进入」按钮构造 /authorize 用 */
  redirectUri?: string
  icon?: string
  remark?: string
  status: number
  sort?: number
  createdAt?: string
  /** 平台内置应用（统一账号中心）：默认存在，不允许编辑/停用/删除/重置密钥 */
  builtIn?: boolean
  /** 脱敏密钥（列表用） */
  appSecretMasked?: string
  /** 明文密钥，仅创建/重置时返回一次 */
  appSecret?: string
}

/** 中心会话快照 */
export interface SessionInfo extends AuthUser {
  appList: ApplicationVO[]
}

/** 一次性票据下发结果 */
export interface TicketResponse {
  ticket: string
  appCode: string
  appName: string
  /** 已拼好 ticket 参数，直接跳转即可 */
  redirectTo: string
  expiresIn: number
}
