import { ref } from 'vue'
import { fetchMyApps } from '@/api/portal'

const cache = new Map<string, string>()
const pending = new Map<string, Promise<string>>()

/** 共享应用名：布局标题与浏览器标签都从这里取，避免重复请求。兜底为原语义名。 */
export const appName = ref('北极星门')

/** 查询应用名；成功结果按 appCode 缓存，失败返回空串由调用方保留兜底名。 */
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

/**
 * 渲染浏览器标签：页面名 · 应用名（应用名以登录接口实际结果为准，失败保留兜底）。
 */
export function renderAppTitle(pageTitle?: string) {
  const base = appName.value || '北极星门'
  document.title = pageTitle ? `${pageTitle} · ${base}` : base
}
