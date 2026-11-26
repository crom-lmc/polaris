/**
 * 令牌存取。
 * accessToken 放 localStorage 是为了跨标签页保持登录且刷新页面不丢；
 * 长期凭据是 HttpOnly 的中心会话 cookie，这里只存短时效的 access token。
 */
const ACCESS_KEY = 'contract_access_token'
const REFRESH_KEY = 'contract_refresh_token'

/** 从 accessToken 的 JWT payload 读取中文昵称（nick），仅用于顶栏展示，不参与鉴权。 */
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

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_KEY) || ''
}

export function setTokens(accessToken?: string, refreshToken?: string) {
  if (accessToken) localStorage.setItem(ACCESS_KEY, accessToken)
  if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}
