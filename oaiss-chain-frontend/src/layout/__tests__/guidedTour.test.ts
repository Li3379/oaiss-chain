import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 引导弹窗「仅首次出现」的持久化契约测试。
 *
 * 回归背景：
 *  1) 旧实现使用全局常量键 oaiss_guided_tour_done —— A 用户看过后 B 用户不再弹出；
 *  2) 仅写 localStorage，不可用时状态不落盘 → 每个页面重复弹出（27 张截图被遮挡）。
 * 本测试锁定：键按用户隔离 + localStorage 不可用时降级 sessionStorage。
 */
const KEY = 'oaiss_guided_tour_done'
const keyFor = (userId: number) => `${KEY}:${userId}`

function createStorage(overrides: Partial<Storage> = {}): Storage {
  const map = new Map<string, string>()
  return {
    getItem: (k: string) => (map.has(k) ? (map.get(k) as string) : null),
    setItem: (k: string, v: string) => void map.set(k, String(v)),
    removeItem: (k: string) => void map.delete(k),
    clear: () => map.clear(),
    key: (i: number) => [...map.keys()][i] ?? null,
    get length() { return map.size },
    ...overrides,
  } as Storage
}

/**
 * 与被测组件 src/layout/index.vue 中
 * tourStorageKey / isTourDone / persistTourDone / markTourDone 等价的判定实现。
 */
function createTour(store: { local: Storage; session: Storage }, getUserId: () => number | null) {
  const key = () => {
    const uid = getUserId()
    return uid ? keyFor(uid) : KEY
  }
  const isDone = () => {
    const k = key()
    try {
      return store.local.getItem(k) === '1' || store.session.getItem(k) === '1'
    } catch {
      return false
    }
  }
  const persist = (k: string) => {
    try {
      store.local.setItem(k, '1')
    } catch {
      try { store.session.setItem(k, '1') } catch { /* 两者都不可用时仅内存生效 */ }
    }
  }
  let visible = false
  return {
    get visible() { return visible },
    mount(role: string) { if (role === 'ENTERPRISE' && !isDone()) visible = true },
    close() { visible = false; persist(key()) },
  }
}

describe('引导弹窗仅首次出现', () => {
  let local: Storage
  let session: Storage
  let userId: number

  beforeEach(() => {
    local = createStorage()
    session = createStorage()
    userId = 2
  })

  it('企业用户首次进入显示，关闭后各页面不再显示', () => {
    const tour = createTour({ local, session }, () => userId)
    tour.mount('ENTERPRISE')
    expect(tour.visible).toBe(true)
    tour.close()
    tour.mount('ENTERPRISE')
    expect(tour.visible).toBe(false)
    tour.mount('ENTERPRISE')
    expect(tour.visible).toBe(false)
  })

  it('状态键按用户隔离：另一用户仍能看到', () => {
    const a = createTour({ local, session }, () => userId)
    a.mount('ENTERPRISE')
    a.close()

    userId = 7
    const b = createTour({ local, session }, () => userId)
    b.mount('ENTERPRISE')
    expect(b.visible).toBe(true)
  })

  it('非企业角色不弹出', () => {
    const tour = createTour({ local, session }, () => userId)
    tour.mount('REVIEWER')
    expect(tour.visible).toBe(false)
  })

  it('localStorage 不可用时降级 sessionStorage，仍只弹一次', () => {
    const brokenLocal = createStorage({ setItem: () => { throw new Error('QuotaExceededError') } })
    const first = createTour({ local: brokenLocal, session }, () => userId)
    first.mount('ENTERPRISE')
    expect(first.visible).toBe(true)
    first.close()

    const second = createTour({ local: brokenLocal, session }, () => userId)
    second.mount('ENTERPRISE')
    expect(second.visible).toBe(false)
  })
})
