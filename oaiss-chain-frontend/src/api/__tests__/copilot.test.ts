import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * AI Copilot 三端点的调用链路契约测试。
 *
 * 回归背景：历史上这三处使用裸 fetch('/copilot/*')，绕过 request 实例 →
 * 不携带 JWT、且浏览器直连 ML 服务导致 X-ML-Service-Secret 缺失（401）。
 * 本测试锁定：必须走后端 request 实例的 /copilot/* 端点。
 */
const requestMocks = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    post: requestMocks.postMock,
    get: requestMocks.getMock,
  },
}))

import { askPolicyQa } from '../carbonFormula'
import { getAiReportAnalysis } from '../carbon'
import { getProcurementAdvice } from '../auction'

const fetchSpy = vi.fn()

describe('AI Copilot 调用链路必须经后端代理', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.postMock.mockResolvedValue({})
    vi.stubGlobal('fetch', fetchSpy)
  })

  it('askPolicyQa 走 request.post -> /copilot/policy-qa', async () => {
    await askPolicyQa('GB/T 32150 是什么', 'power-generation')
    expect(requestMocks.postMock).toHaveBeenCalledWith('/copilot/policy-qa', {
      question: 'GB/T 32150 是什么',
      context_topic: 'power-generation',
    })
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('getAiReportAnalysis 走 request.post -> /copilot/report-analysis', async () => {
    const payload = { total_emission: 1000, enterprise_name: '测试企业' }
    await getAiReportAnalysis(payload)
    expect(requestMocks.postMock).toHaveBeenCalledWith('/copilot/report-analysis', payload)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('getProcurementAdvice 走 request.post -> /copilot/procurement-advice', async () => {
    const payload = { current_emission: 4000, current_quota: 3500 }
    await getProcurementAdvice(payload)
    expect(requestMocks.postMock).toHaveBeenCalledWith('/copilot/procurement-advice', payload)
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
