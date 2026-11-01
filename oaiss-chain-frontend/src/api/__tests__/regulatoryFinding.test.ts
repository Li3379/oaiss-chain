import { describe, it, expect, vi } from 'vitest'

vi.mock('../request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

describe('监管线索 API', () => {
  it('模块导出查询与处置函数', async () => {
    const mod = await import('../regulatoryFinding')
    expect(typeof mod.listFindings).toBe('function')
    expect(typeof mod.resolveFinding).toBe('function')
  })

  it('严重等级与规则代码与后端常量对齐', async () => {
    const mod = await import('../regulatoryFinding')
    expect(mod.FINDING_SEVERITY.HIGH).toBe('HIGH')
    expect(mod.FINDING_SEVERITY.CRITICAL).toBe('CRITICAL')
    expect(mod.FINDING_RULES.REPEATED_REJECTION).toBe('REPEATED_REJECTION')
    expect(mod.FINDING_RULES.QUOTA_OVERDRAFT).toBe('QUOTA_OVERDRAFT')
  })
})
