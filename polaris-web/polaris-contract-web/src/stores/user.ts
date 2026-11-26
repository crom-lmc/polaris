import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, sessionToken, type MeInfo } from '@/api/contract'
import { clearTokens, getAccessToken, getNickname, setTokens } from '@/utils/token'

/** 登录态：令牌在 localStorage，身份/权限由网关 /api/auth/me 实时刷新。 */
export const useUserStore = defineStore('user', () => {
  const me = ref<MeInfo | null>(null)
  const ready = ref(false)

  const permissions = computed(() => me.value?.permissions || [])
  const isSuperAdmin = computed(() => !!me.value?.superAdmin)
  const username = computed(() => me.value?.username || me.value?.name || '')
  /** 顶栏展示中文昵称（JWT nick claim），失败回退登录名。 */
  const displayName = computed(() => getNickname() || me.value?.nickname || me.value?.name || username.value)

  /** 按钮级权限判断：超管直接放行，与后端 @Permit 同口径。 */
  function can(code: string): boolean {
    if (!me.value) return false
    return me.value.superAdmin || permissions.value.includes(code)
  }

  function applyMe(info: MeInfo) {
    me.value = info
    ready.value = true
  }

  async function bootstrap(): Promise<boolean> {
    if (ready.value && me.value) return true
    if (!getAccessToken()) {
      // 中心会话静默换票：从应用中心单点过来的用户走这里；探测接口不触发 401 跳转
      try {
        const res = await sessionToken()
        setTokens(res.accessToken, res.refreshToken)
      } catch {
        return false
      }
    }
    try {
      applyMe(await fetchMe())
      return true
    } catch {
      clearTokens()
      me.value = null
      ready.value = false
      return false
    }
  }

  function logout() {
    clearTokens()
    me.value = null
    ready.value = false
  }

  return { me, ready, permissions, isSuperAdmin, username, displayName, can, applyMe, bootstrap, logout }
})
