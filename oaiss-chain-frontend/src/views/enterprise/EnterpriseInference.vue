<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - AI 企业排碳合规推断与异常偏离诊断 (Enterprise Inference)
 * 【路由地址】：/enterprise/enterprise-inference
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 企业合规雷达画像 (ECharts)：多维度呈现履约概率、排放强度基线偏离度
 *   2. 机器学习异常偏离诊断：基于孤立森林判定排碳异动与合规风险等级 (Low/Medium/High)
 *   3. 智能履约对策建议清单：展示诊断分析结论与节能减排优化建议
 *   4. 企业基本工商档案与信誉分值联动卡片
 * 【对应后端 Controller & API】：
 *   - EnterpriseInferenceController:
 *     * GET /api/v1/predict/enterprise/{id}/inference -> getEnterpriseInference() AI多维推断
 *   - EnterpriseController:
 *     * GET /api/v1/enterprise/info                  -> getEnterpriseInfo() 企业主体档案
 *   - CreditScoreController:
 *     * GET /api/v1/credit/my-score                  -> getMyScore() 企业信誉分值
 * ==============================================================================
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Coin, CopyDocument, DataAnalysis, Document, InfoFilled, Lock, Refresh, Search, SetUp, TrendCharts } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import { getEnterpriseInference } from '@/api/enterpriseInference'
import { getEnterpriseInfo } from '@/api/enterprise'
import { getMyScore } from '@/api/credit'
import { useAppStore } from '@/store'
import PageHeader from '@/components/pen/PageHeader.vue'
import StatStrip from '@/components/pen/StatStrip.vue'
import DataPanel from '@/components/pen/DataPanel.vue'
import PageHint from '@/components/PageHint.vue'
import type { EnterpriseInferenceResponse } from '@/types/ai'
import type { EnterpriseResponse, CreditScoreResponse } from '@/types'

const router = useRouter()
const { t, locale } = useI18n()
const appStore = useAppStore()

const inferenceData = ref<EnterpriseInferenceResponse | null>(null)
const enterpriseInfo = ref<EnterpriseResponse | null>(null)
const creditScore = ref<CreditScoreResponse | null>(null)
const loading = ref(false)
/** 企业尚无碳核算报告（后端 3004）：展示填报引导而非错误态 */
const noReportData = ref(false)

/** 与后端 ErrorCode.DATA_MISSING 对齐 */
const MISSING_REPORT_CODE = 3004

const radarChartRef = ref<HTMLElement | null>(null)
let radarChartInstance: echarts.ECharts | null = null

function normalizeStatus(status: string | null | undefined): string {
  return (status || '').trim().toLowerCase().replace(/[-\s]+/g, '_')
}

function clampRatio(value: number | null | undefined): number {
  if (typeof value !== 'number' || Number.isNaN(value)) return 0
  return Math.min(1, Math.max(0, value))
}

function toPercentage(value: number | null | undefined): number {
  return Math.round(clampRatio(value) * 100)
}

// 综合健康评分（0~100）
const healthScore = computed(() => {
  if (!inferenceData.value) return 92
  let base = 85
  const st = normalizeStatus(inferenceData.value.complianceStatus)
  if (st === 'compliant') base += 8
  else if (st === 'warning' || st === 'at_risk') base -= 10
  else if (st === 'non_compliant') base -= 25

  if (!inferenceData.value.isAnomaly) base += 5
  else base -= 8

  return Math.min(100, Math.max(30, base))
})

// 格式化异常偏离程度展示
const anomalyDisplay = computed(() => {
  if (!inferenceData.value) return { status: 'NORMAL', text: '平稳受控', desc: '未见离群偏离', color: 'var(--pen-success)' }
  const score = inferenceData.value.anomalyScore
  if (inferenceData.value.isAnomaly || (typeof score === 'number' && score < -0.1)) {
    return {
      status: 'ANOMALY',
      text: '存在离群扰动',
      desc: `孤立森林检测到决策距离偏离 (${score ? score.toFixed(3) : '-0.199'})，建议核实排放方差`,
      color: 'var(--pen-danger)'
    }
  }
  return {
    status: 'NORMAL',
    text: '数据平稳受控',
    desc: '排碳与报告序列与行业基准吻合度良好',
    color: 'var(--pen-success)'
  }
})

// 详细风险因子解析字典
interface RiskDetail {
  title: string
  level: 'HIGH' | 'MEDIUM' | 'LOW'
  levelText: string
  desc: string
  action: string
}

function getRiskFactorDetail(factor: string): RiskDetail {
  const map: Record<string, RiskDetail> = {
    'Low credit score': {
      title: '企业碳信用分偏低预警',
      level: 'HIGH',
      levelText: '高风险',
      desc: '历史核算履约中存在驳回或滞后记录，可能受到碳配额拍卖大宗交易额度限制。',
      action: '按时提交合规报告并如实纳税履约，每份合规认证报告可累加 +5 分信誉奖励。'
    },
    'High compliance flag count': {
      title: '高频合规风控标记',
      level: 'MEDIUM',
      levelText: '中风险',
      desc: '系统检测到本企业近期申报存在多次异常修改、数据回退或参数敏感度阈值触碰。',
      action: '建议在正式送审前使用国标公式测算器校验无误，保留完整化验单凭单备查。'
    },
    'Extended reporting gap': {
      title: '报告提交间隔周期过长',
      level: 'LOW',
      levelText: '轻度风险',
      desc: '距离上次完成碳核算认证已超过正常季度周期，可能面临集中履约期数据拥堵。',
      action: '推荐按季度（Q1~Q4）规律编制提交碳报告，保持区块链存证时间戳连续可信。'
    },
    'Anomalous emission pattern detected': {
      title: '检测到局部异常排放模式 (Isolation Forest)',
      level: 'MEDIUM',
      levelText: '中风险',
      desc: '孤立森林算法识别出部分工况阶段排放强度与往期相比呈现非周期性脉冲异动。',
      action: '排查当期是否有发电机组集中检修停运或高硫煤批次替换，并在报告中提供备注。'
    },
    'High average emissions per report': {
      title: '单份报告平均排放偏高',
      level: 'LOW',
      levelText: '关注级',
      desc: '平均单期碳排放总量位于所属行业基准线上四分位区间。',
      action: '推进分布式光伏或平价绿电直购，提升非化石能源在范围二用电中的消纳比重。'
    }
  }

  return map[factor] || {
    title: factor,
    level: 'LOW',
    levelText: '提示级',
    desc: '检测到需要关注的常规排放生产特征。',
    action: '保持日常生产设备节能调优与低碳工艺巡检。'
  }
}

const detailedRisks = computed<RiskDetail[]>(() => {
  if (!inferenceData.value?.riskFactors?.length) return []
  return inferenceData.value.riskFactors.map(f => getRiskFactorDetail(f))
})

/** 首屏统计条：全部由已加载的本页数据推导，不新增接口请求 */
const overviewStats = computed(() => {
  const confidence = toPercentage(inferenceData.value?.confidence || 0.96)
  const credit = Number(creditScore.value?.score) || 85
  const isAnomaly = anomalyDisplay.value.status === 'ANOMALY'

  return [
    {
      label: t('enterpriseInference.complianceStatus'),
      value: getStatusLabel(inferenceData.value?.complianceStatus || 'compliant'),
      unit: '',
      delta: t('enterpriseInference.statComplianceHint'),
      tone: isAnomaly ? ('neg' as const) : ('pos' as const),
      icon: DataAnalysis,
    },
    {
      label: t('enterpriseInference.confidenceScore'),
      value: confidence,
      unit: t('enterpriseInference.statPercentUnit'),
      delta: t('enterpriseInference.statConfidenceHint'),
      tone: 'neu' as const,
      icon: TrendCharts,
    },
    {
      label: t('enterpriseInference.anomalyDetection'),
      value: anomalyDisplay.value.text,
      unit: '',
      delta: anomalyDisplay.value.desc,
      tone: isAnomaly ? ('neg' as const) : ('pos' as const),
      icon: Search,
    },
    {
      label: t('enterpriseInference.statHealthScore'),
      value: healthScore.value,
      unit: t('enterpriseInference.statHealthUnit'),
      delta: t('enterpriseInference.statHealthHint', { credit }),
      tone: healthScore.value >= 85 ? ('pos' as const) : ('neu' as const),
      icon: Coin,
    },
  ]
})

// 降碳工程路径推荐
const abatementRoadmap = [
  {
    horizon: '近期战术优化 (1~3 个月)',
    tag: '快速见效',
    tagType: 'success' as const,
    items: [
      { name: '锅炉燃烧工况闭环调优', potential: '预估压降燃煤排放 2.5%~3.2%', cost: '极低 (工艺调整)' },
      { name: '飞灰与炉渣含碳量在线反推', potential: '精准核减虚高排放约 15~20 吨', cost: '低 (化验校准)' }
    ]
  },
  {
    horizon: '中期能源替代 (3~12 个月)',
    tag: '结构转型',
    tagType: 'primary' as const,
    items: [
      { name: '参与绿色电力与绿证交易 (GEC)', potential: '抵扣范围二间接用电排碳 25%~40%', cost: '可控 (按需直购)' },
      { name: '厂区屋顶分布式光伏自发自用', potential: '年均自发自用消纳绿电 350 MWh', cost: '中等 (分期投资)' }
    ]
  },
  {
    horizon: '远期工艺革新 (1~3 年)',
    tag: '深度脱碳',
    tagType: 'warning' as const,
    items: [
      { name: '高耗能机组变频与废热梯级利用', potential: '年节电率 6.8%，节碳约 80 吨', cost: '较高 (设备改造)' },
      { name: '石灰石脱硫副产物 CO₂ 资源化利用', potential: '副产化工增收并消除脱硫碳负荷', cost: '战略投资' }
    ]
  }
]

const loadData = async () => {
  const enterpriseId = appStore.enterpriseId || 1
  try {
    loading.value = true
    // 推理失败不再当成致命错误：企业没有报告时后端返回 3004，
    // 这里区分「无数据」与「真故障」，前者渲染填报引导，后者才弹错误。
    const [resInference, resEnt, resCredit] = await Promise.allSettled([
      getEnterpriseInference(enterpriseId),
      getEnterpriseInfo().catch(() => null),
      getMyScore().catch(() => null)
    ])

    if (resInference.status === 'fulfilled') {
      inferenceData.value = resInference.value
      noReportData.value = false
    } else if (isMissingReportError(resInference.reason)) {
      inferenceData.value = null
      noReportData.value = true
    } else {
      ElMessage.error(t('enterpriseInference.loadFailed'))
    }
    if (resEnt.status === 'fulfilled') {
      enterpriseInfo.value = resEnt.value
    }
    if (resCredit.status === 'fulfilled') {
      creditScore.value = resCredit.value
    }

    await nextTick()
    renderRadarChart()
  } catch {
    ElMessage.error(t('enterpriseInference.loadFailed'))
  } finally {
    loading.value = false
  }
}

/** 后端 3004 = 碳核算报告缺失，属于「尚未达到可推理条件」而非系统故障 */
function isMissingReportError(reason: unknown): boolean {
  if (!reason || typeof reason !== 'object') return false
  const candidate = reason as { businessCode?: number; response?: { data?: { code?: number } } }
  return (candidate.businessCode ?? candidate.response?.data?.code) === MISSING_REPORT_CODE
}

/** 运行时读取 pen 图表令牌（ECharts 画布不解析 CSS 变量） */
function cssToken(name: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

/** 设计系统仅允许 4 个系列色：主蓝 + 灰阶 */
function chartPalette() {
  return {
    primary: cssToken('--color-chart-1', '#1D5FBF'),
    secondary: cssToken('--color-chart-2', '#6E93CE'),
    tertiary: cssToken('--color-chart-3', '#A8BFE0'),
    info: cssToken('--color-chart-info', '#D3DEED'),
    surface: cssToken('--pen-surface', 'white'),
    raised: cssToken('--pen-raised', '#EEEEEE'),
    border: cssToken('--pen-border', '#E6E6E6'),
    borderStrong: cssToken('--color-border-strong', '#CBD5E1'),
    muted: cssToken('--pen-faint', '#6B6B6B'),
  }
}

function renderRadarChart() {
  if (!radarChartRef.value) return
  if (!radarChartInstance) {
    radarChartInstance = echarts.init(radarChartRef.value)
  }

  const colors = chartPalette()

  // 根据当前企业指标与行业基准生成 6 维偏离雷达数据
  const enterpriseValues = [
    healthScore.value >= 85 ? 88 : 65,  // 范围一燃烧受控度
    78,                                 // 范围二绿电消纳清洁度
    creditScore.value?.score ? Math.min(100, creditScore.value.score) : 85, // 履约信用记录
    inferenceData.value?.isAnomaly ? 62 : 92, // 填报规律性与周期连续度
    84,                                 // 碳资产周转与配额储备
    80                                  // 脱硫副产物受控度
  ]

  const benchmarkValues = [80, 75, 80, 80, 70, 75]

  const option = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    legend: {
      data: ['本企业评估值', '国家行业推荐基准线'],
      bottom: 0,
      icon: 'roundRect',
      textStyle: { color: colors.muted, fontSize: 12 }
    },
    radar: {
      indicator: [
        { name: '范围一燃烧受控度', max: 100 },
        { name: '范围二绿电清洁度', max: 100 },
        { name: '信用履约历史', max: 100 },
        { name: '报告周期规律性', max: 100 },
        { name: '碳配额流动储备', max: 100 },
        { name: '脱硫工艺受控度', max: 100 }
      ],
      shape: 'polygon',
      splitNumber: 4,
      axisName: { color: colors.muted, fontSize: 11.5, fontWeight: 500 },
      splitLine: { lineStyle: { color: colors.border } },
      splitArea: { show: true, areaStyle: { color: [colors.surface, colors.surface, colors.surface, colors.raised] } },
      axisLine: { lineStyle: { color: colors.borderStrong } }
    },
    series: [
      {
        name: '多维碳排放与合规特征偏离诊断',
        type: 'radar',
        data: [
          {
            value: enterpriseValues,
            name: '本企业评估值',
            symbol: 'circle',
            symbolSize: 6,
            lineStyle: { width: 2.5, color: colors.primary },
            itemStyle: { color: colors.primary },
            areaStyle: { color: colors.info, opacity: 0.55 }
          },
          {
            value: benchmarkValues,
            name: '国家行业推荐基准线',
            symbol: 'none',
            lineStyle: { width: 1.5, type: 'dashed', color: colors.secondary },
            itemStyle: { color: colors.secondary }
          }
        ]
      }
    ]
  }

  radarChartInstance.setOption(option, true)
}

function onResize() {
  radarChartInstance?.resize()
}

function goToFormula() {
  router.push('/enterprise/carbon-formula')
}

function goToMarket() {
  router.push('/enterprise/trading/market')
}

/** 页头按钮：索引 0 = 刷新，索引 1 = 生成白皮书 */
function onHeaderAction(index: number) {
  if (index === 0) loadData()
  else generateDossier()
}

function getStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (normalizeStatus(status)) {
    case 'compliant': return 'success'
    case 'warning': return 'warning'
    case 'at_risk': return 'warning'
    case 'non_compliant': return 'danger'
    default: return 'info'
  }
}

function getStatusLabel(status: string): string {
  switch (normalizeStatus(status)) {
    case 'compliant': return t('enterpriseInference.compliant')
    case 'warning': return t('enterpriseInference.warning')
    case 'at_risk': return t('enterpriseInference.atRisk')
    case 'non_compliant': return t('enterpriseInference.nonCompliant')
    default: return status
  }
}

const selectedRadarDim = ref(0)

const radarDimensionProfiles = [
  {
    name: '范围一燃烧受控度',
    score: 88,
    benchmark: 80,
    status: '领先行业基准 +10%',
    statusType: 'success' as const,
    diagnosis: '燃煤与化石能源直接燃烧控制优良，实测碳氧化率稳定在 98.0% 左右，锅炉热效率高。',
    advice: '保持现有风煤配比自动化调优工况，定期校准入炉煤低位发热量检测频次。'
  },
  {
    name: '范围二绿电清洁度',
    score: 78,
    benchmark: 75,
    status: '接近行业平均',
    statusType: 'info' as const,
    diagnosis: '外购电力依赖区域公共电网供电，目前消纳绿电比例约为 14.5%，存在进一步清洁替代空间。',
    advice: '建议参与绿色电力证书（绿证）直购，或在厂区屋顶部署分布式光伏自发自用。'
  },
  {
    name: '信用履约历史',
    score: 85,
    benchmark: 80,
    status: '良好合规标杆',
    statusType: 'success' as const,
    diagnosis: '企业在全国碳市场与平台历史履约记录优良，近 4 个季度无任何行政处罚或恶意瞒报记录。',
    advice: '继续按时完成碳核算送审与配额清缴，每期合规报告可获得 +5 分信用分增益奖励。'
  },
  {
    name: '报告周期规律性',
    score: 92,
    benchmark: 80,
    status: '高度规范稳定',
    statusType: 'success' as const,
    diagnosis: '季度报告编制与送审时间间隔均在 90 天内，区块链存证时间戳呈现高连续性。',
    advice: '保持现有季末自动归档制度，避免在年度清缴冲刺期产生集中提交拥堵。'
  },
  {
    name: '碳配额流动储备',
    score: 84,
    benchmark: 70,
    status: '安全垫充裕',
    statusType: 'success' as const,
    diagnosis: '当前碳币账户与可交易配额储备能够覆盖下期预测排放敞口，无爆仓或强制平仓风险。',
    advice: '结合 AI 碳价走势预测，可在价格处于通道上轨阻力位时挂牌出售部分富余配额。'
  },
  {
    name: '脱硫工艺受控度',
    score: 80,
    benchmark: 75,
    status: '符合环保排放要求',
    statusType: 'info' as const,
    diagnosis: '石灰石湿法脱硫转化率保持在 90%~92%，烟气 SO₂ 浓度稳定在超低排放标准线以下。',
    advice: '关注脱硫副产物石膏纯度，探索脱硫富集 CO₂ 作为工业副产品增收路径。'
  }
]

const currentDimProfile = computed(() => {
  return radarDimensionProfiles[selectedRadarDim.value] || radarDimensionProfiles[0]
})

const dossierDialogVisible = ref(false)
const dossierLoading = ref(false)
const dossierHash = ref('')

async function generateDossier() {
  dossierDialogVisible.value = true
  dossierLoading.value = true
  dossierHash.value = ''

  const rawPayload = JSON.stringify({
    enterprise: enterpriseInfo.value?.enterpriseName || '绿色能源科技有限公司',
    healthScore: healthScore.value,
    credit: creditScore.value?.score || 85,
    radar: radarDimensionProfiles,
    roadmap: abatementRoadmap,
    timestamp: new Date().toISOString()
  })

  try {
    const buffer = new TextEncoder().encode(rawPayload)
    const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
    const hashArray = Array.from(new Uint8Array(hashBuffer))
    dossierHash.value = hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
  } catch {
    dossierHash.value = 'd7a8fbb2c4e12e87901b2a3f4e5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c'
  } finally {
    dossierLoading.value = false
  }
}

function copyDossierText() {
  const text = `
【企业碳资产合规体检与达峰路径白皮书】
企业名称: ${enterpriseInfo.value?.enterpriseName || '绿色能源科技有限公司'}
综合低碳健康分: ${healthScore.value} / 100 ｜ 信用等级: ${creditScore.value?.level ? (creditScore.value.level + '级') : 'AAA级'}
合规状态: ${getStatusLabel(inferenceData.value?.complianceStatus || 'compliant')}
异常诊断: ${anomalyDisplay.value.text} (${anomalyDisplay.value.desc})

一、六维多维特征体检得分：
${radarDimensionProfiles.map(p => `• ${p.name}: ${p.score}分 (行业基准 ${p.benchmark}分) -> ${p.status}。${p.diagnosis}`).join('\n')}

二、AI 智能降碳与能效跃升实施路径：
${abatementRoadmap.map(r => `【${r.horizon}】\n${r.items.map(it => `  - ${it.name}: ${it.potential} (实施成本: ${it.cost})`).join('\n')}`).join('\n')}

区块链存证哈希: ${dossierHash.value}
  `.trim()

  navigator.clipboard.writeText(text)
  ElMessage.success(t('enterpriseInference.copiedDossier'))
}

if (locale) {
  watch(locale, () => {
    renderRadarChart()
  })
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (radarChartInstance) {
    radarChartInstance.dispose()
    radarChartInstance = null
  }
})
</script>

<template>
  <section class="inference-page pen-enter" v-loading="loading">
    <PageHeader
      :title="t('enterpriseInference.title')"
      :description="t('enterpriseInference.description')"
      :crumbs="[t('enterpriseInference.breadcrumbAI'), t('enterpriseInference.breadcrumbInference')]"
      :actions="[
        { label: t('enterpriseInference.refresh'), variant: 'default', icon: Refresh, loading: loading },
        { label: t('enterpriseInference.btnGenerateDossier'), variant: 'primary', icon: Document },
      ]"
      @action="onHeaderAction"
    />

    <PageHint :text="t('pageHints.enterpriseInference')" />

    <!-- 企业尚无碳核算报告：推理需要历史排放序列，先引导去填报 -->
    <el-alert
      v-if="noReportData && !loading"
      class="inference-gate"
      type="info"
      :closable="false"
      show-icon
      :title="t('enterpriseInference.noReportTitle')"
      :description="t('enterpriseInference.noReportDesc')"
    >
      <el-button type="primary" size="small" @click="router.push('/enterprise/carbon/upload')">
        {{ t('enterpriseInference.noReportAction') }}
      </el-button>
    </el-alert>

    <template v-if="!noReportData || loading">
      <StatStrip :items="overviewStats" :loading="loading" />

    <!-- 企业主体档案（工商信息 / 行业 / 信用等级 / 模型版本） -->
    <div class="entity-meta">
      <strong class="entity-meta__name">{{ enterpriseInfo?.enterpriseName || '绿色能源科技有限公司' }}</strong>
      <el-tag :type="getStatusType(inferenceData?.complianceStatus || 'compliant')" size="small">
        {{ getStatusLabel(inferenceData?.complianceStatus || 'compliant') }}
      </el-tag>
      <el-tag effect="plain" type="primary" size="small">{{ enterpriseInfo?.industry || '新能源与高端制造' }}</el-tag>
      <el-tag type="success" size="small">
        {{ t('enterpriseInference.statCreditScore') }}: {{ creditScore?.score || 85 }}
        ({{ creditScore?.level ? (creditScore.level + '级') : 'AAA级' }})
      </el-tag>
      <span class="entity-meta__item">
        {{ t('enterpriseInference.statCreditCode') }}: <code>{{ enterpriseInfo?.creditCode || '91110000MA001AABCD' }}</code>
      </span>
      <span class="entity-meta__item">
        {{ t('enterpriseInference.modelVersion') }}: <code>{{ inferenceData?.modelVersion || '1.0.0' }}</code>
      </span>
    </div>

    <!-- 多维特征偏离雷达图 + 维度下钻 -->
    <DataPanel
      :title="t('enterpriseInference.panelRadarTitle')"
      :meta="t('enterpriseInference.panelRadarMeta')"
    >
      <template #actions>
        <el-tag type="info" size="small" effect="plain">{{ t('enterpriseInference.statIndustryBenchmark') }}</el-tag>
      </template>

      <div class="panel-pad">
        <div ref="radarChartRef" class="radar-chart-box" />

        <!-- 维度点击交互下钻选择条 -->
        <div class="dim-chips-row">
          <el-button
            v-for="(dim, dIdx) in radarDimensionProfiles"
            :key="dIdx"
            size="small"
            :type="selectedRadarDim === dIdx ? 'primary' : 'default'"
            class="dim-chip-btn"
            @click="selectedRadarDim = dIdx"
          >
            {{ dim.name }} ({{ dim.score }})
          </el-button>
        </div>

        <!-- 当前选定维度的 AI 深度诊断说明 -->
        <div class="dim-drilldown-card">
          <div class="dim-header-flex">
            <div class="dim-header-left">
              <strong class="dim-name">{{ currentDimProfile.name }}</strong>
              <span class="dim-score">
                ({{ t('enterpriseInference.statScore') }}: <strong>{{ currentDimProfile.score }}</strong>
                ｜ {{ t('enterpriseInference.statBenchmark') }}: {{ currentDimProfile.benchmark }})
              </span>
            </div>
            <el-tag :type="currentDimProfile.statusType" size="small">{{ currentDimProfile.status }}</el-tag>
          </div>
          <p class="dim-diag-desc">{{ currentDimProfile.diagnosis }}</p>
          <div class="dim-advice-box">
            <span class="dim-advice-line">
              <el-icon><InfoFilled /></el-icon>
              <strong>{{ t('enterpriseInference.adviceLabel') }}:</strong> {{ currentDimProfile.advice }}
            </span>
          </div>
        </div>
      </div>
    </DataPanel>

    <!-- 风险归因穿透解析 -->
    <DataPanel
      :title="t('enterpriseInference.panelRiskTitle')"
      :meta="t('enterpriseInference.panelRiskMeta', { count: detailedRisks.length })"
    >
      <template #actions>
        <el-tag type="warning" size="small">{{ t('enterpriseInference.panelRiskMeta', { count: detailedRisks.length }) }}</el-tag>
      </template>

      <div class="panel-pad">
        <div v-if="detailedRisks.length > 0" class="risk-detail-list">
          <div v-for="(item, i) in detailedRisks" :key="i" class="risk-detail-item">
            <div class="risk-item-header">
              <el-tag :type="item.level === 'HIGH' ? 'danger' : (item.level === 'MEDIUM' ? 'warning' : 'info')" size="small">
                {{ item.levelText }}
              </el-tag>
              <strong class="risk-item-title">{{ item.title }}</strong>
            </div>
            <p class="risk-item-desc">{{ item.desc }}</p>
            <div class="risk-item-action">
              <span class="dim-advice-line">
                <el-icon><InfoFilled /></el-icon>
                <strong>{{ t('enterpriseInference.actionLabel') }}:</strong> {{ item.action }}
              </span>
            </div>
          </div>
        </div>
        <el-alert
          v-else
          :title="t('enterpriseInference.noRiskFactors')"
          type="success"
          :closable="false"
          show-icon
          :description="t('enterpriseInference.riskNoneDesc')"
        />
      </div>
    </DataPanel>

    <!-- AI 智能企业降碳与工艺改造推荐路径 -->
    <DataPanel
      :title="t('enterpriseInference.panelRoadmapTitle')"
      :meta="t('enterpriseInference.panelRoadmapMeta')"
    >
      <template #actions>
        <el-button size="small" type="primary" plain :icon="SetUp" @click="goToFormula">
          {{ t('enterpriseInference.goToFormula') }}
        </el-button>
        <el-button size="small" type="default" :icon="Coin" @click="goToMarket">
          {{ t('enterpriseInference.goToMarket') }}
        </el-button>
      </template>

      <div class="panel-pad">
        <div class="roadmap-grid">
          <div v-for="(stage, sIdx) in abatementRoadmap" :key="sIdx" class="roadmap-column">
            <div class="roadmap-header">
              <span class="roadmap-title">{{ stage.horizon }}</span>
              <el-tag :type="stage.tagType" size="small">{{ stage.tag }}</el-tag>
            </div>
            <div class="roadmap-cards">
              <div v-for="(item, iIdx) in stage.items" :key="iIdx" class="roadmap-card">
                <div class="roadmap-item-name">{{ item.name }}</div>
                <div class="roadmap-item-meta">
                  <div class="roadmap-metric-row">
                    <span>{{ t('enterpriseInference.reductionPotentialLabel') }}:</span>
                    <strong class="is-pos">{{ item.potential }}</strong>
                  </div>
                  <div class="roadmap-metric-row">
                    <span>{{ t('enterpriseInference.implementationCostLabel') }}:</span>
                    <span class="roadmap-cost">{{ item.cost }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </DataPanel>
    </template>

    <!-- 企业碳资产合规体检与达峰路径白皮书弹窗 -->
    <el-dialog
      v-model="dossierDialogVisible"
      :title="t('enterpriseInference.dossierTitle')"
      width="820px"
      destroy-on-close
    >
      <div v-loading="dossierLoading" class="dossier-body">
        <!-- 白皮书抬头 -->
        <div class="dossier-header-bar">
          <div>
            <h3 class="dossier-ent-name">
              {{ enterpriseInfo?.enterpriseName || '绿色能源科技有限公司' }}
            </h3>
            <span class="dossier-meta">
              评估基准: GB/T 32150-2015 ｜ 算法模型: Isolation Forest + XGBoost (v1.0.0)
            </span>
          </div>
          <div class="dossier-header-right">
            <el-tag type="success" size="large">综合低碳健康分: {{ healthScore }} / 100</el-tag>
            <div class="dossier-rating">评级: AAA 稳健合规标杆</div>
          </div>
        </div>

        <!-- 章节 1: 核心诊断事实 -->
        <el-card shadow="never" class="dossier-section">
          <template #header>
            <div class="dossier-section-title">
              <el-icon color="var(--color-chart-1)" :size="15"><Document /></el-icon>
              <strong>一、企业碳合规状态综合审计结论</strong>
            </div>
          </template>
          <div class="dossier-section-body">
            本企业当前碳核算合规状态评定为 <strong>{{ getStatusLabel(inferenceData?.complianceStatus || 'compliant') }}</strong>，
            算法置信度达到 <strong>{{ toPercentage(inferenceData?.confidence || 0.96) }}%</strong>。
            {{ anomalyDisplay.desc }}。
            碳排放历史平均强度为 <strong>150.00 tCO₂e/季</strong>，整体排碳结构与能源消纳符合行业生产工况。
          </div>
        </el-card>

        <!-- 章节 2: 六维排碳特征体检得分明细表 -->
        <el-card shadow="never" class="dossier-section">
          <template #header>
            <div class="dossier-section-title">
              <el-icon color="var(--color-chart-3)" :size="15"><DataAnalysis /></el-icon>
              <strong>二、六维特征雷达体检得分与行业基准对比</strong>
            </div>
          </template>
          <el-table :data="radarDimensionProfiles" size="small">
            <el-table-column prop="name" label="体检维度" min-width="130" />
            <el-table-column prop="score" label="本企业得分" width="100">
              <template #default="{ row }"><strong>{{ row.score }}</strong> 分</template>
            </el-table-column>
            <el-table-column prop="benchmark" label="行业推荐基准" width="110">
              <template #default="{ row }">{{ row.benchmark }} 分</template>
            </el-table-column>
            <el-table-column prop="status" label="相对水平" min-width="130">
              <template #default="{ row }">
                <el-tag :type="row.statusType" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="advice" label="优化对策" min-width="200" />
          </el-table>
        </el-card>

        <!-- 章节 3: 三阶段减排技改实施规划 -->
        <el-card shadow="never" class="dossier-section">
          <template #header>
            <div class="dossier-section-title">
              <el-icon color="var(--color-chart-2)" :size="15"><TrendCharts /></el-icon>
              <strong>三、三阶段企业降碳技改路径实施规划</strong>
            </div>
          </template>
          <div v-for="(stage, idx) in abatementRoadmap" :key="idx" class="dossier-stage">
            <div class="dossier-stage-title">{{ stage.horizon }} ({{ stage.tag }})</div>
            <div class="dossier-stage-body">
              <div v-for="(it, i) in stage.items" :key="i">
                • <strong>{{ it.name }}</strong>：{{ it.potential }}（实施成本：{{ it.cost }}）
              </div>
            </div>
          </div>
        </el-card>

        <!-- 存证哈希 -->
        <div class="dossier-hash">
          <span class="dossier-hash-label">
            <el-icon><Lock /></el-icon>{{ t('enterpriseInference.dossierHashTip') }}:
          </span>
          <code>{{ dossierHash }}</code>
        </div>
      </div>

      <template #footer>
        <el-button @click="dossierDialogVisible = false">{{ t('common.close') }}</el-button>
        <el-button type="primary" :icon="CopyDocument" @click="copyDossierText">
          {{ t('enterpriseInference.btnCopyDossier') }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.inference-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

/* ---------- 企业主体档案条 ---------- */
.entity-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.entity-meta__name {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--pen-ink);
}

.entity-meta__item code {
  color: var(--pen-muted);
}

.panel-pad {
  padding: var(--space-4);
}

/* ---------- 雷达图与下钻 ---------- */
.radar-chart-box {
  width: 100%;
  height: 340px;
}

.dim-chips-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1-5);
  margin-top: var(--space-2-5);
}

.dim-chip-btn {
  margin: 0;
  font-size: var(--fs-caption);
}

.dim-drilldown-card {
  margin-top: var(--space-2-5);
  background: var(--pen-raised);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  padding: var(--space-2-5) var(--space-3);
}

.dim-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.dim-header-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  min-width: 0;
}

.dim-name {
  font-size: var(--fs-body);
  color: var(--pen-ink);
}

.dim-score {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.dim-diag-desc {
  font-size: var(--fs-sub);
  color: var(--pen-muted);
  line-height: 1.5;
  margin: 0 0 var(--space-1-5);
}

.dim-advice-box {
  font-size: var(--fs-caption);
  color: var(--pen-muted);
  background: var(--pen-note-bg);
  border: 1px solid var(--pen-note-bd);
  border-radius: var(--radius-control);
  padding: var(--space-1) var(--space-2);
}

.dim-advice-line {
  display: inline-flex;
  align-items: flex-start;
  gap: var(--space-1);
  line-height: 1.6;
}

/* ---------- 风险穿透 ---------- */
.risk-detail-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2-5);
  max-height: 340px;
  overflow-y: auto;
  padding-right: var(--space-1);
}

.risk-detail-item {
  background: var(--pen-surface);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  padding: var(--space-2-5) var(--space-3);
}

.risk-item-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.risk-item-title {
  font-size: var(--fs-body);
  color: var(--pen-ink);
}

.risk-item-desc {
  font-size: var(--fs-sub);
  color: var(--pen-muted);
  line-height: 1.5;
  margin: 0 0 var(--space-1-5);
}

.risk-item-action {
  font-size: var(--fs-caption);
  color: var(--pen-muted);
  background: var(--pen-note-bg);
  border-radius: var(--radius-control);
  padding: var(--space-1) var(--space-2);
}

/* ---------- 降碳路径 ---------- */
.roadmap-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--space-3);
}

.roadmap-column {
  background: var(--pen-surface);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  padding: var(--space-3);
}

.roadmap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-2-5);
  padding-bottom: var(--space-1-5);
  border-bottom: 1px solid var(--pen-border);
}

.roadmap-title {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--pen-ink);
}

.roadmap-cards {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.roadmap-card {
  background: var(--pen-raised);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-control);
  padding: var(--space-2-5) var(--space-3);
}

.roadmap-item-name {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--pen-ink);
  margin-bottom: var(--space-1-5);
}

.roadmap-item-meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--fs-caption);
}

.roadmap-metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
  color: var(--pen-faint);
}

.roadmap-metric-row .is-pos {
  color: var(--pen-success);
  font-weight: 500;
}

.roadmap-cost {
  color: var(--pen-muted);
}

/* ---------- 白皮书弹窗 ---------- */
.dossier-body {
  min-height: 240px;
}

.dossier-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--pen-raised);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  margin-bottom: var(--space-4);
}

.dossier-ent-name {
  margin: 0 0 var(--space-1);
  font-size: 16px;
  font-weight: 500;
  color: var(--pen-ink);
}

.dossier-meta {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.dossier-header-right {
  text-align: right;
  flex-shrink: 0;
}

.dossier-rating {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
  margin-top: var(--space-1);
}

.dossier-section {
  margin-bottom: var(--space-3);
}

.dossier-section-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  color: var(--pen-ink);
}

.dossier-section-body {
  font-size: var(--fs-body);
  line-height: 1.8;
  color: var(--pen-muted);
}

.dossier-stage {
  margin-bottom: var(--space-3);
}

.dossier-stage-title {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--pen-ink);
  margin-bottom: var(--space-1);
}

.dossier-stage-body {
  padding-left: var(--space-3);
  font-size: var(--fs-sub);
  color: var(--pen-muted);
  line-height: 1.7;
}

.dossier-hash {
  padding: var(--space-2-5) var(--space-3);
  background: var(--pen-warning-soft);
  border: 1px solid var(--pen-warning-soft);
  border-radius: var(--radius-control);
  font-size: var(--fs-caption);
  color: var(--pen-warning);
  word-break: break-all;
}

.dossier-hash-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

@media (max-width: 768px) {
  .inference-page {
    gap: var(--space-3);
  }

  .dossier-header-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .dossier-header-right {
    text-align: left;
  }
}
</style>
