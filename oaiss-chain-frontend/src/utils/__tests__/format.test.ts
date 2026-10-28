import { describe, it, expect } from 'vitest'
import { formatDateTime, formatNumber, formatNumberWithUnit } from '../format'

describe('formatNumber 数字千分位规范化', () => {
  it('千位以上加分隔符，避免长数字难读', () => {
    expect(formatNumber(50000)).toBe('50,000')
    expect(formatNumber(37935)).toBe('37,935')
    expect(formatNumber('12065')).toBe('12,065')
  })

  it('千位以下保持原样', () => {
    expect(formatNumber(0)).toBe('0')
    expect(formatNumber(999)).toBe('999')
    expect(formatNumber(21.6)).toBe('21.6')
  })

  it('保留小数并去掉无意义尾零', () => {
    expect(formatNumber(9449.98)).toBe('9,449.98')
    expect(formatNumber(1100.0)).toBe('1,100')
    expect(formatNumber(1.005, '-', 2)).toBe('1.01')
  })

  it('空值/非法值回退占位符，不出现 NaN', () => {
    expect(formatNumber(null)).toBe('-')
    expect(formatNumber(undefined)).toBe('-')
    expect(formatNumber('')).toBe('-')
    expect(formatNumber('abc')).toBe('-')
    expect(formatNumber(NaN)).toBe('-')
    expect(formatNumber(null, '—')).toBe('—')
  })

  it('带单位展示', () => {
    expect(formatNumberWithUnit(5230, 'tCO₂e')).toBe('5,230 tCO₂e')
    expect(formatNumberWithUnit(null, 'tCO₂e')).toBe('-')
  })
})

describe('formatDateTime 日期规范化', () => {
  it('ISO 转可读格式并清理纳秒/时区', () => {
    expect(formatDateTime('2026-05-14T17:22:28')).toBe('2026-05-14 17:22:28')
    expect(formatDateTime('2026-09-11T09:12:30.212424100+08:00')).toBe('2026-09-11 09:12:30')
  })

  it('空值返回空串', () => {
    expect(formatDateTime(null)).toBe('')
    expect(formatDateTime(undefined)).toBe('')
  })
})
