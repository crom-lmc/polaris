import request from './request'
import type { PageResult } from '@/types'

/** 审计事件记录（统一审计中心的 audit_event 单表） */
export interface AuditEvent {
  id: string
  app: string
  eventCategory: 'ACCESS' | 'LOGIN' | 'BUSINESS' | 'SENSITIVE' | string
  action: string
  httpMethod?: string
  requestPath?: string
  operatorType: 'USER' | 'SYSTEM' | string
  operatorId?: string
  operatorName: string
  targetType?: string
  targetId?: string
  detail?: string
  /** 请求入参（已脱敏） */
  requestParams?: string
  /** HTTP 状态码 */
  httpStatus?: number
  /** 业务返回码 R.code */
  bizCode?: number
  /** 响应结果摘要（已脱敏） */
  responseSummary?: string
  /** 处理耗时毫秒 */
  costMs?: number
  result?: string
  clientIp?: string
  userAgent?: string
  createdAt: string
}

/** 登录日志记录 */
export interface LoginLog {
  id: string
  accountId?: string
  username: string
  loginType: string
  result: 'success' | 'fail' | string
  failReason?: string
  clientIp?: string
  userAgent?: string
  createdAt?: string
}

/** 审计概览统计 */
export interface AuditOverview {
  today: number
  week: number
  total: number
  loginFail: number
  trend: Array<{ day: string; cnt: number }>
  byApp: Array<{ app: string; cnt: number }>
  byCategory: Array<{ category: string; cnt: number }>
}

/** 平台统一审计事件（网关 ACCESS + 账号 LOGIN/SENSITIVE + EHR SENSITIVE） */
export function fetchAuditEvents(params: {
  page: number
  size: number
  app?: string
  eventCategory?: string
  action?: string
  operator?: string
  httpMethod?: string
  result?: string
  startDate?: string
  endDate?: string
}) {
  return request.get<never, { data: PageResult<AuditEvent> }>('/api/audit/admin/events', { params })
}

/** 审计概览统计 */
export function fetchAuditOverview() {
  return request.get<never, { data: AuditOverview }>('/api/audit/admin/overview')
}

/** 登录审计日志 */
export function fetchLoginLogs(params: {
  page: number
  size: number
  keyword?: string
  loginType?: string
}) {
  return request.get<never, { data: PageResult<LoginLog> }>('/api/audit/admin/login-events', { params })
}

/** 系统运行日志（各服务 Logback 上报到 system_log） */
export interface SystemLog {
  id: string
  app: string
  level: 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | string
  logger: string
  thread: string
  message: string
  stackTrace?: string
  createdAt: string
}

/** 系统日志概览统计 */
export interface SystemLogOverview {
  today: number
  total: number
  error: number
  warn: number
  byApp: Array<{ app: string; cnt: number }>
  byLevel: Array<{ level: string; cnt: number }>
}

/** 日志文件列表项：某应用某天的日志分组统计 */
export interface LogFileGroup {
  app: string
  day: string
  cnt: number
  err: number
  warn: number
  first_at: string
  last_at: string
}

/** 系统运行日志分页查询（按应用/级别/关键字/日期） */
export function fetchSystemLogs(params: {
  page: number
  size: number
  app?: string
  level?: string
  keyword?: string
  startDate?: string
  endDate?: string
}) {
  return request.get<never, { data: PageResult<SystemLog> }>('/api/audit/admin/system-logs', { params })
}

/** 有日志上报的应用列表（筛选项） */
export function fetchSystemLogApps() {
  return request.get<never, { data: string[] }>('/api/audit/admin/system-logs/apps')
}

/** 系统日志概览统计 */
export function fetchSystemLogOverview() {
  return request.get<never, { data: SystemLogOverview }>('/api/audit/admin/system-logs/overview')
}

/** 日志文件列表：按应用 + 日期分组 */
export function fetchLogFileGroups(params: { app?: string; startDate?: string; endDate?: string }) {
  return request.get<never, { data: LogFileGroup[] }>('/api/audit/admin/system-logs/groups', { params })
}

/** 终端日志流：某应用某天的日志（时间正序） */
export function fetchLogStream(params: {
  app: string
  day: string
  level?: string
  keyword?: string
  limit?: number
}) {
  return request.get<never, { data: SystemLog[] }>('/api/audit/admin/system-logs/stream', { params })
}

/** 当前登录者可访问的应用详情（登录体系 bootstrap 需要） */
export function fetchMyApps() {
  return request.get<never, { data: import('@/types').ApplicationVO[] }>('/api/accounts/me/apps')
}
