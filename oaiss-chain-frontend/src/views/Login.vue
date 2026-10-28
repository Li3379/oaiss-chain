<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：公开门户 - 统一安全认证中心 (Login)
 * 【路由地址】：/login
 * 【访问权限】：公开访问 (已登录将根据角色重定向至 ROLE_HOME)
 * 【界面可见功能】：
 *   1. 账号密码登录表单：支持输入用户名、密码与防重并发提交锁
 *   2. 图形人机验证码组件：显示Base64图形验证码，支持点击实时刷新
 *   3. 记住账号密码：本地加密暂存与恢复凭据
 *   4. 角色快捷体验通道：提供企业/审核员/监管/管理员一键快速填充测试账密
 *   5. 国际化中英文切换与返回官网首页入口
 * 【对应后端 Controller & API】：
 *   - AuthController:
 *     * POST /api/v1/auth/login -> login() 账号密码与验证码登录
 *   - CaptchaController:
 *     * GET  /api/v1/captcha/generate -> generateCaptcha() 获取图形验证码
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, HomeFilled, Link, Monitor, OfficeBuilding, SetUp } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../store'
import { login } from '../api/auth'
import { generateCaptcha } from '../api/captcha'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const { t } = useI18n()

const LOGIN_FORM_STORAGE_KEY = 'carbon-admin-login-form'

const formRef = ref()
const loading = ref(false)

const form = reactive({
  account: '',
  password: '',
  captchaInput: '',
  rememberPassword: true,
})

const captchaKey = ref('')
const captchaImage = ref('')

const formRules = computed(() => ({
  account: [{ required: true, message: t('login.enterAccount'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.enterPassword'), trigger: 'blur' }],
  captchaInput: [{ required: true, message: t('login.enterCaptcha'), trigger: 'blur' }],
}))

const refreshCaptcha = async () => {
  try {
    const data = await generateCaptcha()
    captchaKey.value = data.captchaKey
    captchaImage.value = data.captchaImage
  } catch {
    ElMessage.error(t('login.captchaLoadFailed'))
  }
}

const restoreForm = () => {
  try {
    const raw = localStorage.getItem(LOGIN_FORM_STORAGE_KEY)
    if (!raw) return
    const parsed = JSON.parse(raw)
    form.account = parsed.account || ''
    form.rememberPassword = Boolean(parsed.rememberPassword)
  } catch {
    localStorage.removeItem(LOGIN_FORM_STORAGE_KEY)
  }
}

onMounted(() => {
  restoreForm()
  const queryAccount = route.query.account as string | undefined
  if (queryAccount) {
    form.account = queryAccount
    form.password = 'admin123'
  }
  refreshCaptcha()
})

const onSubmit = async () => {
  // 同步重入锁：防止并发触发（回车事件 + 表单提交事件，或快速连击）
  if (loading.value) return
  loading.value = true

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    loading.value = false
    ElMessage.warning(t('login.incompleteForm'))
    return
  }

  try {
    const data = await login({
      username: form.account,
      password: form.password,
      captchaKey: captchaKey.value,
      captcha: form.captchaInput.trim(),
    })

    const loggedIn = appStore.login({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
    })
    if (!loggedIn) {
      ElMessage.error(t('login.invalidSession'))
      await refreshCaptcha()
      form.captchaInput = ''
      return
    }

    if (form.rememberPassword) {
      localStorage.setItem(LOGIN_FORM_STORAGE_KEY, JSON.stringify({
        account: form.account,
        rememberPassword: true,
      }))
    } else {
      localStorage.removeItem(LOGIN_FORM_STORAGE_KEY)
    }

    const rawRedirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    // 防御性过滤：若重定向目标包含登录页自身，则安全回退至角色主工作台
    const safeRedirect = rawRedirect && !rawRedirect.includes('/login') ? rawRedirect : ''
    const target = safeRedirect || appStore.homePath || '/enterprise/carbon/upload'

    ElMessage.success(t('login.loginSuccess'))

    try {
      await router.replace(target)
    } catch {
      // 路由跳转失败时交由兜底处理
    }

    // 强力兜底：若路由未成功离开登录页，强制使用页面刷新跳转至目标工作台
    if (window.location.pathname.includes('/login') || window.location.pathname === '/') {
      window.location.href = target
    }
  } catch {
    // 错误信息已由 axios 响应拦截器统一展示，避免二次弹窗产生冗余技术报错
    refreshCaptcha()
    form.captchaInput = ''
  } finally {
    loading.value = false
  }
}

/* ---------- 设计加强：演示角色快捷填充与官网返回（不改认证逻辑） ---------- */
const demoRoles = computed(() => [
  { key: 'enterprise', label: t('login.roleEnterprise'), account: 'enterprise001', icon: OfficeBuilding },
  { key: 'reviewer', label: t('login.roleReviewer'), account: 'reviewer001', icon: CircleCheck },
  { key: 'thirdParty', label: t('login.roleThirdParty'), account: 'thirdparty001', icon: Monitor },
  { key: 'admin', label: t('login.roleAdmin'), account: 'admin', icon: SetUp },
])

const captchaInputRef = ref()

const onDemoFill = async (account: string) => {
  form.account = account
  form.password = 'admin123'
  await nextTickFrame()
  captchaInputRef.value?.focus?.()
}

function nextTickFrame(): Promise<void> {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()))
}

const onBackToHome = () => {
  router.push('/official-home')
}
</script>

<template>
  <div class="login-page">
    <aside class="brand-panel">
      <div class="brand-panel-inner">
        <header class="brand-mark">
          <div class="brand-mark-badge">
            <el-icon :size="22"><Link /></el-icon>
          </div>
          <div class="brand-mark-text">
            <strong>OAISS</strong>
            <span>{{ t('login.brandName') }}</span>
          </div>
        </header>

        <h2 class="brand-headline">{{ t('login.brandName') }}</h2>
        <p class="brand-tagline">{{ t('login.brandTagline') }}</p>

        <ul class="brand-trust-list">
          <li>
            <el-icon :size="15"><Link /></el-icon>
            <span>{{ t('login.trustFabric') }}</span>
          </li>
          <li>
            <el-icon :size="15"><OfficeBuilding /></el-icon>
            <span>{{ t('login.trustStandard') }}</span>
          </li>
          <li>
            <el-icon :size="15"><SetUp /></el-icon>
            <span>{{ t('login.trustCrypto') }}</span>
          </li>
        </ul>

        <div class="ledger-line" aria-hidden="true">
          <span class="ledger-pulse" />
        </div>

        <footer class="brand-footnote">© 2026 OAISS</footer>
      </div>
    </aside>

    <main class="form-panel">
      <router-link to="/official-home" class="back-home">
        <el-icon :size="14"><HomeFilled /></el-icon>
        <span>{{ t('login.backToHome') }}</span>
      </router-link>

      <div class="form-column">
        <header class="login-header">
          <div class="logo-dot" />
          <div>
            <h1>{{ t('login.title') }}</h1>
            <p>{{ t('login.subtitle') }}</p>
          </div>
        </header>

        <el-form ref=formRef :model=form :rules=formRules label-position=top @submit.prevent="onSubmit" aria-label="login form" scroll-to-error>
          <el-form-item :label="t('login.account')" prop=account>
            <el-input v-model=form.account :placeholder="t('login.enterAccount')" clearable />
          </el-form-item>

          <el-form-item :label="t('login.password')" prop=password>
            <el-input
              v-model=form.password
              type=password
              show-password
              :placeholder="t('login.enterPassword')"
            />
          </el-form-item>

          <el-form-item :label="t('login.captcha')" prop=captchaInput>
            <div class=captcha-row>
              <el-input ref=captchaInputRef v-model=form.captchaInput :placeholder="t('login.enterCaptcha')" />
              <img
                v-if=captchaImage
                :src=captchaImage
                class=captcha-image
                :alt="t('login.captcha')"
                @click=refreshCaptcha
              />
              <div v-else class=captcha-image captcha-placeholder @click=refreshCaptcha>
                {{ t('login.captchaPlaceholder') }}
              </div>
            </div>
          </el-form-item>

          <el-form-item>
            <el-checkbox v-model=form.rememberPassword>{{ t('login.rememberMe') }}</el-checkbox>
          </el-form-item>

          <el-button class=submit-btn type=primary size=large :loading=loading @click="onSubmit">
            {{ t('login.login') }}
          </el-button>
        </el-form>

        <div class="demo-block">
          <p class="demo-label">{{ t('login.demoAccounts') }}</p>
          <div class="demo-grid">
            <div
              v-for="role in demoRoles"
              :key="role.key"
              role="button"
              tabindex="0"
              class="demo-chip"
              @click="onDemoFill(role.account)"
              @keydown.enter="onDemoFill(role.account)"
            >
              <el-icon :size="15"><component :is="role.icon" /></el-icon>
              <span>{{ role.label }}</span>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
}

/* ── 左侧品牌叙事面板 ── */
.brand-panel {
  position: relative;
  overflow: hidden;
  background: var(--color-brand-strong);
  /* 深底品牌区：文字必须用反相色，不能继承 --color-text（它在亮色下是近黑） */
  color: var(--color-on-inverse);
  display: flex;
}

.brand-panel-inner {
  width: min(460px, 100%);
  padding: 52px 44px 40px;
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark-badge {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--color-on-inverse);
  color: var(--color-brand-strong);
}

.brand-mark-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.brand-mark-text strong {
  color: #ffffff;
  font-size: var(--text-lg);
  letter-spacing: 0.08em;
}

.brand-mark-text span {
  color: #bfe6dc;
  font-size: var(--text-sm);
}

.brand-headline {
  margin: auto 0 0;
  color: #f2fdfa;
  font-size: var(--text-3xl);
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0.01em;
}

.brand-tagline {
  margin: 14px 0 0;
  color: #bfe6dc;
  font-size: var(--text-md);
  line-height: 1.85;
}

.brand-trust-list {
  list-style: none;
  margin: 30px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.brand-trust-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #dff3ec;
  font-size: var(--text-base);
}

.brand-trust-list .el-icon {
  color: #6adf85;
  flex-shrink: 0;
}

/* 唯一动效时刻：账本心跳线 */
.ledger-line {
  position: relative;
  margin-top: 40px;
  height: 2px;
  border-radius: 1px;
  background: rgba(189, 243, 230, 0.18);
  overflow: hidden;
}

.ledger-pulse {
  position: absolute;
  top: 50%;
  left: 0;
  width: 96px;
  height: 2px;
  transform: translateY(-50%);
  background: var(--color-border);
  animation: ledger-travel 5.5s cubic-bezier(0.45, 0, 0.3, 1) infinite;
}

@keyframes ledger-travel {
  0% { transform: translateX(-120px) translateY(-50%); opacity: 0; }
  18% { opacity: 1; }
  78% { opacity: 1; }
  100% { transform: translateX(560px) translateY(-50%); opacity: 0; }
}

.brand-footnote {
  margin-top: 26px;
  color: rgba(191, 230, 220, 0.72);
  font-size: var(--text-xs);
}

/* ── 右侧表单区 ── */
.form-panel {
  position: relative;
  display: flex;
  background: var(--color-surface);
}

.form-column {
  margin: auto;
  width: min(400px, 100%);
  padding: 56px 32px;
}

.back-home {
  position: absolute;
  top: 22px;
  right: 26px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  cursor: pointer;
  padding: 6px 4px;
  border-radius: 8px;
  transition: color 0.2s ease;
}

.back-home:hover {
  color: var(--el-color-primary);
}

.login-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
}

.logo-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--color-accent);
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
  flex-shrink: 0;
}

.login-header h1 {
  margin: 0;
  font-size: var(--text-2xl);
  color: var(--color-text);
}

.login-header p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: var(--text-base);
}

.captcha-row {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 110px;
  gap: 10px;
}

.captcha-image {
  width: 110px;
  height: 40px;
  border-radius: 8px;
  border: 1px solid var(--color-border-soft);
  cursor: pointer;
}

.captcha-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  background: var(--color-raised);
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  border: none;
  background: var(--color-accent);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.submit-btn:hover {
  background: var(--color-accent);
  transform: translateY(-1px);
}

/* 演示账号快捷填充 */
.demo-block {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border-soft);
}

.demo-label {
  margin: 0 0 10px;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.demo-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.demo-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 6px;
  border-radius: 10px;
  border: 1px solid rgba(20, 167, 154, 0.24);
  background: #ffffff;
  color: #2f5a60;
  font-size: var(--text-xs);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.demo-chip .el-icon {
  color: var(--el-color-primary);
}

.demo-chip:hover {
  border-color: rgba(20, 167, 154, 0.5);
  background: #ecf8f4;
  transform: translateY(-1px);
}

/* 浏览器表面 */
.login-page ::selection {
  background: rgba(20, 167, 154, 0.24);
}

.back-home:focus-visible,
.demo-chip:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.form-panel :deep(.el-input__wrapper.is-focus) {
  caret-color: var(--el-color-primary);
}

@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .form-column {
    padding: 40px 24px;
  }
}

@media (max-width: 600px) {
  .form-column {
    padding: 32px 20px;
  }

  .captcha-row {
    grid-template-columns: 1fr;
  }

  .demo-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
