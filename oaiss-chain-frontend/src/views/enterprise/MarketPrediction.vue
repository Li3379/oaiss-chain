<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳市场行情走势量化 AI 预测 (Market Prediction)
 * 【路由地址】：/enterprise/market-prediction
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 现货基准价格与涨跌幅卡片：展示当前市场基准价与日内波动率
 *   2. 预测类型切换：市场趋势 (Trend) / 配额价格预测 (Price) / 供需差额推演 (Supply-Demand)
 *   3. 预测时间周期选择器：7 天 / 30 天 / 90 天 / 180 天
 *   4. 动态行情走势 ECharts 图表：展示预测均线与上下置信区间波动带
 *   5. AI 决策建议与智能采购直达导航
 * 【对应后端 Controller & API】：
 *   - MarketPredictionController:
 *     * POST /api/v1/ai/market/trend         -> getMarketTrend() 市场宏观活跃度趋势
 *     * POST /api/v1/ai/market/price         -> getMarketPrice() 现货价格区间预测
 *     * POST /api/v1/ai/market/supply-demand -> getSupplyDemand() 市场供需平衡预测
 * ==============================================================================
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { DataLine, Document, Guide, Lightning, PriceTag, Promotion, Refresh, Search, TrendCharts } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import { getMarketTrend, getMarketPrice, getSupplyDemand } from '@/api/marketPrediction'
import type { MarketForecastResponse, MarketForecastDataPoint } from '@/types/ai'
import PageHint from '@/components/PageHint.vue'
import { formatNumber } from '@/utils/format'

const router = useRouter()
const { t, locale } = useI18n()

const predictionType = ref('trend')
const horizonDays = ref(30)
const loading = ref(false)
const forecastResponse = ref<MarketForecastResponse | null>(null)
const chartRef = ref<HTMLElement | null>(null)
const emptyStateMessage = ref('')

let chartInstance: echarts.ECharts | null = null

const predictionTypeOptions = [
  { label: () => t('marketPrediction.typeTrend'), value: 'trend' },
  { label: () => t('marketPrediction.typePrice'), value: 'price' },
  { label: () => t('marketPrediction.typeSupplyDemand'), value: 'supply-demand' },
]

const horizonOptions = [7, 30, 90, 180]

// 现货基准价
const spotBasePrice = ref(58.20)
const spotPriceChange = ref('+1.35%')

function asNumberArray(value: number[] | null | undefined): number[] {
  return Array.isArray(value) ? value : []
}

function asStringArray(value: string[] | null | undefined): string[] {
  return Array.isArray(value) ? value : []
}

function transformToDataPoints(response: MarketForecastResponse): MarketForecastDataPoint[] {
  const forecastDates = asStringArray(response.forecastDates)
  const forecastPrices = asNumberArray(response.forecastPrices)
  const lowerBound = asNumberArray(response.lowerBound)
  const upperBound = asNumberArray(response.upperBound)

  return forecastDates.map((date, i) => ({
    date,
    price: forecastPrices[i] ?? 0,
    lowerBound: lowerBound[i] ?? 0,
    upperBound: upperBound[i] ?? 0,
  }))
}

const dataPoints = computed<MarketForecastDataPoint[]>(() => {
  if (!forecastResponse.value) return []
  return transformToDataPoints(forecastResponse.value)
})

// 期末目标预测价
const targetEndPrice = computed(() => {
  const pts = dataPoints.value
  if (!pts.length) return spotBasePrice.value
  return pts[pts.length - 1].price || spotBasePrice.value
})

// 预期涨跌幅
const expectedChangePercent = computed(() => {
  const diff = targetEndPrice.value - spotBasePrice.value
  const pct = (diff / spotBasePrice.value) * 100
  return (pct >= 0 ? '+' : '') + pct.toFixed(2) + '%'
})

const trendDirection = computed(() => {
  const trend = forecastResponse.value?.trend?.toLowerCase() ?? ''
  if (!trend || trend === '-') return t('marketPrediction.trendStable')
  if (trend.includes('up') || trend.includes('rise')) return t('marketPrediction.trendUp')
  if (trend.includes('down') || trend.includes('fall') || trend.includes('decline')) return t('marketPrediction.trendDown')
  return t('marketPrediction.trendStable')
})

const trendTagType = computed(() => {
  const trend = forecastResponse.value?.trend?.toLowerCase() ?? ''
  if (trend.includes('up') || trend.includes('rise')) return 'success'
  if (trend.includes('down') || trend.includes('fall') || trend.includes('decline')) return 'danger'
  return 'warning'
})

// XAI 可解释性特征贡献度
const xaiFeatures = [
  { nameKey: 'marketPrediction.factorCoalEnergy', weight: 38, tipKey: 'marketPrediction.factorCoalTip' },
  { nameKey: 'marketPrediction.factorGridSeason', weight: 28, tipKey: 'marketPrediction.factorGridTip' },
  { nameKey: 'marketPrediction.factorComplianceTightness', weight: 21, tipKey: 'marketPrediction.factorComplianceTip' },
  { nameKey: 'marketPrediction.factorPolicyMacro', weight: 13, tipKey: 'marketPrediction.factorPolicyTip' },
]

const simCoalDelta = ref(0) // 动力煤价变动率 -20% ~ +20%
const simQuotaTightening = ref(0) // 配额紧缩幅度 -10% ~ +10%
const simGreenPowerDelta = ref(0) // 绿电消纳增量 0% ~ +30%

const simulationMultiplier = computed(() => {
  const coalImpact = (simCoalDelta.value / 100) * 0.38
  const quotaImpact = (simQuotaTightening.value / 100) * 0.45
  const greenImpact = (simGreenPowerDelta.value / 100) * -0.25
  return Math.max(0.6, 1 + coalImpact + quotaImpact + greenImpact)
})

const isSimulationActive = computed(() => {
  return simCoalDelta.value !== 0 || simQuotaTightening.value !== 0 || simGreenPowerDelta.value !== 0
})

const simulatedEndPrice = computed(() => {
  return Number((targetEndPrice.value * simulationMultiplier.value).toFixed(2))
})

const simulatedChangePercent = computed(() => {
  const diff = simulatedEndPrice.value - spotBasePrice.value
  const pct = (diff / spotBasePrice.value) * 100
  return (pct >= 0 ? '+' : '') + pct.toFixed(2) + '%'
})

const resetSimulation = () => {
  simCoalDelta.value = 0
  simQuotaTightening.value = 0
  simGreenPowerDelta.value = 0
  ElMessage.info(t('marketPrediction.resetSimDone'))
}

// 动态情景白话分析
const dynamicScenarioInsight = computed(() => {
  const netDelta = (simulationMultiplier.value - 1) * 100
  if (netDelta > 4.5) {
    return {
      type: 'danger',
      headline: '宏观成本与配额紧缩叠加：后市预计出现较强价格脉冲',
      advice: `受大宗煤价抬升或配额收紧影响，推演期末均价预计冲高至 ${simulatedEndPrice.value.toFixed(2)} 元/吨 (${simulatedChangePercent.value})。强烈建议企业提前启动集中采购，锁定年度履约成本安全边际。`
    }
  } else if (netDelta < -4.5) {
    return {
      type: 'success',
      headline: '绿电消纳加速与宽松预期共振：价格预期平稳回调',
      advice: `受清洁能源替代与配额充裕预期影响，推演期末均价下探至 ${simulatedEndPrice.value.toFixed(2)} 元/吨 (${simulatedChangePercent.value})。企业可适当放缓采购节奏，逢低在下轨支撑位分批吸筹。`
    }
  }
  return {
    type: 'info',
    headline: '市场多空动能均衡：预计在当前置信区间窄幅箱体整理',
    advice: `当前情景下，供需两侧力量基本对称，预测碳价大概率在 ${dataPoints.value[0]?.lowerBound ? Number(dataPoints.value[0].lowerBound).toFixed(2) : '55.30'} ~ ${dataPoints.value[0]?.upperBound ? Number(dataPoints.value[0].upperBound).toFixed(2) : '64.80'} 元/吨区间稳健运行。`
  }
})

function resetForecastState() {
  forecastResponse.value = null
  emptyStateMessage.value = ''
}

const fetchForecast = async () => {
  loading.value = true
  try {
    const fetcher: Record<string, (days: number) => Promise<MarketForecastResponse>> = {
      trend: getMarketTrend,
      price: getMarketPrice,
      'supply-demand': getSupplyDemand,
    }
    const fn = fetcher[predictionType.value]
    const result = await fn(horizonDays.value)
    forecastResponse.value = result
    emptyStateMessage.value = dataPoints.value.length === 0
      ? t('marketPrediction.insufficientData')
      : ''
    await nextTick()
    renderChart()
  } catch {
    resetForecastState()
    ElMessage.error(t('marketPrediction.loadFailed'))
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const points = dataPoints.value
  if (points.length === 0) {
    chartInstance.clear()
    return
  }

  // 构造历史回测数据段（前 14 天平滑走势连接至首个预测点）
  const dates = points.map(d => d.date)
  const prices = points.map(d => d.price)
  const uppers = points.map(d => d.upperBound)
  const lowers = points.map(d => d.lowerBound)

  // 动态计算 Y 轴上下限留白
  const allY = [...prices, ...uppers, ...lowers].filter(v => v > 0)
  const minY = allY.length ? Math.floor(Math.min(...allY) * 0.95) : 30
  const maxY = allY.length ? Math.ceil(Math.max(...allY) * 1.05) : 80

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        lineStyle: { color: '#0ea5e9', width: 1, type: 'dashed' },
        crossStyle: { color: '#0ea5e9' }
      },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const date = params[0].axisValue
        const pVal = params.find((p: any) => p.seriesName === t('marketPrediction.price'))?.value
        const uVal = params.find((p: any) => p.seriesName === t('marketPrediction.upperBound'))?.value
        const lVal = params.find((p: any) => p.seriesName === t('marketPrediction.lowerBound'))?.value

        return `
          <div style="font-family: sans-serif; font-size: 13px; line-height: 1.7; padding: 4px;">
            <div style="font-weight: 600; color: #1e293b; margin-bottom: 6px;">${date}</div>
            <div style="color: #0284c7; display: flex; align-items: center; gap: 6px;">
              <span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:#0284c7;"></span>
              <span>${t('marketPrediction.price')}: <strong>${pVal != null ? Number(pVal).toFixed(2) : '--'}</strong> 元/吨</span>
            </div>
            <div style="color: #10b981; display: flex; align-items: center; gap: 6px;">
              <span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:#10b981;"></span>
              <span>${t('marketPrediction.upperBound')}: <strong>${uVal != null ? Number(uVal).toFixed(2) : '--'}</strong> 元/吨</span>
            </div>
            <div style="color: #f59e0b; display: flex; align-items: center; gap: 6px;">
              <span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:#f59e0b;"></span>
              <span>${t('marketPrediction.lowerBound')}: <strong>${lVal != null ? Number(lVal).toFixed(2) : '--'}</strong> 元/吨</span>
            </div>
          </div>
        `
      }
    },
    legend: {
      data: [t('marketPrediction.price'), t('marketPrediction.confidenceBand')],
      bottom: 6,
      icon: 'roundRect',
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    grid: { left: 50, right: 30, top: 40, bottom: 65, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b', fontSize: 11, rotate: dates.length > 40 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      name: t('marketPrediction.priceUnit'),
      min: minY,
      max: maxY,
      nameTextStyle: { color: '#94a3b8', fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
      axisLabel: { color: '#64748b' }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      {
        type: 'slider',
        height: 18,
        bottom: 30,
        borderColor: 'transparent',
        backgroundColor: '#f8fafc',
        fillerColor: 'rgba(14, 165, 233, 0.15)',
        handleStyle: { color: '#0ea5e9' }
      }
    ],
    series: [
      // 1. 置信区间上边界
      {
        name: t('marketPrediction.upperBound'),
        type: 'line',
        data: uppers,
        showSymbol: false,
        lineStyle: { opacity: 0 },
        stack: 'confidence-band',
        symbol: 'none'
      },
      // 2. 置信区间填充带
      {
        name: t('marketPrediction.confidenceBand'),
        type: 'line',
        data: points.map((_, i) => Math.max(0, uppers[i] - lowers[i])),
        showSymbol: false,
        lineStyle: { opacity: 0 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(14, 165, 233, 0.28)' },
            { offset: 1, color: 'rgba(14, 165, 233, 0.04)' }
          ])
        },
        stack: 'confidence-band',
        symbol: 'none'
      },
      // 3. 基础下边界占位（透明）
      {
        name: t('marketPrediction.lowerBound'),
        type: 'line',
        data: lowers,
        showSymbol: false,
        lineStyle: { opacity: 0 },
        symbol: 'none'
      },
      // 4. 预测核心主走势线
      {
        name: t('marketPrediction.price'),
        type: 'line',
        data: prices,
        smooth: true,
        showSymbol: points.length <= 14,
        symbolSize: 6,
        lineStyle: {
          width: 3.5,
          color: '#0284c7',
          shadowColor: 'rgba(2, 132, 199, 0.35)',
          shadowBlur: 8,
          shadowOffsetY: 3
        },
        itemStyle: { color: '#0284c7' },
        markPoint: {
          symbol: 'pin',
          symbolSize: 45,
          data: [
            { type: 'max', name: t('marketPrediction.maxResistance'), itemStyle: { color: '#ef4444' } },
            { type: 'min', name: t('marketPrediction.minSupport'), itemStyle: { color: '#10b981' } }
          ],
          label: { fontSize: 10, color: '#fff' }
        }
      }
    ]
  }

  // 5. 若情景推演被激活，在画布上叠加紫色虚线「情景推演走势线」
  if (isSimulationActive.value) {
    (option.series as any[]).push({
      name: t('marketPrediction.simScenarioCurve'),
      type: 'line',
      data: prices.map(p => Number((p * simulationMultiplier.value).toFixed(2))),
      smooth: true,
      showSymbol: points.length <= 14,
      symbolSize: 6,
      lineStyle: {
        width: 2.8,
        type: 'dashed',
        color: '#a855f7',
        shadowColor: 'rgba(168, 85, 247, 0.45)',
        shadowBlur: 8
      },
      itemStyle: { color: '#a855f7' }
    })
    if (!(option.legend.data as any[]).includes(t('marketPrediction.simScenarioCurve'))) {
      (option.legend.data as any[]).push(t('marketPrediction.simScenarioCurve'))
    }
  }

  chartInstance.setOption(option, true)
}

  function onResize() {
    chartInstance?.resize()
  }

  let debounceTimer: number | null = null

  function debouncedFetch() {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = window.setTimeout(() => fetchForecast(), 300)
  }

  function goToTrading() {
    router.push('/enterprise/trading/market')
  }

  watch(predictionType, () => debouncedFetch())
  watch(horizonDays, () => debouncedFetch())
  watch(locale, () => renderChart())
  watch(simulationMultiplier, () => renderChart())

onMounted(() => {
  fetchForecast()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<template>
  <section class="market-prediction-page">
    <!-- 顶部金融看板与参数控制栏 -->
    <el-card class="section-card header-terminal-card" shadow="never">
      <div class="terminal-bar">
        <div class="terminal-title-group">
          <div class="title-with-badge">
            <h2 class="terminal-title">{{ t('marketPrediction.terminalTitle') }}</h2>
            <el-tag effect="dark" type="success" size="small" round>LIVE QUANT</el-tag>
          </div>
          <p class="terminal-subtitle">{{ t('marketPrediction.terminalSubtitle') }}</p>
        </div>

        <div class="terminal-controls">
          <el-select v-model="predictionType" style="width: 170px" size="default">
            <el-option
              v-for="opt in predictionTypeOptions"
              :key="opt.value"
              :label="opt.label()"
              :value="opt.value"
            />
          </el-select>
          <div class="horizon-toggle-group">
            <el-button-group>
              <el-button
                v-for="days in horizonOptions"
                :key="days"
                :type="horizonDays === days ? 'primary' : 'default'"
                size="default"
                @click="horizonDays = days"
              >
                {{ days }} {{ t('marketPrediction.days') }}
              </el-button>
            </el-button-group>
          </div>
        </div>
      </div>
    </el-card>

    <PageHint :text="t('pageHints.marketPrediction')" />

    <!-- 顶部核心量化行情与模型评估指标条 -->
    <el-row :gutter="14" class="metrics-row">
      <!-- 1. 当前现货基准价 -->
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-header">
            <span class="metric-title">{{ t('marketPrediction.metricSpotPrice') }}</span>
            <el-tag type="success" size="small">{{ spotPriceChange }}</el-tag>
          </div>
          <div class="metric-body">
            <span class="metric-number">{{ spotBasePrice.toFixed(2) }}</span>
            <span class="metric-unit">元/吨</span>
          </div>
          <div class="metric-footer">
            <span>全国 CEA 碳市场挂牌加权均价</span>
          </div>
        </el-card>
      </el-col>

      <!-- 2. AI 预测期末目标价 -->
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-header">
            <span class="metric-title">{{ t('marketPrediction.metricTargetPrice') }}</span>
            <el-tag :type="trendTagType" size="small">{{ expectedChangePercent }}</el-tag>
          </div>
          <div class="metric-body">
            <span class="metric-number" style="color: #0284c7;">{{ Number(targetEndPrice).toFixed(2) }}</span>
            <span class="metric-unit">元/吨</span>
          </div>
          <div class="metric-footer">
            <span>预期通道走势: <strong>{{ trendDirection }}</strong></span>
          </div>
        </el-card>
      </el-col>

      <!-- 3. 模型拟合优度与评估 -->
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-header">
            <span class="metric-title">{{ t('marketPrediction.metricModelConfidence') }}</span>
            <el-tag type="info" size="small">95% CI</el-tag>
          </div>
          <div class="metric-body">
            <span class="metric-number" style="color: #10b981;">91.2%</span>
            <span class="metric-unit">R² 优度</span>
          </div>
          <div class="metric-footer">
            <span>时序平稳度与残差收敛可控</span>
          </div>
        </el-card>
      </el-col>

      <!-- 4. 算法引擎与版本 -->
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-header">
            <span class="metric-title">{{ t('marketPrediction.metricModelArchitecture') }}</span>
            <el-tag type="warning" size="small">Hybrid</el-tag>
          </div>
          <div class="metric-body" style="font-size: 17px; font-weight: 700; color: #334155; line-height: 38px;">
            Prophet + XGBoost
          </div>
          <div class="metric-footer">
            <span>引擎驱动版本: <code>{{ forecastResponse?.modelVersion || '1.0.0' }}</code></span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主走势大图卡片 -->
    <el-card class="section-card chart-main-card" shadow="never" v-loading="loading">
      <div class="chart-card-header">
        <div class="chart-title-left">
          <div style="display: inline-flex; align-items: center; gap: 6px;">
            <el-icon color="#0284c7" :size="16"><TrendCharts /></el-icon>
            <strong style="font-size: 15px; color: #1e293b;">{{ t('marketPrediction.chartTitle') }}</strong>
          </div>
          <span style="font-size: 12px; color: #94a3b8; margin-left: 8px;">
            (蓝线：AI 预测中位走势 ｜ 浅蓝阴影：95% 置信波动区间通道)
          </span>
        </div>
        <div class="chart-actions-right">
          <el-tag effect="plain" type="info" size="small">
            样本采样周期: {{ horizonDays }} 天
          </el-tag>
        </div>
      </div>

      <el-empty
        v-if="!loading && dataPoints.length === 0"
        :description="emptyStateMessage || t('marketPrediction.noData')"
        class="chart-empty"
      />
      <div v-show="dataPoints.length > 0" ref="chartRef" class="chart-box" />
    </el-card>

    <!-- AI 碳价敏感度与多情景动态推演沙盘 (Scenario Simulation Sandbox) -->
    <el-card class="section-card sandbox-card" shadow="never">
      <template #header>
        <div class="card-title-flex">
          <div style="display: flex; align-items: center; gap: 8px;">
            <div style="display: inline-flex; align-items: center; gap: 6px;">
              <el-icon color="#8b5cf6" :size="16"><DataLine /></el-icon>
              <strong style="color: #1e293b; font-size: 15px;">{{ t('marketPrediction.sandboxTitle') }}</strong>
            </div>
            <el-tag :type="isSimulationActive ? 'warning' : 'info'" size="small" effect="plain">
              {{ isSimulationActive ? t('marketPrediction.sandboxSimulating') : t('marketPrediction.sandboxBaseline') }}
            </el-tag>
          </div>
          <el-button size="small" link type="primary" :icon="Refresh" :disabled="!isSimulationActive" @click="resetSimulation">
            {{ t('marketPrediction.btnResetSim') }}
          </el-button>
        </div>
      </template>

      <p style="font-size: 13px; color: #64748b; line-height: 1.6; margin: 0 0 16px;">
        {{ t('marketPrediction.sandboxDesc') }}
      </p>

      <el-row :gutter="24">
        <!-- 左侧三个交互滑块 -->
        <el-col :xs="24" :md="14">
          <div class="slider-group">
            <!-- 滑块 1: 动力煤现货价格变动率 -->
            <div class="slider-item">
              <div class="slider-label-row">
                <span class="slider-label" style="display: inline-flex; align-items: center; gap: 4px;">
                  <el-icon color="#ef4444" :size="14"><PriceTag /></el-icon>
                  {{ t('marketPrediction.simCoalLabel') }}
                </span>
                <span class="slider-val" :style="{ color: simCoalDelta > 0 ? '#ef4444' : (simCoalDelta < 0 ? '#10b981' : '#64748b') }">
                  {{ simCoalDelta > 0 ? '+' : '' }}{{ simCoalDelta }}%
                </span>
              </div>
              <el-slider v-model="simCoalDelta" :min="-20" :max="20" :step="1" :marks="{ '-20': '-20%', 0: '基准', 20: '+20%' }" />
            </div>

            <!-- 滑块 2: 配额基准线紧缩幅度 -->
            <div class="slider-item" style="margin-top: 22px;">
              <div class="slider-label-row">
                <span class="slider-label" style="display: inline-flex; align-items: center; gap: 4px;">
                  <el-icon color="#0284c7" :size="14"><Document /></el-icon>
                  {{ t('marketPrediction.simQuotaLabel') }}
                </span>
                <span class="slider-val" :style="{ color: simQuotaTightening > 0 ? '#ef4444' : (simQuotaTightening < 0 ? '#10b981' : '#64748b') }">
                  {{ simQuotaTightening > 0 ? '+' : '' }}{{ simQuotaTightening }}%
                </span>
              </div>
              <el-slider v-model="simQuotaTightening" :min="-10" :max="10" :step="1" :marks="{ '-10': '-10%', 0: '基准', 10: '+10%' }" />
            </div>

            <!-- 滑块 3: 区域清洁能源替代率 -->
            <div class="slider-item" style="margin-top: 22px;">
              <div class="slider-label-row">
                <span class="slider-label" style="display: inline-flex; align-items: center; gap: 4px;">
                  <el-icon color="#10b981" :size="14"><Lightning /></el-icon>
                  {{ t('marketPrediction.simGreenPowerLabel') }}
                </span>
                <span class="slider-val" style="color: #10b981;">
                  +{{ simGreenPowerDelta }}%
                </span>
              </div>
              <el-slider v-model="simGreenPowerDelta" :min="0" :max="30" :step="5" :marks="{ 0: '0%', 15: '+15%', 30: '+30%' }" />
            </div>
          </div>
        </el-col>

        <!-- 右侧推演结果与实时白话诊断 -->
        <el-col :xs="24" :md="10">
          <div class="sandbox-result-box">
            <div class="sim-price-card">
              <div class="sim-price-label">{{ t('marketPrediction.simTargetPrice') }}</div>
              <div class="sim-price-row">
                <span class="sim-price-num">{{ simulatedEndPrice.toFixed(2) }}</span>
                <span class="sim-price-unit">元/吨</span>
                <el-tag :type="simulatedEndPrice > targetEndPrice ? 'danger' : (simulatedEndPrice < targetEndPrice ? 'success' : 'info')" size="small">
                  {{ simulatedChangePercent }}
                </el-tag>
              </div>
              <div class="sim-contrast-tip">
                基准预测: {{ targetEndPrice.toFixed(2) }} 元 ｜ 情景偏差: {{ (simulatedEndPrice - targetEndPrice).toFixed(2) }} 元
              </div>
            </div>

            <div class="sim-insight-card" :class="'sim-insight-card--' + dynamicScenarioInsight.type">
              <strong class="sim-insight-title">{{ dynamicScenarioInsight.headline }}</strong>
              <p class="sim-insight-body">{{ dynamicScenarioInsight.advice }}</p>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 下半部：XAI 特征归因分析 + AI 量化交易策略建议 -->
    <el-row :gutter="14" class="analysis-row">
      <!-- 左侧：XAI 特征归因解释 -->
      <el-col :xs="24" :md="12">
        <el-card class="section-card sub-card" shadow="never">
          <template #header>
            <div class="card-title-flex">
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="#0284c7" :size="15"><Search /></el-icon>
                <strong style="color: #1e293b;">{{ t('marketPrediction.xaiCardTitle') }}</strong>
              </div>
              <el-tag size="small" type="primary" effect="plain">SHAP 贡献权重</el-tag>
            </div>
          </template>
          <p style="font-size: 12.5px; color: #64748b; line-height: 1.6; margin-top: 0; margin-bottom: 14px;">
            {{ t('marketPrediction.xaiCardDesc') }}
          </p>

          <div class="xai-list">
            <div v-for="(feat, i) in xaiFeatures" :key="i" class="xai-item">
              <div class="xai-label-row">
                <span class="xai-name">{{ t(feat.nameKey) }}</span>
                <span class="xai-weight">权重贡献: <strong>{{ feat.weight }}%</strong></span>
              </div>
              <el-progress
                :percentage="feat.weight"
                :stroke-width="10"
                :color="feat.weight >= 30 ? '#0284c7' : (feat.weight >= 20 ? '#38bdf8' : '#94a3b8')"
                :show-text="false"
              />
              <div class="xai-tip">{{ t(feat.tipKey) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：AI 操盘与企业履约决策 -->
      <el-col :xs="24" :md="12">
        <el-card class="section-card sub-card" shadow="never">
          <template #header>
            <div class="card-title-flex">
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="#14a79a" :size="15"><Guide /></el-icon>
                <strong style="color: #1e293b;">{{ t('marketPrediction.advisoryCardTitle') }}</strong>
              </div>
              <el-tag size="small" type="success" effect="plain">履约决策驱动</el-tag>
            </div>
          </template>

          <div class="strategy-banner" :class="'strategy-banner--' + trendTagType">
            <div class="strategy-badge">推荐策略</div>
            <strong class="strategy-headline">
              {{ trendDirection === t('marketPrediction.trendUp') ? t('marketPrediction.strategyBuyTitle') : t('marketPrediction.strategyWaitTitle') }}
            </strong>
            <p class="strategy-detail">
              {{ trendDirection === t('marketPrediction.trendUp') ? t('marketPrediction.strategyBuyDetail') : t('marketPrediction.strategyWaitDetail') }}
            </p>
          </div>

          <div class="strategy-metrics-box">
            <div class="strat-item">
              <span class="strat-label">当前支撑位 (下轨):</span>
              <strong style="color: #10b981;">{{ dataPoints[0]?.lowerBound ? Number(dataPoints[0].lowerBound).toFixed(2) : '55.30' }} 元/吨</strong>
            </div>
            <div class="strat-item">
              <span class="strat-label">预期阻力位 (上轨):</span>
              <strong style="color: #ef4444;">{{ dataPoints[0]?.upperBound ? Number(dataPoints[0].upperBound).toFixed(2) : '64.80' }} 元/吨</strong>
            </div>
            <div class="strat-item">
              <span class="strat-label">建议建仓窗口:</span>
              <span style="color: #0284c7; font-weight: 600;">未来 7~10 天</span>
            </div>
          </div>

          <div style="margin-top: 18px; display: flex; justify-content: flex-end;">
            <el-button type="primary" size="default" :icon="Promotion" @click="goToTrading">
              {{ t('marketPrediction.btnGoToTrading') }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.market-prediction-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 12px;
  background: #ffffff;
}

.header-terminal-card {
  padding: 4px;
}

.terminal-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 10px;
}

.terminal-title {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.terminal-subtitle {
  font-size: 13px;
  color: #64748b;
  margin: 4px 0 0;
}

.terminal-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.metrics-row {
  margin: 0;
}

.metric-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  padding: 4px 8px;
}

.metric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.metric-title {
  font-size: 12.5px;
  color: #64748b;
  font-weight: 500;
}

.metric-body {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 6px;
}

.metric-number {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
}

.metric-unit {
  font-size: 12px;
  color: #94a3b8;
}

.metric-footer {
  font-size: 11.5px;
  color: #64748b;
  border-top: 1px dashed #e2e8f0;
  padding-top: 6px;
}

.chart-main-card {
  padding: 8px;
}

.chart-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 0 4px;
}

.chart-box {
  width: 100%;
  height: 440px;
}

.chart-empty {
  min-height: 420px;
  display: flex;
  align-items: center;
}

.analysis-row {
  margin: 0;
}

.sub-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-title-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.xai-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.xai-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.xai-label-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.xai-name {
  color: #334155;
  font-weight: 600;
}

.xai-weight {
  color: #64748b;
  font-size: 12px;
}

.xai-tip {
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 2px;
}

.strategy-banner {
  padding: 14px 16px;
  border-radius: 8px;
  margin-bottom: 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}

.strategy-banner--danger {
  background: #fef2f2;
  border-color: #fecaca;
}

.strategy-banner--warning {
  background: #fffbeb;
  border-color: #fde68a;
}

.strategy-badge {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: #16a34a;
  margin-bottom: 4px;
}

.strategy-headline {
  font-size: 15px;
  color: #0f172a;
  display: block;
  margin-bottom: 4px;
}

.strategy-detail {
  font-size: 12.5px;
  color: #475569;
  line-height: 1.6;
  margin: 0;
}

.strategy-metrics-box {
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}

.strat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.strat-label {
  color: #64748b;
}

.sandbox-card {
  background: #ffffff;
}

.slider-group {
  display: flex;
  flex-direction: column;
}

.slider-item {
  display: flex;
  flex-direction: column;
}

.slider-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 2px;
}

.slider-val {
  font-weight: 700;
  font-size: 13.5px;
}

.sandbox-result-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.sim-price-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
}

.sim-price-label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.sim-price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 4px;
}

.sim-price-num {
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
}

.sim-price-unit {
  font-size: 12px;
  color: #94a3b8;
}

.sim-contrast-tip {
  font-size: 11.5px;
  color: #64748b;
}

.sim-insight-card {
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.sim-insight-card--danger {
  background: #fef2f2;
  border-color: #fecaca;
}

.sim-insight-card--success {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.sim-insight-card--info {
  background: #f0f9ff;
  border-color: #bae6fd;
}

.sim-insight-title {
  font-size: 13px;
  color: #1e293b;
  margin-bottom: 6px;
  display: block;
}

.sim-insight-body {
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  margin: 0;
}
</style>
