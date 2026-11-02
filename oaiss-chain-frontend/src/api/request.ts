import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { getAccessToken, getRefreshToken, clearTokens, isTokenExpired } from '../utils/auth'
import router from '../router'
import { ElMessage } from 'element-plus'
import type { ApiResponse, SpringPage } from '../types'
import { pinia, useAppStore } from '../store'

const RETRY_MAX_ATTEMPTS = 2
const RETRY_BASE_DELAY_MS = 1000
const REQUEST_TIMEOUT_MS = 15000
const SUCCESS_CODES = [200, 0]

// =========================================================================
// [DEFENSE-TRACE-HTTP-INTERCEPTOR]: 全局网络请求统一拦截器与异常容错中枢
// 职责：Token 自动静默续期、401/403/500 异常统一拦截、稳定兜底提示与重试机制
// =========================================================================
const TOKEN_REFRESH_FAILED_MESSAGE = 'Token refresh failed'
const INVALID_REFRESH_SESSION_MESSAGE = 'Refreshed session is invalid'
const LOGIN_EXPIRED_MESSAGE = 'Login session expired, please sign in again'
const REQUEST_FAILED_MESSAGE = 'Request failed'
const SERVER_ERROR_MESSAGE = 'Server error'
const FORBIDDEN_MESSAGE = 'You do not have permission to perform this action'

/**
 * 证书门禁错误码：HTTP 403 但语义是「业务原因」而非「无权限」。
 *
 * 后端 CertificateGateService 对「无有效准入证书 / 无有效审核资格证」
 * 返回 403（而非 400，因为这是「没资格做这件事」而不是「参数写错」）。
 * 但它同时返回了带原因的中文说明——前端若按通用 403 处理，
 * 用户只会看到笼统的「无权限」，完全不知道是证书被吊销。
 * 因此这两个码必须透传后端文案。
 */
const CERT_GATE_ERROR_CODES = new Set([7001, 7002])
const NOT_FOUND_MESSAGE = 'Requested resource was not found'
const NETWORK_ERROR_MESSAGE = 'Network error, please check your connection'

// 后端参数校验失败时把字段级明细（ApiResponse.data 数组）拼进提示，
// 避免用户只看到泛化的「参数校验失败」却不知道错在哪个字段。
function composeErrorMessage(message: string | undefined | null, detail: unknown): string {
  let detailText = ''
  if (Array.isArray(detail)) {
    detailText = detail.map(String).join('；')
  } else if (typeof detail === 'string' && detail.trim()) {
    detailText = detail.trim()
  }
  return [message, detailText].filter(Boolean).join('：')
}

declare module 'axios' {
  interface AxiosRequestConfig {
    suppressErrorMessage?: boolean
  }

  interface InternalAxiosRequestConfig {
    __retryCount?: number
    suppressErrorMessage?: boolean
  }
}

interface PendingRequest {
  resolve: (token: string) => void
  reject: (reason?: unknown) => void
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
})

let isRefreshing = false
let pendingRequests: PendingRequest[] = []
const appStore = useAppStore(pinia)

function onTokenRefreshed(newToken: string): void {
  pendingRequests.forEach(({ resolve }) => resolve(newToken))
  pendingRequests = []
}

function onTokenRefreshFailed(): void {
  pendingRequests.forEach(({ reject }) => reject(new Error(TOKEN_REFRESH_FAILED_MESSAGE)))
  pendingRequests = []
}

request.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  // Normalize frontend paging params to backend Spring Data style.
  if (config.params) {
    const { pageNum, pageSize, ...rest } = config.params as Record<string, unknown>
    if (pageNum !== undefined || pageSize !== undefined) {
      config.params = {
        ...rest,
        ...(pageNum !== undefined && { page: pageNum }),
        ...(pageSize !== undefined && { size: pageSize }),
      }
    }
  }

  const token = getAccessToken()
  if (token) {
    if (isTokenExpired(token)) {
      const refreshToken = getRefreshToken()
      if (refreshToken && !isRefreshing) {
        isRefreshing = true
        try {
          const { data } = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
            `${import.meta.env.VITE_API_BASE_URL}/auth/refresh`,
            {},
            { headers: { 'Refresh-Token': refreshToken } },
          )
          const refreshed = appStore.login({
            accessToken: data.data!.accessToken,
            refreshToken: data.data!.refreshToken,
          })
          if (!refreshed) {
            throw new Error(INVALID_REFRESH_SESSION_MESSAGE)
          }
          onTokenRefreshed(data.data!.accessToken)
          config.headers.Authorization = `Bearer ${data.data!.accessToken}`
        } catch {
          onTokenRefreshFailed()
          clearTokens()
          appStore.logout()
          router.push('/login')
          return Promise.reject(new Error(LOGIN_EXPIRED_MESSAGE))
        } finally {
          isRefreshing = false
        }
      } else if (isRefreshing) {
        return new Promise<InternalAxiosRequestConfig>((resolve, reject) => {
          pendingRequests.push({
            resolve: (newToken: string) => {
              config.headers.Authorization = `Bearer ${newToken}`
              resolve(config)
            },
            reject,
          })
        })
      }
    } else {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

interface TransformedPage<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

interface BusinessApiError<T = unknown> extends Error {
  businessCode: number
  payload: ApiResponse<T>
  response: AxiosResponse<ApiResponse<T>>
}

function createBusinessApiError<T>(response: AxiosResponse<ApiResponse<T>>): BusinessApiError<T> {
  const payload = response.data
  const error = new Error(payload.message || REQUEST_FAILED_MESSAGE) as BusinessApiError<T>
  error.name = 'BusinessApiError'
  error.businessCode = payload.code
  error.payload = payload
  error.response = response
  return error
}

let lastShownErrorMessage = ''
let lastShownErrorTime = 0
const ERROR_DEDUP_INTERVAL_MS = 2000

function showDeduplicatedError(msg: string | null | undefined): void {
  const content = msg || SERVER_ERROR_MESSAGE
  const now = Date.now()
  if (content === lastShownErrorMessage && now - lastShownErrorTime < ERROR_DEDUP_INTERVAL_MS) {
    return // 2秒内相同错误不重复弹窗，消除并发请求失败带来的刷屏噪音
  }
  lastShownErrorMessage = content
  lastShownErrorTime = now
  ElMessage.error(content)
}

// 拦截器按运行时契约把 axios 响应剥壳为业务 data（调用侧 request.get<T> 即 Promise<T>）。
// axios 类型要求 fulfilled 返回 AxiosResponse，此处显式 any 保留既有调用体验。
request.interceptors.response.use(
  (response: AxiosResponse): any => {
    const { code, message, data } = response.data as ApiResponse<unknown>
    if (SUCCESS_CODES.includes(code)) {
      // Transform Spring Data Page responses into the frontend paging shape.
      if (
        data &&
        typeof data === 'object' &&
        'content' in data &&
        Array.isArray((data as SpringPage<unknown>).content) &&
        'totalElements' in data
      ) {
        const page = data as SpringPage<unknown>
        return {
          items: page.content,
          total: page.totalElements,
          page: page.number ?? 0,
          size: page.size ?? 10,
          totalPages: page.totalPages ?? 0,
        } as TransformedPage<unknown>
      }
      return data
    }
    const config = response.config as AxiosRequestConfig
    if (!config?.suppressErrorMessage) {
      showDeduplicatedError(composeErrorMessage(message, data) || REQUEST_FAILED_MESSAGE)
    }
    return Promise.reject(createBusinessApiError(response as AxiosResponse<ApiResponse<unknown>>))
  },
  async (error) => {
    const config = error.config as InternalAxiosRequestConfig | undefined
    if (!config || (config.__retryCount ?? 0) >= RETRY_MAX_ATTEMPTS) {
      // Fall through to standard error handling.
    } else if (!error.response && (error.code === 'ECONNABORTED' || error.message?.includes('Network Error'))) {
      config.__retryCount = (config.__retryCount || 0) + 1
      await new Promise((resolve) => setTimeout(resolve, RETRY_BASE_DELAY_MS * config.__retryCount!))
      return request(config)
    }

    if (error.response) {
      const { status, data } = error.response as AxiosResponse<ApiResponse<null>>
      let msg = composeErrorMessage(data?.message, data?.data) || SERVER_ERROR_MESSAGE
      if (status === 502 || status === 503 || status === 504) {
        msg = '服务响应超时或正在启动中，请稍后刷新重试'
      }

      if (status === 401) {
        const isLoginRequest = error.config?.url?.includes('/auth/login')
        if (!isLoginRequest) {
          onTokenRefreshFailed()
          clearTokens()
          appStore.logout()
          router.push('/login')
          showDeduplicatedError(LOGIN_EXPIRED_MESSAGE)
        } else {
          showDeduplicatedError(msg)
        }
      } else if (status === 403) {
        // 证书门禁：透传后端给出的具体原因（含吊销/过期说明），
        // 其余 403 仍用通用「无权限」文案，避免泄露内部细节。
        const code = Number(data?.code)
        if (CERT_GATE_ERROR_CODES.has(code) && msg) {
          showDeduplicatedError(msg)
        } else {
          showDeduplicatedError(FORBIDDEN_MESSAGE)
        }
      } else if (status === 404) {
        if (!config?.suppressErrorMessage) {
          showDeduplicatedError(NOT_FOUND_MESSAGE)
        }
      } else if (!config?.suppressErrorMessage) {
        showDeduplicatedError(msg)
      }
    } else if (!config?.suppressErrorMessage) {
      showDeduplicatedError(NETWORK_ERROR_MESSAGE)
    }
    return Promise.reject(error)
  },
)

export default request
