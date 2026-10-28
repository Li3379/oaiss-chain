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

import {
  addBonus,
  checkTradePermission,
  deductPoints,
  evaluateLevel,
  getEnterpriseScore,
  getEnterpriseCreditHistory,
  getFrozenEnterprises,
  getRestrictedEnterprises,
  getScoreHistory,
  getScoreRanking,
} from '../credit'

describe('credit api client', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.get.mockResolvedValue({})
    requestMock.post.mockResolvedValue({})
  })

  it('requests paged score history with forwarded params', async () => {
    await getScoreHistory({ pageNum: 2, pageSize: 20, eventType: 3 })

    expect(requestMock.get).toHaveBeenCalledWith('/credit/history', {
      params: { pageNum: 2, pageSize: 20, eventType: 3 },
    })
  })

  it('requests enterprise credit history with forwarded params', async () => {
    await getEnterpriseCreditHistory(8, { pageNum: 1, pageSize: 10, eventType: 2 })

    expect(requestMock.get).toHaveBeenCalledWith('/credit/8/history', {
      params: { pageNum: 1, pageSize: 10, eventType: 2 },
    })
  })

  it('sends add-bonus requests with backend-compatible query params', async () => {
    await addBonus({ enterpriseId: 6, points: 15, reason: 'manual reward' })

    expect(requestMock.post).toHaveBeenCalledWith('/credit/bonus', null, {
      params: {
        enterpriseId: 6,
        points: 15,
        description: 'manual reward',
      },
    })
  })

  it('accepts description as the canonical add-bonus field', async () => {
    await addBonus({ enterpriseId: 9, points: 5, description: 'admin adjustment' })

    expect(requestMock.post).toHaveBeenCalledWith('/credit/bonus', null, {
      params: {
        enterpriseId: 9,
        points: 5,
        description: 'admin adjustment',
      },
    })
  })

  it('uses the backend payload shape for deduct-points', async () => {
    await deductPoints({ enterpriseId: 12, eventType: 4, description: 'violation' })

    expect(requestMock.post).toHaveBeenCalledWith('/credit/deduct', {
      enterpriseId: 12,
      eventType: 4,
      description: 'violation',
    })
  })

  it('does not attach unused paging params to restricted or frozen endpoints', async () => {
    await getRestrictedEnterprises()
    await getFrozenEnterprises()

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/credit/restricted')
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/credit/frozen')
  })

  it('loads score ranking through the paged ranking endpoint', async () => {
    await getScoreRanking({ pageNum: 1, pageSize: 10 })

    expect(requestMock.get).toHaveBeenCalledWith('/credit/ranking', {
      params: { pageNum: 1, pageSize: 10 },
    })
  })

  it('requests raw boolean trade permission checks from the backend endpoint', async () => {
    requestMock.get.mockResolvedValueOnce(true)

    const result = await checkTradePermission(21)

    expect(requestMock.get).toHaveBeenCalledWith('/credit/check-permission/21')
    expect(result).toBe(true)
  })

  it('uses stable validation messages for missing enterprise and credit inputs', async () => {
    await expect(getEnterpriseScore(0)).rejects.toThrow('Enterprise ID is required')
    await expect(getEnterpriseCreditHistory(0)).rejects.toThrow('Enterprise ID is required')
    await expect(deductPoints({ enterpriseId: 0, eventType: 1, description: 'x' })).rejects.toThrow('Enterprise ID is required')
    await expect(deductPoints({ enterpriseId: 1, eventType: 0, description: 'x' })).rejects.toThrow('Credit event type is required')
    await expect(addBonus({ enterpriseId: 0, points: 1, description: 'x' })).rejects.toThrow('Enterprise ID is required')
    await expect(addBonus({ enterpriseId: 1, points: 0, description: 'x' })).rejects.toThrow('Bonus points must be greater than 0')
    await expect(addBonus({ enterpriseId: 1, points: 1 })).rejects.toThrow('Bonus description is required')
    await expect(evaluateLevel(0)).rejects.toThrow('Enterprise ID is required')
    await expect(checkTradePermission(0)).rejects.toThrow('Enterprise ID is required')
  })
})
