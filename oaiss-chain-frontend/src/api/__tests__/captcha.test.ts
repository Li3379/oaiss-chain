import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    get: requestMock.get,
    post: requestMock.post,
  },
}))

import { generateCaptcha, verifyCaptcha, sendSmsCode, sendEmailCode } from '../captcha'

describe('captcha api client', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.get.mockResolvedValue({})
    requestMock.post.mockResolvedValue({})
  })

  it('loads captcha images from the generate endpoint', async () => {
    await generateCaptcha()

    expect(requestMock.get).toHaveBeenCalledWith('/captcha/generate')
  })

  it('verifies captcha with the raw backend boolean shape', async () => {
    requestMock.post.mockResolvedValueOnce(true)

    const result = await verifyCaptcha({
      captchaKey: 'captcha-key',
      captchaCode: 'ABCD',
    })

    expect(requestMock.post).toHaveBeenCalledWith('/captcha/verify', {
      captchaKey: 'captcha-key',
      captchaCode: 'ABCD',
    })
    expect(result).toBe(true)
  })

  it('maps SMS requests to backend target/type payloads', async () => {
    await sendSmsCode({ phone: '13800138000' })

    expect(requestMock.post).toHaveBeenCalledWith('/captcha/sms/send', {
      target: '13800138000',
      type: 1,
    })
  })

  it('maps email requests to backend target/type payloads', async () => {
    await sendEmailCode({ email: 'qa@example.com', type: 2 })

    expect(requestMock.post).toHaveBeenCalledWith('/captcha/email/send', {
      target: 'qa@example.com',
      type: 2,
    })
  })
})
