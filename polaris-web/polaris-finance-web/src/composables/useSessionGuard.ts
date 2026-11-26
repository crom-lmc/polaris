import { onBeforeUnmount, onMounted } from 'vue'
import request from '@/api/request'

/** 会话心跳：20s 探测 + 切回标签页立即探测；401 由请求拦截器统一处理 */
const INTERVAL_MS = 20000

export function useSessionGuard() {
  let timer: number | undefined

  async function probe() {
    try {
      await request.get<never, unknown>('/api/auth/me')
    } catch {
      /* 401 已由响应拦截器清 token 并跳转，其余网络错误静默 */
    }
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
