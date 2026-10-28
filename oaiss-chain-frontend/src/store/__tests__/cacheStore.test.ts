import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCacheStore } from '../cacheStore'

vi.mock('../../api/user', () => ({
  getProfile: vi.fn(() => Promise.resolve({ id: 1, username: 'test' })),
}))

vi.mock('../../api/enterprise', () => ({
  getEnterpriseInfo: vi.fn(() => Promise.resolve({ id: 1, name: 'test-enterprise' })),
  getQuotaInfo: vi.fn(() => Promise.resolve({ totalQuota: 100 })),
}))

vi.mock('../../api/credit', () => ({
  getMyScore: vi.fn(() => Promise.resolve({ score: 85 })),
}))

describe('cacheStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('caches profile and avoids duplicate calls', async () => {
    const store = useCacheStore()
    const [a, b, c] = await Promise.all([
      store.getProfile(),
      store.getProfile(),
      store.getProfile(),
    ])
    expect(a).toEqual(b)
    expect(b).toEqual(c)
    const { getProfile } = await import('../../api/user')
    expect(getProfile).toHaveBeenCalledTimes(1)
  })

  it('refetches after force=true', async () => {
    const store = useCacheStore()
    await store.getEnterprise()
    await store.getEnterprise(true)
    const { getEnterpriseInfo } = await import('../../api/enterprise')
    expect(getEnterpriseInfo).toHaveBeenCalledTimes(2)
  })

  it('invalidates all caches', async () => {
    const store = useCacheStore()
    await store.getCreditScore()
    await store.getQuota()
    store.invalidateAll()
    expect(store.creditScore).toBeNull()
    expect(store.quota).toBeNull()
  })

  it('SWR returns stale data immediately while refreshing in background', async () => {
    const store = useCacheStore()
    const staleData = { id: 1, name: 'stale' }
    const freshData = { id: 1, name: 'fresh' }
    const factory = vi.fn().mockResolvedValue(freshData)

    store.profile = { data: staleData as never, timestamp: Date.now() - 600000 }

    const result = await store.swr('profile', store.profile, factory)

    expect(result).toEqual(staleData)
  })

  it('SWR fetches fresh data when no cache exists', async () => {
    const store = useCacheStore()
    const freshData = { id: 2, name: 'fresh' }
    const factory = vi.fn().mockResolvedValue(freshData)

    const result = await store.swr('test-key', null, factory)

    expect(result).toEqual(freshData)
  })
})