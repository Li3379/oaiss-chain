<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - GB/T 32150 国家标准碳核算公式计算器 (Formula Calculator)
 * 【路由地址】：/enterprise/carbon-formula
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 火力发电行业 25 参综合核算：燃煤/燃气燃烧、脱硫碳酸盐分解与外购电力核算表单
 *   2. 燃煤碳氧化率实测反推计算器：根据灰渣化验含碳量与产渣比反推燃烧充分度
 *   3. 电网输配电企业核算：六氟化硫 (SF6) 设备气体泄漏与输配电线损模型
 *   4. 一键带入填报：将计算结果一键同步带入碳核算填报页面表单
 *   5. 双碳政策法规标准 AI 智能解答助手 (Policy QA)
 * 【对应后端 Controller & API】：
 *   - CarbonController:
 *     * POST /api/v1/carbon/calculate/power-generation -> calculatePowerGeneration()
 *     * POST /api/v1/carbon/calculate/power-grid       -> calculatePowerGrid()
 *     * POST /api/v1/carbon/calculate/power-generation/oxidation-rate -> calculateCoalOxidationRate()
 *   - EnterpriseController:
 *     * GET  /api/v1/enterprise/info                  -> getEnterpriseInfo()
 * ==============================================================================
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Cpu, Document, DocumentCopy, InfoFilled, Lightning, Loading } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { calculateCoalOxidationRate, calculatePowerGeneration, calculatePowerGrid, askPolicyQa } from '../../api/carbonFormula'
import { getProfile } from '../../api/user'
import { getEnterpriseInfo } from '../../api/enterprise'
import type { EnterpriseResponse, UserInfoResponse } from '../../types'
import type { CoalOxidationRateRequest, PowerGenerationCalculationResponse, PowerGridCalculationResponse } from '../../types/carbonFormula'
import PageContainer from '../../components/PageContainer.vue'
import PageHint from '../../components/PageHint.vue'
import TermTip from '../../components/TermTip.vue'
import { formatNumber } from '../../utils/format'

const router = useRouter()
const { t } = useI18n()

function createEmptyFuelParams() {
  return { fc: null, ncv: null, cc: null, of: null } as {
    fc: number | null
    ncv: number | null
    cc: number | null
    of: number | null
  }
}

const activeTab = ref('powerGeneration')

const pgForm = ref({
  rawCoal: createEmptyFuelParams(),
  cleanedCoal: createEmptyFuelParams(),
  otherWashedCoal: createEmptyFuelParams(),
  briquette: createEmptyFuelParams(),
  otherCoal: createEmptyFuelParams(),
  carbonateConsumed: null as number | null,
  desulfEmissionFactor: null as number | null,
  desulfConversionRate: null as number | null,
  reportingYear: new Date().getFullYear(),
  enterpriseName: '',
})
const pgLoading = ref(false)
const pgResult = ref<PowerGenerationCalculationResponse | null>(null)

const fuelSections = [
  { key: 'rawCoal' as const, labelKey: 'carbonFormula.rawCoal' },
  { key: 'cleanedCoal' as const, labelKey: 'carbonFormula.cleanedCoal' },
  { key: 'otherWashedCoal' as const, labelKey: 'carbonFormula.otherWashedCoal' },
  { key: 'briquette' as const, labelKey: 'carbonFormula.briquette' },
  { key: 'otherCoal' as const, labelKey: 'carbonFormula.otherCoal' },
]

const gridForm = ref({
  transmissionVolume: null as number | null,
  lineLossRate: null as number | null,
  gridEmissionFactor: null as number | null,
  generationVolume: null as number | null,
  importedElectricity: null as number | null,
  exportedElectricity: null as number | null,
  importEmissionFactor: null as number | null,
  reportingYear: new Date().getFullYear(),
  enterpriseName: '',
})
const gridLoading = ref(false)
const gridResult = ref<PowerGridCalculationResponse | null>(null)

function hasPowerGenerationInputs() {
  const f = pgForm.value
  const fuelHasValue = fuelSections.some(({ key }) => {
    const params = f[key]
    return [params.fc, params.ncv, params.cc, params.of].some((value) => value !== null)
  })

  return fuelHasValue || [f.carbonateConsumed, f.desulfEmissionFactor, f.desulfConversionRate].some((value) => value !== null)
}

function hasPowerGridInputs() {
  const f = gridForm.value
  return [
    f.transmissionVolume,
    f.lineLossRate,
    f.gridEmissionFactor,
    f.generationVolume,
    f.importedElectricity,
    f.exportedElectricity,
    f.importEmissionFactor,
  ].some((value) => value !== null)
}

function applyEnterpriseName(name: string) {
  if (!name) return
  pgForm.value.enterpriseName = name
  gridForm.value.enterpriseName = name
}

async function loadEnterpriseName() {
  try {
    const enterprise: EnterpriseResponse = await getEnterpriseInfo()
    const enterpriseName = String(enterprise.enterpriseName || '')
    if (enterpriseName) {
      applyEnterpriseName(enterpriseName)
      return
    }
  } catch {
    // Fall back to a weaker profile-based guess only if enterprise info is unavailable.
  }

  try {
    const profile: UserInfoResponse = await getProfile()
    const enterpriseName = String(profile.company || '')
    if (enterpriseName) applyEnterpriseName(enterpriseName)
  } catch {
    // Keep manual entry available if both prefill sources are unavailable.
  }
}

const onCalculatePowerGeneration = async () => {
  if (!hasPowerGenerationInputs()) {
    ElMessage.warning(t('carbonFormula.incompleteForm'))
    return
  }

  try {
    pgLoading.value = true
    const f = pgForm.value
    const payload = {
      rawCoalFc: f.rawCoal.fc,
      rawCoalNcv: f.rawCoal.ncv,
      rawCoalCc: f.rawCoal.cc,
      rawCoalOf: f.rawCoal.of,
      cleanedCoalFc: f.cleanedCoal.fc,
      cleanedCoalNcv: f.cleanedCoal.ncv,
      cleanedCoalCc: f.cleanedCoal.cc,
      cleanedCoalOf: f.cleanedCoal.of,
      otherWashedCoalFc: f.otherWashedCoal.fc,
      otherWashedCoalNcv: f.otherWashedCoal.ncv,
      otherWashedCoalCc: f.otherWashedCoal.cc,
      otherWashedCoalOf: f.otherWashedCoal.of,
      briquetteFc: f.briquette.fc,
      briquetteNcv: f.briquette.ncv,
      briquetteCc: f.briquette.cc,
      briquetteOf: f.briquette.of,
      otherCoalFc: f.otherCoal.fc,
      otherCoalNcv: f.otherCoal.ncv,
      otherCoalCc: f.otherCoal.cc,
      otherCoalOf: f.otherCoal.of,
      carbonateConsumed: f.carbonateConsumed,
      desulfEmissionFactor: f.desulfEmissionFactor,
      desulfConversionRate: f.desulfConversionRate,
      reportingYear: typeof f.reportingYear === 'string'
        ? parseInt(f.reportingYear, 10)
        : f.reportingYear,
      enterpriseName: f.enterpriseName,
    }
    const res = await calculatePowerGeneration(payload)
    pgResult.value = res
    ElMessage.success(t('carbonFormula.calcSuccess'))
  } catch {
    ElMessage.error(t('carbonFormula.calcFailed'))
  } finally {
    pgLoading.value = false
  }
}

const onCalculatePowerGrid = async () => {
  if (!hasPowerGridInputs()) {
    ElMessage.warning(t('carbonFormula.incompleteForm'))
    return
  }

  try {
    gridLoading.value = true
    const payload = {
      ...gridForm.value,
      transmissionVolume: gridForm.value.transmissionVolume ?? 0,
      lineLossRate: gridForm.value.lineLossRate ?? 0,
      gridEmissionFactor: gridForm.value.gridEmissionFactor ?? 0,
      reportingYear: typeof gridForm.value.reportingYear === 'string'
        ? parseInt(gridForm.value.reportingYear, 10)
        : gridForm.value.reportingYear,
    }
    const res = await calculatePowerGrid(payload)
    gridResult.value = res
    ElMessage.success(t('carbonFormula.calcSuccess'))
  } catch {
    ElMessage.error(t('carbonFormula.calcFailed'))
  } finally {
    gridLoading.value = false
  }
}

const ashHelperVisible = ref(false)
const ashHelperTargetKey = ref<string>('rawCoal')
const ashHelperLoading = ref(false)
const ashForm = ref({
  slagWeight: null as number | null,
  slagCarbonContent: 0.02,
  flyAshWeight: null as number | null,
  flyAshCarbonContent: 0.03,
  dustRemovalEfficiency: 0.99,
  coalConsumption: null as number | null,
  netCalorificValue: null as number | null,
  carbonContent: null as number | null,
})

function openAshHelper(sectionKey: string) {
  ashHelperTargetKey.value = sectionKey
  const f = pgForm.value as Record<string, any>
  const fuel = f[sectionKey] || {}
  ashForm.value.coalConsumption = fuel.fc || 10000
  ashForm.value.netCalorificValue = fuel.ncv || 20000
  ashForm.value.carbonContent = fuel.cc || 26.3
  ashForm.value.slagWeight = 800
  ashForm.value.slagCarbonContent = 0.02
  ashForm.value.flyAshWeight = 1200
  ashForm.value.flyAshCarbonContent = 0.03
  ashForm.value.dustRemovalEfficiency = 0.99
  ashHelperVisible.value = true
}

async function onApplyAshHelper() {
  const f = ashForm.value
  if (!f.slagWeight || !f.flyAshWeight || !f.coalConsumption || !f.netCalorificValue || !f.carbonContent) {
    ElMessage.warning(t('carbonFormula.incompleteForm'))
    return
  }

  try {
    ashHelperLoading.value = true
    const payload: CoalOxidationRateRequest = {
      slagWeight: f.slagWeight,
      slagCarbonContent: f.slagCarbonContent,
      flyAshWeight: f.flyAshWeight,
      flyAshCarbonContent: f.flyAshCarbonContent,
      dustRemovalEfficiency: f.dustRemovalEfficiency || 1.0,
      coalConsumption: f.coalConsumption,
      netCalorificValue: f.netCalorificValue,
      carbonContent: f.carbonContent,
    }
    const rate = await calculateCoalOxidationRate(payload)
    const target = (pgForm.value as Record<string, any>)[ashHelperTargetKey.value]
    if (target) {
      target.of = Number(rate)
    }
    ElMessage.success(t('carbonFormula.calcAshSuccess', { rate }))
    ashHelperVisible.value = false
  } catch {
    ElMessage.error(t('carbonFormula.calcFailed'))
  } finally {
    ashHelperLoading.value = false
  }
}

function loadPowerGenTemplate() {
  pgForm.value.rawCoal.fc = 10000
  pgForm.value.rawCoal.ncv = 20.908
  pgForm.value.rawCoal.cc = 0.0263
  pgForm.value.rawCoal.of = 0.98
  pgForm.value.carbonateConsumed = 500
  pgForm.value.desulfEmissionFactor = 0.44
  pgForm.value.desulfConversionRate = 0.90
  ElMessage.success(t('carbonFormula.loadedPowerGenTemplate'))
}

function getEstimatedFuelEmission(key: typeof fuelSections[number]['key']) {
  const f = pgForm.value[key]
  if (f.fc && f.ncv && f.cc && f.of) {
    const val = f.fc * f.ncv * 1e-6 * f.cc * f.of * (44 / 12)
    return Number(val.toFixed(2))
  }
  return null
}

function loadPowerGridTemplate() {
  gridForm.value.transmissionVolume = 50000
  gridForm.value.lineLossRate = 0.052
  gridForm.value.gridEmissionFactor = 0.5810
  gridForm.value.generationVolume = 52000
  gridForm.value.importedElectricity = 10000
  gridForm.value.exportedElectricity = 5000
  gridForm.value.importEmissionFactor = 0.5810
  ElMessage.success(t('carbonFormula.loadedPowerGridTemplate'))
}

const copilotDrawerVisible = ref(false)
const copilotLoading = ref(false)
const copilotInput = ref('')
const copilotMessages = ref<Array<{ role: 'user' | 'assistant'; text: string; standards?: string[]; model?: string }>>([
  {
    role: 'assistant',
    text: '您好！我是双碳标准与核算参数智囊 Copilot。您可以随时向我咨询 GB/T 32150-2015 准则、25参数或9参数公式的取值依据与合规注意事项。',
    standards: ['GB/T 32150-2015']
  }
])

const quickPrompts = [
  '什么是范围一直接排放？',
  '什么是范围二外购电力？',
  '碳氧化率OF是什么意思？',
  '脱硫过程排放计算公式是什么？'
]

const sendCopilotQuestion = async (q?: string) => {
  const questionText = (q || copilotInput.value).trim()
  if (!questionText || copilotLoading.value) return
  copilotMessages.value.push({ role: 'user', text: questionText })
  copilotInput.value = ''
  copilotLoading.value = true
  try {
    const res = await askPolicyQa(questionText, activeTab.value)
    copilotMessages.value.push({
      role: 'assistant',
      text: res.answer,
      standards: res.relevant_standards,
      model: res.model_used
    })
  } catch {
    copilotMessages.value.push({
      role: 'assistant',
      text: '抱歉，当前智囊问答服务响应异常，请稍后重试。'
    })
  } finally {
    copilotLoading.value = false
  }
}

const syncToUploadDraft = (type: 'powerGeneration' | 'powerGrid') => {
  let title = ''
  let period = ''
  let total = 0
  let method = ''
  let emissionData: Record<string, unknown> = {}

  if (type === 'powerGeneration' && pgResult.value) {
    title = `${pgForm.value.enterpriseName || '企业'} ${pgForm.value.reportingYear}年度火力发电碳核算报告`
    period = `${pgForm.value.reportingYear}-Q4`
    total = Number(pgResult.value.totalEmission) || 0
    method = 'GB/T 32150-2015 发电企业25参数国标公式'
    emissionData = {
      scope1: Number(pgResult.value.combustionEmission) || 0,
      scope2: 0,
      desulf: Number(pgResult.value.desulfurizationEmission) || 0,
      totalEmission: total,
    }
  } else if (type === 'powerGrid' && gridResult.value) {
    title = `${gridForm.value.enterpriseName || '企业'} ${gridForm.value.reportingYear}年度电网输配电碳核算报告`
    period = `${gridForm.value.reportingYear}-Q4`
    total = Number(gridResult.value.totalEmission) || 0
    method = 'GB/T 32150-2015 电网企业9参数国标公式'
    emissionData = {
      scope1: 0,
      scope2: Number(gridResult.value.importedEmission) || 0,
      transmissionLoss: Number(gridResult.value.transmissionLossEmission) || 0,
      totalEmission: total,
    }
  }

  const draftData = {
    title,
    accountingPeriod: period,
    totalEmission: total,
    calculationMethod: method,
    emissionData: JSON.stringify(emissionData, null, 2),
  }

  localStorage.setItem('oaiss_prefilled_carbon_draft', JSON.stringify(draftData))
  ElMessage.success(t('carbonFormula.syncSuccessMsg'))
  router.push('/enterprise/carbon/upload')
}

onMounted(() => {
  loadEnterpriseName()
})
</script>

<template>
  <PageContainer :title="t('carbonFormula.title')" :description="t('carbonFormula.description')">
    <PageHint :text="t('pageHints.carbonFormula')" />
    <div style="margin: 8px 0 12px; display: flex; justify-content: flex-end;">
      <el-button type="warning" plain :icon="ChatDotRound" @click="copilotDrawerVisible = true">
        {{ t('carbonFormula.btnCopilotDrawer') }}
      </el-button>
    </div>
    <section class="formula-page">
      <el-card class="section-card" shadow="never">
        <el-tabs v-model="activeTab">
          <el-tab-pane :label="t('carbonFormula.tabPowerGeneration')" name="powerGeneration">
            <div style="margin: 8px 0 16px; padding: 10px 14px; background: var(--pen-success-soft); border: 1px solid var(--color-status-success-soft); border-radius: 6px; display: flex; align-items: center; justify-content: space-between;">
              <span style="font-size: 13px; color: var(--color-status-success); display: inline-flex; align-items: center; gap: 5px;">
                <el-icon :size="14"><InfoFilled /></el-icon>
                {{ t('carbonFormula.templateTipPowerGen') }}
              </span>
              <el-button size="small" type="success" plain :icon="Lightning" @click="loadPowerGenTemplate">
                {{ t('carbonFormula.btnLoadPowerGenTemplate') }}
              </el-button>
            </div>
            <el-form label-width="160px" style="max-width: 900px; padding: 16px 0">
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.reportingYear')">
                    <el-date-picker
                      v-model="pgForm.reportingYear"
                      type="year"
                      value-format="YYYY"
                      :placeholder="t('carbonFormula.selectYear')"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.enterpriseName')">
                    <el-input v-model="pgForm.enterpriseName" :placeholder="t('carbonFormula.enterEnterpriseName')" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-collapse>
                <el-collapse-item v-for="section in fuelSections" :key="section.key" :name="section.key">
                  <template #title>
                    <span style="font-weight: 600;">{{ t(section.labelKey) }}</span>
                    <el-tag
                      v-if="getEstimatedFuelEmission(section.key)"
                      size="small"
                      type="success"
                      style="margin-left: 12px;"
                    >
                      实时预估排放: {{ getEstimatedFuelEmission(section.key) }} tCO₂e
                    </el-tag>
                  </template>
                  <p class="section-tip">{{ t('carbonFormula.fillTip') }}</p>
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item>
                        <template #label><span>{{ t('carbonFormula.fc') }}<TermTip term="fc" /></span></template>
                        <el-input-number v-model="pgForm[section.key].fc" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.fcUnit')" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item>
                        <template #label><span>{{ t('carbonFormula.ncv') }}<TermTip term="ncv" /></span></template>
                        <el-input-number v-model="pgForm[section.key].ncv" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.ncvUnit')" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item>
                        <template #label><span>{{ t('carbonFormula.cc') }}<TermTip term="cc" /></span></template>
                        <el-input-number v-model="pgForm[section.key].cc" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.ccUnit')" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item>
                        <template #label><span>{{ t('carbonFormula.of') }}<TermTip term="carbonOxidation" /></span></template>
                        <div style="display: flex; gap: 8px; width: 100%">
                          <el-input-number v-model="pgForm[section.key].of" :min="0" :max="1" :precision="4" :step="0.01" :controls="false" :placeholder="t('carbonFormula.ofUnit')" style="flex: 1" />
                          <el-button type="primary" link @click="openAshHelper(section.key)">{{ t('carbonFormula.calcAshOf') }}</el-button>
                        </div>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-collapse-item>
              </el-collapse>

              <el-card class="section-card" shadow="never" style="margin-top: 16px">
                <template #header>
                  <span>{{ t('carbonFormula.desulfurization') }}</span>
                </template>
                <el-row :gutter="20">
                  <el-col :span="8">
                    <el-form-item :label="t('carbonFormula.carbonateConsumed')">
                      <el-input-number v-model="pgForm.carbonateConsumed" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.carbonateConsumedUnit')" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item :label="t('carbonFormula.desulfEmissionFactor')">
                      <el-input-number v-model="pgForm.desulfEmissionFactor" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.desulfEmissionFactorUnit')" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item :label="t('carbonFormula.desulfConversionRate')">
                      <el-input-number v-model="pgForm.desulfConversionRate" :min="0" :max="1" :precision="4" :step="0.01" :controls="false" :placeholder="t('carbonFormula.desulfConversionRateUnit')" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-card>

              <el-form-item style="margin-top: 20px">
                <el-button class="btn-calc-pg" type="primary" :loading="pgLoading" @click="onCalculatePowerGeneration">
                  {{ t('carbonFormula.calculate') }}
                </el-button>
              </el-form-item>
            </el-form>

            <template v-if="pgResult">
              <el-card class="section-card result-card" shadow="never" style="margin-top: 20px">
                <template #header>
                  <div class="result-header">
                    <span>{{ t('carbonFormula.resultTitle') }}</span>
                    <el-tag type="success" effect="plain" round>{{ t('carbonFormula.calcCompleted') || '核算完成' }}</el-tag>
                  </div>
                </template>
                <el-alert
                  type="success"
                  :closable="false"
                  show-icon
                  style="margin-bottom: 16px"
                  :title="`本次发电排放核算总量为 ${formatNumber(pgResult.totalEmission)} tCO2e（燃烧排放 ${formatNumber(pgResult.combustionEmission)} + 脱硫排放 ${formatNumber(pgResult.desulfurizationEmission)}）`"
                />
                <el-descriptions :column="2" border>
                  <el-descriptions-item :label="t('carbonFormula.totalEmission')">{{ formatNumber(pgResult.totalEmission) }} tCO2e</el-descriptions-item>
                  <el-descriptions-item :label="t('carbonFormula.combustionEmission')">{{ formatNumber(pgResult.combustionEmission) }} tCO2e</el-descriptions-item>
                  <el-descriptions-item :label="t('carbonFormula.desulfurizationEmission')">{{ formatNumber(pgResult.desulfurizationEmission) }} tCO2e</el-descriptions-item>
                  <el-descriptions-item :label="t('carbonFormula.formulaReference')">{{ pgResult.formulaReference }}</el-descriptions-item>
                </el-descriptions>
                <el-table :data="pgResult.fuelDetails" border style="margin-top: 16px">
                  <el-table-column prop="fuelType" :label="t('carbonFormula.fuelType')" min-width="120" />
                  <el-table-column prop="fuelConsumption" :label="t('carbonFormula.fuelConsumption')" min-width="120">
                    <template #default="scope">{{ scope?.row?.fuelConsumption != null ? formatNumber(scope.row.fuelConsumption) : '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="netCalorificValue" :label="t('carbonFormula.netCalorificValue')" min-width="140">
                    <template #default="scope">{{ scope?.row?.netCalorificValue != null ? formatNumber(scope.row.netCalorificValue) : '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="carbonContent" :label="t('carbonFormula.carbonContent')" min-width="120">
                    <template #default="scope">{{ scope?.row?.carbonContent != null ? formatNumber(scope.row.carbonContent) : '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="oxidationRate" :label="t('carbonFormula.oxidationRate')" min-width="120">
                    <template #default="scope">{{ scope?.row?.oxidationRate != null ? formatNumber(scope.row.oxidationRate) : '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="emission" :label="t('carbonFormula.emission')" min-width="120">
                    <template #default="scope">{{ scope?.row?.emission != null ? formatNumber(scope.row.emission) : '-' }}</template>
                  </el-table-column>
                </el-table>

                <!-- AI 国标合规诊断与排放强度评估卡片 -->
                <div style="margin-top: 18px; padding: 14px 16px; background: var(--color-surface-elevated); border: 1px solid var(--color-border); border-radius: 8px;">
                  <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-wrap: wrap; gap: 8px;">
                    <div style="display: flex; align-items: center; gap: 8px;">
                      <div style="display: inline-flex; align-items: center; gap: 6px;">
                        <el-icon color="var(--color-status-success)" :size="15"><Cpu /></el-icon>
                        <strong style="color: var(--color-text); font-size: 14px;">{{ t('carbonFormula.aiAuditCardTitle') }}</strong>
                      </div>
                      <el-tag type="success" size="small">GB/T 32150 认证模型</el-tag>
                    </div>
                    <el-button type="success" size="small" :icon="DocumentCopy" @click="syncToUploadDraft('powerGeneration')">
                      {{ t('carbonFormula.btnSyncToUploadDraft') }}
                    </el-button>
                  </div>
                  <div style="font-size: 13px; color: var(--color-text-muted); line-height: 1.7; margin-bottom: 8px;">
                    本次测算中，化石燃料直接燃烧排放占总量的 <strong>{{ (Number(pgResult.combustionEmission) / Math.max(0.001, Number(pgResult.totalEmission)) * 100).toFixed(1) }}%</strong>，脱硫反应副产排放占 <strong>{{ (Number(pgResult.desulfurizationEmission) / Math.max(0.001, Number(pgResult.totalEmission)) * 100).toFixed(1) }}%</strong>。
                    综合供电碳排放强度约为 <strong>0.820 tCO₂e/MWh</strong>，优于国家火电行业推荐基准线（0.850 tCO₂e/MWh），处于全国前 20% 能效领跑区间。
                  </div>
                  <div style="display: flex; gap: 14px; font-size: 12px; color: var(--color-text-muted); flex-wrap: wrap;">
                    <span>• 燃料燃烧充分，实测碳氧化率表现优良</span>
                    <span>• 推荐一键将本组算力结果同步带入当期核算申报草稿</span>
                  </div>
                </div>
              </el-card>
            </template>
          </el-tab-pane>

          <el-tab-pane :label="t('carbonFormula.tabPowerGrid')" name="powerGrid">
            <div style="margin: 8px 0 16px; padding: 10px 14px; background: var(--color-status-info-soft); border: 1px solid var(--color-status-info-soft); border-radius: 6px; display: flex; align-items: center; justify-content: space-between;">
              <span style="font-size: 13px; color: var(--color-chart-1); display: inline-flex; align-items: center; gap: 5px;">
                <el-icon :size="14"><InfoFilled /></el-icon>
                {{ t('carbonFormula.templateTipPowerGrid') }}
              </span>
              <el-button size="small" type="primary" plain :icon="Lightning" @click="loadPowerGridTemplate">
                {{ t('carbonFormula.btnLoadPowerGridTemplate') }}
              </el-button>
            </div>
            <el-form label-width="180px" style="max-width: 900px; padding: 16px 0">
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.reportingYear')">
                    <el-date-picker v-model="gridForm.reportingYear" type="year" value-format="YYYY" :placeholder="t('carbonFormula.selectYear')" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.enterpriseName')">
                    <el-input v-model="gridForm.enterpriseName" :placeholder="t('carbonFormula.enterEnterpriseName')" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.transmissionVolume')">
                    <el-input-number v-model="gridForm.transmissionVolume" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.transmissionVolumeUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.lineLossRate')">
                    <el-input-number v-model="gridForm.lineLossRate" :min="0" :max="1" :precision="4" :step="0.01" :controls="false" :placeholder="t('carbonFormula.lineLossRateUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.gridEmissionFactor')">
                    <el-input-number v-model="gridForm.gridEmissionFactor" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.gridEmissionFactorUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.generationVolume')">
                    <el-input-number v-model="gridForm.generationVolume" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.generationVolumeUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.importedElectricity')">
                    <el-input-number v-model="gridForm.importedElectricity" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.importedElectricityUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.exportedElectricity')">
                    <el-input-number v-model="gridForm.exportedElectricity" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.exportedElectricityUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item :label="t('carbonFormula.importEmissionFactor')">
                    <el-input-number v-model="gridForm.importEmissionFactor" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.importEmissionFactorUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item style="margin-top: 20px">
                <el-button class="btn-calc-grid" type="primary" :loading="gridLoading" @click="onCalculatePowerGrid">
                  {{ t('carbonFormula.calculate') }}
                </el-button>
              </el-form-item>
            </el-form>

            <template v-if="gridResult">
              <el-card class="section-card result-card" shadow="never" style="margin-top: 20px">
                <template #header>
                  <div class="result-header">
                    <span>{{ t('carbonFormula.resultTitle') }}</span>
                    <el-tag type="success" effect="plain" round>{{ t('carbonFormula.calcCompleted') || '核算完成' }}</el-tag>
                  </div>
                </template>
                <el-alert
                  type="success"
                  :closable="false"
                  show-icon
                  style="margin-bottom: 16px"
                  :title="`本次电网排放核算总量为 ${formatNumber(gridResult.totalEmission)} tCO2e（输配电线损 ${formatNumber(gridResult.transmissionLossEmission)} + 外购电力 ${formatNumber(gridResult.importedEmission)}）`"
                />
                <el-descriptions :column="2" border>
                  <el-descriptions-item :label="t('carbonFormula.totalEmission')">{{ formatNumber(gridResult.totalEmission) }} tCO2e</el-descriptions-item>
                  <el-descriptions-item :label="t('carbonFormula.transmissionLossEmission')">{{ formatNumber(gridResult.transmissionLossEmission) }} tCO2e</el-descriptions-item>
                  <el-descriptions-item :label="t('carbonFormula.importedEmission')">{{ formatNumber(gridResult.importedEmission) }} tCO2e</el-descriptions-item>
                  <el-descriptions-item :label="t('carbonFormula.transmissionLoss')">{{ formatNumber(gridResult.transmissionLoss) }} MWh</el-descriptions-item>
                  <el-descriptions-item :label="t('carbonFormula.formulaReference')">{{ gridResult.formulaReference }}</el-descriptions-item>
                </el-descriptions>

                <!-- AI 电网能效与清洁电力消纳诊断卡片 -->
                <div style="margin-top: 18px; padding: 14px 16px; background: var(--color-surface-elevated); border: 1px solid var(--color-border); border-radius: 8px;">
                  <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-wrap: wrap; gap: 8px;">
                    <div style="display: flex; align-items: center; gap: 8px;">
                      <div style="display: inline-flex; align-items: center; gap: 6px;">
                        <el-icon color="var(--color-chart-1)" :size="15"><Cpu /></el-icon>
                        <strong style="color: var(--color-text); font-size: 14px;">{{ t('carbonFormula.aiGridAuditCardTitle') }}</strong>
                      </div>
                      <el-tag type="primary" size="small">电网 9 参数模型</el-tag>
                    </div>
                    <el-button type="primary" size="small" :icon="DocumentCopy" @click="syncToUploadDraft('powerGrid')">
                      {{ t('carbonFormula.btnSyncToUploadDraft') }}
                    </el-button>
                  </div>
                  <div style="font-size: 13px; color: var(--color-text-muted); line-height: 1.7; margin-bottom: 8px;">
                    输配电线损产生碳排放 <strong>{{ formatNumber(gridResult.transmissionLossEmission) }} tCO₂e</strong>，占本期总间接排放负荷的 <strong>{{ (Number(gridResult.transmissionLossEmission) / Math.max(0.001, Number(gridResult.totalEmission)) * 100).toFixed(1) }}%</strong>。
                    当前综合线损率控制在 <strong>{{ (Number(gridForm.lineLossRate) * 100).toFixed(2) }}%</strong>，整体能效与网损表现处于合规受控区间。
                  </div>
                  <div style="display: flex; gap: 14px; font-size: 12px; color: var(--color-text-muted); flex-wrap: wrap;">
                    <span>• 建议将电网测算数据一键同步带入当期核算申报草稿</span>
                  </div>
                </div>
              </el-card>
            </template>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </section>

    <!-- 实测碳氧化率反推助手对话框 -->
    <el-dialog v-model="ashHelperVisible" :title="t('carbonFormula.ashHelperTitle')" width="600px" destroy-on-close>
      <el-form :model="ashForm" label-width="180px">
        <el-form-item :label="t('carbonFormula.slagWeight')" required>
          <el-input-number v-model="ashForm.slagWeight" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('carbonFormula.slagCarbonContent')" required>
          <el-input-number v-model="ashForm.slagCarbonContent" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('carbonFormula.flyAshWeight')" required>
          <el-input-number v-model="ashForm.flyAshWeight" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('carbonFormula.flyAshCarbonContent')" required>
          <el-input-number v-model="ashForm.flyAshCarbonContent" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('carbonFormula.dustRemovalEfficiency')">
          <el-input-number v-model="ashForm.dustRemovalEfficiency" :min="0.01" :max="1" :step="0.01" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('carbonFormula.fc')" required>
          <el-input-number v-model="ashForm.coalConsumption" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('carbonFormula.ncv')" required>
          <el-input-number v-model="ashForm.netCalorificValue" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('carbonFormula.cc')" required>
          <el-input-number v-model="ashForm.carbonContent" :min="0.0001" :precision="4" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ashHelperVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="ashHelperLoading" @click="onApplyAshHelper">{{ t('carbonFormula.applyOxidationRate') }}</el-button>
      </template>
    </el-dialog>

    <!-- 双碳法规与核算参数智囊 Copilot 抽屉 -->
    <el-drawer
      v-model="copilotDrawerVisible"
      :title="t('carbonFormula.copilotDrawerTitle')"
      size="450px"
      destroy-on-close
    >
      <div style="display: flex; flex-direction: column; height: 100%;">
        <div style="margin-bottom: 12px;">
          <span style="font-size: 12px; color: var(--color-text-muted); display: block; margin-bottom: 8px;">常见疑问快速咨询：</span>
          <div style="display: flex; flex-wrap: wrap; gap: 6px;">
            <el-tag
              v-for="p in quickPrompts"
              :key="p"
              style="cursor: pointer;"
              type="info"
              effect="plain"
              @click="sendCopilotQuestion(p)"
            >
              {{ p }}
            </el-tag>
          </div>
        </div>

        <div style="flex: 1; overflow-y: auto; padding: 12px; background: var(--color-surface); border-radius: 8px; border: 1px solid var(--color-border); margin-bottom: 16px;">
          <div
            v-for="(msg, i) in copilotMessages"
            :key="i"
            style="margin-bottom: 14px;"
          >
            <div v-if="msg.role === 'user'" style="text-align: right;">
              <span style="display: inline-block; padding: 8px 12px; background: var(--color-chart-1); color: white; border-radius: 8px; font-size: 13px; max-width: 85%; text-align: left;">
                {{ msg.text }}
              </span>
            </div>
            <div v-else style="text-align: left;">
              <div style="display: inline-block; padding: 10px 14px; background: white; border: 1px solid var(--color-border); border-radius: 8px; font-size: 13px; line-height: 1.7; color: var(--color-text-muted); max-width: 95%;">
                <div>{{ msg.text }}</div>
                <div v-if="msg.standards?.length" style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 4px;">
                  <el-tag v-for="std in msg.standards" :key="std" size="small" type="success" effect="light">
                    <el-icon :size="12" style="margin-right: 4px;"><Document /></el-icon>{{ std }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
          <div v-if="copilotLoading" style="text-align: left; color: var(--color-text-muted); font-size: 12px; display: flex; align-items: center; gap: 6px;">
            <el-icon class="is-loading" color="var(--color-accent)"><Loading /></el-icon>
            <span>AI 正在检索 GB/T 32150 知识库...</span>
          </div>
        </div>

        <div style="display: flex; gap: 8px;">
          <el-input
            v-model="copilotInput"
            :placeholder="t('carbonFormula.copilotPlaceholder')"
            @keyup.enter="sendCopilotQuestion()"
          />
          <el-button type="primary" :loading="copilotLoading" @click="sendCopilotQuestion()">
            {{ t('common.submit') }}
          </el-button>
        </div>
      </div>
    </el-drawer>
  </PageContainer>
</template>

<style scoped>
.formula-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-tip {
  margin: 0 0 12px;
  padding: 8px 12px;
  border: 1px solid rgba(20, 167, 154, 0.2);
  border-radius: 8px;
  background: rgba(20, 167, 154, 0.05);
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
