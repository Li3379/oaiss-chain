import { describe, it, expect, vi, beforeEach } from 'vitest'
import request from '../request'
import { getMarketOverview } from '../../api/search'

vi.mock('../request', () => ({
  default: {
    get: vi.fn(),
  },
}))

describe('Search API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should export functions', async () => {
    const mod = await import('../../api/search')
    expect(Object.keys(mod).length).toBeGreaterThan(0)
  })

  it('getMarketOverview passes suppressErrorMessage: true to request.get', async () => {
    vi.mocked(request.get).mockResolvedValue({ totalEnterprises: 100 })
    await getMarketOverview()
    expect(request.get).toHaveBeenCalledWith('/search/market-overview', {
      suppressErrorMessage: true,
    })
  })
})
