import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

/** 证书接口按用例返回不同数据 */
vi.mock('../../api/enterprise', () => ({
  getMyEnterpriseAdmission: vi.fn(() => Promise.resolve([])),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElAlert: {
      template: '<div class="el-alert" :data-type="type"><slot name="title" /><slot /></div>',
      props: ['type', 'closable', 'showIcon', 'title'],
    },
    ElTooltip: { template: '<div><slot /></div>', props: ['content', 'placement', 'disabled'] },
    ElIcon: { template: '<i><slot /></i>' },
  }
})

import AdmissionStatusBanner from '../AdmissionStatusBanner.vue'
import { getMyEnterpriseAdmission } from '../../api/enterprise'
import { useAppStore } from '../../store'
import { useCertificateStatus } from '../../composables/useCertificateStatus'

const stubs = {
  'el-alert': {
    template: '<div class="el-alert" :data-type="type"><slot name="title" /><slot /></div>',
    props: ['type', 'closable', 'showIcon', 'title'],
  },
}

function mountBanner(role: 'ENTERPRISE' | 'REVIEWER' = 'ENTERPRISE') {
  // 只创建**一个** pinia 实例：先 setActivePinia 让 store 写入它，
  // 再用同一个实例作为组件插件。早先创建了两个（setActivePinia 一个、
  // plugins 里又一个），组件读到的是空 store，role 为 null，
  // 于是 onMounted 里的 refreshAdmission 根本不执行（loaded 恒 false）。
  const pinia = createPinia()
  setActivePinia(pinia)
  const appStore = useAppStore()
  appStore.role = role
  appStore.username = 'enterprise001'
  appStore.loggedIn = true
  return mount(AdmissionStatusBanner, { global: { plugins: [pinia], stubs } })
}

/** 构造一张证书 */
function cert(overrides: Record<string, unknown> = {}) {
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

describe('AdmissionStatusBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // composable 是模块级单例（admissions/loaded 是 ref），
    // 用例之间必须显式清空，否则上一个用例的状态会串到下一个。
    // 注意不能用 vi.resetModules()——那会让组件与测试引用到**不同**的
    // 模块实例，reset 作用在测试侧实例上而组件读的是另一份，等于没重置。
    useCertificateStatus().resetAdmission()
  })

  it('有有效证书时不显示横幅（不打扰）', async () => {
    vi.mocked(getMyEnterpriseAdmission).mockResolvedValueOnce([cert()] as never)

    const wrapper = mountBanner()
    await flushPromises()

    expect(wrapper.find('.el-alert').exists()).toBe(false)
    wrapper.unmount()
  })

  it('从未签发时显示提示，引导联系管理员', async () => {
    vi.mocked(getMyEnterpriseAdmission).mockResolvedValueOnce([] as never)

    const wrapper = mountBanner()
    await flushPromises()

    expect(wrapper.find('.el-alert').exists()).toBe(true)
    // i18n 在测试环境加载真实语言包，断言实际渲染文案而非 key
    expect(wrapper.text()).toContain('尚未签发准入证书')
    wrapper.unmount()
  })

  it('已吊销时显示原因与时间（V19 审计留痕）', async () => {
    vi.mocked(getMyEnterpriseAdmission).mockResolvedValueOnce([
      cert({
        status: 2,
        revokedAt: '2026-10-31T09:31:31',
        revokeReason: '连续三期报告数据不合规',
      }),
    ] as never)

    const wrapper = mountBanner()
    await flushPromises()

    expect(wrapper.find('.el-alert').exists()).toBe(true)
    expect(wrapper.text()).toContain('准入证书已被吊销')
    expect(wrapper.text()).toContain('连续三期报告数据不合规')
    expect(wrapper.text()).toContain('EA-20260101-000001')
    wrapper.unmount()
  })

  it('已吊销但管理员未填原因时显示占位说明，不留空白', async () => {
    vi.mocked(getMyEnterpriseAdmission).mockResolvedValueOnce([
      cert({ status: 2, revokedAt: '2026-10-31T09:31:31', revokeReason: null }),
    ] as never)

    const wrapper = mountBanner()
    await flushPromises()

    expect(wrapper.text()).toContain('管理员未填写')
    wrapper.unmount()
  })

  it('已过期（status=1 但有效期已过）用 warning 语气而非 danger', async () => {
    vi.mocked(getMyEnterpriseAdmission).mockResolvedValueOnce([
      cert({ status: 1, expiryDate: '2020-01-01' }),
    ] as never)

    const wrapper = mountBanner()
    await flushPromises()

    const alert = wrapper.find('.el-alert')
    expect(alert.exists()).toBe(true)
    expect(alert.attributes('data-type')).toBe('warning')
    expect(wrapper.text()).toContain('准入证书已过期')
    wrapper.unmount()
  })

  it('非企业角色不显示横幅（审核员/管理员不受准入证书约束）', async () => {
    vi.mocked(getMyEnterpriseAdmission).mockResolvedValueOnce([] as never)

    const wrapper = mountBanner('REVIEWER')
    await flushPromises()

    expect(wrapper.find('.el-alert').exists()).toBe(false)
    wrapper.unmount()
  })
})
