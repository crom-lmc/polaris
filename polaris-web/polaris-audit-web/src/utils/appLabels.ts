import { ref } from 'vue'
import { fetchMyApps } from '@/api/audit'

/**
 * 审计页面统一应用名：显示「实际应用名（应用编码）」，避免只看到编码或旧语义名时分不清。
 * 名称优先取 /api/accounts/me/apps（数据库 sso_application.name）；接口失败时用同步兜底表，
 * 保证首屏和离线场景也能显示正确名称。sso-auth / sso-gateway / sso-account 是账号域的子服务，
 * 统一归到 sso（北极星钥）名下。
 */
const fallbackNames: Record<string, string> = {
  sso: '北极星钥',
  'sso-center': '北极星钥',
  'sso-account': '北极星钥',
  'sso-auth': '北极星钥',
  'sso-gateway': '北极星钥',
  ehr: '北极星宿',
  cms: '北极星源',
  settings: '北极星典',
  job: '北极星轨',
  portal: '北极星门',
  forum: '北极星空',
  'forum-web': '北极星空',
  'forum-admin': '北极星核',
  openapi: '北极星桥',
  file: '北极星云',
  audit: '北极星眼'
}

const names = ref<Record<string, string>>({ ...fallbackNames })
let loaded = false
let pending: Promise<void> | null = null

/** 页面挂载时调用一次；多个页面并发共享同一个请求。失败时保留兜底名，不阻断页面。 */
export function ensureAppNames(): Promise<void> {
  if (loaded) return Promise.resolve()
  if (!pending) {
    pending = fetchMyApps()
      .then((res) => {
        const apps = res.data || []
        apps.forEach((a) => {
          if (a?.code && a?.name) names.value[a.code] = a.name
        })
        const ssoName = apps.find((a) => a.code === 'sso-center')?.name || apps.find((a) => a.code === 'sso')?.name
        if (ssoName) {
          names.value.sso = ssoName
          names.value['sso-center'] = ssoName
          names.value['sso-account'] = ssoName
          names.value['sso-auth'] = ssoName
          names.value['sso-gateway'] = ssoName
        }
        loaded = true
      })
      .catch(() => { /* 名称取不到时继续用内置兜底 */ })
      .finally(() => { pending = null })
  }
  return pending
}

/** 展示用标签：北极星钥（sso），未知编码原样返回。 */
export function appLabel(code?: string): string {
  if (!code) return '-'
  const name = names.value[code] || (code.startsWith('sso-') ? names.value.sso : undefined)
  return name ? `${name}（${code}）` : code
}

/** 下拉选项用：已知应用编码 + 数据中实际出现的编码。 */
export function appCodes(extra: string[] = []): string[] {
  const set = new Set([...Object.keys(names.value), ...extra.filter(Boolean)])
  return [...set].sort()
}
