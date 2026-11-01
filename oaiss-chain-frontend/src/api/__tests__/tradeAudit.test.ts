import { describe, it, expect, vi, beforeEach } from 'vitest'
import request from '../request'
import { listPendingAudits, approveTrade, rejectTrade } from '../tradeAudit'

vi.mock('../request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

const mockedRequest = vi.mocked(request, true)

describe('Trade Audit API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listPendingAudits 默认查询待审核工单', async () => {
    mockedRequest.get.mockResolvedValue({ list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })

    await listPendingAudits({ pageNum: 1, pageSize: 20 })

    expect(mockedRequest.get).toHaveBeenCalledWith('/trade-audit', {
      params: { page: 1, size: 20 },
    })
  })

  it('listPendingAudits 支持指定审核态筛选', async () => {
    mockedRequest.get.mockResolvedValue({ list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })

    await listPendingAudits({ pageNum: 2, pageSize: 10, status: 3 })

    expect(mockedRequest.get).toHaveBeenCalledWith('/trade-audit', {
      params: { page: 2, size: 10, status: 3 },
    })
  })

  it('approveTrade 提交审核意见', async () => {
    mockedRequest.post.mockResolvedValue(undefined)

    await approveTrade(100, '核对无误')

    expect(mockedRequest.post).toHaveBeenCalledWith('/trade-audit/100/approve', {
      remark: '核对无误',
    })
  })

  it('rejectTrade 提交审核意见与驳回理由', async () => {
    mockedRequest.post.mockResolvedValue(undefined)

    await rejectTrade(100, '成交价异常', 'PRICE_DEVIATION')

    expect(mockedRequest.post).toHaveBeenCalledWith('/trade-audit/100/reject', {
      remark: '成交价异常',
      reason: 'PRICE_DEVIATION',
    })
  })

  it('approveTrade 拒绝空交易ID（防误调用打到 /trade-audit//approve）', async () => {
    await expect(approveTrade(0, 'ok')).rejects.toThrow()
    expect(mockedRequest.post).not.toHaveBeenCalled()
  })

  it('rejectTrade 拒绝空审核意见（后端也要求必填，前端前置拦截给出更快的反馈）', async () => {
    await expect(rejectTrade(100, '   ', 'OTHER')).rejects.toThrow()
    expect(mockedRequest.post).not.toHaveBeenCalled()
  })
})
