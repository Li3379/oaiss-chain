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

import { getCarbonReports, getOrgInfo, getStatistics } from '../thirdParty'

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

})