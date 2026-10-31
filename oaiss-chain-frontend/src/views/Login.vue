<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：公开门户 - 统一安全认证中心 (Login)
 * 【路由地址】：/login
 * 【访问权限】：公开访问 (已登录将根据角色重定向至 ROLE_HOME)
 * 【界面可见功能】：
 *   1. 左栏品牌叙事区：标题/正文 + 链上存证凭证卡 + 晨雾森林实拍配图
 *   2. 右栏登录卡：账号、密码、图形验证码（点击刷新）
 *   3. 记住账号（仅存账号，不存密码）与首次登录指引
 *   4. 四角色演示账号快捷填充
 *   5. 返回官网首页入口
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
import { LOGIN_ART } from '../config/penImagery'

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
const captchaInputRef = ref()

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

/* ---------- 演示角色快捷填充与官网返回（不改认证逻辑） ---------- */
const demoRoles = computed(() => [
  { key: 'enterprise', label: t('login.roleEnterprise'), account: 'enterprise001', icon: OfficeBuilding },
  { key: 'reviewer', label: t('login.roleReviewer'), account: 'reviewer001', icon: CircleCheck },
  { key: 'thirdParty', label: t('login.roleThirdParty'), account: 'thirdparty001', icon: Monitor },
  { key: 'admin', label: t('login.roleAdmin'), account: 'admin', icon: SetUp },
])

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
    <!-- 左栏：品牌叙事 + 存证凭证 + 实拍配图（pen Aside 760px / 浅色 shell） -->
    <aside class="brand-panel">
      <header class="brand-mark">
        <span class="brand-mark__badge" aria-hidden="true">
          <el-icon :size="17"><Link /></el-icon>
        </span>
        <span class="brand-mark__col">
          <strong>{{ t('login.brandName') }}</strong>
          <em>{{ t('login.brandPlatform') }}</em>
        </span>
      </header>

      <div class="brand-mid">
        <h1 class="brand-headline">{{ t('login.brandHeadline') }}</h1>
        <p class="brand-narrative">{{ t('login.brandNarrative') }}</p>

        <!-- 链上存证凭证：登录态未知时如实呈现「待同步」，不编造链上数据 -->
        <section class="proof-card" :aria-label="t('login.proofTitle')">
          <header class="proof-card__head">
            <span class="proof-card__caps">{{ t('login.proofTitle') }}</span>
            <span class="proof-card__stamp">{{ t('login.proofBlockPending') }}</span>
          </header>
          <div class="proof-card__body">
            <span class="proof-card__key">{{ t('login.proofHashLabel') }}</span>
            <span class="proof-card__value">{{ t('login.proofHashPending') }}</span>
          </div>
        </section>
      </div>

      <figure class="brand-art">
        <img :src="LOGIN_ART" :alt="t('login.brandHeadline')" loading="eager" decoding="async" />
      </figure>

      <footer class="brand-foot">
        <span class="brand-foot__copyright">{{ t('login.brandCopyright') }}</span>
        <span class="brand-foot__version">{{ t('login.brandVersion') }}</span>
      </footer>
    </aside>

    <!-- 右栏：392px 登录卡 -->
    <main class="form-panel">
      <router-link to="/official-home" class="back-home">
        <el-icon :size="14"><HomeFilled /></el-icon>
        <span>{{ t('login.backToHome') }}</span>
      </router-link>

      <div class="form-card">
        <h1 class="form-card__title">{{ t('login.formTitle') }}</h1>
        <p class="form-card__subtitle">{{ t('login.formSubtitle') }}</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="formRules"
          label-position="top"
          @submit.prevent="onSubmit"
          aria-label="login form"
          scroll-to-error
        >
          <el-form-item :label="t('login.account')" prop="account">
            <el-input v-model="form.account" :placeholder="t('login.enterAccount')" clearable />
          </el-form-item>

          <el-form-item :label="t('login.password')" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              :placeholder="t('login.enterPassword')"
            />
          </el-form-item>

          <el-form-item :label="t('login.captcha')" prop="captchaInput" class="captcha-item">
            <div class="captcha-row">
              <el-input
                ref="captchaInputRef"
                v-model="form.captchaInput"
                :placeholder="t('login.enterCaptcha')"
              />
              <img
                v-if="captchaImage"
                :src="captchaImage"
                class="captcha-image"
                :alt="t('login.captcha')"
                @click="refreshCaptcha"
              />
              <div
                v-else
                class="captcha-image is-placeholder"
                @click="refreshCaptcha"
              >
                {{ t('login.captchaPlaceholder') }}
              </div>
            </div>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="form.rememberPassword">{{ t('login.rememberMe') }}</el-checkbox>
          </div>

          <button type="button" class="submit-btn" :disabled="loading" @click="onSubmit">
            <span v-if="loading" class="submit-btn__spinner" aria-hidden="true" />
            <span>{{ t('login.submitEnter') }}</span>
          </button>
        </el-form>

        <!-- 首次登录指引（pen Hint：noteBg 底 + 发丝线） -->
        <p class="login-hint">{{ t('login.loginHint') }}</p>

        <div class="demo-block">
          <p class="demo-label">{{ t('login.demoAccounts') }}</p>
          <div class="demo-grid">
            <button
              v-for="role in demoRoles"
              :key="role.key"
              type="button"
              class="demo-chip"
              @click="onDemoFill(role.account)"
            >
              <el-icon :size="14"><component :is="role.icon" /></el-icon>
              <span>{{ role.label }}</span>
            </button>
          </div>
        </div>
      </div>

      <button type="button" class="back-home--mobile" @click="onBackToHome">
        {{ t('login.backToHome') }}
      </button>
    </main>
  </div>
</template>

<style scoped>
/* 设计源：1440×900，左 Aside 760 / 右 FormCol 自适应，整页底色 #F8F8F8 */
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 760fr) minmax(0, 680fr);
  background: var(--pen-ground);
}

/* ---------- 左栏：浅色 shell，纵向 44/52 内边距，内容 space-between ---------- */
.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--space-6);
  padding: 52px 44px 44px;
  background: var(--pen-shell);
  border-right: 1px solid var(--pen-border);
  min-height: 100vh;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 11px;
  flex-shrink: 0;
}

.brand-mark__badge {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-control);
  display: grid;
  place-items: center;
  background: var(--pen-brand-mark);
  color: var(--color-on-inverse);
  flex-shrink: 0;
}

.brand-mark__col {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.brand-mark__col strong {
  color: var(--pen-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.brand-mark__col em {
  color: var(--pen-faint);
  font-family: var(--font-mono);
  font-size: 8.5px;
  font-style: normal;
  letter-spacing: 0.08em;
}

.brand-mid {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 560px;
}

.brand-headline {
  margin: 0;
  color: var(--pen-ink);
  font-size: 32px;
  font-weight: 600;
  line-height: 1.28;
  letter-spacing: -0.01em;
}

.brand-narrative {
  margin: 0;
  max-width: 520px;
  color: var(--pen-muted);
  font-size: var(--fs-body);
  line-height: 1.85;
}

/* 存证凭证卡：sunken 底、2px 圆角、头 38px、体 13/14 内边距 */
.proof-card {
  max-width: 520px;
  background: var(--pen-sunken);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.proof-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  height: 38px;
  padding: 0 14px;
  border-bottom: 1px solid var(--pen-border);
}

.proof-card__caps {
  color: var(--pen-faint);
  font-size: 9.5px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.proof-card__stamp {
  color: var(--pen-faint);
  font-family: var(--font-mono);
  font-size: 10.5px;
}

.proof-card__body {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 13px 14px;
}

.proof-card__key {
  color: var(--pen-faint);
  font-size: 10px;
}

.proof-card__value {
  color: var(--pen-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.5;
  word-break: break-all;
}

/* 实拍配图：与 Aside 同宽，190px 高，cover 填充，2px 圆角 */
.brand-art {
  margin: 0;
  width: 100%;
  height: 190px;
  border-radius: var(--radius-card);
  overflow: hidden;
  flex-shrink: 0;
}

.brand-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.brand-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-shrink: 0;
}

.brand-foot__copyright,
.brand-foot__version {
  color: var(--pen-faint);
  font-size: 11px;
}

.brand-foot__version {
  font-family: var(--font-mono);
  font-size: 10.5px;
}

/* ---------- 右栏：392px 卡片垂直居中 ---------- */
.form-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 140px;
  background: var(--pen-ground);
}

.form-card {
  width: 392px;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.form-card__title {
  margin: 0;
  color: var(--pen-ink);
  font-size: 26px;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: -0.01em;
}

.form-card__subtitle {
  margin: -8px 0 0;
  color: var(--pen-muted);
  font-size: var(--fs-sub);
  line-height: 1.6;
}

.form-card :deep(.el-form-item) {
  margin-bottom: var(--space-3);
}

.form-card :deep(.el-form-item__label) {
  color: var(--pen-faint);
  font-size: 10.5px;
  font-weight: 500;
  line-height: 1.4;
  padding-bottom: 6px;
}

.form-card :deep(.el-input__wrapper) {
  height: 36px;
  padding: 0 11px;
  background: var(--pen-sunken);
  border-radius: var(--radius-control);
}

.captcha-item :deep(.el-form-item__content) {
  width: 100%;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px;
  gap: 10px;
  width: 100%;
}

/* 输错验证码后点击图片刷新：保持与设计稿 112×36 的框体一致 */
.captcha-image {
  width: 112px;
  height: 36px;
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-control);
  background: var(--pen-sunken);
  cursor: pointer;
  object-fit: cover;
  display: block;
}

.captcha-image.is-placeholder {
  display: grid;
  place-items: center;
  color: var(--pen-faint);
  font-size: 10.5px;
}

.captcha-image.is-placeholder:hover {
  color: var(--pen-ink);
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.form-options :deep(.el-checkbox__label) {
  color: var(--pen-muted);
  font-size: var(--fs-sub);
}

/* 主操作：42px 高、墨色填充、2px 圆角 */
.submit-btn {
  appearance: none;
  width: 100%;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius-control);
  background: var(--pen-action);
  color: var(--color-on-inverse);
  font-family: inherit;
  font-size: var(--fs-body);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--motion-ease);
}

.submit-btn:hover:not(:disabled) {
  background: var(--pen-action-hover);
}

.submit-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.submit-btn__spinner {
  width: 12px;
  height: 12px;
  border: 1.5px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: login-spin 0.7s linear infinite;
}

@keyframes login-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 首登指引：noteBg 底 + noteBd 边 */
.login-hint {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--pen-note-bd);
  border-radius: var(--radius-card);
  background: var(--pen-note-bg);
  color: var(--pen-muted);
  font-size: 11.5px;
  line-height: 1.7;
}

/* 演示账号快捷填充（既有功能，按 pen 语言重绘为描边按钮） */
.demo-block {
  padding-top: var(--space-3);
  border-top: 1px solid var(--pen-border);
}

.demo-label {
  margin: 0 0 var(--space-2);
  color: var(--pen-faint);
  font-size: 10.5px;
}

.demo-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.demo-chip {
  appearance: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 52px;
  padding: 8px 4px;
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-control);
  background: var(--pen-surface);
  color: var(--pen-muted);
  font-family: inherit;
  font-size: 10.5px;
  line-height: 1.2;
  cursor: pointer;
  transition: border-color var(--motion-fast) var(--motion-ease),
    background-color var(--motion-fast) var(--motion-ease),
    color var(--motion-fast) var(--motion-ease);
}

.demo-chip:hover {
  background: var(--pen-raised);
  color: var(--pen-ink);
}

.demo-chip:focus-visible,
.captcha-image:focus-visible,
.submit-btn:focus-visible,
.back-home:focus-visible {
  outline: 2px solid var(--pen-ink);
  outline-offset: 2px;
}

.back-home {
  position: absolute;
  top: 22px;
  right: 26px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--pen-faint);
  font-size: var(--fs-sub);
  text-decoration: none;
  transition: color var(--motion-fast) var(--motion-ease);
}

.back-home:hover {
  color: var(--pen-ink);
}

.back-home--mobile {
  display: none;
}

/* ---------- 窄屏：左栏隐藏，卡片铺满 ---------- */
@media (max-width: 1080px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .form-panel {
    padding: 48px 24px;
    min-height: 100vh;
  }

  .back-home {
    display: none;
  }

  .back-home--mobile {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin-top: var(--space-5);
    appearance: none;
    border: none;
    background: transparent;
    color: var(--pen-faint);
    font-family: inherit;
    font-size: var(--fs-sub);
    cursor: pointer;
  }
}

@media (max-width: 480px) {
  .demo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .form-card__title {
    font-size: 22px;
  }
}
</style>