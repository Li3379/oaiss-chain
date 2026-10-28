<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 个人中心与安全证书管理 (User Profile & Security)
 * 【路由地址】：/enterprise/user/profile
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 个人基本资料卡片：查看并修改真实姓名、联系电话、绑定邮箱、通讯地址
 *   2. 账号安全设置面板：修改用户登录密码（原密码校验与强度规则）
 *   3. RSA 数字签名证书管理：生成密钥对、公钥指纹展示、密钥吊销/作废操作
 *   4. 企业市场准入证书状态卡片：查看管理员签发的准入资质与有效期
 *   5. 用户会话在线停留时长统计与动态心跳上报
 * 【对应后端 Controller & API】：
 *   - UserController:
 *     * GET  /api/v1/user/profile              -> getProfile() 获取个人资料
 *     * PUT  /api/v1/user/profile              -> updateProfile() 更新个人信息
 *     * PUT  /api/v1/user/password             -> changePassword() 修改登录密码
 *     * POST /api/v1/user/stay-time            -> reportStayTime() 在线时长上报
 *   - DigitalSignatureController:
 *     * GET    /api/v1/signature/keypair          -> getKeyPair() 查询密钥状态
 *     * POST   /api/v1/signature/keypair/generate -> generateKeyPair() 生成密钥对
 *     * DELETE /api/v1/signature/keypair          -> deleteKeyPair() 吊销密钥
 *   - EnterpriseController:
 *     * GET    /api/v1/enterprise/admission/my    -> getMyEnterpriseAdmission() 准入资质
 * ==============================================================================
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { changePassword, getProfile, reportStayTime, updateProfile } from '../../api/user'
import { getMyEnterpriseAdmission } from '../../api/enterprise'
import { deleteKeyPair, generateKeyPair, getKeyPair } from '../../api/signature'
import PageContainer from '../../components/PageContainer.vue'
import PageHint from '../../components/PageHint.vue'
import TermTip from '../../components/TermTip.vue'
import type {
  EnterpriseAdmissionResponse,
  PasswordChangeRequest,
  RsaKeyPairResponse,
  UserInfoResponse,
  UserProfileUpdateRequest,
} from '../../types'
import { getAccessToken } from '../../utils/auth'
import { formatDateTime } from '../../utils/format'

const { t, locale } = useI18n()
const RSA_KEY_REVOKED_CODE = 5015

interface ProfileEditForm {
  realName: string
  email: string
  phone: string
  company: string
  address: string
}

type PasswordForm = PasswordChangeRequest

const activeTab = ref('info')
const profileLoading = ref(false)
const profile = ref<UserInfoResponse | null>(null)

const pwdFormRef = ref<FormInstance>()
const pwdLoading = ref(false)
const pwdForm = ref<PasswordForm>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules: FormRules<PasswordForm> = {
  oldPassword: [{ required: true, message: t('userProfile.enterCurrentPassword'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('userProfile.enterNewPassword'), trigger: 'blur' },
    { min: 6, max: 20, message: t('userProfile.passwordLength'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: t('userProfile.confirmNewPassword'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== pwdForm.value.newPassword) {
          callback(new Error(t('userProfile.passwordMismatch')))
          return
        }
        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
}

watch(() => pwdForm.value.newPassword, () => {
  if (pwdForm.value.confirmPassword) {
    pwdFormRef.value?.validateField('confirmPassword')
  }
})

const editFormRef = ref<FormInstance>()
const editLoading = ref(false)
const editForm = ref<ProfileEditForm>({
  realName: '',
  email: '',
  phone: '',
  company: '',
  address: '',
})

const editRules: FormRules<ProfileEditForm> = {
  realName: [{ required: true, message: t('userProfile.enterRealName'), trigger: 'blur' }],
  email: [{ type: 'email', message: t('userProfile.invalidEmail'), trigger: 'blur' }],
}

function applyProfileToForm(result: UserInfoResponse) {
  editForm.value = {
    realName: result.realName || '',
    email: result.email || '',
    phone: result.phone || '',
    company: result.company || '',
    address: result.address || '',
  }
}

const loadProfile = async () => {
  try {
    profileLoading.value = true
    const result = await getProfile()
    profile.value = result
    applyProfileToForm(result)
  } catch {
    ElMessage.error(t('userProfile.loadUserFailed'))
  } finally {
    profileLoading.value = false
  }
}

const stayDurationText = computed(() => {
  const total = profile.value?.totalStaySeconds ?? 0
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return t('userProfile.staySecondsUnit', { hours: h, minutes: m, seconds: s })
})

const onSaveProfile = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    editLoading.value = true
    const payload: UserProfileUpdateRequest = { ...editForm.value }
    const result = await updateProfile(payload)
    profile.value = result
    applyProfileToForm(result)
    ElMessage.success(t('userProfile.updateSuccess'))
  } catch {
    ElMessage.error(t('userProfile.updateFailed'))
  } finally {
    editLoading.value = false
  }
}

const onChangePassword = async () => {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    pwdLoading.value = true
    await changePassword({
      oldPassword: pwdForm.value.oldPassword,
      newPassword: pwdForm.value.newPassword,
      confirmPassword: pwdForm.value.confirmPassword,
    })
    ElMessage.success(t('userProfile.passwordChangeSuccess'))
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch {
    ElMessage.error(t('userProfile.passwordChangeFailed'))
  } finally {
    pwdLoading.value = false
  }
}

const admissionStatus = ref<EnterpriseAdmissionResponse | null>(null)
const admissionLoading = ref(false)
const signatureLoading = ref(false)
const signatureActionLoading = ref(false)
const signatureLoaded = ref(false)
const signatureKeyPair = ref<RsaKeyPairResponse | null>(null)

const fetchAdmissionStatus = async () => {
  admissionLoading.value = true
  try {
    const list = await getMyEnterpriseAdmission()
    admissionStatus.value = list[0] || null
  } catch {
    admissionStatus.value = null
  } finally {
    admissionLoading.value = false
  }
}

const admissionStatusType = computed(() => {
  if (!admissionStatus.value) return 'info'
  return admissionStatus.value.status === 1 ? 'success' : 'danger'
})

const admissionStatusText = computed(() => {
  if (!admissionStatus.value) return t('certificateManage.notIssued')
  return admissionStatus.value.status === 1 ? t('certificateManage.active') : t('certificateManage.revoked')
})

const signatureStatusType = computed(() => {
  if (!signatureLoaded.value) return 'info'
  if (!signatureKeyPair.value) return 'warning'
  return signatureKeyPair.value.keyStatus === 1 ? 'success' : 'danger'
})

const signatureStatusText = computed(() => {
  if (!signatureLoaded.value) return t('common.loading')
  if (!signatureKeyPair.value) return t('userProfile.signatureNotGenerated')
  if (locale.value === 'en-US') {
    if (signatureKeyPair.value.keyStatus === 1) return t('userProfile.signatureStatusActive')
    if (signatureKeyPair.value.keyStatus === 0) return t('userProfile.signatureStatusRevoked')
    if (signatureKeyPair.value.keyStatus === 2) return t('userProfile.signatureStatusExpired')
    return t('userProfile.signatureReady')
  }
  return signatureKeyPair.value.keyStatusText || t('userProfile.signatureReady')
})

const signaturePublicKeyPreview = computed(() => {
  const publicKey = signatureKeyPair.value?.publicKey?.trim()
  if (!publicKey) return ''
  if (publicKey.length <= 120) return publicKey
  return `${publicKey.slice(0, 72)}...${publicKey.slice(-36)}`
})

const signatureKeyVersionText = computed(() => {
  if (signatureKeyPair.value?.keyVersion === undefined || signatureKeyPair.value?.keyVersion === null) {
    return '-'
  }
  return `v${signatureKeyPair.value.keyVersion}`
})

const signatureKeyUsageText = computed(() => {
  if (!signatureKeyPair.value?.keyUsage) return '-'
  return String(signatureKeyPair.value.keyUsage)
})

async function fetchSignatureKeyPair(): Promise<RsaKeyPairResponse | null> {
  const token = getAccessToken()
  if (!token) return null

  try {
    return await getKeyPair({ suppressErrorMessage: true })
  } catch (error) {
    const businessCode = typeof error === 'object' && error
      ? (error as { businessCode?: unknown; response?: { data?: { code?: unknown } } }).businessCode
        ?? (error as { response?: { data?: { code?: unknown } } }).response?.data?.code
      : null
    if (businessCode === RSA_KEY_REVOKED_CODE) {
      return null
    }
    throw error
  }
}

const loadSignatureKeyPair = async () => {
  signatureLoading.value = true
  try {
    signatureKeyPair.value = await fetchSignatureKeyPair()
    signatureLoaded.value = true
  } catch {
    signatureLoaded.value = true
    signatureKeyPair.value = null
    ElMessage.error(t('userProfile.loadSignatureFailed'))
  } finally {
    signatureLoading.value = false
  }
}

const onGenerateKeyPair = async () => {
  try {
    signatureActionLoading.value = true
    await generateKeyPair()
    ElMessage.success(t('userProfile.generateKeyPairSuccess'))
    await loadSignatureKeyPair()
  } catch {
    ElMessage.error(t('userProfile.generateKeyPairFailed'))
  } finally {
    signatureActionLoading.value = false
  }
}

const onDeleteKeyPair = async () => {
  try {
    await ElMessageBox.confirm(
      t('userProfile.deleteKeyPairConfirm'),
      t('userProfile.deleteKeyPairTitle'),
      {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      },
    )

    signatureActionLoading.value = true
    await deleteKeyPair()
    signatureKeyPair.value = null
    signatureLoaded.value = true
    ElMessage.success(t('userProfile.deleteKeyPairSuccess'))
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('userProfile.deleteKeyPairFailed'))
    }
  } finally {
    signatureActionLoading.value = false
  }
}

let stayInterval: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  loadProfile()
  fetchAdmissionStatus()
  loadSignatureKeyPair()

  // 每一分钟上报一次停留时间 (60秒)
  stayInterval = setInterval(() => {
    reportStayTime(60).catch(() => {})
  }, 60000)
})

onUnmounted(() => {
  if (stayInterval) {
    clearInterval(stayInterval)
    stayInterval = null
  }
})
</script>

<template>
  <PageContainer :title="t('userProfile.title')" :description="t('userProfile.description')">
    <PageHint :text="t('pageHints.userProfile')" />
    <section class="profile-page">
      <el-card class="section-card" shadow="never">
        <el-tabs v-model="activeTab">
          <el-tab-pane :label="t('userProfile.basicInfo')" name="info">
            <div v-loading="profileLoading" style="padding: 20px 0">
              <el-form
                ref="editFormRef"
                :model="editForm"
                :rules="editRules"
                label-width="100px"
                class="profile-form"
  scroll-to-error>
                <el-form-item :label="t('userProfile.username')">
                  <el-input :model-value="profile?.username" disabled />
                </el-form-item>
                <el-form-item :label="t('userProfile.role')">
                  <el-input :model-value="profile?.userTypeDesc" disabled />
                </el-form-item>
                <el-form-item :label="t('userProfile.realName')" prop="realName">
                  <el-input v-model="editForm.realName" :placeholder="t('userProfile.enterRealName')" />
                </el-form-item>
                <el-form-item :label="t('userProfile.email')" prop="email">
                  <el-input v-model="editForm.email" :placeholder="t('userProfile.enterEmail')" />
                </el-form-item>
                <el-form-item :label="t('userProfile.phone')">
                  <el-input v-model="editForm.phone" :placeholder="t('userProfile.enterPhone')" />
                </el-form-item>
                <el-form-item :label="t('userProfile.company')">
                  <el-input v-model="editForm.company" :placeholder="t('userProfile.enterCompany')" />
                </el-form-item>
                <el-form-item :label="t('userProfile.address')">
                  <el-input v-model="editForm.address" :placeholder="t('userProfile.enterAddress')" />
                </el-form-item>
                <el-form-item :label="t('userProfile.onlineStayDuration')">
                  <el-tag type="info" size="large">{{ stayDurationText }}</el-tag>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="editLoading" @click="onSaveProfile">{{ t('userProfile.saveChanges') }}</el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="t('userProfile.changePassword')" name="password">
            <div style="padding: 20px 0">
              <el-form
                ref="pwdFormRef"
                :model="pwdForm"
                :rules="pwdRules"
                label-width="100px"
                class="pwd-form"
  scroll-to-error>
                <el-form-item :label="t('userProfile.currentPassword')" prop="oldPassword">
                  <el-input v-model="pwdForm.oldPassword" type="password" show-password :placeholder="t('userProfile.enterCurrentPassword')" />
                </el-form-item>
                <el-form-item :label="t('userProfile.newPassword')" prop="newPassword">
                  <el-input v-model="pwdForm.newPassword" type="password" show-password :placeholder="t('userProfile.enterNewPassword')" />
                </el-form-item>
                <el-form-item :label="t('userProfile.confirmPassword')" prop="confirmPassword">
                  <el-input v-model="pwdForm.confirmPassword" type="password" show-password :placeholder="t('userProfile.confirmNewPassword')" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="pwdLoading" @click="onChangePassword">{{ t('userProfile.changePassword') }}</el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>
          <span>{{ t('certificateManage.myAdmission') }}</span>
        </template>
        <el-descriptions :column="2" border v-loading="admissionLoading">
          <el-descriptions-item :label="t('certificateManage.certStatus')">
            <el-tag :type="admissionStatusType">{{ admissionStatusText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="admissionStatus" :label="t('certificateManage.certNo')">
            {{ admissionStatus.certificateNo }}
          </el-descriptions-item>
          <el-descriptions-item v-if="admissionStatus" :label="t('certificateManage.issuedDate')">
            {{ admissionStatus.issuedDate }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="section-card" shadow="never" data-testid="signature-card">
        <template #header>
          <div class="signature-card__header">
            <div>
              <div class="signature-card__title">
                <span>{{ t('userProfile.digitalSignature') }}<TermTip term="rsaKey" /></span>
              </div>
              <div class="signature-card__description">{{ t('userProfile.digitalSignatureDescription') }}</div>
            </div>
            <el-space wrap>
              <el-button
                :loading="signatureLoading"
                data-testid="signature-refresh-button"
                @click="loadSignatureKeyPair"
              >
                {{ t('userProfile.refreshKeyPair') }}
              </el-button>
              <el-button
                v-if="!signatureKeyPair"
                type="primary"
                :loading="signatureActionLoading"
                data-testid="signature-generate-button"
                @click="onGenerateKeyPair"
              >
                {{ t('userProfile.generateKeyPair') }}
              </el-button>
              <el-button
                v-else
                type="danger"
                plain
                :loading="signatureActionLoading"
                data-testid="signature-delete-button"
                @click="onDeleteKeyPair"
              >
                {{ t('userProfile.deleteKeyPair') }}
              </el-button>
            </el-space>
          </div>
        </template>

        <div v-loading="signatureLoading">
          <div class="signature-status-row">
            <span class="signature-status-row__label">{{ t('userProfile.keyPairStatus') }}</span>
            <el-tag :type="signatureStatusType" data-testid="signature-status">
              {{ signatureStatusText }}
            </el-tag>
          </div>

          <el-empty
            v-if="signatureLoaded && !signatureKeyPair"
            :description="t('userProfile.signatureEmptyDescription')"
            :image-size="92"
          />

          <div v-else-if="signatureKeyPair" class="signature-details">
            <el-descriptions :column="2" border>
              <el-descriptions-item :label="t('userProfile.keyVersion')">
                <span data-testid="signature-key-version">{{ signatureKeyVersionText }}</span>
              </el-descriptions-item>
              <el-descriptions-item :label="t('userProfile.keyUsage')">
                {{ signatureKeyUsageText }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('userProfile.createdAt')">
                {{ formatDateTime(signatureKeyPair.createdAt) }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('userProfile.expiresAt')">
                {{ formatDateTime(signatureKeyPair.expiresAt) }}
              </el-descriptions-item>
            </el-descriptions>

            <div class="signature-public-key">
              <div class="signature-public-key__label">{{ t('userProfile.publicKeyPreview') }}</div>
              <el-input
                type="textarea"
                :rows="4"
                readonly
                resize="none"
                data-testid="signature-public-key"
                :model-value="signaturePublicKeyPreview"
              />
            </div>
          </div>
        </div>
      </el-card>
    </section>
  </PageContainer>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.profile-form {
  max-width: 600px;
}

.pwd-form {
  max-width: 500px;
}

.signature-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.signature-card__title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--text-primary);
}

.signature-card__description {
  margin-top: 4px;
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.signature-status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.signature-status-row__label {
  font-size: var(--text-base);
  color: var(--text-secondary);
}

.signature-details {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.signature-public-key {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.signature-public-key__label {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--text-primary);
}
</style>
