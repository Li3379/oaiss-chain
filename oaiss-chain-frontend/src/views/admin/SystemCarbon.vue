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
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getReportList } from '../../api/carbon'
import type { CarbonReportResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
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

onMounted(() => {
  fetchData()
})
</script>

<template>
  <section class="carbon-page">
    <el-card class="section-card" shadow="never">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>{{ t('systemCarbon.breadcrumbSystem') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ t('systemCarbon.breadcrumbCarbon') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </el-card>

    <PageHint :text="t('pageHints.systemCarbon')" />

    <el-card class="section-card" shadow="never">
      <el-form :inline="true" class="search-form">
        <el-form-item :label="t('common.keyword')">
          <el-input v-model="searchForm.keyword" :placeholder="t('common.enterKeyword')" clearable />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="section-card" shadow="never">
      <el-table :data="tableData" border v-loading="loading">
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

      <div class="pagination-row">
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
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.carbon-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
}

.pagination-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
