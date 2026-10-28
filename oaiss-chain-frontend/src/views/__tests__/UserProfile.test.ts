// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
// 注意：行首勿用 "(wrapper.vm as any)"——与上行 await 组合会触发 ASI 解析陷阱。
function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: ref('zh-CN'),
  }),
}))

vi.mock('../../api/user', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  changePassword: vi.fn(),
}))

vi.mock('../../api/enterprise', () => ({
  getMyEnterpriseAdmission: vi.fn(),
}))

vi.mock('../../api/signature', () => ({
  getKeyPair: vi.fn(),
  generateKeyPair: vi.fn(() => Promise.resolve({})),
  deleteKeyPair: vi.fn(() => Promise.resolve()),
}))

vi.mock('../../utils/auth', () => ({
  getAccessToken: vi.fn(() => 'test-access-token'),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import UserProfile from '../enterprise/UserProfile.vue'
import { getMyEnterpriseAdmission } from '../../api/enterprise'
import { changePassword, getProfile, updateProfile } from '../../api/user'
import { getKeyPair } from '../../api/signature'
import { ElMessage } from 'element-plus'

const profileResponse = {
  userId: 7,
  username: 'green-enterprise',
  realName: 'Alice',
  phone: '13800138000',
  email: 'alice@example.com',
  avatar: '',
  company: 'Green Corp',
  address: 'Suzhou',
  userType: 1,
  userTypeDesc: 'ENTERPRISE',
  status: 1,
  lastLoginAt: '2026-05-31T10:00:00',
  lastLoginIp: '127.0.0.1',
  createdAt: '2026-05-01T08:00:00',
}

const updatedProfileResponse = {
  ...profileResponse,
  realName: 'Alice Updated',
  company: 'Green Corp Updated',
}

const admissionResponse = [
  {
    id: 1,
    enterpriseId: 22,
    certificateNo: 'CERT-2026-001',
    issuedDate: '2026-05-20',
    expiryDate: '2027-05-20',
    status: 1,
    createdAt: '2026-05-20T10:00:00',
    updatedAt: '2026-05-20T10:00:00',
  },
]

const keyPairNotFoundError = Object.assign(new Error('no key pair'), {
  businessCode: 5015,
  response: { data: { code: 5015, message: 'no active key pair' } },
})

const stubs = {
  PageContainer: { template: '<div><slot /></div>', props: ['title', 'description'] },
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>' },
  'el-space': { template: '<div class="el-space"><slot /></div>' },
  'el-form': {
    template: '<form @submit.prevent><slot /></form>',
    methods: {
      validate() { return Promise.resolve(true) },
      validateField() { return Promise.resolve(true) },
      resetFields() {},
    },
  },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label', 'prop'] },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'placeholder', 'showPassword', 'disabled', 'rows', 'readonly', 'resize'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'plain'],
    emits: ['click'],
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type'] },
  'el-tabs': { template: '<div class="el-tabs"><slot /></div>', props: ['modelValue'] },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label', 'span'] },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description', 'imageSize'] },
}

function mountComponent() {
  return mount(UserProfile, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('UserProfile.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.mocked(getProfile).mockResolvedValue(profileResponse)
    vi.mocked(updateProfile).mockResolvedValue(updatedProfileResponse)
    vi.mocked(changePassword).mockResolvedValue()
    vi.mocked(getMyEnterpriseAdmission).mockResolvedValue(admissionResponse)
    vi.mocked(getKeyPair).mockRejectedValue(keyPairNotFoundError)
  })

  it('loads profile and admission data on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getProfile).toHaveBeenCalledTimes(1)
    expect(getMyEnterpriseAdmission).toHaveBeenCalledTimes(1)
    expect(vmOf(wrapper).profile).toEqual(profileResponse)
    expect(vmOf(wrapper).editForm.realName).toBe('Alice')
    expect(vmOf(wrapper).editForm.company).toBe('Green Corp')
    expect(vmOf(wrapper).admissionStatus?.certificateNo).toBe('CERT-2026-001')

    wrapper.unmount()
  })

  it('treats business code 5015 as an empty signature state without a duplicate error toast', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getKeyPair).toHaveBeenCalledTimes(1)
    expect(vmOf(wrapper).signatureLoaded).toBe(true)
    expect(vmOf(wrapper).signatureKeyPair).toBeNull()
    expect(ElMessage.error).not.toHaveBeenCalledWith('userProfile.loadSignatureFailed')

    wrapper.unmount()
  })

  it('saves the profile through the typed update contract', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).editForm.realName = 'Alice Updated'
    vmOf(wrapper).editForm.company = 'Green Corp Updated'

    await vmOf(wrapper).onSaveProfile()
    await flushPromises()

    expect(updateProfile).toHaveBeenCalledWith({
      realName: 'Alice Updated',
      email: 'alice@example.com',
      phone: '13800138000',
      company: 'Green Corp Updated',
      address: 'Suzhou',
    })
    expect(vmOf(wrapper).profile?.realName).toBe('Alice Updated')
    expect(ElMessage.success).toHaveBeenCalledWith('userProfile.updateSuccess')

    wrapper.unmount()
  })

  it('submits password changes and resets the form on success', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).pwdForm.oldPassword = 'old-password'
    vmOf(wrapper).pwdForm.newPassword = 'new-password'
    vmOf(wrapper).pwdForm.confirmPassword = 'new-password'

    await vmOf(wrapper).onChangePassword()
    await flushPromises()

    expect(changePassword).toHaveBeenCalledWith({
      oldPassword: 'old-password',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    })
    expect(vmOf(wrapper).pwdForm).toEqual({
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
    })
    expect(ElMessage.success).toHaveBeenCalledWith('userProfile.passwordChangeSuccess')

    wrapper.unmount()
  })

  it('shows an error when profile loading fails', async () => {
    vi.mocked(getProfile).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('userProfile.loadUserFailed')
    wrapper.unmount()
  })
})
