import type { Dayjs } from 'dayjs'

/** 金额展示；后端 BigDecimal 以 number 返回，null 表示无数据 */
export function yuan(v?: number | string | null): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  return `¥${n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

/** 应收应付金额可能被后端按 finance:salary:view 脱敏为 null */
export function yuanOrMasked(v?: number | null): string {
  return v === null || v === undefined ? '****' : yuan(v)
}

/** LocalDateTime 后端返回 ISO 串（含 T），展示时替换为空格 */
export function fmtDateTime(v?: string | null): string {
  return v ? v.replace('T', ' ').slice(0, 16) : '-'
}

export function fmtDate(v?: string | null): string {
  return v || '-'
}

/** 查询日期选择器字符串口径：YYYY-MM-DD */
export function dateOf(v?: Dayjs | null): string | undefined {
  return v ? v.format('YYYY-MM-DD') : undefined
}

const STATUS: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  POSTED: '已入账',
  PAID: '已付款',
  ENABLED: '启用',
  DISABLED: '停用'
}

export function statusLabel(s?: string): string {
  return s ? (STATUS[s] || s) : '-'
}

export function statusColor(s?: string): string {
  if (s === 'PENDING') return 'blue'
  if (s === 'APPROVED' || s === 'POSTED' || s === 'PAID' || s === 'ENABLED') return 'green'
  if (s === 'REJECTED' || s === 'DISABLED') return 'red'
  return 'default'
}

const SOURCE: Record<string, string> = {
  MANUAL: '手工凭证',
  EXPENSE: '报销单',
  AR: '应收单',
  AP: '应付单',
  PAYMENT: '付款单',
  PAYROLL: '薪资计提'
}

export function sourceLabel(s?: string): string {
  return s ? (SOURCE[s] || s) : '-'
}

export function arApTypeLabel(s?: string): string {
  return s === 'AR' ? '应收' : s === 'AP' ? '应付' : '-'
}

export function arApTypeColor(s?: string): string {
  return s === 'AR' ? 'blue' : 'orange'
}

/** 展示层归一：后端存英文常量，提交沿用同一常量 */
const CATEGORY: Record<string, string> = {
  ASSET: '资产',
  LIABILITY: '负债',
  EQUITY: '权益',
  INCOME: '收入',
  EXPENSE: '费用'
}
export function categoryLabel(s?: string): string {
  return s ? (CATEGORY[s] || s) : '-'
}

export function directionLabel(s?: string): string {
  return s === 'DEBIT' ? '借' : s === 'CREDIT' ? '贷' : '-'
}
