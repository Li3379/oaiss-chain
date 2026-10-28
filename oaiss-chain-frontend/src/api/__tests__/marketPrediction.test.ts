import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  postMock: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    post: requestMocks.postMock,
  },
}))

import { getMarketPrice, getMarketTrend, getSupplyDemand } from '../marketPrediction'

describe('marketPrediction api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.postMock.mockResolvedValue({})
  })

  it('exports all 3 functions', () => {
    expect(typeof getMarketTrend).toBe('function')
    expect(typeof getMarketPrice).toBe('function')
    expect(typeof getSupplyDemand).toBe('function')
  })

  it('forwards trend request with default horizon', async () => {
    await getMarketTrend()
    expect(requestMocks.postMock).toHaveBeenCalledWith('/ai/market/trend', null, {
      params: { horizonDays: 30 },
      suppressErrorMessage: true,
    })
  })

  it('forwards price request with custom horizon', async () => {
    await getMarketPrice(60)
    expect(requestMocks.postMock).toHaveBeenCalledWith('/ai/market/price', null, {
      params: { horizonDays: 60 },
      suppressErrorMessage: true,
    })
  })

  it('forwards supply-demand request with default horizon', async () => {
    await getSupplyDemand()
    expect(requestMocks.postMock).toHaveBeenCalledWith('/ai/market/supply-demand', null, {
      params: { horizonDays: 30 },
      suppressErrorMessage: true,
    })
  })
})