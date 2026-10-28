import { beforeEach, describe, expect, it, vi } from 'vitest'

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

import {
  executeMatching,
  getAuctionOrders,
  getMatchResults,
  getMyOrders,
  submitBuyOrder,
  submitSellOrder,
} from '../auction'

describe('auction api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.postMock.mockResolvedValue(undefined)
    requestMocks.getMock.mockResolvedValue({})
  })

  it('exports all 6 functions', () => {
    expect(typeof submitBuyOrder).toBe('function')
    expect(typeof submitSellOrder).toBe('function')
    expect(typeof getAuctionOrders).toBe('function')
    expect(typeof getMyOrders).toBe('function')
    expect(typeof getMatchResults).toBe('function')
    expect(typeof executeMatching).toBe('function')
  })

  it('forwards buy/sell orders to correct endpoints', async () => {
    const orderData = { price: 50, quantity: 100, direction: 1 } as import('../../types').AuctionOrderRequest
    await submitBuyOrder(orderData)
    await submitSellOrder(orderData)

    expect(requestMocks.postMock).toHaveBeenNthCalledWith(1, '/auction/buy', orderData)
    expect(requestMocks.postMock).toHaveBeenNthCalledWith(2, '/auction/sell', orderData)
  })

  it('forwards GET requests with pagination params', async () => {
    await getAuctionOrders({ pageNum: 1, pageSize: 20 })
    await getMyOrders({ pageNum: 2, pageSize: 10 })
    await getMatchResults()

    expect(requestMocks.getMock).toHaveBeenNthCalledWith(1, '/auction/orders', {
      params: { pageNum: 1, pageSize: 20 },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(2, '/auction/my-orders', {
      params: { pageNum: 2, pageSize: 10 },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(3, '/auction/results', { params: undefined })
  })

  it('calls executeMatching via POST', async () => {
    await executeMatching()
    expect(requestMocks.postMock).toHaveBeenCalledWith('/auction/match')
  })
})