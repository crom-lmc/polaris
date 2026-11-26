const ACCESS_KEY = 'portal_access_token'
const REFRESH_KEY = 'portal_refresh_token'

/** 从 accessToken 的 JWT payload 读取中文昵称（nick），仅用于顶栏展示，解析失败由调用方回退登录名 */
export function getNickname(): string {
  const token = getAccessToken()
  try {
    const part = token.split('.')[1]
    if (!part) return ''
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - base64.length % 4) % 4)
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0))
    return (JSON.parse(new TextDecoder().decode(bytes)).nick as string) || ''
  } catch {
    return ''
  }
}

export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_KEY) || ''
}

export function setTokens(access?: string, refresh?: string) {
  if (access) localStorage.setItem(ACCESS_KEY, access)
  if (refresh) localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}
