/**
 * 令牌存取。
 * <p>
 * accessToken 放 localStorage 是权衡后的选择：SSO 管理台是内部工具，需要跨标签页保持登录、
 * 且要能刷页面不丢；真正的长期凭据是 HttpOnly 的中心会话 cookie（跨应用单点用），
 * 这里只存 2 小时就过期的 access token，泄露窗口有限。
 */
const ACCESS_KEY = 'settings_access_token'
const REFRESH_KEY = 'settings_refresh_token'

/**
 * 从 accessToken 的 JWT payload 读取中文昵称（nick）。
 * 仅用于顶栏展示，不参与鉴权；解析失败返回空串，由调用方回退登录名。
 */
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
  if (accessToken) {
    localStorage.setItem(ACCESS_KEY, accessToken)
  }
  if (refreshToken) {
    localStorage.setItem(REFRESH_KEY, refreshToken)
  }
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}
