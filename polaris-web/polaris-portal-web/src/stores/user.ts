import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, type MeInfo } from '@/api/portal'
import { clearTokens, getAccessToken, getNickname } from '@/utils/token'

/** 登录态：令牌在 localStorage，身份由 /api/portal/me 实时刷新。门户公开内容无需登录 */
export const useUserStore = defineStore('user', () => {
  const me = ref<MeInfo | null>(null)
  const ready = ref(false)

  const isLoggedIn = computed(() => !!me.value)
  const username = computed(() => me.value?.username || '')
  /** 顶栏展示名：优先 JWT 中文昵称，回退登录名（/me 的 username 是登录名） */
  const displayName = computed(() => getNickname() || username.value)

  function applyMe(info: MeInfo) {
    me.value = info
    ready.value = true
  }

  async function bootstrap(): Promise<boolean> {
    if (ready.value && me.value) return true
    if (!getAccessToken()) return false
    try {
      const info = await fetchMe()
      applyMe(info)
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

  return { me, ready, isLoggedIn, username, displayName, applyMe, bootstrap, logout }
})
