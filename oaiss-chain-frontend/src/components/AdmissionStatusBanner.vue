<script setup lang="ts">
/**
 * ==============================================================================
 * 【组件模块】：企业准入证书状态横幅 (Admission Status Banner)
 * 【挂载位置】：layout/index.vue → el-main 内、router-view 之前（仅企业角色）
 * 【业务背景】：
 *   后端已把准入证书变成真正的业务约束——无有效证书的企业提交碳报告、
 *   发起 P2P / 拍卖挂单、成交结算、碳币充值都会被拒（业务码 7001）。
 *   但前端此前对此零感知：用户只有在「点了按钮、被服务器拒绝」之后才知道
 *   自己没资格。本组件把结果提前告知：
 *     * 有有效证书        → 不显示（不打扰）
 *     * 从未签发          → 提示联系管理员签发
 *     * 已吊销            → 展示吊销原因与时间，并说明哪些操作被冻结
 *     * 已过期            → 提示续期
 * 【设计取舍】：
 *   只提示、不锁死登录与浏览。吊销的后果应当让人看得见（横幅 + 写操作被拒），
 *   而不是把人锁在门外看不到原因。
 * ==============================================================================
 */
import { computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../store'
import { useCertificateStatus } from '../composables/useCertificateStatus'
import { formatDateTime } from '../utils/format'

const { t } = useI18n()
const appStore = useAppStore()
const { latestAdmission, blockReason, loaded, refreshAdmission, resetAdmission } =
  useCertificateStatus()

/** 仅企业角色需要关心准入证书 */
const isEnterprise = computed(() => appStore.role === 'ENTERPRISE')

/** 有提示才渲染；其他角色或无阻断原因时整个组件不占位 */
const visible = computed(() => isEnterprise.value && loaded.value && blockReason.value !== null)

const tone = computed(() => (blockReason.value === 'expired' ? 'warning' : 'danger'))

const title = computed(() => {
  if (blockReason.value === 'none') return t('admissionBanner.titleNone')
  if (blockReason.value === 'expired') return t('admissionBanner.titleExpired')
  return t('admissionBanner.titleRevoked')
})

const body = computed(() => {
  if (blockReason.value === 'none') return t('admissionBanner.bodyNone')
  if (blockReason.value === 'expired') {
    return t('admissionBanner.bodyExpired', {
      date: latestAdmission.value?.expiryDate || '-',
    })
  }
  return t('admissionBanner.bodyRevoked')
})

/** 已吊销时展示原因与时间（这两个字段是 V19 起才有的审计留痕） */
const revokedDetail = computed(() => {
  if (blockReason.value !== 'revoked') return null
  const cert = latestAdmission.value
  if (!cert?.revokedAt && !cert?.revokeReason) return null
  return {
    reason: cert.revokeReason || t('admissionBanner.reasonUnspecified'),
    at: cert.revokedAt ? formatDateTime(cert.revokedAt) : '-',
    certificateNo: cert.certificateNo,
  }
})

onMounted(() => {
  if (isEnterprise.value) refreshAdmission()
})

// 切换账号（角色/用户名变化）时重新拉取，避免把上一个企业的状态带过来
watch(
  () => [appStore.role, appStore.username],
  () => {
    if (isEnterprise.value) {
      refreshAdmission()
    } else {
      resetAdmission()
    }
  },
)
</script>

<template>
  <el-alert
    v-if="visible"
    class="admission-banner"
    data-testid="admission-banner"
    :type="tone"
    :closable="false"
    show-icon
  >
    <template #title>
      <span class="admission-banner__title">{{ title }}</span>
    </template>

    <p class="admission-banner__body">{{ body }}</p>

    <p v-if="revokedDetail" class="admission-banner__detail">
      {{ t('admissionBanner.certificateNo') }}: <code>{{ revokedDetail.certificateNo }}</code>
      · {{ t('admissionBanner.revokedAt') }}: {{ revokedDetail.at }}
      · {{ t('admissionBanner.revokeReason') }}: {{ revokedDetail.reason }}
    </p>

    <p class="admission-banner__hint">{{ t('admissionBanner.contactHint') }}</p>
  </el-alert>
</template>

<style scoped>
.admission-banner {
  margin-bottom: var(--space-4);
}

.admission-banner__title {
  font-weight: 600;
}

.admission-banner__body {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: 1.6;
}

.admission-banner__detail {
  margin: var(--space-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--pen-muted);
  word-break: break-all;
}

.admission-banner__hint {
  margin: var(--space-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}
</style>
