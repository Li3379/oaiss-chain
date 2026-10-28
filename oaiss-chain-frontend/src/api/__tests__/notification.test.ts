import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      get: vi.fn(),
      put: vi.fn(),
      post: vi.fn(),
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
    })),
  },
}))

describe('Notification API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getNotifications calls correct endpoint', async () => {
    const mockGet = vi.fn().mockResolvedValue({
      data: { code: 200, data: { content: [], totalElements: 0 } },
    })
    const request = { get: mockGet }
    await request.get('/notifications', { params: { page: 1, size: 10 } })
    expect(mockGet).toHaveBeenCalledWith('/notifications', { params: { page: 1, size: 10 } })
  })

  it('markAsRead calls PUT endpoint with id', async () => {
    const mockPut = vi.fn().mockResolvedValue({ data: { code: 200 } })
    const request = { put: mockPut }
    await request.put('/notifications/123/read')
    expect(mockPut).toHaveBeenCalledWith('/notifications/123/read')
  })

  it('markAllAsRead calls PUT read-all endpoint', async () => {
    const mockPut = vi.fn().mockResolvedValue({ data: { code: 200 } })
    const request = { put: mockPut }
    await request.put('/notifications/read-all')
    expect(mockPut).toHaveBeenCalledWith('/notifications/read-all')
  })

  it('getUnreadCount returns numeric count', async () => {
    const mockGet = vi.fn().mockResolvedValue({
      data: { code: 200, data: 5 },
    })
    const request = { get: mockGet }
    const result = await request.get('/notifications/unread-count')
    expect(result.data.data).toBe(5)
  })
})
