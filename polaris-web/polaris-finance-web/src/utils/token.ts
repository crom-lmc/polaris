/**
 * 令牌存取。键名使用 finance_*，避免与其它应用撞名。
 * accessToken 只存 2 小时短令牌，长期凭据仍是 HttpOnly 的中心会话 cookie。
 */
const ACCESS_KEY = 'finance_access_token'
const REFRESH_KEY = 'finance_refresh_token'

/** 从 accessToken 的 JWT payload 读取中文昵称（nick），仅用于顶栏展示，不参与鉴权 */
export function getNickname(): string {
  const token = getAccessToken()
  try {
    const part = token.split('.')[1]
    if (!part) return ''
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
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
