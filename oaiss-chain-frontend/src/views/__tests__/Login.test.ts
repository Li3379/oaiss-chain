import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

vi.mock('../../api/auth', () => ({
  login: vi.fn(),
}))

vi.mock('../../api/captcha', () => ({
  generateCaptcha: vi.fn(),
}))

const mockRouterReplace = vi.fn()
const mockRouteQuery: Record<string, unknown> = {}

vi.mock('vue-router', () => ({
  useRoute: vi.fn(() => ({ query: mockRouteQuery })),
  useRouter: vi.fn(() => ({ replace: mockRouterReplace })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import Login from '../Login.vue'
import { login } from '../../api/auth'
import { generateCaptcha } from '../../api/captcha'
import { ElMessage } from 'element-plus'

function createJwt(
  payload: Record<string, unknown>,
  header: Record<string, unknown> = { alg: 'HS256' },
) {
  const encode = (value: Record<string, unknown>) =>
    Buffer.from(JSON.stringify(value)).toString('base64url')

  return `${encode(header)}.${encode(payload)}.signature`
}

const stubGlobal = {
  stubs: {
    'el-card': { template: '<div class="el-card"><slot /></div>' },
    'el-form': {
      template: '<form @submit.prevent><slot /></form>',
      methods: {
        validate() {
          return Promise.resolve(true)
        },
      },
    },
    'el-form-item': {
      template: '<div class="el-form-item"><slot /></div>',
      props: ['label', 'prop'],
    },
    'el-input': {
      template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
      props: ['modelValue', 'type', 'placeholder', 'showPassword', 'clearable'],
      emits: ['update:modelValue'],
    },
    'el-checkbox': {
      template:
        '<label><input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" /><slot /></label>',
      props: ['modelValue'],
      emits: ['update:modelValue'],
    },
    'el-button': {
      template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
      props: ['type', 'size', 'loading'],
      emits: ['click'],
    },
  },
}

function mountLogin() {
  return mount(Login, { global: stubGlobal })
}

async function flush() {
  await nextTick()
  await nextTick()
  await nextTick()
}

describe('Login.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
    vi.mocked(mockRouterReplace).mockReset()

    Object.keys(mockRouteQuery).forEach((key) => delete mockRouteQuery[key])

    vi.mocked(generateCaptcha).mockResolvedValue({
      captchaKey: 'test-key-123',
      captchaImage: 'data:image/png;base64,abc123',
      expiresIn: 300,
    })
  })

  describe('captcha loading', () => {
    it('calls generateCaptcha on mount', async () => {
      mountLogin()
      await flush()

      expect(generateCaptcha).toHaveBeenCalledTimes(1)
    })

    it('renders captcha image when loaded', async () => {
      const wrapper = mountLogin()
      await flush()

      expect(wrapper.find('.captcha-image').exists()).toBe(true)
    })

    it('shows a fallback placeholder when captcha loading fails', async () => {
      vi.mocked(generateCaptcha).mockRejectedValue(new Error('fail'))

      const wrapper = mountLogin()
      await flush()

      expect(wrapper.find('img.captcha-image').exists()).toBe(false)
      expect(wrapper.text()).toContain('点击加载')
      expect(ElMessage.error).toHaveBeenCalledWith('获取验证码失败')
    })
  })

  describe('form rendering', () => {
    it('renders account, password, and captcha inputs', () => {
      const wrapper = mountLogin()
      const inputs = wrapper.findAll('input')

      expect(inputs.length).toBeGreaterThanOrEqual(3)
    })

    it('renders remember password checkbox', () => {
      const wrapper = mountLogin()

      expect(wrapper.find('label').exists()).toBe(true)
    })

    it('renders submit button', () => {
      const wrapper = mountLogin()

      expect(wrapper.find('button').exists()).toBe(true)
    })
  })

  describe('login success', () => {
    async function submitForm(wrapper: ReturnType<typeof mountLogin>) {
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('testuser')
      await inputs[1].setValue('password123')
      await inputs[2].setValue('ABC1')

      await wrapper.find('button').trigger('click')
      await flush()
    }

    it('calls login API with form data', async () => {
      vi.mocked(login).mockResolvedValue({
        accessToken: createJwt({ sub: 'enterprise-user', roles: ['ENTERPRISE'], exp: 9999999999 }),
        refreshToken: 'refresh-token-456',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 11,
        username: 'testuser',
        userType: 1,
        realName: '企业用户',
      })

      const wrapper = mountLogin()
      await flush()
      await submitForm(wrapper)

      expect(login).toHaveBeenCalledWith({
        username: 'testuser',
        password: 'password123',
        captchaKey: 'test-key-123',
        captcha: 'ABC1',
      })
    })

    it('shows success message on login', async () => {
      vi.mocked(login).mockResolvedValue({
        accessToken: createJwt({ sub: 'enterprise-user', roles: ['ENTERPRISE'], exp: 9999999999 }),
        refreshToken: 'refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 12,
        username: 'user',
        userType: 1,
        realName: '测试用户',
      })

      const wrapper = mountLogin()
      await flush()
      await submitForm(wrapper)

      expect(ElMessage.success).toHaveBeenCalledWith('登录成功')
    })

    it('navigates to redirect path on success', async () => {
      mockRouteQuery.redirect = '/dashboard'

      vi.mocked(login).mockResolvedValue({
        accessToken: createJwt({ sub: 'reviewer-user', roles: ['REVIEWER'], exp: 9999999999 }),
        refreshToken: 'refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 13,
        username: 'reviewer',
        userType: 2,
        realName: '审核员',
      })

      const wrapper = mountLogin()
      await flush()
      await submitForm(wrapper)

      expect(mockRouterReplace).toHaveBeenCalledWith('/dashboard')
    })

    it('navigates to the role home path when no redirect is provided', async () => {
      vi.mocked(login).mockResolvedValue({
        accessToken: createJwt({ sub: 'reviewer-user', roles: ['REVIEWER'], exp: 9999999999 }),
        refreshToken: 'refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 14,
        username: 'reviewer',
        userType: 2,
        realName: '审核员',
      })

      const wrapper = mountLogin()
      await flush()
      await submitForm(wrapper)

      expect(mockRouterReplace).toHaveBeenCalledWith('/auditor/audit/list')
    })

    it('shows an error and stays on the login page when the token has no resolvable role', async () => {
      vi.mocked(login).mockResolvedValue({
        accessToken: createJwt({ sub: 'broken-user', userId: 7, exp: 9999999999 }),
        refreshToken: 'refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 7,
        username: 'broken-user',
        userType: 0,
        realName: '异常用户',
      })

      const wrapper = mountLogin()
      await flush()
      await submitForm(wrapper)

      expect(ElMessage.error).toHaveBeenCalledWith('登录状态异常，请重新登录')
      expect(mockRouterReplace).not.toHaveBeenCalled()
    })
  })

  describe('login failure', () => {
    it('refreshes captcha on login failure', async () => {
      vi.mocked(login).mockRejectedValue(new Error('Invalid credentials'))
      vi.mocked(generateCaptcha).mockResolvedValue({
        captchaKey: 'new-key',
        captchaImage: 'data:image/png;base64,xyz',
        expiresIn: 300,
      })

      const wrapper = mountLogin()
      await flush()
      vi.clearAllMocks()

      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('user')
      await inputs[1].setValue('wrong')
      await inputs[2].setValue('code')
      await wrapper.find('button').trigger('click')
      await flush()

      expect(generateCaptcha).toHaveBeenCalled()
    })

    it('clears captcha input on failure', async () => {
      vi.mocked(login).mockRejectedValue(new Error('fail'))

      const wrapper = mountLogin()
      await flush()

      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('user')
      await inputs[1].setValue('pass')
      await inputs[2].setValue('code')
      await wrapper.find('button').trigger('click')
      await flush()

      expect((wrapper.findAll('input')[2].element as HTMLInputElement).value).toBe('')
    })
  })

  describe('remember password', () => {
    it('saves the account to localStorage when rememberPassword is enabled', async () => {
      vi.mocked(login).mockResolvedValue({
        accessToken: createJwt({ sub: 'enterprise-user', roles: ['ENTERPRISE'], exp: 9999999999 }),
        refreshToken: 'refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 15,
        username: 'myuser',
        userType: 1,
        realName: '企业用户',
      })

      const wrapper = mountLogin()
      await flush()

      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('myuser')
      await inputs[1].setValue('mypass')
      await inputs[2].setValue('code')
      await wrapper.find('button').trigger('click')
      await flush()

      expect(JSON.parse(localStorage.getItem('carbon-admin-login-form') || 'null')).toEqual({
        account: 'myuser',
        rememberPassword: true,
      })
    })

    it('restores the account from localStorage on mount', async () => {
      localStorage.setItem(
        'carbon-admin-login-form',
        JSON.stringify({
          account: 'saved-user',
          rememberPassword: true,
        }),
      )

      const wrapper = mountLogin()
      await flush()

      expect((wrapper.findAll('input')[0].element as HTMLInputElement).value).toBe('saved-user')
    })
  })
})
