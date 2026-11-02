import { describe, expect, it } from 'vitest'
import {
  hasAnyAdmission,
  isAdmissionValid,
  pickActiveAdmission,
} from '../useCertificateStatus'
import type { EnterpriseAdmissionResponse } from '../../types/admin'

/** 构造一张证书；不传 status 默认有效 */
function cert(
  overrides: Partial<EnterpriseAdmissionResponse> = {},
): EnterpriseAdmissionResponse {
  return {
    id: 1,
    enterpriseId: 1,
    certificateNo: 'EA-20260101-000001',
    issuedDate: '2026-01-01',
    expiryDate: '2027-01-01',
    status: 1,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
    ...overrides,
  }
}

/** 相对今天的日期字符串（+n 天 / -n 天） */
function dayOffset(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

describe('isAdmissionValid（与后端 CertificateGateService 口径一致）', () => {
  it('status=1 且未过期 → 有效', () => {
    expect(isAdmissionValid(cert())).toBe(true)
  })

  it('status=2（已吊销）→ 无效，即使有效期未到', () => {
    expect(isAdmissionValid(cert({ status: 2 }))).toBe(false)
  })

  it('有效期为空 → 视为长期有效', () => {
    expect(isAdmissionValid(cert({ expiryDate: '' }))).toBe(true)
    expect(isAdmissionValid(cert({ expiryDate: null as unknown as string }))).toBe(true)
  })

  it('已过期 → 无效', () => {
    expect(isAdmissionValid(cert({ expiryDate: dayOffset(-1) }))).toBe(false)
  })

  it('到期日当天 → 仍有效（含当天，与后端 !isBefore(today) 一致）', () => {
    expect(isAdmissionValid(cert({ expiryDate: dayOffset(0) }))).toBe(true)
  })

  it('null / undefined → 无效（缺数据不等于合规）', () => {
    expect(isAdmissionValid(null)).toBe(false)
    expect(isAdmissionValid(undefined)).toBe(false)
  })
})

describe('pickActiveAdmission', () => {
  it('多张证书中取第一张有效的（列表按创建时间倒序）', () => {
    const list = [cert({ id: 3, status: 2 }), cert({ id: 2, status: 1 }), cert({ id: 1, status: 2 })]
    expect(pickActiveAdmission(list)?.id).toBe(2)
  })

  it('全部吊销 → null', () => {
    expect(pickActiveAdmission([cert({ status: 2 }), cert({ id: 2, status: 2 })])).toBeNull()
  })

  it('空列表 / 非数组 → null', () => {
    expect(pickActiveAdmission([])).toBeNull()
    expect(pickActiveAdmission(null)).toBeNull()
    expect(pickActiveAdmission(undefined)).toBeNull()
  })

  it('跳过已过期但仍为 status=1 的记录', () => {
    const list = [cert({ id: 9, status: 1, expiryDate: dayOffset(-30) }), cert({ id: 8 })]
    expect(pickActiveAdmission(list)?.id).toBe(8)
  })
})

describe('hasAnyAdmission', () => {
  it('有记录（无论有效与否）→ true，用于区分「从未签发」与「已吊销」', () => {
    expect(hasAnyAdmission([cert({ status: 2 })])).toBe(true)
  })

  it('无记录 → false', () => {
    expect(hasAnyAdmission([])).toBe(false)
    expect(hasAnyAdmission(null)).toBe(false)
  })
})
