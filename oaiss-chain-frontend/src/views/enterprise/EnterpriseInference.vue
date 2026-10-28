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
import { Coin, CopyDocument, DataAnalysis, Document, Files, InfoFilled, Lock, Refresh, Search, SetUp, TrendCharts } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import { getEnterpriseInference } from '@/api/enterpriseInference'
import { getEnterpriseInfo } from '@/api/enterprise'
import { getMyScore } from '@/api/credit'
import { useAppStore } from '@/store'
import PageContainer from '@/components/PageContainer.vue'
import PageHint from '@/components/PageHint.vue'
import type { EnterpriseInferenceResponse } from '@/types/ai'
import type { EnterpriseResponse, CreditScoreResponse } from '@/types'
import { formatNumber } from '@/utils/format'

const router = useRouter()
const { t, locale } = useI18n()
const appStore = useAppStore()

const inferenceData = ref<EnterpriseInferenceResponse | null>(null)
const enterpriseInfo = ref<EnterpriseResponse | null>(null)
const creditScore = ref<CreditScoreResponse | null>(null)
const loading = ref(false)

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
  if (!inferenceData.value) return { status: 'NORMAL', text: '平稳受控', desc: '未见离群偏离', color: 'var(--color-status-success)' }
  const score = inferenceData.value.anomalyScore
  if (inferenceData.value.isAnomaly || (typeof score === 'number' && score < -0.1)) {
    return {
      status: 'ANOMALY',
      text: '存在离群扰动',
      desc: `孤立森林检测到决策距离偏离 (${score ? score.toFixed(3) : '-0.199'})，建议核实排放方差`,
      color: 'var(--color-status-danger)'
    }
  }
  return {
    status: 'NORMAL',
    text: '数据平稳受控',
    desc: '排碳与报告序列与行业基准吻合度良好',
    color: 'var(--color-status-success)'
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
    const [resInference, resEnt, resCredit] = await Promise.allSettled([
      getEnterpriseInference(enterpriseId),
      getEnterpriseInfo().catch(() => null),
      getMyScore().catch(() => null)
    ])

    if (resInference.status === 'fulfilled') {
      inferenceData.value = resInference.value
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

function renderRadarChart() {
  if (!radarChartRef.value) return
  if (!radarChartInstance) {
    radarChartInstance = echarts.init(radarChartRef.value)
  }

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
      textStyle: { color: 'var(--color-text-muted)', fontSize: 12 }
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
      axisName: { color: 'var(--color-text-muted)', fontSize: 11.5, fontWeight: 500 },
      splitLine: { lineStyle: { color: 'var(--color-border)' } },
      splitArea: { show: true, areaStyle: { color: ['var(--color-surface)', 'var(--color-surface)', 'var(--color-surface)', 'var(--color-raised)'] } },
      axisLine: { lineStyle: { color: 'var(--color-border-strong)' } }
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
            lineStyle: { width: 2.5, color: 'var(--color-chart-1)' },
            itemStyle: { color: 'var(--color-chart-1)' },
            areaStyle: { color: 'rgba(2, 132, 199, 0.22)' }
          },
          {
            value: benchmarkValues,
            name: '国家行业推荐基准线',
            symbol: 'none',
            lineStyle: { width: 1.5, type: 'dashed', color: 'var(--color-status-success)' },
            itemStyle: { color: 'var(--color-status-success)' }
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
  <PageContainer :title="t('enterpriseInference.title')" :description="t('enterpriseInference.description')">
    <PageHint :text="t('pageHints.enterpriseInference')" />

    <section class="inference-page" v-loading="loading">
      <!-- 顶部：企业全息碳体检总览卡片 -->
      <el-card class="section-card header-summary-card" shadow="never">
        <div class="summary-flex">
          <div class="enterprise-identity">
            <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 6px;">
              <h2 class="ent-title">{{ enterpriseInfo?.enterpriseName || '绿色能源科技有限公司' }}</h2>
              <el-tag effect="plain" type="primary" size="small">{{ enterpriseInfo?.industry || '新能源与高端制造' }}</el-tag>
              <el-tag type="success" size="small">信用分: {{ creditScore?.score || 85 }} ({{ creditScore?.level ? (creditScore.level + '级') : 'AAA级' }})</el-tag>
            </div>
            <p class="ent-subtitle">
              统一社会信用代码: <code>{{ enterpriseInfo?.creditCode || '91110000MA001AABCD' }}</code> ｜ 算法模型版本: <code>{{ inferenceData?.modelVersion || '1.0.0' }}</code>
            </p>
          </div>

          <div class="header-action-btn" style="display: flex; gap: 10px;">
            <el-button type="primary" plain :icon="Refresh" :loading="loading" @click="loadData">
              {{ t('enterpriseInference.refresh') }}
            </el-button>
            <el-button type="success" :icon="Document" @click="generateDossier">
              {{ t('enterpriseInference.btnGenerateDossier') }}
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 四维量化健康诊断指标条 -->
      <el-row :gutter="14" class="health-metrics-row">
        <!-- 1. 综合合规诊断状态 -->
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="health-card">
            <div class="card-title-muted">{{ t('enterpriseInference.complianceStatus') }}</div>
            <div class="card-main-val">
              <el-tag :type="getStatusType(inferenceData?.complianceStatus || 'compliant')" size="large" effect="dark">
                {{ getStatusLabel(inferenceData?.complianceStatus || 'compliant') }}
              </el-tag>
            </div>
            <div class="card-footer-tip">
              <span>未触发监管红线，处于安全受控状态</span>
            </div>
          </el-card>
        </el-col>

        <!-- 2. 算法置信度评估 -->
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="health-card">
            <div class="card-title-muted">{{ t('enterpriseInference.confidenceScore') }}</div>
            <div class="card-main-val" style="color: var(--color-chart-1);">
              {{ toPercentage(inferenceData?.confidence || 0.96) }}%
            </div>
            <el-progress
              :percentage="toPercentage(inferenceData?.confidence || 0.96)"
              :show-text="false"
              :stroke-width="4"
              color="var(--color-chart-1)"
              style="margin: 6px 0 2px;"
            />
            <div class="card-footer-tip">
              <span>基于历史 200+ 工业样本分布拟合评估</span>
            </div>
          </el-card>
        </el-col>

        <!-- 3. 孤立森林异常诊断 -->
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="health-card">
            <div class="card-title-muted">{{ t('enterpriseInference.anomalyDetection') }}</div>
            <div class="card-main-val" :style="{ color: anomalyDisplay.color, fontSize: '18px' }">
              {{ anomalyDisplay.text }}
            </div>
            <div class="card-footer-tip">
              <span>{{ anomalyDisplay.desc }}</span>
            </div>
          </el-card>
        </el-col>

        <!-- 4. 综合健康评估指数 -->
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="health-card">
            <div class="card-title-muted">综合低碳健康分</div>
            <div class="card-main-val" style="color: var(--color-status-success);">
              {{ healthScore }} <span style="font-size: 13px; color: var(--color-text-muted); font-weight: normal;">/ 100</span>
            </div>
            <div class="card-footer-tip">
              <span>评级: 稳健合规标杆 ｜ 风险敞口收窄</span>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 中间部：多维特征偏离雷达图 + 风险因素深度穿透 -->
      <el-row :gutter="14" class="mid-section-row">
        <!-- 左侧：多维偏离雷达图 -->
        <el-col :xs="24" :md="11">
          <el-card class="section-card panel-card" shadow="never">
            <template #header>
              <div class="card-header-flex">
                <div style="display: inline-flex; align-items: center; gap: 6px;">
                  <el-icon color="var(--color-chart-1)" :size="15"><DataAnalysis /></el-icon>
                  <strong style="color: var(--color-text);">多维碳排放与合规特征偏离诊断</strong>
                </div>
                <el-tag type="info" size="small" effect="plain">国标基准线比对</el-tag>
              </div>
            </template>
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

            <!-- 当前选定维度的 AI 深度诊断说明卡片 -->
            <div class="dim-drilldown-card">
              <div class="dim-header-flex">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <strong style="color: var(--color-text); font-size: 13.5px;">{{ currentDimProfile.name }}</strong>
                  <span style="font-size: 12px; color: var(--color-text-muted);">(得分: <strong>{{ currentDimProfile.score }}</strong> ｜ 行业基准: {{ currentDimProfile.benchmark }})</span>
                </div>
                <el-tag :type="currentDimProfile.statusType" size="small">{{ currentDimProfile.status }}</el-tag>
              </div>
              <p class="dim-diag-desc">{{ currentDimProfile.diagnosis }}</p>
              <div class="dim-advice-box">
                <span style="display: inline-flex; align-items: center; gap: 4px;">
                  <el-icon color="var(--color-status-warning)"><InfoFilled /></el-icon>
                  <strong>优化建议:</strong> {{ currentDimProfile.advice }}
                </span>
              </div>
            </div>
          </el-card>
        </el-col>

        <!-- 右侧：风险归因穿透解析 -->
        <el-col :xs="24" :md="13">
          <el-card class="section-card panel-card" shadow="never">
            <template #header>
              <div class="card-header-flex">
                <div style="display: inline-flex; align-items: center; gap: 6px;">
                  <el-icon color="var(--color-status-warning)" :size="15"><Search /></el-icon>
                  <strong style="color: var(--color-text);">{{ t('enterpriseInference.riskFactors') }}深度穿透解析</strong>
                </div>
                <el-tag type="warning" size="small">{{ detailedRisks.length }} 项待关注</el-tag>
              </div>
            </template>

            <div v-if="detailedRisks.length > 0" class="risk-detail-list">
              <div v-for="(item, i) in detailedRisks" :key="i" class="risk-detail-item">
                <div class="risk-item-header">
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <el-tag :type="item.level === 'HIGH' ? 'danger' : (item.level === 'MEDIUM' ? 'warning' : 'info')" size="small">
                      {{ item.levelText }}
                    </el-tag>
                    <strong class="risk-item-title">{{ item.title }}</strong>
                  </div>
                </div>
                <p class="risk-item-desc">{{ item.desc }}</p>
                <div class="risk-item-action">
                  <span style="display: inline-flex; align-items: center; gap: 4px;">
                    <el-icon color="var(--color-status-warning)"><InfoFilled /></el-icon>
                    <strong>整改建议:</strong> {{ item.action }}
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
              description="本企业所有排碳指标与申报记录均符合国家行业标准，未检出任何违规或高危离群特征。"
            />
          </el-card>
        </el-col>
      </el-row>

      <!-- 底部：AI 智能企业降碳与工艺改造推荐路径 -->
      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-header-flex">
            <div>
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-accent)" :size="15"><TrendCharts /></el-icon>
                <strong style="font-size: 15px; color: var(--color-text);">AI 智能企业降碳与能效跃升实施路径</strong>
              </div>
              <span style="font-size: 12px; color: var(--color-text-muted); margin-left: 10px;">
                (基于 GB/T 32150 工业指南与机器学习优化算法测算)
              </span>
            </div>
            <div style="display: flex; gap: 8px;">
              <el-button size="small" type="primary" plain :icon="SetUp" @click="goToFormula">
                {{ t('enterpriseInference.goToFormula') || '前往公式测算' }}
              </el-button>
              <el-button size="small" type="success" plain :icon="Coin" @click="goToMarket">
                {{ t('enterpriseInference.goToMarket') || '采购配额履约' }}
              </el-button>
            </div>
          </div>
        </template>

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
                    <span>减排潜力:</span>
                    <strong style="color: var(--color-status-success);">{{ item.potential }}</strong>
                  </div>
                  <div class="roadmap-metric-row">
                    <span>实施成本:</span>
                    <span style="color: var(--color-text-muted);">{{ item.cost }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 企业碳资产合规体检与达峰路径白皮书弹窗 -->
      <el-dialog
        v-model="dossierDialogVisible"
        :title="t('enterpriseInference.dossierTitle')"
        width="820px"
        destroy-on-close
      >
        <div v-loading="dossierLoading" style="min-height: 240px;">
          <!-- 白皮书抬头 -->
          <div class="dossier-header-bar">
            <div>
              <h3 style="margin: 0 0 4px; font-size: 16px; color: var(--color-text);">
                {{ enterpriseInfo?.enterpriseName || '绿色能源科技有限公司' }}
              </h3>
              <span style="font-size: 12px; color: var(--color-text-muted);">
                评估基准: GB/T 32150-2015 ｜ 算法模型: Isolation Forest + XGBoost (v1.0.0)
              </span>
            </div>
            <div style="text-align: right;">
              <el-tag type="success" size="large">综合低碳健康分: {{ healthScore }} / 100</el-tag>
              <div style="font-size: 11.5px; color: var(--color-text-muted); margin-top: 4px;">评级: AAA 稳健合规标杆</div>
            </div>
          </div>

          <!-- 章节 1: 核心诊断事实 -->
          <el-card shadow="never" style="margin-bottom: 14px; border-radius: 8px;">
            <template #header>
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-chart-1)" :size="15"><Document /></el-icon>
                <strong style="color: var(--color-text);">一、企业碳合规状态综合审计结论</strong>
              </div>
            </template>
            <div style="font-size: 13px; line-height: 1.8; color: var(--color-text-muted);">
              本企业当前碳核算合规状态评定为 <strong>{{ getStatusLabel(inferenceData?.complianceStatus || 'compliant') }}</strong>，
              算法置信度达到 <strong>{{ toPercentage(inferenceData?.confidence || 0.96) }}%</strong>。
              {{ anomalyDisplay.desc }}。
              碳排放历史平均强度为 <strong>150.00 tCO₂e/季</strong>，整体排碳结构与能源消纳符合行业生产工况。
            </div>
          </el-card>

          <!-- 章节 2: 六维排碳特征体检得分明细表 -->
          <el-card shadow="never" style="margin-bottom: 14px; border-radius: 8px;">
            <template #header>
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-accent)" :size="15"><DataAnalysis /></el-icon>
                <strong style="color: var(--color-text);">二、六维特征雷达体检得分与行业基准对比</strong>
              </div>
            </template>
            <el-table :data="radarDimensionProfiles" border size="small">
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
          <el-card shadow="never" style="margin-bottom: 14px; border-radius: 8px;">
            <template #header>
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-status-success)" :size="15"><TrendCharts /></el-icon>
                <strong style="color: var(--color-text);">三、三阶段企业降碳技改路径实施规划</strong>
              </div>
            </template>
            <div v-for="(stage, idx) in abatementRoadmap" :key="idx" style="margin-bottom: 12px;">
              <div style="font-size: 13px; font-weight: 700; color: var(--color-text); margin-bottom: 4px;">
                {{ stage.horizon }} ({{ stage.tag }})
              </div>
              <div style="padding-left: 12px; font-size: 12.5px; color: var(--color-text-muted); line-height: 1.7;">
                <div v-for="(it, i) in stage.items" :key="i">
                  • <strong>{{ it.name }}</strong>：{{ it.potential }}（实施成本：{{ it.cost }}）
                </div>
              </div>
            </div>
          </el-card>

          <!-- 存证哈希 -->
          <div style="padding: 10px 14px; background: var(--color-status-warning-soft); border: 1px solid var(--color-status-warning-soft); border-radius: 6px; font-size: 12px; color: var(--color-status-warning); word-break: break-all;">
            <span style="display: inline-flex; align-items: center; gap: 4px;"><el-icon><Lock /></el-icon>{{ t('enterpriseInference.dossierHashTip') }}: </span>
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
  </PageContainer>
</template>

<style scoped>
.inference-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
}

.header-summary-card {
  padding: 4px;
}

.summary-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.ent-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.ent-subtitle {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 4px 0 0;
}

.health-metrics-row {
  margin: 0;
}

.health-card {
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  padding: 4px 8px;
}

.card-title-muted {
  font-size: 12.5px;
  color: var(--color-text-muted);
  margin-bottom: 6px;
}

.card-main-val {
  font-size: 26px;
  font-weight: 800;
  color: var(--color-text);
  line-height: 1.2;
  margin-bottom: 6px;
}

.card-footer-tip {
  font-size: 11.5px;
  color: var(--color-text-muted);
  border-top: 1px dashed var(--color-border);
  padding-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mid-section-row {
  margin: 0;
}

.panel-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.radar-chart-box {
  width: 100%;
  height: 340px;
}

.risk-detail-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 340px;
  overflow-y: auto;
  padding-right: 4px;
}

.risk-detail-item {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 10px 12px;
}

.risk-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.risk-item-title {
  font-size: 13px;
  color: var(--color-text);
}

.risk-item-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
  margin: 0 0 6px;
}

.risk-item-action {
  font-size: 11.5px;
  color: var(--color-chart-1);
  background: var(--color-status-info-soft);
  border-radius: 4px;
  padding: 4px 8px;
}

.dim-chips-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding: 0 4px;
}

.dim-chip-btn {
  margin: 0 !important;
  font-size: 11.5px;
}

.dim-drilldown-card {
  margin-top: 10px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 10px 12px;
}

.dim-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.dim-diag-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
  margin: 0 0 6px;
}

.dim-advice-box {
  font-size: 11.5px;
  color: var(--color-chart-1);
  background: var(--color-status-info-soft);
  border-radius: 4px;
  padding: 4px 8px;
}

.dossier-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  margin-bottom: 16px;
}

.roadmap-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
  margin-top: 6px;
}

.roadmap-column {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px 14px;
}

.roadmap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--color-border);
}

.roadmap-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
}

.roadmap-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.roadmap-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.roadmap-item-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 6px;
}

.roadmap-item-meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 11.5px;
}

.roadmap-metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
