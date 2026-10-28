import { describe, expect, it, beforeEach, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
  deleteMock: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    post: requestMocks.postMock,
    get: requestMocks.getMock,
    delete: requestMocks.deleteMock,
  },
}))

import { ReportStatusEnum } from '../../types'
import { certifyReport, createReport, deleteReport, getReport, reviewReport, submitReport } from '../carbon'

describe('carbon api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.postMock.mockResolvedValue(undefined)
  })

  it('maps reviewer approval and rejection to shared report status codes', async () => {
    await reviewReport({ reportId: 101, approved: true, comment: 'ok' })
    await reviewReport({ reportId: 102, approved: false, comment: 'no' })

    expect(requestMocks.postMock).toHaveBeenNthCalledWith(1, '/carbon/review', {
      reportId: 101,
      reviewResult: ReportStatusEnum.APPROVED,
      reviewComment: 'ok',
    })
    expect(requestMocks.postMock).toHaveBeenNthCalledWith(2, '/carbon/review', {
      reportId: 102,
      reviewResult: ReportStatusEnum.REJECTED,
      reviewComment: 'no',
    })
  })

  it('maps certification approval and rejection to shared report status codes', async () => {
    await certifyReport({ reportId: 201, approved: true, comment: 'chain it' })
    await certifyReport({ reportId: 202, approved: false, comment: 'reject it' })

    expect(requestMocks.postMock).toHaveBeenNthCalledWith(1, '/carbon/certify', {
      reportId: 201,
      reviewResult: ReportStatusEnum.ON_CHAIN,
      reviewComment: 'chain it',
    })
    expect(requestMocks.postMock).toHaveBeenNthCalledWith(2, '/carbon/certify', {
      reportId: 202,
      reviewResult: ReportStatusEnum.REJECTED,
      reviewComment: 'reject it',
    })
  })

  it('uses stable validation messages for missing report identifiers and title', async () => {
    await expect(createReport({ title: '', accountingPeriod: '2026-Q1', reportType: 1, emissionData: '{}' }))
      .rejects.toThrow('Report title is required')
    await expect(submitReport(0)).rejects.toThrow('Report ID is required')
    await expect(deleteReport(0)).rejects.toThrow('Report ID is required')
    await expect(getReport(0)).rejects.toThrow('Report ID is required')
    await expect(reviewReport({ reportId: 0, approved: true, comment: '' })).rejects.toThrow('Report ID is required')
    await expect(certifyReport({ reportId: 0, approved: true, comment: '' })).rejects.toThrow('Report ID is required')
  })
})
