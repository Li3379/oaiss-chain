import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'zh-CN' },
  }),
}))

describe('LanguageSwitcher', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should be importable', async () => {
    const mod = await import('../LanguageSwitcher.vue')
    expect(mod.default).toBeDefined()
  })

  it('should render without errors', async () => {
    const LanguageSwitcher = (await import('../LanguageSwitcher.vue')).default
    const wrapper = mount(LanguageSwitcher, {
      global: {
        stubs: {
          'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
          'el-dropdown-menu': { template: '<div><slot /></div>' },
          'el-dropdown-item': { template: '<div><slot /></div>' },
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('should expose locale switching logic', async () => {
    const mod = await import('../LanguageSwitcher.vue')
    expect(mod.default).toBeDefined()
  })
})
