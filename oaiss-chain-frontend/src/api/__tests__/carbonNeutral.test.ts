import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
  putMock: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    post: requestMocks.postMock,
    get: requestMocks.getMock,
    put: requestMocks.putMock,
  },
}))

import {
  applyCertification,
  completeCertification,
  createProject,
  getMyProjects,
  getPendingVerification,
  getProject,
  getProjects,
  reviewProject,
  startProject,
  submitProject,
  submitVerification,
  terminateProject,
  updateMonitoring,
  updateProject,
  useCredits,
  verifyProject,
} from '../carbonNeutral'

describe('carbonNeutral api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.postMock.mockResolvedValue(undefined)
    requestMocks.getMock.mockResolvedValue({})
    requestMocks.putMock.mockResolvedValue(undefined)
  })

  it('exports all 16 functions', () => {
    expect(typeof getProjects).toBe('function')
    expect(typeof getProject).toBe('function')
    expect(typeof createProject).toBe('function')
    expect(typeof updateProject).toBe('function')
    expect(typeof submitProject).toBe('function')
    expect(typeof startProject).toBe('function')
    expect(typeof submitVerification).toBe('function')
    expect(typeof updateMonitoring).toBe('function')
    expect(typeof applyCertification).toBe('function')
    expect(typeof terminateProject).toBe('function')
    expect(typeof getMyProjects).toBe('function')
    expect(typeof reviewProject).toBe('function')
    expect(typeof verifyProject).toBe('function')
    expect(typeof useCredits).toBe('function')
    expect(typeof completeCertification).toBe('function')
    expect(typeof getPendingVerification).toBe('function')
  })

  it('forwards GET requests to correct endpoints', async () => {
    await getProjects({ pageNum: 1, pageSize: 10 })
    await getProject(42)
    await getMyProjects()
    await getPendingVerification({ pageNum: 1, pageSize: 5 })

    expect(requestMocks.getMock).toHaveBeenNthCalledWith(1, '/carbon-neutral/search', {
      params: { pageNum: 1, pageSize: 10 },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(2, '/carbon-neutral/42')
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(3, '/carbon-neutral/my', { params: undefined })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(4, '/carbon-neutral/pending-verification', {
      params: { pageNum: 1, pageSize: 5 },
    })
  })

  it('forwards create/update to correct endpoints', async () => {
    const data: import('../../types').CarbonNeutralProjectRequest = { projectName: 'Tree Planting', projectType: 1 }
    await createProject(data)
    await updateProject(42, { projectName: 'Updated' } as Partial<import('../../types').CarbonNeutralProjectRequest>)

    expect(requestMocks.postMock).toHaveBeenNthCalledWith(1, '/carbon-neutral', data)
    expect(requestMocks.putMock).toHaveBeenCalledWith('/carbon-neutral/42', { projectName: 'Updated' })
  })

  it('forwards lifecycle actions to correct endpoints', async () => {
    await submitProject(1)
    await startProject(1)
    await applyCertification(1)
    await terminateProject(1, { reason: 'budget' })

    expect(requestMocks.postMock).toHaveBeenNthCalledWith(1, '/carbon-neutral/1/submit')
    expect(requestMocks.postMock).toHaveBeenNthCalledWith(2, '/carbon-neutral/1/start')
    expect(requestMocks.postMock).toHaveBeenNthCalledWith(3, '/carbon-neutral/1/apply-certification')
    expect(requestMocks.postMock).toHaveBeenNthCalledWith(4, '/carbon-neutral/1/terminate', { reason: 'budget' })
  })

  it('forwards verification and monitoring to correct endpoints', async () => {
    const verifyData: import('../../types').ProjectVerificationRequest = { projectId: 1, verifiedReduction: 100, remark: 'ok' }
    await submitVerification(1, verifyData)
    await updateMonitoring(1, { monitoringData: { co2: 100 } })

    expect(requestMocks.postMock).toHaveBeenCalledWith('/carbon-neutral/1/submit-verification', verifyData)
    expect(requestMocks.putMock).toHaveBeenCalledWith('/carbon-neutral/1/monitoring', { monitoringData: { co2: 100 } })
  })

  it('rejects reviewProject when projectId is falsy', async () => {
    await expect(reviewProject(0 as unknown as number, { approved: true, comment: 'ok' }))
      .rejects.toThrow('项目ID不能为空')
  })

  it('rejects verifyProject when projectId is missing', async () => {
    await expect(verifyProject({ projectId: 0 } as import('../../types').ProjectVerificationRequest))
      .rejects.toThrow('项目ID不能为空')
  })

  it('rejects useCredits when projectId is falsy', async () => {
    await expect(useCredits(0 as unknown as number, { amount: 100 }))
      .rejects.toThrow('项目ID不能为空')
  })

  it('rejects completeCertification when projectId is falsy', async () => {
    await expect(completeCertification(0 as unknown as number))
      .rejects.toThrow('项目ID不能为空')
  })
})
