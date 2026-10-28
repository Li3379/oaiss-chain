import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  getMock: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    get: requestMocks.getMock,
  },
}))

import { getEnterpriseInference } from '../enterpriseInference'

describe('enterpriseInference api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.getMock.mockResolvedValue({})
  })

  it('exports the inference function', () => {
    expect(typeof getEnterpriseInference).toBe('function')
  })

  it('forwards request to correct endpoint with enterprise ID', async () => {
    await getEnterpriseInference(42)
    expect(requestMocks.getMock).toHaveBeenCalledWith('/predict/enterprise/42/inference')
  })
})