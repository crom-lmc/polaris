import { ref } from 'vue'
import { fetchMyApps } from '@/api/contract'

const cache = new Map<string, string>()
const pending = new Map<string, Promise<string>>()

/** 正式应用名：从 /api/accounts/me/apps 的 sso_application.name 取，失败保留兜底名。 */
export const appName = ref('北极星契')

export function resolveAppName(appCode: string): Promise<string> {
  const cached = cache.get(appCode)
  if (cached) {
    appName.value = cached
    return Promise.resolve(cached)
  }
  let task = pending.get(appCode)
  if (!task) {
    task = fetchMyApps()
      .then((apps) => (apps || []).find((a) => a.code === appCode)?.name || '')
      .then((name) => {
        if (name) {
          cache.set(appCode, name)
          appName.value = name
        }
        return name
      })
      .catch(() => '')
      .finally(() => pending.delete(appCode))
    pending.set(appCode, task)
  }
  return task
}

/** 浏览器标签：页面名 · 应用名（应用名以登录接口实际结果为准）。 */
export function renderAppTitle(pageTitle?: string) {
  const base = appName.value || '北极星契'
  document.title = pageTitle ? `${pageTitle} · ${base}` : base
}
