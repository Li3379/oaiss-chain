import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    get: requestMock.get,
  },
}))

import * as blockchainApi from '../blockchain'
import { getLatestBlocks, getStatus, getTransactions, queryBlock } from '../blockchain'

describe('blockchain api client', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.get.mockResolvedValue({})
  })

  it('forwards typed blockchain queries to the backend endpoints', async () => {
    await getStatus()
    await getTransactions({ pageNum: 2, pageSize: 20 })
    await getLatestBlocks({ pageNum: 1, pageSize: 5 })
    await queryBlock(10)

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/blockchain/status')
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/blockchain/transactions', {
      params: { pageNum: 2, pageSize: 20 },
    })
    expect(requestMock.get).toHaveBeenNthCalledWith(3, '/blockchain/blocks/latest', {
      params: { pageNum: 1, pageSize: 5 },
    })
    expect(requestMock.get).toHaveBeenNthCalledWith(4, '/blockchain/block/10')
  })

  // 交易哈希面板已移除（2026-10-31）：链上交易一律从交易列表下钻查看，
  // 不再保留「输入哈希直接查询」这条独立入口，避免出现第二条查询口径。
  it('no longer exposes the removed transaction-hash lookup', () => {
    expect('queryTransaction' in blockchainApi).toBe(false)
  })
})
