<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 企业主体档案与基本信息维护 (Enterprise Info)
 * 【路由地址】：/enterprise/info
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 企业工商主体档案展示：企业名称、统一社会信用代码、法定代表人与注册时间
 *   2. 法定配额资产概览：初始核定配额、可用结余配额与当前配额使用百分比
 *   3. 碳币钱包资金余额：展示平台内可用虚拟代币资产余额
 *   4. 应急联系人维护表单：更新企业法定联系人姓名与联系电话
 * 【对应后端 Controller & API】：
 *   - EnterpriseController:
 *     * GET /api/v1/enterprise/info    -> getEnterpriseInfo() 获取企业主体档案
 *     * GET /api/v1/enterprise/quota   -> getQuotaInfo() 获取法定配额使用详情
 *     * PUT /api/v1/enterprise/contact -> updateContact() 更新企业联系人信息
 *   - CarbonCoinController:
 *     * GET /api/v1/carbon-coin/account -> getMyAccount() 获取碳币钱包资产
 * ==============================================================================
 */
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getEnterpriseInfo, getQuotaInfo, updateContact } from '../../api/enterprise'
import { getMyAccount } from '../../api/carbonCoin'
import type { EnterpriseQuotaResponse, EnterpriseResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHint from '../../components/PageHint.vue'
import TermTip from '../../components/TermTip.vue'

const { t } = useI18n()

const loading = ref(false)
const enterpriseInfo = ref<EnterpriseResponse | null>(null)
const quotaInfo = ref<EnterpriseQuotaResponse | null>(null)
const carbonCoinBalance = ref<number | null>(null)

const enterpriseName = computed(() => {
  return (enterpriseInfo.value?.enterpriseName as string) || '-'
})

const registrationTime = computed(() => {
  return formatDateTime(enterpriseInfo.value?.createdAt as string) || '-'
})

const remainingQuota = computed(() => {
  const value = quotaInfo.value?.remainingQuota
  return value === null || value === undefined ? '-' : formatNumber(value)
})

const usageRate = computed(() => {
  const value = Number(quotaInfo.value?.usageRate)
  return Number.isFinite(value) ? `${value.toFixed(2)}%` : '-'
})

const contactDialogVisible = ref(false)
const contactForm = ref({ contactPerson: '', contactPhone: '' })
const contactLoading = ref(false)

const fetchInfo = async () => {
  loading.value = true
  try {
    const [info, quota, account] = await Promise.all([
      getEnterpriseInfo(),
      getQuotaInfo(),
      getMyAccount(),
    ])
    enterpriseInfo.value = info
    quotaInfo.value = quota
    carbonCoinBalance.value = Number((account as { balance?: number })?.balance || 0)
    if (info.contactPerson) {
      contactForm.value.contactPerson = info.contactPerson
    }
    if (info.contactPhone) {
      contactForm.value.contactPhone = info.contactPhone
    }
  } catch {
    ElMessage.error(t('enterpriseInfo.loadFailed'))
  } finally {
    loading.value = false
  }
}

const openContactDialog = () => {
  contactDialogVisible.value = true
}

const submitContact = async () => {
  contactLoading.value = true
  try {
    await updateContact({
      contactPerson: contactForm.value.contactPerson.trim(),
      contactPhone: contactForm.value.contactPhone.trim(),
    })
    ElMessage.success(t('enterpriseInfo.contactUpdated'))
    contactDialogVisible.value = false
    await fetchInfo()
  } catch {
    ElMessage.error(t('enterpriseInfo.contactUpdateFailed'))
  } finally {
    contactLoading.value = false
  }
}

onMounted(() => fetchInfo())
</script>

<template>
  <section class="info-page">
    <el-card class="section-card" shadow="never">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>{{ t('enterpriseInfo.breadcrumbEnterprise') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ t('enterpriseInfo.breadcrumbInfo') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </el-card>

    <PageHint :text="t('pageHints.enterpriseInfo')" />

    <div v-loading="loading">
      <el-card class="section-card" shadow="never">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span>{{ t('enterpriseInfo.enterpriseInfoTitle') }}</span>
            <el-button type="primary" size="small" @click="openContactDialog">{{ t('enterpriseInfo.editContact') }}</el-button>
          </div>
        </template>
        <el-descriptions v-if="enterpriseInfo" :column="2" border>
          <el-descriptions-item :label="t('enterpriseInfo.companyName')">{{ enterpriseName }}</el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.industry')">{{ enterpriseInfo.industry || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.contactPerson')">{{ enterpriseInfo.contactPerson || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.contactPhone')">{{ enterpriseInfo.contactPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.registrationDate')">{{ registrationTime }}</el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.address')">{{ enterpriseInfo.address || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="section-card" shadow="never" style="margin-top: 14px;">
        <template #header>
          <span>{{ t('enterpriseInfo.quotaInfoTitle') }}</span>
        </template>
        <el-descriptions v-if="quotaInfo" :column="2" border>
          <el-descriptions-item>
            <template #label><span>{{ t('enterpriseInfo.totalQuota') }}<TermTip term="carbonQuota" /></span></template>
            {{ formatNumber(quotaInfo.totalQuota) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.usedQuota')">{{ formatNumber(quotaInfo.usedQuota) }}</el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.tradableQuota')">{{ formatNumber(quotaInfo.tradableQuota) }}</el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.remainingQuota')">{{ remainingQuota }}</el-descriptions-item>
          <el-descriptions-item :label="t('enterpriseInfo.usageRate')">{{ usageRate }}</el-descriptions-item>
          <el-descriptions-item :label="t('carbonCoin.balance')">{{ formatNumber(carbonCoinBalance) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>

    <el-dialog v-model="contactDialogVisible" :title="t('enterpriseInfo.editContactTitle')" width="500px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item :label="t('enterpriseInfo.contactPerson')">
          <el-input v-model="contactForm.contactPerson" :placeholder="t('enterpriseInfo.enterContactPerson')" />
        </el-form-item>
        <el-form-item :label="t('enterpriseInfo.contactPhone')">
          <el-input v-model="contactForm.contactPhone" :placeholder="t('enterpriseInfo.enterContactPhone')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="contactLoading" @click="submitContact">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.info-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}
</style>
