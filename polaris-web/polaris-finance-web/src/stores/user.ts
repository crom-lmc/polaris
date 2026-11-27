import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, type MeInfo } from '@/api/finance'
import { clearTokens, getAccessToken, getNickname } from '@/utils/token'

/** 登录态：令牌在 localStorage，身份/权限由 /api/auth/me 实时刷新 */
export const useUserStore = defineStore('user', () => {
  const me = ref<MeInfo | null>(null)
  const ready = ref(false)

  const permissions = computed(() => me.value?.permissions || [])
  /** SSO 权限树下发的菜单：侧栏名称与顺序以此为准 */
  const menus = computed(() => me.value?.menus || [])
  const isSuperAdmin = computed(() => !!me.value?.superAdmin)
  const username = computed(() => me.value?.name || me.value?.username || '')
  /** 顶栏展示中文昵称：JWT nick 优先，其次 /me 的 nickname，最后登录名 */
  const displayName = computed(() => getNickname() || me.value?.nickname || username.value)

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
    if (!getAccessToken()) return false
    try {
      applyMe(await fetchMe())
      return true
    } catch {
      clearTokens()
      return false
    }
  }

  function logout() {
    clearTokens()
    me.value = null
    ready.value = false
  }

  return { me, ready, permissions, menus, isSuperAdmin, username, displayName, can, applyMe, bootstrap, logout }
})
