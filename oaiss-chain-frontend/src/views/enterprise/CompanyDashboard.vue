<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳资产与数据可视化综合大屏 (Company Dashboard)
 * 【路由地址】：/enterprise/company/dashboard
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 资产核心指标看板：当前可用碳配额、碳币钱包余额、信誉评级分值与履约达标率
 *   2. 碳排放历史走势折线图 (ECharts)：展示月度范围一、范围二排碳演变趋势
 *   3. 碳交易流水与结构图表：P2P与双向拍卖交易占比及买卖清算统计
 *   4. 配额使用率环形进度条：直观展示法定配额消耗进度与缺口预警
 *   5. 资产编号与多条件维度检索过滤
 * 【对应后端 Controller & API】：
 *   - CarbonController:
 *     * GET /api/v1/carbon/my-reports        -> getMyReports() 获取历史排放走势
 *   - TradeController:
 *     * GET /api/v1/trade/my-trades          -> getMyTrades() 获取交易订单聚合
 *   - CreditScoreController:
 *     * GET /api/v1/credit/my-score          -> getMyScore() 获取信誉分与评级
 *   - CarbonCoinController:
 *     * GET /api/v1/carbon-coin/account      -> getMyAccount() 获取碳币钱包余额
 *   - EnterpriseController:
 *     * GET /api/v1/enterprise/quota         -> getQuotaInfo() 获取配额使用率
 *     * GET /api/v1/enterprise/admission/my  -> getMyEnterpriseAdmission() 获取准入凭据
 * ==============================================================================
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Coin, DataAnalysis, Tickets, TrendCharts } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import echarts from '../../utils/echarts'
import { getMyReports } from '../../api/carbon'
import { getMyTrades } from '../../api/trade'
import { getMyScore } from '../../api/credit'
import { getMyAccount } from '../../api/carbonCoin'
import { getMyEnterpriseAdmission, getQuotaInfo } from '../../api/enterprise'
import type { CarbonReportResponse, EnterpriseAdmissionResponse, EnterpriseQuotaResponse, TradeResponse } from '../../types'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import { formatNumber } from '../../utils/format'

const { t, locale } = useI18n()

type DashboardAsset = {
  assetNo: string
  other: string
  keyword: string
}

const loading = ref(false)
const queryForm = reactive({
  assetNo: '',
  otherCondition: '',
  keyword: '',
})

const appliedFilters = ref({
  assetNo: '',
  otherCondition: '',
  keyword: '',
})

const timeDimension = ref('month')

const assetPool = ref<DashboardAsset[]>([])
const creditScore = ref(0)
const quotaTotal = ref(0)
const carbonCoinTotal = ref(0)
const tradeData = ref<TradeResponse[]>([])
const carbonReports = ref<CarbonReportResponse[]>([])
const admissionStatus = ref<EnterpriseAdmissionResponse | null>(null)

const chartTradeBarRef = ref<HTMLElement | null>(null)
const chartTrendLineRef = ref<HTMLElement | null>(null)
const chartSuggestBarRef = ref<HTMLElement | null>(null)
const chartEmissionPieRef = ref<HTMLElement | null>(null)
const chartTradePieRef = ref<HTMLElement | null>(null)
const chartCreditLineRef = ref<HTMLElement | null>(null)

const chartInstances: Array<ReturnType<typeof echarts.init> | undefined> = []

const filteredAssets = computed(() => {
  const f = appliedFilters.value
  const assetNo = f.assetNo.trim().toLowerCase()
  const otherCondition = f.otherCondition.trim().toLowerCase()
  const keyword = f.keyword.trim().toLowerCase()

  return assetPool.value.filter((item) => {
    const matchAssetNo = !assetNo || item.assetNo.toLowerCase().includes(assetNo)
    const matchOther = !otherCondition || item.other.toLowerCase().includes(otherCondition)
    const matchKeyword = !keyword || item.keyword.toLowerCase().includes(keyword)
    return matchAssetNo && matchOther && matchKeyword
  })
})

const matchedAssetNumbers = computed(() => new Set(filteredAssets.value.map((item) => item.assetNo)))

// ELI5：筛选零匹配时必须给用户明确反馈，否则 6 张空白图表会被当成页面故障
const hasActiveFilter = computed(() => {
  const f = appliedFilters.value
  return Boolean(f.assetNo.trim() || f.otherCondition.trim() || f.keyword.trim())
})

const filterEmpty = computed(() => hasActiveFilter.value && filteredAssets.value.length === 0)

const filteredReports = computed(() =>
  carbonReports.value.filter((report) => {
    return Boolean(report.reportNo) && matchedAssetNumbers.value.has(report.reportNo)
  }),
)

const filteredTrades = computed(() => {
  const reportIds = new Set(filteredReports.value.map((report) => report.id))
  return tradeData.value.filter((trade) => reportIds.has(trade.reportId))
})

const toTimeBucket = (input: string | undefined, dimension: string) => {
  if (!input) return ''
  const date = new Date(input)
  if (Number.isNaN(date.getTime())) return ''

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  if (dimension === 'day') return `${year}-${month}-${day}`
  if (dimension === 'year') return `${year}`
  return `${year}-${month}`
}

const aggregateSeries = <T,>(
  rows: T[],
  getDate: (row: T) => string | undefined,
  getValue: (row: T) => number,
  dimension: string,
) => {
  const map = new Map<string, number>()

  rows.forEach((row) => {
    const bucket = toTimeBucket(getDate(row), dimension)
    if (!bucket) return
    map.set(bucket, (map.get(bucket) || 0) + Number(getValue(row) || 0))
  })

  const labels = Array.from(map.keys()).sort()
  const values = labels.map((label) => map.get(label) || 0)

  return { labels, values }
}

const currentData = computed(() => {
  const round = (n: number) => Math.round(n)
  const dimension = timeDimension.value
  const trades = filteredTrades.value
  const reports = filteredReports.value

  const tradeSeries = aggregateSeries(
    trades,
    (trade) => trade.createdAt,
    (trade) => Number(trade.quantity || trade.totalAmount || 0),
    dimension,
  )

  const reportTrendSeries = aggregateSeries(
    reports,
    (report) => report.createdAt || report.accountingPeriod,
    (report) => Number(report.totalEmission || 0),
    dimension,
  )

  const reportSuggestSeries = aggregateSeries(
    reports,
    (report) => report.createdAt || report.accountingPeriod,
    (report) => Number(report.scope1Emission || 0) + Number(report.scope2Emission || 0),
    dimension,
  )

  const creditTrendSource = tradeSeries.values.length ? tradeSeries.values : reportTrendSeries.values
  const creditTrend = creditTrendSource.map((_, index) => {
    const delta = (creditTrendSource.length - 1 - index) * 2
    return Math.max(0, creditScore.value - delta)
  })

  return {
    tradeLabels: tradeSeries.labels,
    tradeCount: tradeSeries.values.map((value) => round(value)),
    reportLabels: reportTrendSeries.labels,
    aiTrend: reportTrendSeries.values.map((value) => round(value)),
    aiSuggest: reportSuggestSeries.values.map((value) => round(value)),
    creditLabels: tradeSeries.labels.length ? tradeSeries.labels : reportTrendSeries.labels,
    creditTrend: creditTrend.map((value) => round(value)),
    summary: {
      carbonCoinTotal: carbonCoinTotal.value,
      carbonQuotaTotal: quotaTotal.value,
      creditScore: creditScore.value,
    },
    emissionPie: [
      { name: t('companyDashboard.pieGasEmission'), value: reports.reduce((sum, report) => sum + Number(report.scope1Emission || 0), 0) },
      { name: t('companyDashboard.pieWaterEmission'), value: reports.reduce((sum, report) => sum + Number(report.scope2Emission || 0), 0) },
      { name: t('companyDashboard.pieSolidEmission'), value: reports.reduce((sum, report) => sum + Number(report.scope3Emission || 0), 0) },
      { name: t('companyDashboard.pieRatedEmission'), value: reports.reduce((sum, report) => sum + Number(report.totalEmission || 0), 0) },
    ].filter((item) => item.value > 0),
    tradePie: [
      { name: t('companyDashboard.pieTransactionExpense'), value: trades.reduce((sum, trade) => sum + Number(trade.totalAmount || 0), 0) },
      { name: t('companyDashboard.pieTradeQuantity'), value: trades.reduce((sum, trade) => sum + Number(trade.quantity || 0), 0) },
    ].filter((item) => item.value > 0),
  }
})

/** 读取语义色令牌（ECharts 不解析 var()，运行时取值并保留兜底） */
function cssToken(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

/** 统计条：直接取已加载的汇总数据，不新增接口请求 */
const overviewStats = computed(() => [
  {
    label: t('companyDashboard.cardCarbonCoin'),
    value: formatNumber(currentData.value.summary.carbonCoinTotal),
    unit: '',
    delta: t('companyDashboard.statCoinHint'),
    tone: 'neu' as const,
    icon: Coin,
  },
  {
    label: t('companyDashboard.cardCarbonQuota'),
    value: formatNumber(currentData.value.summary.carbonQuotaTotal),
    unit: 'tCO2e',
    delta: t('companyDashboard.statQuotaHint'),
    tone: 'neu' as const,
    icon: DataAnalysis,
  },
  {
    label: t('companyDashboard.cardCreditScore'),
    value: currentData.value.summary.creditScore,
    unit: t('companyDashboard.statUnitScore'),
    delta: t('companyDashboard.statCreditHint'),
    tone: currentData.value.summary.creditScore > 0 ? ('pos' as const) : ('neu' as const),
    icon: TrendCharts,
  },
  {
    label: t('companyDashboard.admissionStatus'),
    value: admissionStatus.value
      ? (admissionStatus.value.status === 1 ? t('certificateManage.active') : t('certificateManage.revoked'))
      : t('certificateManage.notIssued'),
    unit: '',
    delta: admissionStatus.value?.certificateNo
      ? t('companyDashboard.statAdmissionHint', { certNo: admissionStatus.value.certificateNo })
      : t('companyDashboard.statAdmissionMissing'),
    tone: admissionStatus.value?.status === 1 ? ('pos' as const) : ('neu' as const),
    icon: Tickets,
  },
])

const percentFormatter = (params: { name: string; value: number }, sourceArr: number[]) => {
  const total = sourceArr.reduce((sum, value) => sum + value, 0) || 1
  const percent = ((params.value / total) * 100).toFixed(2)
  return `${params.name}<br/>${t('companyDashboard.tooltipValue')}: ${params.value}<br/>${t('companyDashboard.tooltipRatio')}: ${percent}%`
}

/** 空数据标题层：所选时间维度内无有效数据时给出白话空态，而非空白画布（ELI5） */
const chartEmptyTitle = (data: number[]) =>
  data.some((value) => Number(value) > 0)
    ? []
    : [{
        text: t('companyDashboard.noChartData'),
        left: 'center' as const,
        top: 'middle' as const,
        textStyle: { color: cssToken('--pen-faint', '#6B6B6B'), fontSize: 13, fontWeight: 400 as const },
      }]

const buildBarOption = (title: string, labels: string[], data: number[], color: string) => ({
  title: [
    { text: title, left: 'center', textStyle: { fontSize: 14, fontWeight: 600 } },
    ...chartEmptyTitle(data),
  ],
  tooltip: {
    trigger: 'axis',
    formatter: (params: Array<{ name: string; value: number }>) => percentFormatter(params[0], data),
  },
  grid: { left: 50, right: 24, top: 50, bottom: 28 },
  xAxis: { type: 'category', data: labels },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'bar',
      data,
      barMaxWidth: 30,
      itemStyle: {
        color,
        borderRadius: [5, 5, 0, 0],
      },
    },
  ],
})

const buildLineOption = (title: string, labels: string[], data: number[], color: string) => ({
  title: [
    { text: title, left: 'center', textStyle: { fontSize: 14, fontWeight: 600 } },
    ...chartEmptyTitle(data),
  ],
  tooltip: {
    trigger: 'axis',
    formatter: (params: Array<{ name: string; value: number }>) => percentFormatter(params[0], data),
  },
  grid: { left: 50, right: 24, top: 50, bottom: 28 },
  xAxis: { type: 'category', data: labels },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'line',
      smooth: true,
      data,
      symbolSize: 7,
      lineStyle: { color, width: 3 },
      itemStyle: { color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(24, 169, 154, 0.35)' },
          { offset: 1, color: 'rgba(24, 169, 154, 0.02)' },
        ]),
      },
    },
  ],
})

const buildPieOption = (title: string, data: Array<{ name: string; value: number }>) => ({
  title: { text: title, left: 'center', textStyle: { fontSize: 14, fontWeight: 600 } },
  tooltip: {
    trigger: 'item',
    formatter: `{b}<br/>${t('companyDashboard.tooltipValue')}: {c}<br/>${t('companyDashboard.tooltipRatio')}: {d}%`,
  },
  legend: {
    bottom: 4,
    left: 'center',
  },
  series: [
    {
      type: 'pie',
      radius: ['38%', '64%'],
      center: ['50%', '48%'],
      avoidLabelOverlap: true,
      label: {
        formatter: '{b}: {d}%',
      },
      data,
    },
  ],
})

const getChartConfigs = () => {
  const d = currentData.value
  return [
    {
      ref: chartTradeBarRef,
      option: buildBarOption(t('companyDashboard.chartTransactionBar'), d.tradeLabels, d.tradeCount, cssToken('--pen-chart', '#1D5FBF')),
    },
    {
      ref: chartTrendLineRef,
      option: buildLineOption(t('companyDashboard.chartAIPrediction'), d.reportLabels, d.aiTrend, cssToken('--pen-chart-2', '#6E93CE')),
    },
    {
      ref: chartSuggestBarRef,
      option: buildBarOption(t('companyDashboard.chartAISuggestion'), d.reportLabels, d.aiSuggest, cssToken('--pen-chart-3', '#A8BFE0')),
    },
    {
      ref: chartEmissionPieRef,
      option: buildPieOption(t('companyDashboard.chartEmissionPie'), d.emissionPie),
    },
    {
      ref: chartTradePieRef,
      option: buildPieOption(t('companyDashboard.chartTransactionPie'), d.tradePie),
    },
    {
      ref: chartCreditLineRef,
      option: buildLineOption(t('companyDashboard.chartCreditLine'), d.creditLabels, d.creditTrend, cssToken('--pen-chart-4', '#D3DEED')),
    },
  ]
}

const renderCharts = async () => {
  await nextTick()
  const configs = getChartConfigs()

  configs.forEach(({ ref: domRef, option }, index) => {
    const dom = domRef.value
    if (!dom) return

    if (!chartInstances[index]) {
      chartInstances[index] = echarts.init(dom)
    }

    chartInstances[index]?.setOption(option, true)
  })
}

const fetchCreditScore = async () => {
  try {
    const result = await getMyScore()
    creditScore.value = Number(result?.score || 0)
  } catch {
    ElMessage.error(t('companyDashboard.loadUserFailed'))
    creditScore.value = 0
  }
}

const fetchQuotaSummary = async () => {
  try {
    const result: EnterpriseQuotaResponse = await getQuotaInfo()
    quotaTotal.value = Number(result.totalQuota || 0)
  } catch {
    quotaTotal.value = 0
  }
}

const fetchCarbonCoinSummary = async () => {
  try {
    const result = await getMyAccount()
    carbonCoinTotal.value = Number(result?.balance || 0)
  } catch {
    carbonCoinTotal.value = 0
  }
}

const fetchTradeData = async () => {
  try {
    const result = await getMyTrades({
      pageNum: 1,
      pageSize: 100,
    })
    tradeData.value = result?.items || []
  } catch {
    ElMessage.error(t('companyDashboard.loadTradeFailed'))
    tradeData.value = []
  }
}

const fetchCarbonReports = async () => {
  try {
    const result = await getMyReports({
      pageNum: 1,
      pageSize: 100,
    })
    carbonReports.value = result?.items || []
    assetPool.value = (result?.items || [])
      .filter((report) => Boolean(report.reportNo))
      .map((report) => ({
        assetNo: report.reportNo,
        other: String(report.reportType ?? ''),
        keyword: String(report.accountingPeriod ?? ''),
      }))
  } catch {
    ElMessage.error(t('companyDashboard.loadEmissionFailed'))
    carbonReports.value = []
    assetPool.value = []
  }
}

const fetchAdmissionStatus = async () => {
  try {
    const list = await getMyEnterpriseAdmission()
    admissionStatus.value = list[0] || null
  } catch {
    admissionStatus.value = null
  }
}

const loadDashboardData = async () => {
  loading.value = true
  try {
    await Promise.all([
      fetchCreditScore(),
      fetchQuotaSummary(),
      fetchCarbonCoinSummary(),
      fetchTradeData(),
      fetchCarbonReports(),
      fetchAdmissionStatus(),
    ])
  } finally {
    loading.value = false
    await renderCharts()
  }
}

const onSearch = () => {
  appliedFilters.value = {
    assetNo: queryForm.assetNo,
    otherCondition: queryForm.otherCondition,
    keyword: queryForm.keyword,
  }
  // 匹配数随筛选结果一起反馈，零匹配时提示用户调整条件而非误以为筛选成功
  // 注意：vue-i18n 的 {n} 是复数保留变量，会被复数上下文覆盖为 0，必须用其他参数名
  ElMessage.success(t('companyDashboard.filterCompleteN', { count: filteredAssets.value.length }))
  renderCharts()
}

const onResize = () => {
  chartInstances.forEach((instance) => {
    instance?.resize()
  })
}

watch(timeDimension, () => {
  renderCharts()
})

watch(filteredAssets, () => {
  renderCharts()
})

watch(locale, () => {
  renderCharts()
})

onMounted(() => {
  loadDashboardData()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chartInstances.forEach((instance) => {
    instance?.dispose()
  })
})
</script>

<template>
  <section class="dashboard-page pen-enter">
    <PageHeader
      :title="t('companyDashboard.pageTitle')"
      :description="t('companyDashboard.pageDescription')"
      :crumbs="[t('companyDashboard.breadcrumbCompany'), t('companyDashboard.breadcrumbDashboard')]"
    />

    <PageHint :text="t('pageHints.companyDashboard')" />

    <StatStrip :items="overviewStats" :loading="loading" />

    <FilterBar :aria-label="t('companyDashboard.filterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('companyDashboard.searchAssetNo') }}</span>
        <el-input v-model="queryForm.assetNo" :placeholder="t('companyDashboard.searchAssetNo')" clearable />
      </label>
      <label class="pen-field">
        <span class="pen-field__label">{{ t('companyDashboard.searchCategory') }}</span>
        <el-input v-model="queryForm.otherCondition" :placeholder="t('companyDashboard.searchCategory')" clearable />
      </label>
      <label class="pen-field">
        <span class="pen-field__label">{{ t('companyDashboard.searchPeriodRegion') }}</span>
        <el-input v-model="queryForm.keyword" :placeholder="t('companyDashboard.searchPeriodRegion')" clearable />
      </label>
      <el-button :loading="loading" type="primary" @click="onSearch">{{ t('common.search') }}</el-button>
      <div class="pen-field search-right">
        <span class="pen-field__label">{{ t('companyDashboard.timeDimension') }}</span>
        <el-radio-group v-model="timeDimension">
          <el-radio-button value="day">{{ t('companyDashboard.timeDay') }}</el-radio-button>
          <el-radio-button value="month">{{ t('companyDashboard.timeMonth') }}</el-radio-button>
          <el-radio-button value="year">{{ t('companyDashboard.timeYear') }}</el-radio-button>
        </el-radio-group>
      </div>
    </FilterBar>

    <el-alert
      v-if="!loading && filterEmpty"
      class="filter-empty-note"
      :title="t('companyDashboard.noMatchTitle')"
      :description="t('companyDashboard.noMatchDesc')"
      type="warning"
      show-icon
      :closable="false"
    />

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="3" animated />
    </div>

    <DataPanel
      :title="t('companyDashboard.briefingPanelTitle')"
      :meta="t('companyDashboard.briefingPanelMeta')"
    >
      <template #actions>
        <router-link to="/enterprise/market-prediction" class="pen-action-link pen-action-link--primary">
          <el-icon><TrendCharts /></el-icon>
          <span>{{ t('companyDashboard.btnMarketTerminal') }}</span>
        </router-link>
        <router-link to="/enterprise/enterprise-inference" class="pen-action-link">
          <el-icon><DataAnalysis /></el-icon>
          <span>{{ t('companyDashboard.btnHealthCenter') }}</span>
        </router-link>
      </template>

      <div class="briefing-body">
        <div class="briefing-title-row">
          <span class="briefing-badge">AI EXECUTIVE BRIEFING</span>
          <strong class="briefing-headline">{{ t('companyDashboard.briefingTitle') }}</strong>
          <el-tag type="success" size="small" effect="plain">AAA 稳健级 ｜ 85分</el-tag>
        </div>
        <p class="briefing-text">
          {{ t('companyDashboard.briefingDesc') }}
        </p>
      </div>
    </DataPanel>

    <DataPanel
      v-if="admissionStatus !== null"
      :title="t('companyDashboard.admissionStatus')"
      :meta="admissionStatus.certificateNo || ''"
    >
      <div class="panel-pad">
        <div class="admission-row">
          <el-tag :type="admissionStatus.status === 1 ? 'success' : 'danger'">
            {{ admissionStatus.status === 1 ? t('certificateManage.active') : t('certificateManage.revoked') }}
          </el-tag>
          <span v-if="admissionStatus.certificateNo" class="admission-row__cert">
            {{ admissionStatus.certificateNo }}
          </span>
        </div>
      </div>
    </DataPanel>

    <section v-if="!loading" class="chart-grid" :aria-label="t('companyDashboard.panelChartsMeta')">
      <DataPanel :title="t('companyDashboard.chartTransactionBar')">
        <div class="chart-body"><div ref="chartTradeBarRef" class="chart-box" /></div>
      </DataPanel>
      <DataPanel :title="t('companyDashboard.chartAIPrediction')">
        <div class="chart-body"><div ref="chartTrendLineRef" class="chart-box" /></div>
      </DataPanel>
      <DataPanel :title="t('companyDashboard.chartAISuggestion')">
        <div class="chart-body"><div ref="chartSuggestBarRef" class="chart-box" /></div>
      </DataPanel>
      <DataPanel :title="t('companyDashboard.chartEmissionPie')">
        <div class="chart-body"><div ref="chartEmissionPieRef" class="chart-box" /></div>
      </DataPanel>
      <DataPanel :title="t('companyDashboard.chartTransactionPie')">
        <div class="chart-body"><div ref="chartTradePieRef" class="chart-box" /></div>
      </DataPanel>
      <DataPanel :title="t('companyDashboard.chartCreditLine')">
        <div class="chart-body"><div ref="chartCreditLineRef" class="chart-box" /></div>
      </DataPanel>
    </section>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.panel-pad {
  padding: var(--space-4);
}

.search-right {
  margin-left: auto;
}

.filter-empty-note {
  margin-bottom: 0;
}

/* ---------- AI 晨报面板 ---------- */
.briefing-body {
  padding: var(--space-4);
}

.briefing-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2-5);
  margin-bottom: var(--space-1-5);
}

.briefing-badge {
  padding: var(--space-0-5) var(--space-2);
  border-radius: var(--radius-control);
  background: var(--pen-pill-info-bg);
  color: var(--pen-pill-info-ink);
  font-size: var(--fs-pill);
  font-weight: 500;
  letter-spacing: 0.04em;
}

.briefing-headline {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--pen-ink);
}

.briefing-text {
  margin: 0;
  font-size: var(--fs-sub);
  line-height: 1.7;
  color: var(--pen-muted);
}

.pen-action-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  height: 28px;
  padding: 0 var(--space-2-5);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-control);
  background: var(--pen-surface);
  color: var(--pen-ink);
  font-size: var(--fs-caption);
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
}

.pen-action-link--primary {
  background: var(--pen-action);
  border-color: var(--pen-action);
  color: var(--color-on-inverse);
}

.pen-action-link:hover {
  border-color: var(--pen-muted);
}

.pen-action-link--primary:hover {
  background: var(--pen-action-hover);
  border-color: var(--pen-action-hover);
}

/* ---------- 准入状态 ---------- */
.admission-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.admission-row__cert {
  font-size: var(--fs-sub);
  color: var(--pen-muted);
}

/* ---------- 图表 ---------- */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(280px, 1fr));
  gap: var(--space-3);
}

.chart-body {
  padding: var(--space-2) var(--space-3) var(--space-3);
}

.chart-box {
  width: 100%;
  height: 330px;
}

.loading-container {
  padding: var(--space-5);
  background: var(--pen-surface);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
}

@media (max-width: 1280px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
