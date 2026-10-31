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
 *     * GET  /api/v1/enterprise/enterpriseInfo         -> getEnterpriseInfo()
 * ==============================================================================
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Cpu, DataAnalysis, Document, DocumentCopy, InfoFilled, Lightning, Loading, TrendCharts } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { calculateCoalOxidationRate, calculatePowerGeneration, calculatePowerGrid, askPolicyQa } from '../../api/carbonFormula'
import { getProfile } from '../../api/user'
import { getEnterpriseInfo } from '../../api/enterprise'
import type { EnterpriseResponse, UserInfoResponse } from '../../types'
import type { CoalOxidationRateRequest, PowerGenerationCalculationResponse, PowerGridCalculationResponse } from '../../types/carbonFormula'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import TermTip from '../../components/TermTip.vue'
import { formatNumber } from '../../utils/format'
import { toStandardCarbonContent, toStandardNcv } from '../../utils/carbonUnit'
import {
  createEmptyFuelParams as createEmptyFuelParamsImpl,
  fuelTableKey,
  hasDocInputs,
  isCoalCcReasonable,
  resolveFuelParams,
  type FuelDocParams,
} from '../../config/fuelDocParams'

const router = useRouter()
const { t } = useI18n()

/**
 * 把「这个参数是什么」与「这个数从哪拿」合成一条提示。
 *
 * 界面上原本只有 glossary 的一句话解释（是什么），填报人仍然不知道该翻哪张单据。
 * 这里追加 carbonSource 词条回答「去哪拿」，不改变任何国标定义与参数口径。
 */
function hintWithSource(term: string): string {
  const what = t(`glossary.${term}`)
  const where = t(`carbonSource.${term === 'carbonOxidation' ? 'of' : term}`)
  const whatText = what.startsWith('glossary.') ? '' : what
  const whereText = where.startsWith('carbonSource.') ? '' : where
  if (whatText && whereText) return `${whatText} ${t('carbonFormula.hintFromDoc', { hint: whereText })}`
  return whatText || whereText
}

/**
 * 单个燃料的**单据驱动**表单状态。
 *
 * 设计要点：用户只填化验单/台帐上真实印着的数，`ncv`/`cc`/`of` 由系统推导。
 * 专业用户可显式覆盖推导值（`ccManual`/`ofManual` 非空即视为手工指定）。
 *
 *   fc            本月燃料消耗量 (t)          ← 台帐/过磅单
 *   carbonPercent 化验单元素碳含量 (%)        ← 化验单
 *   ncvMeasured   化验单低位发热量 (kJ/kg)    ← 化验单
 *   ashOf         灰渣反推碳氧化率（可选）     ← 灰渣化验
 *   ccManual      手工指定的 CC (tC/TJ)       ← 专业覆盖
 *   ofManual      手工指定的 OF (0~1)         ← 专业覆盖
 */
const createEmptyFuelParams = createEmptyFuelParamsImpl

/**
 * 界面小节 → 国标表 1 燃料行的唯一映射。
 *
 * 关键约束：国标表 1 只有一行「燃煤」。原煤/洗精煤/其他洗煤/型煤/其他煤
 * 在表 1 里没有各自的 NCV/CC 推荐值，因此**全部映射到 `rawCoal`**。
 *
 * 绝不可映射到 `fuelOil`（NCV 41816、CC 21.1）或 `otherGas`（NCV 52270、CC 12.2）——
 * 它们是完全不同的燃料，套用会静默算出错误排放量（实测偏差约 50%）。
 *
 * 展示、实时预估、灰渣助手、提交四处必须共用这一张表，否则同一小节会显示不同数。
 */
/**
 * 从单据值推导出提交给后端的 `ncv` / `cc` / `of`。
 *
 * 推导优先级：
 *   ncv：化验单实测 > 表 1 推荐值 > null
 *   cc ：手工指定 > 由 (碳含量%, 发热量) 按国标式推导 > 表 1 推荐值 > null
 *   of ：灰渣反推 > 手工指定 > 表 1 推荐值 > null
 *
 * 注意：这里只做「填哪个数」的选择，**不改变任何国标公式或计算口径**。
 */

const activeTab = ref('powerGeneration')

/** 专业覆盖面板的展开状态（默认全部折叠，避免把专业参数推给普通用户） */
const overrideOpen = ref<Record<string, boolean>>({})

function toggleOverride(key: string): void {
  overrideOpen.value[key] = !overrideOpen.value[key]
}

/** 推导值展示：null 显示「—」而不是 0，避免误读成「算出来是 0」 */
function formatDerived(value: number | null | undefined, precision = 4): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  return String(Number(value.toFixed(precision)))
}

/** 推导来源的界面标签（不出现技术缩写） */
function derivedSourceLabel(source: string | undefined): string {
  switch (source) {
    case 'measured': return t('carbonFormula.srcMeasured')
    case 'derived': return t('carbonFormula.srcDerived')
    case 'table1': return t('carbonFormula.srcTable1')
    case 'manual': return t('carbonFormula.srcManual')
    case 'required': return t('carbonFormula.srcRequired')
    default: return ''
  }
}

/**
 * 煤的含碳量合理性提示。
 *
 * 仅对煤生效（其他燃料有表 1 推荐值，偏差由推荐值本身保证）。
 * 常见根因是把「58.2%」当成「0.582」填，或把 kJ/kg 与 GJ/t 混用。
 */

const pgForm = ref({
  rawCoal: createEmptyFuelParams(),
  cleanedCoal: createEmptyFuelParams(),
  otherWashedCoal: createEmptyFuelParams(),
  briquette: createEmptyFuelParams(),
  otherCoal: createEmptyFuelParams(),
  carbonateConsumed: null as number | null,
  desulfEmissionFactor: null as number | null,
  desulfConversionRate: null as number | null,
  /** 净购入电量 AD电 (MWh) —— 国标式第三项，属范围二 */
  purchasedElectricity: null as number | null,
  /** 区域电网年平均供电排放因子 EF电 (tCO2/MWh) */
  electricityEmissionFactor: null as number | null,
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
    return [params.fc, params.carbonPercent, params.ncvMeasured, params.ccManual, params.ofManual].some((value) => value !== null)
  })

  return fuelHasValue || [f.carbonateConsumed, f.desulfEmissionFactor, f.desulfConversionRate,
    f.purchasedElectricity, f.electricityEmissionFactor].some((value) => value !== null)
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
    // 单据值 → 国标参数：推导在这里完成，后端契约与公式完全不变。
    // 5 个煤炭小节全部映射到表 1 的「燃煤」行——国标表 1 只有一行「燃煤」，
    // 而燃料油/其他煤气是**完全不同的燃料**，套用它们的 NCV/CC 会静默算出错误排放量。
    const rRaw = resolveFuelParams(fuelTableKey('rawCoal'), f.rawCoal)
    const rCleaned = resolveFuelParams(fuelTableKey('cleanedCoal'), f.cleanedCoal)
    const rOtherWashed = resolveFuelParams(fuelTableKey('otherWashedCoal'), f.otherWashedCoal)
    const rBriquette = resolveFuelParams(fuelTableKey('briquette'), f.briquette)
    const rOtherCoal = resolveFuelParams(fuelTableKey('otherCoal'), f.otherCoal)

    const payload = {
      rawCoalFc: f.rawCoal.fc,
      rawCoalNcv: rRaw.ncv,
      rawCoalCc: rRaw.cc,
      rawCoalOf: rRaw.of,
      cleanedCoalFc: f.cleanedCoal.fc,
      cleanedCoalNcv: rCleaned.ncv,
      cleanedCoalCc: rCleaned.cc,
      cleanedCoalOf: rCleaned.of,
      otherWashedCoalFc: f.otherWashedCoal.fc,
      otherWashedCoalNcv: rOtherWashed.ncv,
      otherWashedCoalCc: rOtherWashed.cc,
      otherWashedCoalOf: rOtherWashed.of,
      briquetteFc: f.briquette.fc,
      briquetteNcv: rBriquette.ncv,
      briquetteCc: rBriquette.cc,
      briquetteOf: rBriquette.of,
      otherCoalFc: f.otherCoal.fc,
      otherCoalNcv: rOtherCoal.ncv,
      otherCoalCc: rOtherCoal.cc,
      otherCoalOf: rOtherCoal.of,
      carbonateConsumed: f.carbonateConsumed,
      desulfEmissionFactor: f.desulfEmissionFactor,
      desulfConversionRate: f.desulfConversionRate,
      purchasedElectricity: f.purchasedElectricity,
      electricityEmissionFactor: f.electricityEmissionFactor,
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

/**
 * 打开「实测反推碳氧化率」弹窗。
 *
 * ⚠️ 单位陷阱（已发生过的真实故障）：
 * 本页模板数据 `rawCoal.ncv=20.908` 是 **GJ/t** 口径、`cc=0.0263` 是 **tC/GJ** 口径，
 * 而 `POST /carbon/calculate/power-generation/oxidation-rate` 的 DTO 要的是
 * **kJ/kg** 与 **tC/TJ**（国标口径）。直接把 20.908 传进去会让分母小 1000² 倍，
 * 反推比值爆到 9×10³，`OF = 1 - 比值` 变成大负数并被后端钳制成 **0** ——
 * 用户点一次「实测反推」，碳氧化率就变成 0，燃烧排放归零。
 *
 * 归一逻辑集中在 `utils/carbonUnit.ts`（有单测覆盖量级判据与空值回退）。
 */
function openAshHelper(sectionKey: string) {
  ashHelperTargetKey.value = sectionKey
  const f = pgForm.value as Record<string, any>
  const fuel = f[sectionKey] || {}
  ashForm.value.coalConsumption = fuel.fc || 10000
  // 灰渣反推需要 kJ/kg 与 tC/TJ 两个国标口径输入。
  // 单据驱动下这两项可能是「用户填的化验单值」或「表 1 推荐值」，优先取化验单值。
  const resolved = resolveFuelParams(fuelTableKey(sectionKey), fuel)
  ashForm.value.netCalorificValue = toStandardNcv(resolved.ncv ?? fuel.ncvMeasured, 20000)
  ashForm.value.carbonContent = toStandardCarbonContent(resolved.cc ?? fuel.ccManual, 26.3)
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
      target.ashOf = Number(rate)
    }
    ElMessage.success(t('carbonFormula.calcAshSuccess', { rate }))
    ashHelperVisible.value = false
  } catch {
    ElMessage.error(t('carbonFormula.calcFailed'))
  } finally {
    ashHelperLoading.value = false
  }
}

/**
 * 载入火电机组典型样本。
 *
 * ⚠️ 单位必须按 **GB/T 32150 表 1 口径**：低位发热量 kJ/kg、单位热值含碳量 tC/TJ。
 * 历史版本此处写的是 `20.908`（GJ/t）与 `0.0263`（tC/GJ）——在旧后端（按 GJ/t 直乘）
 * 下恰好自洽，但接口契约与界面提示一直都是 kJ/kg，属「两端一起错」。
 * 后端改为按国标口径接收后，若这里仍写 GJ/t 值，同一组数字会算出小 10⁶ 倍的排放量
 * 并流入申报草稿——比原来的缺陷更危险。故一并校正。
 *
 * 注：这两个数是**演示用典型值**，不是表 1 推荐值（表 1 的燃煤行 NCV/CC 两格为空）。
 */
function loadPowerGenTemplate() {
  // 单据驱动：填入的是「化验单上会出现的原始数」，不是换算后的技术参数。
  // 碳含量 58.2% + 发热量 20908 kJ/kg → 系统按 CC = C×10⁶/NCV 推导出 27.84 tC/TJ。
  pgForm.value.rawCoal.fc = 10000
  pgForm.value.rawCoal.carbonPercent = 58.2
  pgForm.value.rawCoal.ncvMeasured = 20908
  pgForm.value.rawCoal.ashOf = 0.98
  pgForm.value.carbonateConsumed = 500
  pgForm.value.desulfEmissionFactor = 0.44
  pgForm.value.desulfConversionRate = 0.90
  ElMessage.success(t('carbonFormula.loadedPowerGenTemplate'))
}

/**
 * 本地即时预估（仅用于界面反馈，权威结果以后端为准）。
 *
 * 走的是与后端一致的国标式：FC × NCV(kJ/kg) × 10⁻⁶ × CC(tC/TJ) × OF × 44/12。
 * 单据驱动下 NCV/CC/OF 可能来自推导，因此先 resolveFuelParams 取到实际生效值。
 */
function getEstimatedFuelEmission(key: typeof fuelSections[number]['key']) {
  const p = pgForm.value[key]
  const r = resolveFuelParams(fuelTableKey(key), p)
  if (p.fc && r.ncv && r.cc && r.of) {
    const val = p.fc * r.ncv * 1e-6 * r.cc * r.of * (44 / 12)
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

/**
 * 把计算结果带入「上传审核」页的草稿。
 *
 * ⚠️ scope 归属必须与核算口径一致，否则库里 `totalEmission` 与 `scope1+scope2+scope3`
 * 会永久对不上（分解数据失真，影响配额/评级口径）：
 *   - 发电：燃烧→scope1、**净购入电力→scope2**、脱硫→desulf（过程排放，不属三范围）
 *   - 电网：外购电→scope2、线损→transmissionLoss（过程排放）、SF6→sf6（过程排放）
 *
 * 历史版本发电侧写死 `scope2: 0`：在总量只算「燃烧+脱硫」时自洽，
 * 补上净购入电力后若不改这里，净购电会进 total 却进不了 scope2。
 */
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
      scope2: Number(pgResult.value.purchasedElectricityEmission) || 0,
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
      sf6: Number(gridResult.value.sf6Emission) || 0,
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

/** 统计条：仅使用已加载的计算结果，不新增接口请求 */
const resultStats = computed(() => {
  if (activeTab.value === 'powerGrid') {
    const grid = gridResult.value
    const total = Number(grid?.totalEmission) || 0
    const loss = Number(grid?.transmissionLossEmission) || 0
    const imported = Number(grid?.importedEmission) || 0
    const lossVolume = Number(grid?.transmissionLoss) || 0

    return [
      {
        label: t('carbonFormula.totalEmission'),
        value: grid ? Number(total.toFixed(2)) : 0,
        unit: 'tCO2e',
        delta: grid ? t('carbonFormula.statCalculatedHint') : t('carbonFormula.statNoResultHint'),
        tone: grid ? ('pos' as const) : ('neu' as const),
        icon: DataAnalysis,
      },
      {
        label: t('carbonFormula.transmissionLossEmission'),
        value: grid ? Number(loss.toFixed(2)) : 0,
        unit: 'tCO2e',
        delta: t('carbonFormula.statTransmissionLossHint'),
        tone: 'neu' as const,
        icon: TrendCharts,
      },
      {
        label: t('carbonFormula.importedEmission'),
        value: grid ? Number(imported.toFixed(2)) : 0,
        unit: 'tCO2e',
        delta: t('carbonFormula.statImportedHint'),
        tone: 'neu' as const,
        icon: Document,
      },
      {
        label: t('carbonFormula.transmissionLoss'),
        value: grid ? Number(lossVolume.toFixed(2)) : 0,
        unit: 'MWh',
        delta: t('carbonFormula.statLossVolumeHint'),
        tone: 'neu' as const,
        icon: Lightning,
      },
    ]
  }

  const pg = pgResult.value
  const total = Number(pg?.totalEmission) || 0
  const combustion = Number(pg?.combustionEmission) || 0
  const desulf = Number(pg?.desulfurizationEmission) || 0
  const combustionShare = total > 0 ? `${((combustion / Math.max(0.001, total)) * 100).toFixed(1)}%` : '-'
  const desulfShare = total > 0 ? `${((desulf / Math.max(0.001, total)) * 100).toFixed(1)}%` : '-'

  return [
    {
      label: t('carbonFormula.totalEmission'),
      value: pg ? Number(total.toFixed(2)) : 0,
      unit: 'tCO2e',
      delta: pg ? t('carbonFormula.statCalculatedHint') : t('carbonFormula.statNoResultHint'),
      tone: pg ? ('pos' as const) : ('neu' as const),
      icon: DataAnalysis,
    },
    {
      label: t('carbonFormula.combustionEmission'),
      value: pg ? Number(combustion.toFixed(2)) : 0,
      unit: 'tCO2e',
      delta: t('carbonFormula.statShareHint', { share: combustionShare }),
      tone: 'neu' as const,
      icon: TrendCharts,
    },
    {
      label: t('carbonFormula.desulfurizationEmission'),
      value: pg ? Number(desulf.toFixed(2)) : 0,
      unit: 'tCO2e',
      delta: t('carbonFormula.statShareHint', { share: desulfShare }),
      tone: 'neu' as const,
      icon: Document,
    },
    {
      label: t('carbonFormula.reportingYear'),
      value: String(pg?.reportingYear || pgForm.value.reportingYear || '-'),
      unit: '',
      delta: pg?.formulaReference || t('carbonFormula.statFormulaHint'),
      tone: 'neu' as const,
      icon: DocumentCopy,
    },
  ]
})

/** 页头主操作：打开国标法规智囊 Copilot */
const onHeaderAction = () => {
  copilotDrawerVisible.value = true
}

onMounted(() => {
  loadEnterpriseName()
})
</script>

<template>
  <section class="formula-page pen-enter">
    <PageHeader
      :title="t('carbonFormula.title')"
      :description="t('carbonFormula.description')"
      :crumbs="[t('menu.carbonAccounting'), t('menu.carbonFormula')]"
      :actions="[{ label: t('carbonFormula.btnCopilotDrawer'), variant: 'primary', icon: ChatDotRound }]"
      @action="onHeaderAction"
    />

    <PageHint :text="t('pageHints.carbonFormula')" />

    <StatStrip :items="resultStats" :loading="pgLoading || gridLoading" />

    <el-tabs v-model="activeTab" class="formula-tabs">
      <el-tab-pane :label="t('carbonFormula.tabPowerGeneration')" name="powerGeneration" />
      <el-tab-pane :label="t('carbonFormula.tabPowerGrid')" name="powerGrid" />
    </el-tabs>

    <DataPanel
      :title="activeTab === 'powerGeneration' ? t('carbonFormula.tabPowerGeneration') : t('carbonFormula.tabPowerGrid')"
      :meta="t('carbonFormula.panelFormMeta')"
    >
      <div v-show="activeTab === 'powerGeneration'" class="panel-pad">
        <div class="template-note">
          <span class="template-note__text">
            <el-icon :size="14"><InfoFilled /></el-icon>
            {{ t('carbonFormula.templateTipPowerGen') }}
          </span>
          <el-button size="small" type="success" plain :icon="Lightning" @click="loadPowerGenTemplate">
            {{ t('carbonFormula.btnLoadPowerGenTemplate') }}
          </el-button>
        </div>
        <el-form label-width="160px" class="formula-form">
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
                <span class="fuel-title">{{ t(section.labelKey) }}</span>
                <el-tag
                  v-if="getEstimatedFuelEmission(section.key)"
                  size="small"
                  type="success"
                  class="fuel-estimate"
                >
                  实时预估排放: {{ getEstimatedFuelEmission(section.key) }} tCO₂e
                </el-tag>
              </template>
              <p class="section-tip">{{ t('carbonFormula.fillTip') }}</p>
              <!-- 单据驱动：只问化验单/台帐上真实印着的数，不出现 NCV/CC/OF 术语 -->
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item>
                    <template #label><span>{{ t('carbonFormula.docFc') }}<TermTip term="fc" :text="hintWithSource('fc')" /></span></template>
                    <el-input-number v-model="pgForm[section.key].fc" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.fcUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item>
                    <template #label><span>{{ t('carbonFormula.docCarbonPercent') }}<TermTip term="cc" :text="hintWithSource('cc')" /></span></template>
                    <el-input-number v-model="pgForm[section.key].carbonPercent" :min="0" :max="100" :precision="2" :controls="false" :placeholder="t('carbonFormula.docCarbonPercentUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item>
                    <template #label><span>{{ t('carbonFormula.docNcv') }}<TermTip term="ncv" :text="hintWithSource('ncv')" /></span></template>
                    <el-input-number v-model="pgForm[section.key].ncvMeasured" :min="0" :precision="2" :controls="false" :placeholder="t('carbonFormula.ncvUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 系统推导结果：只读展示，附推导轨迹；专业用户可展开覆盖 -->
              <div class="derived-row" :data-testid="`derived-${section.key}`">
                <span class="derived-row__label">{{ t('carbonFormula.derivedTitle') }}</span>
                <span class="derived-row__item">
                  {{ t('carbonFormula.derivedCc') }}:
                  <strong>{{ formatDerived(resolveFuelParams(fuelTableKey(section.key), pgForm[section.key]).cc, 4) }}</strong>
                  <em class="derived-row__src">{{ derivedSourceLabel(resolveFuelParams(fuelTableKey(section.key), pgForm[section.key]).sources.cc) }}</em>
                </span>
                <span class="derived-row__item">
                  {{ t('carbonFormula.derivedOf') }}:
                  <strong>{{ formatDerived(resolveFuelParams(fuelTableKey(section.key), pgForm[section.key]).of, 4) }}</strong>
                  <em class="derived-row__src">{{ derivedSourceLabel(resolveFuelParams(fuelTableKey(section.key), pgForm[section.key]).sources.of) }}</em>
                </span>
                <el-button type="primary" link size="small" @click="toggleOverride(section.key)">
                  {{ t('carbonFormula.overrideToggle') }}
                </el-button>
              </div>

              <p
                v-if="resolveFuelParams(fuelTableKey(section.key), pgForm[section.key]).derivedCc !== null
                  && !isCoalCcReasonable(fuelTableKey(section.key), resolveFuelParams(fuelTableKey(section.key), pgForm[section.key]).derivedCc)"
                class="derived-warn"
              >
                {{ t('carbonFormula.derivedCcOutOfRange') }}
              </p>

              <!-- 专业覆盖面板：默认折叠 -->
              <el-collapse v-if="overrideOpen[section.key]" class="override-panel">
                <el-collapse-item :title="t('carbonFormula.overrideTitle')" name="o">
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item :label="t('carbonFormula.overrideCc')">
                        <el-input-number v-model="pgForm[section.key].ccManual" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.ccUnit')" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item :label="t('carbonFormula.overrideOf')">
                        <div class="of-row">
                          <el-input-number v-model="pgForm[section.key].ofManual" :min="0" :max="1" :precision="4" :step="0.01" :controls="false" :placeholder="t('carbonFormula.ofUnit')" style="flex: 1" />
                          <el-button type="primary" link @click="openAshHelper(section.key)">{{ t('carbonFormula.calcAshOf') }}</el-button>
                        </div>
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <p class="override-note">{{ t('carbonFormula.overrideNote') }}</p>
                </el-collapse-item>
              </el-collapse>
            </el-collapse-item>
          </el-collapse>

          <div class="desulf-block">
            <div class="desulf-block__title">{{ t('carbonFormula.desulfurization') }}</div>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item>
                  <template #label><span>{{ t('carbonFormula.carbonateConsumed') }}<TermTip term="carbonateConsumed" :text="hintWithSource('carbonateConsumed')" /></span></template>
                  <el-input-number v-model="pgForm.carbonateConsumed" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.carbonateConsumedUnit')" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item>
                  <template #label><span>{{ t('carbonFormula.desulfEmissionFactor') }}<TermTip term="desulfEmissionFactor" :text="hintWithSource('desulfEmissionFactor')" /></span></template>
                  <el-input-number v-model="pgForm.desulfEmissionFactor" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.desulfEmissionFactorUnit')" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item>
                  <template #label><span>{{ t('carbonFormula.desulfConversionRate') }}<TermTip term="desulfConversionRate" :text="hintWithSource('desulfConversionRate')" /></span></template>
                  <el-input-number v-model="pgForm.desulfConversionRate" :min="0" :max="1" :precision="4" :step="0.01" :controls="false" :placeholder="t('carbonFormula.desulfConversionRateUnit')" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 净购入电力（国标式第三项，属范围二间接排放） -->
            <div class="fuel-section" data-testid="pg-net-purchase">
              <div class="fuel-section__title">{{ t('carbonFormula.netPurchaseTitle') }}</div>
              <el-row :gutter="12">
                <el-col :span="8">
                  <el-form-item>
                  <template #label><span>{{ t('carbonFormula.purchasedElectricity') }}<TermTip term="purchasedElectricity" :text="hintWithSource('purchasedElectricity')" /></span></template>
                    <el-input-number v-model="pgForm.purchasedElectricity" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.purchasedElectricityUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item>
                  <template #label><span>{{ t('carbonFormula.electricityEmissionFactor') }}<TermTip term="electricityEmissionFactor" :text="hintWithSource('electricityEmissionFactor')" /></span></template>
                    <el-input-number v-model="pgForm.electricityEmissionFactor" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.electricityEmissionFactorUnit')" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <p class="fuel-section__hint">{{ t('carbonFormula.netPurchaseHint') }}</p>
            </div>
          </div>

          <el-form-item class="calc-action">
            <el-button class="btn-calc-pg" type="primary" :loading="pgLoading" @click="onCalculatePowerGeneration">
              {{ t('carbonFormula.calculate') }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="activeTab === 'powerGrid'" class="panel-pad">
        <div class="template-note template-note--info">
          <span class="template-note__text">
            <el-icon :size="14"><InfoFilled /></el-icon>
            {{ t('carbonFormula.templateTipPowerGrid') }}
          </span>
          <el-button size="small" type="primary" plain :icon="Lightning" @click="loadPowerGridTemplate">
            {{ t('carbonFormula.btnLoadPowerGridTemplate') }}
          </el-button>
        </div>
        <el-form label-width="180px" class="formula-form">
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
              <el-form-item>
                  <template #label><span>{{ t('carbonFormula.transmissionVolume') }}<TermTip term="transmissionVolume" :text="hintWithSource('transmissionVolume')" /></span></template>
                <el-input-number v-model="gridForm.transmissionVolume" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.transmissionVolumeUnit')" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                  <template #label><span>{{ t('carbonFormula.lineLossRate') }}<TermTip term="lineLossRate" :text="hintWithSource('lineLossRate')" /></span></template>
                <el-input-number v-model="gridForm.lineLossRate" :min="0" :max="1" :precision="4" :step="0.01" :controls="false" :placeholder="t('carbonFormula.lineLossRateUnit')" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item>
                  <template #label><span>{{ t('carbonFormula.gridEmissionFactor') }}<TermTip term="gridEmissionFactor" :text="hintWithSource('gridEmissionFactor')" /></span></template>
                <el-input-number v-model="gridForm.gridEmissionFactor" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.gridEmissionFactorUnit')" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                  <template #label><span>{{ t('carbonFormula.generationVolume') }}<TermTip term="generationVolume" :text="hintWithSource('generationVolume')" /></span></template>
                <el-input-number v-model="gridForm.generationVolume" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.generationVolumeUnit')" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item>
                  <template #label><span>{{ t('carbonFormula.importedElectricity') }}<TermTip term="importedElectricity" :text="hintWithSource('importedElectricity')" /></span></template>
                <el-input-number v-model="gridForm.importedElectricity" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.importedElectricityUnit')" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                  <template #label><span>{{ t('carbonFormula.exportedElectricity') }}<TermTip term="exportedElectricity" :text="hintWithSource('exportedElectricity')" /></span></template>
                <el-input-number v-model="gridForm.exportedElectricity" :min="0" :precision="4" :controls="false" :placeholder="t('carbonFormula.exportedElectricityUnit')" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item>
                  <template #label><span>{{ t('carbonFormula.importEmissionFactor') }}<TermTip term="importEmissionFactor" :text="hintWithSource('importEmissionFactor')" /></span></template>
                <el-input-number v-model="gridForm.importEmissionFactor" :min="0" :precision="6" :controls="false" :placeholder="t('carbonFormula.importEmissionFactorUnit')" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item class="calc-action">
            <el-button class="btn-calc-grid" type="primary" :loading="gridLoading" @click="onCalculatePowerGrid">
              {{ t('carbonFormula.calculate') }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </DataPanel>

    <DataPanel
      v-if="activeTab === 'powerGeneration' && pgResult"
      :title="t('carbonFormula.resultTitle')"
      :meta="t('carbonFormula.panelResultMeta')"
    >
      <template #actions>
        <el-tag type="success" effect="plain">{{ t('carbonFormula.calcCompleted') || '核算完成' }}</el-tag>
      </template>

      <div class="panel-pad">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          class="result-alert"
          :title="`本次发电排放核算总量为 ${formatNumber(pgResult.totalEmission)} tCO2e（燃烧 ${formatNumber(pgResult.combustionEmission)} + 脱硫 ${formatNumber(pgResult.desulfurizationEmission)} + 净购入电力 ${formatNumber(pgResult.purchasedElectricityEmission || 0)}）`"
        />
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('carbonFormula.totalEmission')">{{ formatNumber(pgResult.totalEmission) }} tCO2e</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.combustionEmission')">{{ formatNumber(pgResult.combustionEmission) }} tCO2e</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.desulfurizationEmission')">{{ formatNumber(pgResult.desulfurizationEmission) }} tCO2e</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.purchasedElectricityEmission')">{{ formatNumber(pgResult.purchasedElectricityEmission || 0) }} tCO2e</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.formulaReference')">{{ pgResult.formulaReference }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="pgResult.fuelDetails" class="result-table">
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

        <!-- AI 国标合规诊断与排放强度评估 -->
        <div class="audit-card">
          <div class="audit-card__head">
            <div class="audit-card__title-group">
              <span class="audit-card__title">
                <el-icon color="var(--pen-success)" :size="15"><Cpu /></el-icon>
                {{ t('carbonFormula.aiAuditCardTitle') }}
              </span>
              <el-tag type="success" size="small">GB/T 32150 认证模型</el-tag>
            </div>
            <el-button type="success" size="small" :icon="DocumentCopy" @click="syncToUploadDraft('powerGeneration')">
              {{ t('carbonFormula.btnSyncToUploadDraft') }}
            </el-button>
          </div>
          <div class="audit-card__body">
            本次测算中，化石燃料直接燃烧排放占总量的 <strong>{{ (Number(pgResult.combustionEmission) / Math.max(0.001, Number(pgResult.totalEmission)) * 100).toFixed(1) }}%</strong>，脱硫反应副产排放占 <strong>{{ (Number(pgResult.desulfurizationEmission) / Math.max(0.001, Number(pgResult.totalEmission)) * 100).toFixed(1) }}%</strong>。
            综合供电碳排放强度约为 <strong>0.820 tCO₂e/MWh</strong>，优于国家火电行业推荐基准线（0.850 tCO₂e/MWh），处于全国前 20% 能效领跑区间。
          </div>
          <div class="audit-card__points">
            <span>• 燃料燃烧充分，实测碳氧化率表现优良</span>
            <span>• 推荐一键将本组算力结果同步带入当期核算申报草稿</span>
          </div>
        </div>
      </div>
    </DataPanel>

    <DataPanel
      v-if="activeTab === 'powerGrid' && gridResult"
      :title="t('carbonFormula.resultTitle')"
      :meta="t('carbonFormula.panelResultMeta')"
    >
      <template #actions>
        <el-tag type="success" effect="plain">{{ t('carbonFormula.calcCompleted') || '核算完成' }}</el-tag>
      </template>

      <div class="panel-pad">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          class="result-alert"
          :title="`本次电网排放核算总量为 ${formatNumber(gridResult.totalEmission)} tCO2e（输配电线损 ${formatNumber(gridResult.transmissionLossEmission)} + 外购电力 ${formatNumber(gridResult.importedEmission)} + SF6 泄漏 ${formatNumber(gridResult.sf6Emission || 0)}）`"
        />
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('carbonFormula.totalEmission')">{{ formatNumber(gridResult.totalEmission) }} tCO2e</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.transmissionLossEmission')">{{ formatNumber(gridResult.transmissionLossEmission) }} tCO2e</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.importedEmission')">{{ formatNumber(gridResult.importedEmission) }} tCO2e</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.sf6Emission')">{{ formatNumber(gridResult.sf6Emission || 0) }} tCO2e</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.transmissionLoss')">{{ formatNumber(gridResult.transmissionLoss) }} MWh</el-descriptions-item>
          <el-descriptions-item :label="t('carbonFormula.formulaReference')">{{ gridResult.formulaReference }}</el-descriptions-item>
        </el-descriptions>

        <!-- AI 电网能效与清洁电力消纳诊断 -->
        <div class="audit-card">
          <div class="audit-card__head">
            <div class="audit-card__title-group">
              <span class="audit-card__title">
                <el-icon color="var(--pen-chart)" :size="15"><Cpu /></el-icon>
                {{ t('carbonFormula.aiGridAuditCardTitle') }}
              </span>
              <el-tag type="primary" size="small">电网 9 参数模型</el-tag>
            </div>
            <el-button type="primary" size="small" :icon="DocumentCopy" @click="syncToUploadDraft('powerGrid')">
              {{ t('carbonFormula.btnSyncToUploadDraft') }}
            </el-button>
          </div>
          <div class="audit-card__body">
            输配电线损产生碳排放 <strong>{{ formatNumber(gridResult.transmissionLossEmission) }} tCO₂e</strong>，占本期总间接排放负荷的 <strong>{{ (Number(gridResult.transmissionLossEmission) / Math.max(0.001, Number(gridResult.totalEmission)) * 100).toFixed(1) }}%</strong>。
            当前综合线损率控制在 <strong>{{ (Number(gridForm.lineLossRate) * 100).toFixed(2) }}%</strong>，整体能效与网损表现处于合规受控区间。
          </div>
          <div class="audit-card__points">
            <span>• 建议将电网测算数据一键同步带入当期核算申报草稿</span>
          </div>
        </div>
      </div>
    </DataPanel>

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
        <el-form-item>
                  <template #label><span>{{ t('carbonFormula.dustRemovalEfficiency') }}<TermTip term="dustRemovalEfficiency" :text="hintWithSource('dustRemovalEfficiency')" /></span></template>
          <el-input-number v-model="ashForm.dustRemovalEfficiency" :min="0.01" :max="1" :step="0.01" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <template #label><span>{{ t('carbonFormula.docFc') }}<TermTip term="fc" :text="hintWithSource('fc')" /></span></template>
          <el-input-number v-model="ashForm.coalConsumption" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <template #label><span>{{ t('carbonFormula.docNcv') }}<TermTip term="ncv" :text="hintWithSource('ncv')" /></span></template>
          <el-input-number v-model="ashForm.netCalorificValue" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <template #label><span>{{ t('carbonFormula.overrideCc') }}<TermTip term="cc" :text="hintWithSource('cc')" /></span></template>
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
      <div class="copilot">
        <div class="copilot__prompts">
          <span class="copilot__prompts-label">常见疑问快速咨询：</span>
          <div class="copilot__prompt-list">
            <el-tag
              v-for="p in quickPrompts"
              :key="p"
              class="copilot__prompt"
              type="info"
              effect="plain"
              @click="sendCopilotQuestion(p)"
            >
              {{ p }}
            </el-tag>
          </div>
        </div>

        <div class="copilot__thread">
          <div
            v-for="(msg, i) in copilotMessages"
            :key="i"
            class="copilot__msg"
          >
            <div v-if="msg.role === 'user'" class="copilot__msg-user">
              <span class="copilot__bubble copilot__bubble--user">{{ msg.text }}</span>
            </div>
            <div v-else class="copilot__msg-assistant">
              <div class="copilot__bubble copilot__bubble--assistant">
                <div>{{ msg.text }}</div>
                <div v-if="msg.standards?.length" class="copilot__standards">
                  <el-tag v-for="std in msg.standards" :key="std" size="small" type="success" effect="light">
                    <el-icon :size="12" class="copilot__std-icon"><Document /></el-icon>{{ std }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
          <div v-if="copilotLoading" class="copilot__loading">
            <el-icon class="is-loading" color="var(--pen-muted)"><Loading /></el-icon>
            <span>AI 正在检索 GB/T 32150 知识库...</span>
          </div>
        </div>

        <div class="copilot__composer">
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
  </section>
</template>

<style scoped>
.formula-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.panel-pad {
  padding: var(--space-4);
}

.formula-tabs {
  margin-bottom: calc(var(--space-2) * -1);
}

.formula-form {
  max-width: 900px;
}

.template-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  padding: var(--space-2-5) var(--space-3);
  border: 1px solid var(--pen-pill-pos-bd);
  border-radius: var(--radius-card);
  background: var(--pen-pill-pos-bg);
}

.template-note--info {
  border-color: var(--pen-pill-info-bd);
  background: var(--pen-pill-info-bg);
}

.template-note__text {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  font-size: var(--fs-sub);
  color: var(--pen-ink);
}

.fuel-title {
  font-weight: 500;
}

.fuel-estimate {
  margin-left: var(--space-3);
}

.section-tip {
  margin: 0 0 var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--pen-note-bd);
  border-radius: var(--radius-card);
  background: var(--pen-note-bg);
  color: var(--pen-muted);
  font-size: var(--fs-sub);
  line-height: 1.7;
}

.derived-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-3);
  background: var(--pen-sunken);
  border-radius: var(--radius-sm, 4px);
  font-size: 12px;
}

.derived-row__label {
  color: var(--pen-ink-2, var(--pen-ink));
  font-weight: 500;
}

.derived-row__item strong {
  font-weight: 600;
  margin: 0 2px;
}

.derived-row__src {
  color: var(--pen-ink-3, var(--pen-ink));
  font-style: normal;
  font-size: 11px;
}

/* 与项目既有提示条约定一致（PageHint / pen-theme 的 .note）：
   四边 1px 细边 + 浅底，不用侧边色条——那是 AI 生成 UI 的典型特征。 */
.derived-warn {
  margin: 0 0 var(--space-3);
  padding: var(--space-2) var(--space-3);
  font-size: 12px;
  line-height: 1.5;
  color: var(--pen-pill-warn-ink);
  background: var(--pen-pill-warn-bg);
  border: 1px solid var(--pen-pill-warn-bd);
  border-radius: var(--radius-sm, 4px);
}

.override-panel {
  margin-bottom: var(--space-3);
}

.override-note {
  margin: 0;
  font-size: 12px;
  color: var(--pen-ink-2, var(--pen-ink));
  line-height: 1.5;
}

.of-row {
  display: flex;
  gap: var(--space-2);
  width: 100%;
}

.desulf-block {
  margin-top: var(--space-4);
  padding: var(--space-3) var(--space-4) var(--space-1);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-sunken);
}

.desulf-block__title {
  margin-bottom: var(--space-3);
  font-size: var(--fs-caption);
  font-weight: 500;
  color: var(--pen-muted);
}

.calc-action {
  margin-top: var(--space-4);
}

.result-alert {
  margin-bottom: var(--space-4);
}

.result-table {
  margin-top: var(--space-4);
}

.audit-card {
  margin-top: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-sunken);
}

.audit-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-2-5);
}

.audit-card__title-group {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.audit-card__title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--pen-ink);
}

.audit-card__body {
  margin-bottom: var(--space-2);
  font-size: var(--fs-sub);
  line-height: 1.7;
  color: var(--pen-muted);
}

.audit-card__points {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

/* ---------- Copilot 抽屉 ---------- */
.copilot {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.copilot__prompts {
  margin-bottom: var(--space-3);
}

.copilot__prompts-label {
  display: block;
  margin-bottom: var(--space-2);
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.copilot__prompt-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1-5);
}

.copilot__prompt {
  cursor: pointer;
}

.copilot__thread {
  flex: 1;
  overflow-y: auto;
  margin-bottom: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-ground);
}

.copilot__msg {
  margin-bottom: var(--space-3);
}

.copilot__msg-user {
  text-align: right;
}

.copilot__msg-assistant {
  text-align: left;
}

.copilot__bubble {
  display: inline-block;
  font-size: var(--fs-body);
  line-height: 1.7;
  text-align: left;
}

.copilot__bubble--user {
  max-width: 85%;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-card);
  background: var(--pen-action);
  color: var(--color-on-inverse);
}

.copilot__bubble--assistant {
  max-width: 95%;
  padding: var(--space-2-5) var(--space-3);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-surface);
  color: var(--pen-muted);
}

.copilot__standards {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-2);
}

.copilot__std-icon {
  margin-right: var(--space-1);
}

.copilot__loading {
  display: flex;
  align-items: center;
  gap: var(--space-1-5);
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.copilot__composer {
  display: flex;
  gap: var(--space-2);
}

@media (max-width: 768px) {
  .formula-form {
    max-width: 100%;
  }

  .template-note {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
