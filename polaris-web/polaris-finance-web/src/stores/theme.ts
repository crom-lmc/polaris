import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'finance_theme'
const THEME_COOKIE = 'polaris_theme'
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365

function readCookieMode(): ThemeMode | null {
  try {
    const hit = document.cookie.split('; ').find((item) => item.startsWith(`${THEME_COOKIE}=`))
    const value = hit ? hit.slice(THEME_COOKIE.length + 1) : ''
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

function writeCookieMode(mode: ThemeMode) {
  try {
    const host = window.location.hostname
    const isIpv4 = /^\d{1,3}(\.\d{1,3}){3}$/.test(host)
    const parts = host.split('.')
    const domain = !isIpv4 && parts.length >= 3 ? `; domain=.${parts.slice(-2).join('.')}` : ''
    document.cookie = `${THEME_COOKIE}=${mode}; path=/; max-age=${COOKIE_MAX_AGE}; SameSite=Lax${domain}`
  } catch { /* cookie 不可用时忽略 */ }
}

function resolveInitial(): ThemeMode {
  const shared = readCookieMode()
  if (shared) return shared
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark'
  } catch { /* 隐私模式回退 */ }
  return 'light'
}

function applyDom(mode: ThemeMode) {
  document.documentElement.setAttribute('data-theme', mode)
  writeCookieMode(mode)
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch { /* 忽略写入失败 */ }
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(resolveInitial())
  applyDom(mode.value)

  window.addEventListener('focus', () => {
    const shared = readCookieMode()
    if (shared && shared !== mode.value) mode.value = shared
  })

  const antTheme = computed(() => {
    const isDark = mode.value === 'dark'
    return {
      token: { colorPrimary: '#2f54eb', borderRadius: 6 },
      components: {
        Menu: {
          itemSelectedBg: isDark ? 'rgba(47, 84, 235, 0.20)' : '#e6ebff',
          itemSelectedColor: isDark ? 'rgba(255, 255, 255, 0.95)' : '#2f54eb',
          itemHoverBg: isDark ? 'rgba(255, 255, 255, 0.06)' : 'rgba(0, 0, 0, 0.04)',
          itemColor: isDark ? 'rgba(255, 255, 255, 0.75)' : 'rgba(0, 0, 0, 0.88)'
        },
        Card: { colorBgContainer: isDark ? '#161a22' : '#ffffff' }
      }
    }
  })

  function setMode(m: ThemeMode) { mode.value = m }
  function toggle() { mode.value = mode.value === 'dark' ? 'light' : 'dark' }
  watch(mode, (m) => applyDom(m))

  return { mode, antTheme, setMode, toggle }
})
