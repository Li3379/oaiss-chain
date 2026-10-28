import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('../../api/user', () => ({
  getProfile: vi.fn(() => Promise.resolve({
    username: 'enterprise001',
    realName: 'Green Energy Co',
    company: 'Green Energy Co',
  })),
}))

vi.mock('../../api/enterprise', () => ({
  getEnterpriseInfo: vi.fn(() => Promise.resolve({
    id: 1,
    enterpriseName: 'Green Energy Co',
  })),
}))

vi.mock('../../api/carbonFormula', () => ({
  calculatePowerGeneration: vi.fn(() => Promise.resolve({
    totalEmission: 100,
    combustionEmission: 80,
    desulfurizationEmission: 20,
    fuelDetails: [],
    reportingYear: '2026',
    enterpriseName: 'Green Energy Co',
    formulaReference: 'GB/T 32150-2015',
    calculatedAt: '2026-05-22T00:00:00Z',
  })),
  calculatePowerGrid: vi.fn(() => Promise.resolve({
    totalEmission: 80,
    transmissionLossEmission: 50,
    importedEmission: 30,
    transmissionLoss: 10,
    formulaReference: 'GB/T 32150-2015',
    reportingYear: '2026',
    enterpriseName: 'Green Energy Co',
    calculatedAt: '2026-05-22T00:00:00Z',
  })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import CarbonFormulaCalculator from '../enterprise/CarbonFormulaCalculator.vue'
import { getProfile } from '../../api/user'
import { getEnterpriseInfo } from '../../api/enterprise'
import { calculatePowerGeneration, calculatePowerGrid } from '../../api/carbonFormula'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>' },
  'el-tabs': {
    template: '<div class="el-tabs"><slot /></div>',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
  'el-form': { template: '<form><slot /></form>', props: ['labelWidth'] },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label'] },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['span'] },
  'el-collapse': { template: '<div class="el-collapse"><slot /></div>' },
  'el-collapse-item': { template: '<div class="el-collapse-item"><slot /></div>', props: ['title'] },
  'el-input': {
    template: '<input :value="modelValue" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
  },
  'el-input-number': {
    template: '<input role="spinbutton" :value="modelValue ?? \'\'" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
  'el-date-picker': {
    template: '<input class="date-picker" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'valueFormat', 'placeholder'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'loading'],
    emits: ['click'],
  },
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label'] },
  'el-table': { template: '<table><slot /></table>', props: ['data', 'border'] },
  'el-table-column': { template: '<td><slot /></td>', props: ['prop', 'label', 'minWidth'] },
}

function mountComponent() {
  return mount(CarbonFormulaCalculator, {
    global: {
      stubs,
    },
  })
}

describe('CarbonFormulaCalculator.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads enterprise info into both formula tabs', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getEnterpriseInfo).toHaveBeenCalled()
    expect(getProfile).not.toHaveBeenCalled()

    const enterpriseInputs = wrapper
      .findAll('input[placeholder="carbonFormula.enterEnterpriseName"]')
      .map((input) => (input.element as HTMLInputElement).value)

    expect(enterpriseInputs).toEqual(['Green Energy Co', 'Green Energy Co'])
    wrapper.unmount()
  })

  it('submits power generation with the prefilled enterprise name', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.pgForm.rawCoal.fc = 1

    await wrapper.find('.btn-calc-pg').trigger('click')

    expect(calculatePowerGeneration).toHaveBeenCalledWith(
      expect.objectContaining({
        enterpriseName: 'Green Energy Co',
        rawCoalFc: 1,
      }),
    )
    wrapper.unmount()
  })

  it('submits power grid with the prefilled enterprise name', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.gridForm.transmissionVolume = 1
    vm.gridForm.lineLossRate = 0.1
    vm.gridForm.gridEmissionFactor = 1

    await wrapper.find('.btn-calc-grid').trigger('click')

    expect(calculatePowerGrid).toHaveBeenCalledWith(
      expect.objectContaining({
        transmissionVolume: 1,
        lineLossRate: 0.1,
        gridEmissionFactor: 1,
        enterpriseName: 'Green Energy Co',
      }),
    )
    wrapper.unmount()
  })
})
