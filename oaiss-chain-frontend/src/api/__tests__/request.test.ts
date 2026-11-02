import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'

vi.mock('../../utils/auth', () => ({
  getAccessToken: vi.fn(() => null),
  getRefreshToken: vi.fn(() => null),
  clearTokens: vi.fn(),
  isTokenExpired: vi.fn(() => false),
}))

vi.mock('../../router', () => ({
  default: {
    push: vi.fn(),
    replace: vi.fn(),
  },
}))

vi.mock('../../store', () => ({
  pinia: {},
  useAppStore: vi.fn(() => ({
    login: vi.fn(() => true),
    logout: vi.fn(),
  })),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
  },
}))

import request from '../request'
import { ElMessage } from 'element-plus'

function getResponseFulfilledHandler() {
  return (request.interceptors.response as unknown as {
    handlers: Array<{ fulfilled: (response: AxiosResponse) => unknown }>
  }).handlers[0].fulfilled
}

function getResponseRejectedHandler() {
  return (request.interceptors.response as unknown as {
    handlers: Array<{ rejected: (error: unknown) => unknown }>
  }).handlers[0].rejected
}

describe('request response interceptor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('suppresses business error toasts when requested and keeps the business code on the rejection', async () => {
    const handler = getResponseFulfilledHandler()
    const response = {
      data: {
        code: 5015,
        message: 'No active key pair',
        data: null,
      },
      config: {
        suppressErrorMessage: true,
      },
    } as AxiosResponse

    await expect(handler(response)).rejects.toMatchObject({
      businessCode: 5015,
      response: {
        data: {
          code: 5015,
        },
      },
    })
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('still shows business error toasts by default', async () => {
    const handler = getResponseFulfilledHandler()
    const response = {
      data: {
        code: 4001,
        message: 'Request failed',
        data: null,
      },
      config: {},
    } as AxiosResponse

    await expect(handler(response)).rejects.toMatchObject({
      businessCode: 4001,
    })
    expect(ElMessage.error).toHaveBeenCalledWith('Request failed')
  })

  it('uses stable fallback messages for common transport failures', async () => {
    const rejected = getResponseRejectedHandler()

    await expect(rejected({
      response: {
        status: 404,
        data: { code: 404, message: '' },
      },
      config: {},
    })).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith('Requested resource was not found')

    vi.clearAllMocks()

    await expect(rejected({
      response: {
        status: 403,
        data: { code: 403, message: '' },
      },
      config: {},
    })).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith('You do not have permission to perform this action')

    // 证书门禁（7001/7002）虽然也返回 HTTP 403，但它是**业务原因**而非「无权限」。
    // 后端已返回可读的中文说明（含吊销原因），前端必须透传，
    // 否则用户只看到笼统的「无权限」，不知道是证书被吊销。
    vi.clearAllMocks()
    await expect(rejected({
      response: {
        status: 403,
        data: { code: 7001, message: '该企业没有有效的准入证书（未签发、已吊销或已过期），暂不可提交碳报告或参与碳交易，请联系平台管理员' },
      },
      config: {},
    })).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith(
      '该企业没有有效的准入证书（未签发、已吊销或已过期），暂不可提交碳报告或参与碳交易，请联系平台管理员',
    )

    vi.clearAllMocks()
    await expect(rejected({
      response: {
        status: 403,
        data: { code: 7002, message: '该审核员没有有效的审核资格证（未签发、已吊销或已过期），暂不可执行审核，请联系平台管理员' },
      },
      config: {},
    })).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith(
      '该审核员没有有效的审核资格证（未签发、已吊销或已过期），暂不可执行审核，请联系平台管理员',
    )

    vi.clearAllMocks()

    await expect(rejected({
      response: {
        status: 500,
        data: { code: 500, message: '' },
      },
      config: {},
    })).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith('Server error')

    vi.clearAllMocks()

    await expect(rejected({
      message: 'Network Error',
      code: 'ERR_NETWORK',
      config: { __retryCount: 2 },
    })).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith('Network error, please check your connection')
  })
})
