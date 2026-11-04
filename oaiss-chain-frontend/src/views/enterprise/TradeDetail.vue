<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 交易详情 (Trade Detail)
 * 【路由地址】：/enterprise/trading/orders/:id
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 基本信息：交易编号、类型、买卖双方、数量、单价、总金额
 *   2. 核算关联：指向该交易关联的碳核算报告入口
 *   3. 状态时间线：创建 → 成交审核（如有） → 完成，由现有字段推导
 *   4. 备注与审计：备注与成交审核态
 * 【数据来源】：
 *   - TradeController: GET /api/v1/trade/{id} -> getTrade()
 * 【刻意不展示】区块链哈希等基础设施细节（Phase 8 D-02）。
 * ==============================================================================
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getTrade } from '../../api/trade'
import type { TradeResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import StatusTag from '../../components/StatusTag.vue'

const { t } = useI18n()
const route = useRoute()

const tradeId = computed(() => Number(route.params.id))

const loading = ref(false)
const notFound = ref(false)
const trade = ref<TradeResponse | null>(null)

/** 交易类型文案走前端 i18n（后端 tradeTypeText 为中文，en 界面下会混语言） */
const tradeTypeLabel = (tradeType: number | null | undefined) => {
  if (tradeType === 1) return t('ordersManage.tradeTypeAuction')
  if (tradeType === 2) return t('ordersManage.tradeTypeP2P')
  return '-'
}

/** 成交审核态：null/0=无需审核，1=待审核，2=已通过，3=已驳回 */
const hasAuditStatus = computed(() => {
  const status = trade.value?.auditStatus
  return status != null && status !== 0
})

const auditStatusText = computed(() => {
  switch (trade.value?.auditStatus) {
    case 1: return t('tradeDetail.auditPending')
    case 2: return t('tradeDetail.auditApproved')
    case 3: return t('tradeDetail.auditRejected')
    default: return '-'
  }
})

const reportId = computed(() => Number(trade.value?.reportId) || 0)

const timelineSteps = computed(() => {
  const row = trade.value
  if (!row) return []
  const steps = [
    { title: t('tradeDetail.timelineCreated'), description: formatDateTime(row.createdAt) || '-' },
  ]
  if (hasAuditStatus.value) {
    steps.push({ title: t('tradeDetail.timelineAudit'), description: auditStatusText.value })
  }
  steps.push({
    title: t('tradeDetail.timelineCompleted'),
    description: formatDateTime(row.completedAt) || t('tradeDetail.timelinePending'),
  })
  return steps
})

/** 已完成走到末节点，其余停在首节点——状态时间线只表达已发生的事实 */
const timelineActive = computed(() => (trade.value?.status === 2 ? timelineSteps.value.length : 1))

const loadTrade = async () => {
  if (!tradeId.value) {
    notFound.value = true
    return
  }
  try {
    loading.value = true
    notFound.value = false
    trade.value = await getTrade(tradeId.value)
  } catch {
    // 404/403 的提示由 axios 拦截器统一弹出，这里只负责页面级空态
    trade.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTrade()
})
</script>

<template>
  <section class="detail-page pen-enter" v-loading="loading">
    <PageHeader
      :title="trade?.tradeNo || t('tradeDetail.title')"
      :description="t('tradeDetail.description')"
      :crumbs="[t('tradeDetail.breadcrumbTrading'), t('tradeDetail.breadcrumbDetail')]"
    />

    <template v-if="trade">
      <DataPanel :title="t('tradeDetail.sectionBasic')">
        <template #actions>
          <StatusTag domain="trade" :status="trade.status" size="large" />
        </template>
        <div class="detail-body">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('ordersManage.colTradeNo')">{{ trade.tradeNo }}</el-descriptions-item>
            <el-descriptions-item :label="t('ordersManage.colTradeType')">{{ tradeTypeLabel(trade.tradeType) }}</el-descriptions-item>
            <el-descriptions-item :label="t('ordersManage.labelSeller')">{{ trade.sellerName || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('ordersManage.labelBuyer')">{{ trade.buyerName || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('ordersManage.colQuantity')">{{ formatNumber(trade.quantity) }}</el-descriptions-item>
            <el-descriptions-item :label="t('ordersManage.colUnitPrice')">{{ formatNumber(trade.unitPrice) }}</el-descriptions-item>
            <el-descriptions-item :label="t('ordersManage.colTotalAmount')" :span="2">{{ formatNumber(trade.totalAmount) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </DataPanel>

      <DataPanel :title="t('tradeDetail.sectionReport')">
        <div class="detail-body">
          <span v-if="reportId" class="report-link">
            <span>#{{ reportId }}</span>
            <router-link :to="'/enterprise/carbon/upload'">{{ t('tradeDetail.reportLink') }}</router-link>
          </span>
          <span v-else>-</span>
        </div>
      </DataPanel>

      <DataPanel :title="t('tradeDetail.sectionTimeline')">
        <div class="detail-body">
          <el-steps :active="timelineActive" finish-status="success" align-center>
            <el-step
              v-for="(step, index) in timelineSteps"
              :key="index"
              :title="step.title"
              :description="step.description"
            />
          </el-steps>
        </div>
      </DataPanel>

      <DataPanel :title="t('tradeDetail.sectionAudit')">
        <div class="detail-body">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('ordersManage.labelRemark')" :span="2">{{ trade.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('tradeDetail.labelAuditStatus')" :span="2">
              {{ hasAuditStatus ? auditStatusText : '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </DataPanel>

      <div class="detail-actions">
        <router-link :to="'/enterprise/orders/manage'">
          <el-button>{{ t('tradeDetail.backToList') }}</el-button>
        </router-link>
      </div>
    </template>

    <el-empty v-else-if="notFound" :description="t('tradeDetail.notFound')">
      <router-link :to="'/enterprise/orders/manage'">
        <el-button type="primary">{{ t('tradeDetail.backToList') }}</el-button>
      </router-link>
    </el-empty>
  </section>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.detail-body {
  padding: var(--space-5) var(--space-4);
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
}

.report-link {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-3);
}
</style>
