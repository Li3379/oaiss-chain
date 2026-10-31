<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：管理员端 - 系统组织架构与用户构成统计大盘 (Data Statistics)
 * 【路由地址】：/admin/data/statistics
 * 【访问权限】：ROLE_ADMIN (系统管理员)
 * 【界面可见功能】：
 *   1. 用户组织指标卡片：全网用户总数、控排企业总数、专业审核员总数、第三方机构总数
 *   2. 用户角色构成饼图 (ECharts)：展示企业、审核员、第三方机构与管理员的比例分布
 *   3. 统计口径与运营大盘释义（PageHint）
 * 【对应后端 Controller & API】：
 *   - AdminController:
 *     * GET /api/v1/admin/statistics -> getStatistics() 获取全网各角色用户统计
 *     * GET /api/v1/admin/dashboard  -> getDashboard() 获取系统仪表盘数据
 * ==============================================================================
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { User, OfficeBuilding, DocumentChecked, UserFilled } from '@element-plus/icons-vue'
import echarts from '../../utils/echarts'
import { getStatistics } from '../../api/admin'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'

const { t, locale } = useI18n()

const statistics = ref({
  totalUsers: 0,
  enterpriseCount: 0,
  reviewerCount: 0,
  thirdPartyCount: 0,
})

const loading = ref(false)
const chartRef = ref(null)
let chartInstance: ReturnType<typeof echarts.init> | null = null

const fetchData = async () => {
  loading.value = true
  try {
    const response = await getStatistics()
    statistics.value = response
  } catch (error) {
    ElMessage.error(t('dataStatistics.loadFailed'))
  } finally {
    loading.value = false
  }
}

const getUserTypeDistribution = computed(() => {
  return [
    { name: t('dataStatistics.pieEnterprise'), value: statistics.value.enterpriseCount },
    { name: t('dataStatistics.pieAuditor'), value: statistics.value.reviewerCount },
    { name: t('dataStatistics.pieThirdParty'), value: statistics.value.thirdPartyCount },
    { name: t('dataStatistics.pieAdmin'), value: statistics.value.totalUsers - statistics.value.enterpriseCount - statistics.value.reviewerCount - statistics.value.thirdPartyCount },
  ].filter(item => item.value > 0)
})

/** 首屏统计条：全部由已加载的统计接口返回值推导，不新增接口请求 */
const overviewStats = computed(() => {
  const s = statistics.value
  const adminCount = Math.max(0, s.totalUsers - s.enterpriseCount - s.reviewerCount - s.thirdPartyCount)
  const enterpriseShare = s.totalUsers > 0 ? Number(((s.enterpriseCount / s.totalUsers) * 100).toFixed(1)) : 0

  return [
    {
      label: t('dataStatistics.statTotalUsers'),
      value: s.totalUsers,
      unit: t('dataStatistics.statUnitUsers'),
      delta: t('dataStatistics.statAdminHint', { count: adminCount }),
      tone: 'neu' as const,
      icon: User,
    },
    {
      label: t('dataStatistics.statEnterpriseCount'),
      value: s.enterpriseCount,
      unit: t('dataStatistics.statUnitEnterprises'),
      delta: t('dataStatistics.statShareHint', { count: enterpriseShare }),
      tone: 'pos' as const,
      icon: OfficeBuilding,
    },
    {
      label: t('dataStatistics.statAuditorCount'),
      value: s.reviewerCount,
      unit: t('dataStatistics.statUnitUsers'),
      delta: t('dataStatistics.statAuditorHint'),
      tone: 'neu' as const,
      icon: DocumentChecked,
    },
    {
      label: t('dataStatistics.statThirdPartyCount'),
      value: s.thirdPartyCount,
      unit: t('dataStatistics.statUnitEnterprises'),
      delta: t('dataStatistics.statThirdPartyHint'),
      tone: 'neu' as const,
      icon: UserFilled,
    },
  ]
})

const renderChart = async () => {
  await nextTick()
  const dom = chartRef.value
  if (!dom) {
    return
  }

  if (!chartInstance) {
    chartInstance = echarts.init(dom)
  }

  const option = {
    title: {
      text: t('dataStatistics.chartUserTypePie'),
      left: 'center',
      textStyle: {
        fontSize: 16,
        fontWeight: 600,
      },
    },
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      left: 'center',
      bottom: 10,
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['50%', '50%'],
        label: {
          formatter: '{b}: {d}%',
        },
        data: getUserTypeDistribution.value,
      },
    ],
  }

  chartInstance.setOption(option, true)
}

const onResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

watch([statistics, locale], () => {
  renderChart()
})

onMounted(() => {
  fetchData()
  renderChart()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (chartInstance) {
    chartInstance.dispose()
  }
})
</script>

<template>
  <section class="stats-page pen-enter">
    <PageHeader
      :title="t('dataStatistics.title')"
      :description="t('dataStatistics.description')"
      :crumbs="[t('dataStatistics.breadcrumbDataManage'), t('dataStatistics.breadcrumbStatistics')]"
    />

    <PageHint :text="t('pageHints.dataStatistics')" />

    <StatStrip :items="overviewStats" :loading="loading" />

    <DataPanel
      :title="t('dataStatistics.panelTitle')"
      :meta="t('dataStatistics.panelMeta', { total: statistics.totalUsers })"
    >
      <div class="chart-wrap">
        <div ref="chartRef" class="chart-box" />
      </div>
    </DataPanel>
  </section>
</template>

<style scoped>
.stats-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.chart-wrap {
  padding: var(--space-4);
}

.chart-box {
  width: 100%;
  height: 400px;
}
</style>
