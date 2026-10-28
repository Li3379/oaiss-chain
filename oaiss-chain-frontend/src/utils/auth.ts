const TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'
const TOKEN_EXPIRY_KEY = 'token_expiry'
const REMEMBER_ME_KEY = 'remember_me'

let memoryAccessToken: string | null = null

/**
 * Token数据结构
 */
interface TokenData {
  accessToken: string
  expiresAt: number
  rememberMe: boolean
}

/**
 * 设置Token（支持多种调用方式）
 *
 * 方式1（向后兼容）: setTokens(accessToken, refreshToken)
 * 方式2（完整参数）: setTokens(accessToken, expiresIn, rememberMe, refreshToken)
 *
 * @param access 访问令牌
 * @param arg2 第二个参数（refreshToken 或 expiresIn）
 * @param arg3 第三个参数（rememberMe，可选）
 * @param arg4 第四个参数（refreshToken，可选）
 */
export function setTokens(
  access: string,
  arg2?: string | number | null,
  arg3?: boolean,
  arg4?: string
): void {
  memoryAccessToken = access

  let expiresIn: number | undefined
  let rememberMe: boolean = true
  let refresh: string | undefined

  if (typeof arg2 === 'string' || arg2 === null || arg2 === undefined) {
    refresh = arg2 ?? undefined
  } else if (typeof arg2 === 'number') {
    expiresIn = arg2
    rememberMe = arg3 ?? true
    refresh = arg4
  }

  const storage = rememberMe ? localStorage : sessionStorage

  storage.setItem(TOKEN_KEY, access)

  if (expiresIn) {
    const expiresAt = Date.now() + expiresIn * 1000
    storage.setItem(TOKEN_EXPIRY_KEY, String(expiresAt))
  } else {
    const payload = parseJwtPayload(access)
    if (payload?.exp) {
      storage.setItem(TOKEN_EXPIRY_KEY, String(payload.exp * 1000))
    }
  }

  storage.setItem(REMEMBER_ME_KEY, String(rememberMe))

  if (refresh) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  }
}

/**
 * 获取Access Token（智能检测两个存储位置）
 * @returns 有效Token或null
 */
export function getAccessToken(): string | null {
  // 1. 优先使用内存缓存
  if (memoryAccessToken && !isTokenExpired(memoryAccessToken)) {
    return memoryAccessToken
  }

  // 2. 防御性检测：检查两个存储位置
  const localToken = localStorage.getItem(TOKEN_KEY)
  const sessionToken = sessionStorage.getItem(TOKEN_KEY)

  // 3. 优先使用未过期的 token
  if (localToken && !isTokenExpired(localToken)) {
    memoryAccessToken = localToken
    return memoryAccessToken
  }
  if (sessionToken && !isTokenExpired(sessionToken)) {
    memoryAccessToken = sessionToken
    return memoryAccessToken
  }

  // 4. Token 过期或不存在，清理
  clearTokens()
  return null
}

/**
 * 获取Refresh Token
 * @returns Refresh Token或null
 */
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

/**
 * 获取记住我设置
 * @returns 是否记住登录
 */
export function getRememberMe(): boolean {
  const localRemember = localStorage.getItem(REMEMBER_ME_KEY)
  if (localRemember !== null) {
    return localRemember === 'true'
  }

  const sessionRemember = sessionStorage.getItem(REMEMBER_ME_KEY)
  if (sessionRemember !== null) {
    return sessionRemember === 'true'
  }

  // 默认true
  return true
}

/**
 * 获取Token剩余有效时间（秒）
 * @returns 剩余秒数，已过期或无Token返回0
 */
export function getTokenRemainingTime(): number {
  const rememberMe = getRememberMe()
  const storage = rememberMe ? localStorage : sessionStorage

  const expiryStr = storage.getItem(TOKEN_EXPIRY_KEY)
  if (!expiryStr) return 0

  const expiresAt = parseInt(expiryStr, 10)
  const remaining = Math.floor((expiresAt - Date.now()) / 1000)
  return Math.max(0, remaining)
}

/**
 * 检查Token是否即将过期（5分钟内）
 * @returns 是否即将过期
 */
export function isTokenExpiringSoon(): boolean {
  const remaining = getTokenRemainingTime()
  return remaining < 5 * 60 // 5分钟内过期
}

/**
 * 清理所有Token
 */
export function clearTokens(): void {
  memoryAccessToken = null
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_EXPIRY_KEY)
  sessionStorage.removeItem(REMEMBER_ME_KEY)
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_EXPIRY_KEY)
  localStorage.removeItem(REMEMBER_ME_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/**
 * JWT Payload结构
 */
export interface JwtPayload {
  exp?: number
  sub?: string
  userId?: number
  userType?: number
  roles?: string[]
  enterpriseId?: number
  [key: string]: unknown
}

function decodeBase64UrlSegment(segment: string): string {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/**
 * 解析JWT Payload
 * @param token JWT令牌
 * @returns Payload对象或null
 */
export function parseJwtPayload(token: string | null): JwtPayload | null {
  if (!token) return null
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const header = JSON.parse(decodeBase64UrlSegment(parts[0]))
    if (header.alg === 'none') return null
    return JSON.parse(decodeBase64UrlSegment(parts[1]))
  } catch {
    return null
  }
}

/**
 * 检查Token是否过期
 * @param token JWT令牌
 * @returns 是否已过期
 */
export function isTokenExpired(token: string | null): boolean {
  // 解析JWT检查exp字段
  const payload = parseJwtPayload(token)
  if (!payload || !payload.exp) return true
  return payload.exp * 1000 < Date.now()
}
