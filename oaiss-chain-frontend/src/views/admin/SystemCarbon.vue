<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：管理员端 - 全网碳核算报告监管中心 (System Carbon Accounting)
 * 【路由地址】：/admin/system/carbon
 * 【访问权限】：ROLE_ADMIN (系统管理员)
 * 【界面可见功能】：
 *   1. 全网报告检索栏：输入企业名称、报告编号或核算周期关键字模糊检索
 *   2. 全量报告监管列表：展示报告编号、企业名称、核算周期、总排放量、状态与时间
 *   3. 报告详情审查抽屉：穿透查阅各报告的核算因子数据与附件发票清单
 *   4. 监管与复核标准提示（PageHint / StatusTag）
 * 【对应后端 Controller & API】：
 *   - CarbonController:
 *     * GET /api/v1/carbon/reports -> getReportList() 分页查询全平台碳报告列表
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Clock, DataAnalysis, Link, Odometer } from '@element-plus/icons-vue'
import { getReportList } from '../../api/carbon'
import type { CarbonReportResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'
import TermTip from '../../components/TermTip.vue'

const { t } = useI18n()

const searchForm = reactive({
  keyword: '',
})

const tableData = ref<CarbonReportResponse[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword.trim() || undefined,
    }
    const response = await getReportList(params)
    tableData.value = response.items || []
    total.value = response.total || 0
  } catch {
    ElMessage.error(t('systemCarbon.loadFailed'))
  } finally {
    loading.value = false
  }
}

const onQuery = () => {
  currentPage.value = 1
  fetchData()
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const onCurrentChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

/** 首屏统计条：全部由已加载的本页报告数据推导，不新增接口请求 */
const overviewStats = computed(() => {
  const rows = tableData.value
  const totalEmission = rows.reduce((sum, row) => sum + (Number(row.totalEmission) || 0), 0)
  const pending = rows.filter((row) => row.status === 1 || row.status === 2).length
  const locked = rows.filter((row) => row.status === 3 || row.status === 5).length
  const rejected = rows.filter((row) => row.status === 4).length

  return [
    {
      label: t('systemCarbon.statTotalReports'),
      value: total.value,
      unit: t('systemCarbon.statUnitReports'),
      delta: t('systemCarbon.statTotalReportsHint', { count: rows.length }),
      tone: 'neu' as const,
      icon: DataAnalysis,
    },
    {
      label: t('systemCarbon.statTotalEmission'),
      value: Number(totalEmission.toFixed(1)),
      unit: 'tCO2e',
      delta: t('systemCarbon.statTotalEmissionHint'),
      tone: 'neu' as const,
      icon: Odometer,
    },
    {
      label: t('systemCarbon.statPending'),
      value: pending,
      unit: t('systemCarbon.statUnitReports'),
      delta: t('systemCarbon.statPendingHint'),
      tone: pending > 0 ? ('neu' as const) : ('pos' as const),
      icon: Clock,
    },
    {
      label: t('systemCarbon.statSettled'),
      value: locked,
      unit: t('systemCarbon.statUnitReports'),
      delta: t('systemCarbon.statSettledHint', { rejected }),
      tone: rejected > 0 ? ('neg' as const) : ('pos' as const),
      icon: Link,
    },
  ]
})

onMounted(() => {
  fetchData()
})
</script>

<template>
  <section class="carbon-page pen-enter">
    <PageHeader
      :title="t('menu.carbonManage')"
      :description="t('systemCarbon.pageDescription')"
      :crumbs="[t('systemCarbon.breadcrumbSystem'), t('systemCarbon.breadcrumbCarbon')]"
    />

    <PageHint :text="t('pageHints.systemCarbon')" />

    <StatStrip :items="overviewStats" :loading="loading" />

    <FilterBar :aria-label="t('systemCarbon.filterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('common.keyword') }}</span>
        <el-input
          v-model="searchForm.keyword"
          :placeholder="t('common.enterKeyword')"
          clearable
          @keyup.enter="onQuery"
        />
      </label>
      <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
    </FilterBar>

    <DataPanel
      :title="t('systemCarbon.panelTitle')"
      :meta="t('systemCarbon.panelMeta', { total })"
    >
      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="reportNo" :label="t('systemCarbon.colReportNo')" min-width="140" />
        <el-table-column prop="enterpriseName" :label="t('systemCarbon.colEnterpriseName')" min-width="180" />
        <el-table-column prop="title" :label="t('systemCarbon.colReportTitle')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="totalEmission" :label="t('systemCarbon.colTotalEmission')" min-width="130">
          <template #header>
            <span>{{ t('systemCarbon.colTotalEmission') }}<TermTip term="tco2e" /></span>
          </template>
          <template #default="{ row }">{{ formatNumber(row.totalEmission) }} tCO2e</template>
        </el-table-column>
        <el-table-column prop="statusText" :label="t('common.status')" min-width="100">
          <template #default="{ row }">
            <StatusTag domain="report" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" :label="t('systemCarbon.colReviewer')" min-width="120" />
        <el-table-column prop="createdAt" :label="t('common.createTime')" min-width="170" :formatter="(row: CarbonReportResponse) => formatDateTime(row.createdAt)" />
      </el-table>

      <template #footer>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="onSizeChange"
          @current-change="onCurrentChange"
        />
      </template>
    </DataPanel>
  </section>
</template>

<style scoped>
.carbon-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
</style>
