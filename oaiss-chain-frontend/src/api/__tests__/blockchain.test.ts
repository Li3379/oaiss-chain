import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    get: requestMock.get,
  },
}))

import { getLatestBlocks, getStatus, getTransactions, queryBlock, queryTransaction } from '../blockchain'

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
    await queryTransaction('tx-abc')

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/blockchain/status')
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/blockchain/transactions', {
      params: { pageNum: 2, pageSize: 20 },
    })
    expect(requestMock.get).toHaveBeenNthCalledWith(3, '/blockchain/blocks/latest', {
      params: { pageNum: 1, pageSize: 5 },
    })
    expect(requestMock.get).toHaveBeenNthCalledWith(4, '/blockchain/block/10')
    expect(requestMock.get).toHaveBeenNthCalledWith(5, '/blockchain/transaction/tx-abc')
  })

  it('uses a stable validation message for empty transaction hashes', async () => {
    await expect(queryTransaction('')).rejects.toThrow('Transaction hash is required')
  })
})
