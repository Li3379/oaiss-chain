import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  getMock: vi.fn(),
  putMock: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    get: requestMocks.getMock,
    put: requestMocks.putMock,
  },
}))

import { getCarbonReports, getOrgInfo, getStatistics, updateContact } from '../thirdParty'

describe('thirdParty api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.getMock.mockResolvedValue({})
    requestMocks.putMock.mockResolvedValue(undefined)
  })

  it('exports all 4 functions', () => {
    expect(typeof getCarbonReports).toBe('function')
    expect(typeof getStatistics).toBe('function')
    expect(typeof getOrgInfo).toBe('function')
    expect(typeof updateContact).toBe('function')
  })

  it('forwards GET requests to correct endpoints', async () => {
    await getCarbonReports({ pageNum: 1, pageSize: 10 })
    await getStatistics()
    await getOrgInfo()

    expect(requestMocks.getMock).toHaveBeenNthCalledWith(1, '/third-party/carbon-reports', {
      params: { pageNum: 1, pageSize: 10 },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(2, '/third-party/statistics')
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(3, '/third-party/org-info')
  })

  it('forwards updateContact with params', async () => {
    await updateContact({ contactPerson: 'Zhang', contactPhone: '13800000000' })
    expect(requestMocks.putMock).toHaveBeenCalledWith('/third-party/contact', null, {
      params: { contactPerson: 'Zhang', contactPhone: '13800000000' },
    })
  })

  it('rejects updateContact without contact person', async () => {
    await expect(updateContact({ contactPerson: '', contactPhone: '13800000000' }))
      .rejects.toThrow('Contact person is required')
  })

  it('rejects updateContact without contact phone', async () => {
    await expect(updateContact({ contactPerson: 'Zhang', contactPhone: '' }))
      .rejects.toThrow('Contact phone is required')
  })
})