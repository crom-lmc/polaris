import request from './request'

/** 当前登录者可访问的应用详情（含 name），用于取本应用登记名 */
export interface MyAppInfo { code: string; name: string }
export const fetchMyApps = () => request.get<never, MyAppInfo[]>('/api/accounts/me/apps')

/** 统一账号中心 sso-web 的 /authorize 页（授权码签发入口） */
export const SSO_AUTHORIZE_URL = import.meta.env.VITE_SSO_ORIGIN || 'http://localhost:5173'

/** 本应用回调地址（须与统一账号中心 file 应用的 baseUrl 同 origin） */
export function callbackUrl(): string {
  return window.location.origin + '/callback'
}

/** 跳转统一账号中心签授权码（client_id=file） */
export function gotoAuthorize(redirect: string) {
  const state = Math.random().toString(36).slice(2) + Date.now().toString(36)
  try {
    sessionStorage.setItem('file_oauth_state', state)
    sessionStorage.setItem('file_oauth_redirect', redirect)
  } catch { /* ignore */ }
  const url =
    `${SSO_AUTHORIZE_URL}/authorize?response_type=code` +
    `&client_id=file&redirect_uri=${encodeURIComponent(callbackUrl())}` +
    `&state=${encodeURIComponent(state)}`
  window.location.replace(url)
}

export function consumeState(): string | null {
  try {
    const s = sessionStorage.getItem('file_oauth_state')
    sessionStorage.removeItem('file_oauth_state')
    return s
  } catch { return null }
}

export function takeRedirect(): string {
  try {
    const r = sessionStorage.getItem('file_oauth_redirect') || '/'
    sessionStorage.removeItem('file_oauth_redirect')
    return r
  } catch { return '/' }
}

/** OAuth2 授权码换 file 用户令牌（appSecret 在 file 后端） */
export function exchangeCode(code: string, redirectUri: string) {
  return request.post('/api/file/sso/oauth/exchange', { code, redirectUri })
}

export function logoutSso(refreshToken?: string) {
  return request.post<never, void>('/api/auth/logout', { refreshToken })
}

export interface MeInfo {
  accountId: number | string
  username: string
  superAdmin: boolean
  appCode: string
  permissions: string[]
}

export function fetchMe() {
  return request.get<never, MeInfo>('/api/file/admin/me')
}

// ---------------- 存储概览 ----------------
export interface Overview {
  total: number
  publicCount: number
  privateCount: number
  totalBytes: number
  todayCount?: number
  byBizType?: Array<{ bizType: string; cnt: number; bytes: number }>
  trend?: Array<{ day: string; cnt: number; bytes?: number }>
  recent?: FileMeta[]
}
export const getOverview = () => request.get<never, Overview>('/api/file/admin/overview')

/** 自定义时间范围的每日上传统计（含首尾，缺失日期补 0） */
export interface DailyUploadStat { day: string; cnt: number; bytes: number }
export const getUploadTrend = (startDate: string, endDate: string) =>
  request.get<never, DailyUploadStat[]>('/api/file/admin/overview/trend', { params: { startDate, endDate } })

// ---------------- 文件管理 ----------------
export interface FileMeta {
  id: string
  bizType: string
  visibility: 'PUBLIC' | 'PRIVATE' | string
  originalName: string
  contentType: string
  size: number
  sha256: string
  ownerType: string
  ownerId: string
  status: string
  url?: string
  thumbUrl?: string
  deduplicated?: boolean
  createdAt: string
  deletedAt?: string
}
export interface FilePage {
  records: FileMeta[]
  total: number
  page: number
  size: number
}
export function listFiles(params: {
  page: number
  size: number
  bizType?: string
  visibility?: string
  keyword?: string
}) {
  return request.get<never, FilePage>('/api/file/admin/files', { params })
}
export function uploadFile(payload: {
  bizType: string
  visibility?: string
  originalName: string
  contentType: string
  content: string
  ownerType?: string
  ownerId?: string
}) {
  return request.post<never, FileMeta>('/api/file/admin/files/upload', payload)
}
export function signFile(id: string, purpose: 'VIEW' | 'DOWNLOAD', expiresSeconds?: number) {
  return request.post<never, { url: string; expiresAt: number }>(`/api/file/admin/files/${id}/sign`,
    { purpose, expiresSeconds })
}

/** 预览信息：IMAGE / PDF / TEXT / UNSUPPORTED */
export interface PreviewInfo {
  mode: 'IMAGE' | 'PDF' | 'TEXT' | 'UNSUPPORTED'
  name: string
  contentType: string
  url: string
  thumbUrl?: string
}
export const previewFile = (id: string) => request.get<never, PreviewInfo>(`/api/file/admin/files/${id}/preview`)

/** 读取文本内容用于文本预览 */
export const readFileText = (url: string) => request.get<never, string>(url, { responseType: 'text' })
export function deleteFile(id: string) {
  return request.delete<never, void>(`/api/file/admin/files/${id}`)
}

// ---------------- 回收站 ----------------
export function listRecycle(params: { page: number; size: number; bizType?: string; keyword?: string }) {
  return request.get<never, FilePage>('/api/file/admin/recycle', { params })
}
export function restoreFile(id: string) {
  return request.post<never, void>(`/api/file/admin/files/${id}/restore`)
}
export function purgeFile(id: string) {
  return request.delete<never, void>(`/api/file/admin/files/${id}/purge`)
}

// ---------------- 文件分享 ----------------
export interface FileShare {
  id: string
  fileId: string
  shareCode: string
  url: string
  fileName?: string
  expiresAt?: string
  maxViews: number
  viewCount: number
  enabled: boolean
  createdAt: string
}
export interface ShareCreatePayload {
  fileId: string
  password?: string
  expiresHours?: number
  maxViews?: number
}
export const listShares = () => request.get<never, FileShare[]>('/api/file/admin/shares')
export const createShare = (payload: ShareCreatePayload) => request.post<never, FileShare>('/api/file/admin/shares', payload)
export const revokeShare = (id: string) => request.delete<never, void>(`/api/file/admin/shares/${id}`)

// ---------------- 分片上传 ----------------
export interface MultipartInit {
  bizType: string
  visibility?: string
  originalName: string
  contentType: string
  totalSize: number
  ownerType?: string
  ownerId?: string
}
export interface MultipartSession {
  uploadId: string
  chunkSize: number
  totalChunks: number
}
export const initMultipart = (payload: MultipartInit) =>
  request.post<never, MultipartSession>('/api/file/admin/multipart/init', payload)
export const uploadPart = (uploadId: string, index: number, content: string) =>
  request.post<never, void>(`/api/file/admin/multipart/${uploadId}/part/${index}`, { content })
export const completeMultipart = (uploadId: string) =>
  request.post<never, FileMeta>(`/api/file/admin/multipart/${uploadId}/complete`)

// ---------------- 存储策略 ----------------
export interface StoragePolicy {
  id?: string
  bizType: string
  bizName: string
  maxSizeMb: number
  allowedExt: string
  defaultVisibility: 'PUBLIC' | 'PRIVATE' | string
  forcePrivate: boolean
  quotaGb?: number
  ttlDays?: number
  enabled: boolean
  remark?: string
  createdAt?: string
  updatedAt?: string
}
export const listPolicies = () => request.get<never, StoragePolicy[]>('/api/file/admin/policies')
export const savePolicy = (data: StoragePolicy) => request.put<never, void>('/api/file/admin/policies', data)

/** bizType 下拉（取自策略） */
export const bizTypeOptions = async () => (await listPolicies()).map(p => ({
  label: p.bizName || p.bizType,
  value: p.bizType
}))
