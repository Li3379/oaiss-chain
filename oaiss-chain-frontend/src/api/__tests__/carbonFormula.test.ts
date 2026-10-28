import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  postMock: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    post: requestMocks.postMock,
  },
}))

import { calculatePowerGeneration, calculatePowerGrid } from '../carbonFormula'

describe('carbonFormula api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.postMock.mockResolvedValue({})
  })

  it('exports both calculation functions', () => {
    expect(typeof calculatePowerGeneration).toBe('function')
    expect(typeof calculatePowerGrid).toBe('function')
  })

  it('forwards power generation calculation to correct endpoint', async () => {
    const data = { rawCoalFc: 1000, rawCoalNcv: 20, rawCoalCc: 0.6, rawCoalOf: 0.9 } as import('../../types/carbonFormula').PowerGenerationCalculationRequest
    await calculatePowerGeneration(data)
    expect(requestMocks.postMock).toHaveBeenCalledWith('/carbon/calculate/power-generation', data)
  })

  it('forwards power grid calculation to correct endpoint', async () => {
    const data = { transmissionVolume: 10000, lineLossRate: 0.05, gridEmissionFactor: 0.5, generationVolume: null, importedElectricity: null, exportedElectricity: null, importEmissionFactor: null } as import('../../types/carbonFormula').PowerGridCalculationRequest
    await calculatePowerGrid(data)
    expect(requestMocks.postMock).toHaveBeenCalledWith('/carbon/calculate/power-grid', data)
  })
})