/** 金额展示：后端 BigDecimal 以 number 返回，展示层统一格式化。 */
export function yuan(value?: number | string | null): string {
  const n = Number(value || 0)
  return `¥${n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

/** 数值展示：空值显示 -。 */
export function num(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

/** 时间展示：后端 LocalDate 为 YYYY-MM-DD，LocalDateTime 为 ISO 8601，展示时把 T 换成空格。 */
export function fmtTime(value?: string, withTime = true): string {
  if (!value) return '-'
  const text = value.replace('T', ' ')
  return withTime ? text.slice(0, 16) : text.slice(0, 10)
}

/** 雪花 ID 等长数字统一以字符串传递，提交时避免 number 精度丢失。 */
export function asId(value?: string | number): string {
  return value === undefined || value === null ? '' : String(value)
}
