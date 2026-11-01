/**
 * API 基址规范化的回归测试。
 *
 * 背景：Playwright 的 newContext({ baseURL }) 按 RFC 3986 相对引用解析，
 * baseURL 的最后一段会被当「文件名」替换掉：
 *   baseURL='http://h:8080/api/v1' + '/auth/login' → 'http://h:8080/auth/login'
 * 于是所有 API 请求打到不存在的路径，拿到 Tomcat 的 404 HTML，
 * 上层 .json() 抛 "Unexpected token '<'"——报错完全指不出根因。
 *
 * 这里钉住两点：
 *   1. normalizeApiBase 一定产出以 / 结尾的形式
 *   2. 用该值拼接后，路径段（/api/v1）必须保留
 */
import { describe, expect, it } from 'vitest'
import { normalizeApiBase } from '../e2e/fixtures/api-base'

describe('normalizeApiBase', () => {
  it('无末尾斜杠时补上', () => {
    expect(normalizeApiBase('http://localhost:8080/api/v1')).toBe('http://localhost:8080/api/v1/')
  })

  it('已有末尾斜杠时保持原样，不重复追加', () => {
    expect(normalizeApiBase('http://localhost:8080/api/v1/')).toBe('http://localhost:8080/api/v1/')
  })

  it('规范化后 + 无前导斜杠的路径，保留 /api/v1 段', () => {
    // 两条规则缺一不可：
    //   1) baseURL 以 / 结尾 → 被当作目录
    //   2) 请求路径不带前导 / → 否则被视为根绝对路径
    const base = normalizeApiBase('http://localhost:8080/api/v1')
    const resolved = new URL('auth/login', base).toString()
    expect(resolved).toBe('http://localhost:8080/api/v1/auth/login')
  })

  it('baseURL 以 / 结尾但路径带前导斜杠 → 仍丢 /api/v1', () => {
    // 记录这个坑：只加末尾斜杠还不够
    const base = normalizeApiBase('http://localhost:8080/api/v1')
    const resolved = new URL('/auth/login', base).toString()
    expect(resolved).toBe('http://localhost:8080/auth/login')
    expect(resolved).not.toContain('/api/v1')
  })

  it('未规范化 baseURL 时也会丢掉 /api/v1（反证：说明这个函数不是多余的）', () => {
    const raw = 'http://localhost:8080/api/v1'
    const resolved = new URL('auth/login', raw).toString()
    expect(resolved).not.toContain('/api/v1')
  })
})