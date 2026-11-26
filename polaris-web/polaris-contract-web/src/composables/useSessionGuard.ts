import { onBeforeUnmount, onMounted } from 'vue'
import request from '@/api/request'

/**
 * 会话心跳：探 /api/auth/me。中心会话靠 HttpOnly cookie 承载，
 * 令牌里带 sid，网关据此校验是否已登出；access token 过期时 401 由拦截器换票。
 */
const INTERVAL_MS = 20000

export function useSessionGuard() {
  let timer: number | undefined

  async function probe() {
    try {
      await request.get<never, unknown>('/api/auth/me')
    } catch { /* 401 已由拦截器处理；其余网络错误静默 */ }
  }

  function onVisibilityChange() {
    if (document.visibilityState === 'visible') probe()
  }

  onMounted(() => {
    timer = window.setInterval(probe, INTERVAL_MS)
    document.addEventListener('visibilitychange', onVisibilityChange)
  })

  onBeforeUnmount(() => {
    if (timer) window.clearInterval(timer)
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })
}
