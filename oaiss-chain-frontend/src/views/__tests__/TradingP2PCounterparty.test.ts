import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (k: string) => k }),
}))

vi.mock('../../api/enterprise', () => ({
  searchEnterprises: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  getEnterpriseById: vi.fn(),
}))

vi.mock('../../api/trade', () => ({
  createP2PTrade: vi.fn(() => Promise.resolve()),
  getMyTrades: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  cancelTrade: vi.fn(() => Promise.resolve()),
}))

vi.mock('../../api/credit', () => ({
  getScoreRanking: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
}))

import TradingP2P from '../enterprise/TradingP2P.vue'
import { searchEnterprises } from '../../api/enterprise'
import { getScoreRanking } from '../../api/credit'

const mockedSearch = vi.mocked(searchEnterprises)
const mockedRanking = vi.mocked(getScoreRanking)

/** 读 SFC 源码，用于「不该存在的东西」这类结构性断言 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
const SFC_PATH = resolve(__dirname, '../enterprise/TradingP2P.vue')
function sfcSource(): string {
  return readFileSync(SFC_PATH, 'utf8')
}

describe('P2P 交易对手选择器（Batch 4）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockedSearch.mockResolvedValue({ items: [], total: 0 })
    mockedRanking.mockResolvedValue({ items: [], total: 0 } as never)
  })

  it('AC-17a：不再使用 loadBuyerOptions（N+1 旧实现已删除）', () => {
    const src = sfcSource()
    expect(src).not.toContain('loadBuyerOptions')
    // 旧实现的特征：逐个查询详情（Promise.all + getEnterpriseById）
    expect(src).not.toContain('Promise.all')
  })

  it('AC-17b：删除后无死导入残留', () => {
    const src = sfcSource()
    expect(src).not.toContain('getScoreRanking')
    expect(src).not.toContain('getEnterpriseById')
  })

  it('AC-16：下拉 label 只含企业名与信用码后 6 位，不含 userId', () => {
    const src = sfcSource()
    // 旧实现是 `${enterprise.enterpriseName}（用户 ${enterprise.userId}）`
    expect(src).not.toMatch(/（用户\s*\$\{/)
    expect(src).not.toContain('用户 ${')
  })

  it('AC-15：使用 remote 远程搜索而非一次性拉取', () => {
    const src = sfcSource()
    expect(src).toContain('remote')
    expect(src).toContain('remote-method')
    expect(src).toContain('reserve-keyword')
  })

  it('AC-15a：输入不足 2 字符时不发起请求（源码守卫）', () => {
    const src = sfcSource()
    // <script setup> 的局部函数不上 vm，故此处断言源码层的守卫存在
    expect(src).toMatch(/kw\.length\s*<\s*2/)
    expect(src).toContain('buyerOptions.value = []')
  })

  it('挂载时不再请求信誉榜（旧实现的数据来源）', async () => {
    mount(TradingP2P, {
      global: { stubs: { 'el-table': true, 'el-table-column': true, 'el-pagination': true } },
    })
    await flushPromises()

    expect(mockedRanking).not.toHaveBeenCalled()
  })
})