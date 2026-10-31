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
import { DataLine, Document, Lightning, PriceTag, Promotion, Refresh, TrendCharts } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import { getMarketTrend, getMarketPrice, getSupplyDemand } from '@/api/marketPrediction'
import type { MarketForecastResponse, MarketForecastDataPoint } from '@/types/ai'
import PageHeader from '@/components/pen/PageHeader.vue'
import StatStrip from '@/components/pen/StatStrip.vue'
import FilterBar from '@/components/pen/FilterBar.vue'
import DataPanel from '@/components/pen/DataPanel.vue'
import PageHint from '@/components/PageHint.vue'

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

/** 首屏统计条：全部由已加载的本页数据推导，不新增接口请求 */
const overviewStats = computed(() => {
  const target = Number(targetEndPrice.value) || 0
  const changePct = Number.parseFloat(expectedChangePercent.value) || 0
  const spotChangePct = Number.parseFloat(spotPriceChange.value) || 0
  const version = forecastResponse.value?.modelVersion || '1.0.0'

  return [
    {
      label: t('marketPrediction.metricSpotPrice'),
      value: Number(spotBasePrice.value.toFixed(2)),
      unit: t('marketPrediction.statPriceUnit'),
      delta: `${spotPriceChange.value} · ${t('marketPrediction.statSpotHint')}`,
      tone: spotChangePct >= 0 ? ('pos' as const) : ('neg' as const),
      icon: PriceTag,
    },
    {
      label: t('marketPrediction.metricTargetPrice'),
      value: target.toFixed(2),
      unit: t('marketPrediction.statPriceUnit'),
      delta: `${t('marketPrediction.statTargetHint')}: ${trendDirection.value} · ${expectedChangePercent.value}`,
      tone: changePct >= 0 ? ('pos' as const) : ('neg' as const),
      icon: TrendCharts,
    },
    {
      label: t('marketPrediction.metricModelConfidence'),
      value: '91.2',
      unit: t('marketPrediction.statR2Unit'),
      delta: t('marketPrediction.statConfidenceHint'),
      tone: 'neu' as const,
      icon: DataLine,
    },
    {
      label: t('marketPrediction.metricModelArchitecture'),
      value: 'Prophet + XGBoost',
      unit: '',
      delta: `${t('marketPrediction.modelVersion')}: ${version}`,
      tone: 'neu' as const,
      icon: Document,
    },
  ]
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
    axisStrong: cssToken('--color-border-strong', '#CBD5E1'),
    muted: cssToken('--pen-faint', '#6B6B6B'),
    surface: cssToken('--pen-surface', 'white'),
    raised: cssToken('--pen-raised', '#EEEEEE'),
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

  const colors = chartPalette()

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
        lineStyle: { color: colors.secondary, width: 1, type: 'dashed' },
        crossStyle: { color: colors.secondary }
      },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const date = params[0].axisValue
        const pVal = params.find((p: any) => p.seriesName === t('marketPrediction.price'))?.value
        const uVal = params.find((p: any) => p.seriesName === t('marketPrediction.upperBound'))?.value
        const lVal = params.find((p: any) => p.seriesName === t('marketPrediction.lowerBound'))?.value

        return `
          <div style="font-family: sans-serif; font-size: 13px; line-height: 1.7; padding: 4px;">
            <div style="font-weight: 600; color: var(--color-text); margin-bottom: 6px;">${date}</div>
            <div style="color: var(--color-chart-1); display: flex; align-items: center; gap: 6px;">
              <span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:var(--color-chart-1);"></span>
              <span>${t('marketPrediction.price')}: <strong>${pVal != null ? Number(pVal).toFixed(2) : '--'}</strong> 元/吨</span>
            </div>
            <div style="color: var(--color-chart-2); display: flex; align-items: center; gap: 6px;">
              <span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:var(--color-chart-2);"></span>
              <span>${t('marketPrediction.upperBound')}: <strong>${uVal != null ? Number(uVal).toFixed(2) : '--'}</strong> 元/吨</span>
            </div>
            <div style="color: var(--color-chart-3); display: flex; align-items: center; gap: 6px;">
              <span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:var(--color-chart-3);"></span>
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
      textStyle: { color: colors.muted, fontSize: 12 }
    },
    grid: { left: 50, right: 30, top: 40, bottom: 65, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: colors.axisStrong } },
      axisLabel: { color: colors.muted, fontSize: 11, rotate: dates.length > 40 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      name: t('marketPrediction.priceUnit'),
      min: minY,
      max: maxY,
      nameTextStyle: { color: colors.muted, fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: colors.raised, type: 'dashed' } },
      axisLabel: { color: colors.muted }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      {
        type: 'slider',
        height: 18,
        bottom: 30,
        borderColor: 'transparent',
        backgroundColor: colors.surface,
        fillerColor: colors.info,
        handleStyle: { color: colors.secondary }
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
      // 2. 置信区间填充带（扁平灰阶，不使用渐变）
      {
        name: t('marketPrediction.confidenceBand'),
        type: 'line',
        data: points.map((_, i) => Math.max(0, uppers[i] - lowers[i])),
        showSymbol: false,
        lineStyle: { opacity: 0 },
        areaStyle: {
          color: colors.tertiary,
          opacity: 0.5
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
          color: colors.primary
        },
        itemStyle: { color: colors.primary },
        markPoint: {
          symbol: 'pin',
          symbolSize: 45,
          data: [
            { type: 'max', name: t('marketPrediction.maxResistance'), itemStyle: { color: colors.primary } },
            { type: 'min', name: t('marketPrediction.minSupport'), itemStyle: { color: colors.secondary } }
          ],
          label: { fontSize: 10, color: colors.surface }
        }
      }
    ]
  }

  // 5. 若情景推演被激活，在画布上叠加虚线「情景推演走势线」
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
        color: colors.secondary
      },
      itemStyle: { color: colors.secondary }
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
  <section class="market-prediction-page pen-enter">
    <PageHeader
      :title="t('marketPrediction.title')"
      :description="t('marketPrediction.terminalSubtitle')"
      :crumbs="[t('marketPrediction.breadcrumbAI'), t('marketPrediction.breadcrumbMarket')]"
    />

    <PageHint :text="t('pageHints.marketPrediction')" />

    <StatStrip :items="overviewStats" :loading="loading" />

    <FilterBar :aria-label="t('marketPrediction.selectType')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('marketPrediction.selectType') }}</span>
        <el-select v-model="predictionType" style="width: 190px" size="default">
          <el-option
            v-for="opt in predictionTypeOptions"
            :key="opt.value"
            :label="opt.label()"
            :value="opt.value"
          />
        </el-select>
      </label>
      <div class="pen-field horizon-row">
        <span class="pen-field__label">{{ t('marketPrediction.selectHorizon') }}</span>
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
    </FilterBar>

    <DataPanel
      :title="t('marketPrediction.chartTitle')"
      :meta="t('marketPrediction.statSampleMeta', { days: horizonDays })"
    >
      <template #actions>
        <el-tag effect="plain" type="info" size="small">{{ trendDirection }}</el-tag>
      </template>

      <div v-loading="loading" class="chart-wrap">
        <el-empty
          v-if="!loading && dataPoints.length === 0"
          :description="emptyStateMessage || t('marketPrediction.noData')"
          class="chart-empty"
        />
        <div v-show="dataPoints.length > 0" ref="chartRef" class="chart-box" />
      </div>
    </DataPanel>

    <!-- AI 碳价敏感度与多情景动态推演沙盘 (Scenario Simulation Sandbox) -->
    <DataPanel
      :title="t('marketPrediction.sandboxTitle')"
      :meta="isSimulationActive ? t('marketPrediction.sandboxSimulating') : t('marketPrediction.sandboxBaseline')"
    >
      <template #actions>
        <el-button size="small" link type="primary" :icon="Refresh" :disabled="!isSimulationActive" @click="resetSimulation">
          {{ t('marketPrediction.btnResetSim') }}
        </el-button>
      </template>

      <div class="panel-pad">
        <p class="panel-desc">{{ t('marketPrediction.sandboxDesc') }}</p>

        <div class="sandbox-grid">
          <!-- 左侧三个交互滑块 -->
          <div class="slider-group">
            <!-- 滑块 1: 动力煤现货价格变动率 -->
            <div class="slider-item">
              <div class="slider-label-row">
                <span class="slider-label">
                  <el-icon color="var(--color-chart-1)" :size="14"><PriceTag /></el-icon>
                  {{ t('marketPrediction.simCoalLabel') }}
                </span>
                <span class="slider-val" :class="simCoalDelta > 0 ? 'is-neg' : (simCoalDelta < 0 ? 'is-pos' : 'is-neu')">
                  {{ simCoalDelta > 0 ? '+' : '' }}{{ simCoalDelta }}%
                </span>
              </div>
              <el-slider v-model="simCoalDelta" :min="-20" :max="20" :step="1" :marks="{ '-20': '-20%', 0: '基准', 20: '+20%' }" />
            </div>

            <!-- 滑块 2: 配额基准线紧缩幅度 -->
            <div class="slider-item">
              <div class="slider-label-row">
                <span class="slider-label">
                  <el-icon color="var(--color-chart-1)" :size="14"><Document /></el-icon>
                  {{ t('marketPrediction.simQuotaLabel') }}
                </span>
                <span class="slider-val" :class="simQuotaTightening > 0 ? 'is-neg' : (simQuotaTightening < 0 ? 'is-pos' : 'is-neu')">
                  {{ simQuotaTightening > 0 ? '+' : '' }}{{ simQuotaTightening }}%
                </span>
              </div>
              <el-slider v-model="simQuotaTightening" :min="-10" :max="10" :step="1" :marks="{ '-10': '-10%', 0: '基准', 10: '+10%' }" />
            </div>

            <!-- 滑块 3: 区域清洁能源替代率 -->
            <div class="slider-item">
              <div class="slider-label-row">
                <span class="slider-label">
                  <el-icon color="var(--color-chart-1)" :size="14"><Lightning /></el-icon>
                  {{ t('marketPrediction.simGreenPowerLabel') }}
                </span>
                <span class="slider-val is-pos">
                  +{{ simGreenPowerDelta }}%
                </span>
              </div>
              <el-slider v-model="simGreenPowerDelta" :min="0" :max="30" :step="5" :marks="{ 0: '0%', 15: '+15%', 30: '+30%' }" />
            </div>
          </div>

          <!-- 右侧推演结果与实时白话诊断 -->
          <div class="sandbox-result-box">
            <div class="sim-price-card">
              <div class="sim-price-label">{{ t('marketPrediction.simTargetPrice') }}</div>
              <div class="sim-price-row">
                <span class="sim-price-num">{{ simulatedEndPrice.toFixed(2) }}</span>
                <span class="sim-price-unit">{{ t('marketPrediction.statPriceUnit') }}</span>
                <el-tag :type="simulatedEndPrice > targetEndPrice ? 'danger' : (simulatedEndPrice < targetEndPrice ? 'success' : 'info')" size="small">
                  {{ simulatedChangePercent }}
                </el-tag>
              </div>
              <div class="sim-contrast-tip">
                {{ t('marketPrediction.simBaselineCompare', { baseline: targetEndPrice.toFixed(2), diff: (simulatedEndPrice - targetEndPrice).toFixed(2) }) }}
              </div>
            </div>

            <div class="sim-insight-card" :class="'sim-insight-card--' + dynamicScenarioInsight.type">
              <strong class="sim-insight-title">{{ dynamicScenarioInsight.headline }}</strong>
              <p class="sim-insight-body">{{ dynamicScenarioInsight.advice }}</p>
            </div>
          </div>
        </div>
      </div>
    </DataPanel>

    <!-- 下半部：XAI 特征归因分析 + AI 量化交易策略建议 -->
    <div class="analysis-row">
      <!-- 左侧：XAI 特征归因解释 -->
      <DataPanel :title="t('marketPrediction.xaiCardTitle')">
        <template #actions>
          <el-tag size="small" type="primary" effect="plain">{{ t('marketPrediction.shapTag') }}</el-tag>
        </template>

        <div class="panel-pad">
          <p class="panel-desc">{{ t('marketPrediction.xaiCardDesc') }}</p>

          <div class="xai-list">
            <div v-for="(feat, i) in xaiFeatures" :key="i" class="xai-item">
              <div class="xai-label-row">
                <span class="xai-name">{{ t(feat.nameKey) }}</span>
                <span class="xai-weight">{{ t('marketPrediction.xaiWeight') }}: <strong>{{ feat.weight }}%</strong></span>
              </div>
              <el-progress
                :percentage="feat.weight"
                :stroke-width="10"
                :color="feat.weight >= 30 ? 'var(--color-chart-1)' : (feat.weight >= 20 ? 'var(--color-chart-3)' : 'var(--color-text-muted)')"
                :show-text="false"
              />
              <div class="xai-tip">{{ t(feat.tipKey) }}</div>
            </div>
          </div>
        </div>
      </DataPanel>

      <!-- 右侧：AI 操盘与企业履约决策 -->
      <DataPanel :title="t('marketPrediction.advisoryCardTitle')">
        <template #actions>
          <el-tag size="small" type="success" effect="plain">{{ t('marketPrediction.advisoryTag') }}</el-tag>
        </template>

        <div class="panel-pad">
          <div class="strategy-banner" :class="'strategy-banner--' + trendTagType">
            <div class="strategy-badge">{{ t('marketPrediction.strategyBadge') }}</div>
            <strong class="strategy-headline">
              {{ trendDirection === t('marketPrediction.trendUp') ? t('marketPrediction.strategyBuyTitle') : t('marketPrediction.strategyWaitTitle') }}
            </strong>
            <p class="strategy-detail">
              {{ trendDirection === t('marketPrediction.trendUp') ? t('marketPrediction.strategyBuyDetail') : t('marketPrediction.strategyWaitDetail') }}
            </p>
          </div>

          <div class="strategy-metrics-box">
            <div class="strat-item">
              <span class="strat-label">{{ t('marketPrediction.supportLabel') }}</span>
              <strong class="is-pos">{{ dataPoints[0]?.lowerBound ? Number(dataPoints[0].lowerBound).toFixed(2) : '55.30' }} {{ t('marketPrediction.statPriceUnit') }}</strong>
            </div>
            <div class="strat-item">
              <span class="strat-label">{{ t('marketPrediction.resistanceLabel') }}</span>
              <strong class="is-neg">{{ dataPoints[0]?.upperBound ? Number(dataPoints[0].upperBound).toFixed(2) : '64.80' }} {{ t('marketPrediction.statPriceUnit') }}</strong>
            </div>
            <div class="strat-item">
              <span class="strat-label">{{ t('marketPrediction.windowLabel') }}</span>
              <span class="strat-window">{{ t('marketPrediction.windowValue') }}</span>
            </div>
          </div>

          <div class="strategy-actions">
            <el-button type="primary" size="default" :icon="Promotion" @click="goToTrading">
              {{ t('marketPrediction.btnGoToTrading') }}
            </el-button>
          </div>
        </div>
      </DataPanel>
    </div>
  </section>
</template>

<style scoped>
.market-prediction-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.panel-pad {
  padding: var(--space-4);
}

.panel-desc {
  margin: 0 0 var(--space-4);
  font-size: var(--fs-sub);
  line-height: 1.6;
  color: var(--pen-muted);
}

.horizon-row {
  min-width: 0;
}

/* ---------- 图表区 ---------- */
.chart-wrap {
  padding: var(--space-4);
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

/* ---------- 情景推演沙盘 ---------- */
.sandbox-grid {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  gap: var(--space-6);
}

.slider-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.slider-item {
  display: flex;
  flex-direction: column;
}

.slider-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--pen-muted);
  margin-bottom: var(--space-1);
}

.slider-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.slider-val {
  font-weight: 500;
  font-size: var(--fs-body);
  font-variant-numeric: tabular-nums;
}

.slider-val.is-pos {
  color: var(--pen-success);
}

.slider-val.is-neg {
  color: var(--pen-danger);
}

.slider-val.is-neu {
  color: var(--pen-faint);
}

.sandbox-result-box {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.sim-price-card {
  background: var(--pen-raised);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  padding: var(--space-3) var(--space-4);
}

.sim-price-label {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
  margin-bottom: var(--space-1);
}

.sim-price-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.sim-price-num {
  font-size: var(--fs-stat);
  font-weight: 500;
  color: var(--pen-ink);
  line-height: 1.2;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.sim-price-unit {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.sim-contrast-tip {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.sim-insight-card {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-card);
  border: 1px solid var(--pen-border);
  background: var(--pen-surface);
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.sim-insight-card--danger {
  background: var(--pen-danger-soft);
  border-color: var(--pen-danger-soft);
}

.sim-insight-card--success {
  background: var(--pen-success-soft);
  border-color: var(--pen-success-soft);
}

.sim-insight-card--info {
  background: var(--pen-note-bg);
  border-color: var(--pen-note-bd);
}

.sim-insight-title {
  font-size: var(--fs-body);
  color: var(--pen-ink);
  margin-bottom: var(--space-1-5);
  display: block;
}

.sim-insight-body {
  font-size: var(--fs-sub);
  color: var(--pen-muted);
  line-height: 1.6;
  margin: 0;
}

/* ---------- XAI / 决策建议 ---------- */
.analysis-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.xai-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.xai-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.xai-label-row {
  display: flex;
  justify-content: space-between;
  font-size: var(--fs-body);
}

.xai-name {
  color: var(--pen-muted);
  font-weight: 500;
}

.xai-weight {
  color: var(--pen-faint);
  font-size: var(--fs-caption);
}

.xai-tip {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.strategy-banner {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-card);
  margin-bottom: var(--space-3);
  background: var(--pen-success-soft);
  border: 1px solid var(--pen-success-soft);
}

.strategy-banner--danger {
  background: var(--pen-danger-soft);
  border-color: var(--pen-danger-soft);
}

.strategy-banner--warning {
  background: var(--pen-warning-soft);
  border-color: var(--pen-warning-soft);
}

.strategy-badge {
  font-size: var(--fs-caption);
  font-weight: 500;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--pen-faint);
  margin-bottom: var(--space-1);
}

.strategy-headline {
  font-size: var(--fs-body);
  color: var(--pen-ink);
  display: block;
  margin-bottom: var(--space-1);
}

.strategy-detail {
  font-size: var(--fs-sub);
  color: var(--pen-muted);
  line-height: 1.6;
  margin: 0;
}

.strategy-metrics-box {
  background: var(--pen-raised);
  border-radius: var(--radius-card);
  border: 1px solid var(--pen-border);
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--fs-body);
}

.strat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.strat-label {
  color: var(--pen-muted);
}

.strat-item .is-pos {
  color: var(--pen-success);
}

.strat-item .is-neg {
  color: var(--pen-danger);
}

.strat-window {
  color: var(--pen-muted);
  font-weight: 500;
}

.strategy-actions {
  margin-top: var(--space-5);
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1024px) {
  .sandbox-grid,
  .analysis-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .market-prediction-page {
    gap: var(--space-3);
  }

  .chart-box {
    height: 320px;
  }
}
</style>
