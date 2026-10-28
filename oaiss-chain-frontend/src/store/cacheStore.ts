import { defineStore } from 'pinia'
import { getProfile } from '../api/user'
import { getEnterpriseInfo, getQuotaInfo } from '../api/enterprise'
import { getMyScore } from '../api/credit'
import type { EnterpriseQuotaResponse, EnterpriseResponse, UserInfoResponse } from '../types/user'
import type { CreditScoreResponse } from '../types/credit'

interface CacheEntry<T> {
  data: T
  timestamp: number
}

const DEFAULT_TTL = 5 * 60 * 1000 // 5 minutes

/**
 * Request deduplication store.
 * Prevents duplicate API calls when multiple components request the same data
 * simultaneously. Tracks in-flight promises and shares the result.
 */
export const useCacheStore = defineStore('cache', {
  state: () => ({
    profile: null as CacheEntry<UserInfoResponse> | null,
    enterprise: null as CacheEntry<EnterpriseResponse> | null,
    creditScore: null as CacheEntry<CreditScoreResponse> | null,
    quota: null as CacheEntry<EnterpriseQuotaResponse> | null,
    ttl: DEFAULT_TTL,
    // In-flight request promises for deduplication
    _pending: {} as Record<string, Promise<unknown>>,
  }),

  actions: {
    isFresh(entry: CacheEntry<unknown> | null): boolean {
      if (!entry) return false
      return Date.now() - entry.timestamp < this.ttl
    },

    /**
     * Deduplicate concurrent requests for the same key.
     * If a request is already in-flight, returns the existing promise.
     */
    _dedupe<T>(key: string, factory: () => Promise<T>): Promise<T> {
      if (this._pending[key] !== undefined) {
        return this._pending[key] as Promise<T>
      }
      const promise = factory().finally(() => {
        delete this._pending[key]
      })
      this._pending[key] = promise
      return promise
    },

    async getProfile(force = false): Promise<UserInfoResponse> {
      if (!force && this.isFresh(this.profile)) {
        return this.profile!.data
      }
      const data = await this._dedupe('profile', () => getProfile())
      this.profile = { data, timestamp: Date.now() }
      return data
    },

    async getEnterprise(force = false): Promise<EnterpriseResponse> {
      if (!force && this.isFresh(this.enterprise)) {
        return this.enterprise!.data
      }
      const data = await this._dedupe('enterprise', () => getEnterpriseInfo())
      this.enterprise = { data, timestamp: Date.now() }
      return data
    },

    async getCreditScore(force = false): Promise<CreditScoreResponse> {
      if (!force && this.isFresh(this.creditScore)) {
        return this.creditScore!.data
      }
      const data = await this._dedupe('creditScore', () => getMyScore())
      this.creditScore = { data, timestamp: Date.now() }
      return data
    },

    async getQuota(force = false): Promise<EnterpriseQuotaResponse> {
      if (!force && this.isFresh(this.quota)) {
        return this.quota!.data
      }
      const data = await this._dedupe('quota', () => getQuotaInfo())
      this.quota = { data, timestamp: Date.now() }
      return data
    },

    /**
     * R115: Stale-while-revalidate - return cached data immediately,
     * then refresh in background for next request.
     */
    async swr<T>(key: string, cached: CacheEntry<T> | null, factory: () => Promise<T>): Promise<T> {
      // Return stale data immediately if available
      const staleData = cached?.data
      
      // Trigger background refresh (don't await)
      this._dedupe(key, factory).then((data) => {
        this.$patch((state: Record<string, unknown>) => {
          (state as Record<string, CacheEntry<unknown>>)[key] = { data, timestamp: Date.now() }
        })
      }).catch(() => {
        // Background refresh failed — stale data still serves
      })
      
      if (staleData !== undefined && staleData !== null) {
        return staleData
      }
      // No stale data — must await fresh
      return this._dedupe(key, factory)
    },

    invalidateAll() {
      this.profile = null
      this.enterprise = null
      this.creditScore = null
      this.quota = null
      this._pending = {}
    },
  },
})
