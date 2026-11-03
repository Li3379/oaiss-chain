<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 点对点（P2P）定向协议转让 (P2P Carbon Trading)
 * 【路由地址】：/enterprise/trading/p2p
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 定向转让发起面板：选择对手方企业（下拉搜索带信用等级）、录入配额数量与单价
 *   2. 身份视图筛选（买方/卖方/全部）：快速定位我发起的出让或收到的待承接订单
 *   3. 待确认订单承接操作：买方在受让订单卡片上点击【确认承接/支付】，触发交割
 *   4. 待确认订单撤回操作：卖方在对方未确认前可点击【撤单】释放配额锁定
 *   5. 交易对手方企业信用画像联动：穿透展示买卖双方信誉分值与信用等级
 * 【对应后端 Controller & API】：
 *   - TradeController:
 *     * POST /api/v1/trade/p2p          -> createP2PTrade() 发起定向转让
 *     * GET  /api/v1/trade/my-trades    -> getMyTrades() 查询P2P转让记录
 *     * POST /api/v1/trade/{id}/confirm -> confirmTrade() 确认承接订单
 *     * POST /api/v1/trade/{id}/cancel  -> cancelTrade() 撤销待确认订单
 *   - EnterpriseController:
 *     * GET  /api/v1/enterprise/search  -> searchEnterprises() 按企业名/信用码远程搜索交易对手
 * ==============================================================================
 */
import { computed, reactive, ref, onMounted } from "vue"
import { useI18n } from "vue-i18n"
import { ElMessage, ElMessageBox } from "element-plus"
import { CircleCheck, Clock, Histogram, PriceTag } from "@element-plus/icons-vue"
import { getMyTrades, createP2PTrade, cancelTrade, confirmTrade } from "../../api/trade"
import { useCertificateStatus } from '../../composables/useCertificateStatus'
import { searchEnterprises, type EnterpriseSearchItem } from "../../api/enterprise"
import type { TradeIdentityFilter, TradeResponse } from "../../types"
import { formatDateTime } from "../../utils/format"
import PageHint from '../../components/PageHint.vue'
import { formatNumber } from '../../utils/format'
import { useAppStore } from "../../store"
import StatusTag from '../../components/StatusTag.vue'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import DataPanel from '../../components/pen/DataPanel.vue'

const { t } = useI18n()

// 准入证书门禁：无有效证书时不得发起 P2P 转让（后端以 7001 拒绝）
const { admitted, blockReason } = useCertificateStatus()
const appStore = useAppStore()

const loading = ref(false)
const searchForm = reactive<{
  keyword: string
  identity: TradeIdentityFilter | ""
  tradeNo: string
}>({
  keyword: "",
  identity: "",
  tradeNo: "",
})

// "全部"选项：一旦选过身份，用户可一键恢复全量视图（clearable 仅悬停可见，可发现性弱）
const identityOptions: Array<{ label: string; value: TradeIdentityFilter | "" }> = [
  { label: "tradingP2P.identityAll", value: "" },
  { label: "tradingP2P.identityBuyer", value: "buyer" },
  { label: "tradingP2P.identitySeller", value: "seller" },
]

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const tableData = ref<TradeResponse[]>([])

const dialogVisible = ref(false)
const dialogFormRef = ref()
const dialogForm = reactive({
  buyerId: null as number | null,
  quantity: null as number | null,
  // 2026-11-03：P2P 改为「碳币换碳币」，无单价维度
  remark: "",
})

const dialogRules = {
  buyerId: [
    { required: true, message: t("tradingP2P.enterBuyerId"), trigger: "blur" },
  ],
  quantity: [
    { required: true, message: t("tradingP2P.enterQuantity"), trigger: "blur" },
    { type: "number", min: 0.01, message: t("tradingP2P.quantityPositive"), trigger: "blur" }
  ],
}

/**
 * 转让总量 = 数量本身。
 *
 * 2026-11-03：P2P 是「碳币换碳币」，1 枚换 1 枚，没有单价维度。
 * 保留这个 computed 只是为了让模板不必内联算式。
 */
const estimatedTotal = computed(() => {
  const q = Number(dialogForm.quantity || 0)
  return Number.isFinite(q) && q > 0 ? q.toFixed(2) : '0.00'
})

const buildTradeQuery = () => ({
  pageNum: page.value,
  pageSize: pageSize.value,
  tradeType: 2,
  tradeNo: searchForm.tradeNo.trim() || undefined,
  keyword: searchForm.keyword.trim() || undefined,
  identity: searchForm.identity || undefined,
})

const loadTrades = async () => {
  loading.value = true
  try {
    const result = await getMyTrades(buildTradeQuery())
    tableData.value = result?.items || []
    total.value = result?.total || 0
  } catch (error) {
    ElMessage.error(t("tradingP2P.loadFailed"))
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const onQuery = () => {
  page.value = 1
  loadTrades()
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  page.value = 1
  loadTrades()
}

const onPageChange = (val: number) => {
  page.value = val
  loadTrades()
}

const resetDialogForm = () => {
  dialogForm.buyerId = null
  dialogForm.quantity = null
  // unitPrice 已移除：P2P 无价格维度
  dialogForm.remark = ""
}

/** 交易对手下拉：信誉排名提供企业名单，逐个取详情映射出 userId（后端 P2P 以用户 ID 定位买方） */
const buyerOptions = ref<Array<{ userId: number; label: string }>>([])
const buyerOptionsLoading = ref(false)
/** 请求序号：用于丢弃过期响应（连续输入时只认最后一次） */
let buyerSearchSeq = 0

/**
 * 交易对手远程搜索。
 *
 * 替代原先「取信誉榜前 50 名 -> 逐个查企业详情」的 N+1 实现：
 * 那个实现在企业超过 50 家时静默截断，且把内部 userId 拼进了用户可见文案。
 *
 * 三条约束：
 *  - 不足 2 个字符不发请求（避免每敲一个键就打一次后端）
 *  - 下拉只展示「企业名（信用码后 6 位）」，不展示 userId
 *  - 内部仍以 userId 提交（后端 P2P 契约如此），只是不渲染给用户
 */
async function buyerSearch(keyword: string): Promise<void> {
  const kw = (keyword || '').trim()
  if (kw.length < 2) {
    buyerOptions.value = []
    return
  }
  const seq = ++buyerSearchSeq
  buyerOptionsLoading.value = true
  try {
    const result = await searchEnterprises(kw, 1, 20)
    if (seq !== buyerSearchSeq) return
    const items = result?.items ?? []
    buyerOptions.value = items.map((item: EnterpriseSearchItem) => ({
      userId: item.userId,
      label: `${item.enterpriseName}（${(item.creditCode || '').slice(-6)}）`,
    }))
  } catch {
    if (seq === buyerSearchSeq) buyerOptions.value = []
  } finally {
    if (seq === buyerSearchSeq) buyerOptionsLoading.value = false
  }
}

const openAddDialog = () => {
  // 兜底拦截：无有效准入证书时后端会以 7001 拒绝，
  // 前端在此拦住，避免用户填完表单才被拒。
  if (!admitted.value) {
    ElMessage.warning(admissionBlockMessage.value)
    return
  }
  resetDialogForm()
  dialogVisible.value = true
}

/** 无证书时的提示文案，按「未签发 / 已吊销 / 已过期」区分 */
const admissionBlockMessage = computed(() => {
  if (blockReason.value === 'expired') return t('admissionBanner.bodyExpired', { date: '-' })
  if (blockReason.value === 'revoked') return t('admissionBanner.bodyRevoked')
  return t('admissionBanner.bodyNone')
})

const onCancel = () => {
  dialogVisible.value = false
  ElMessage.info(t("tradingP2P.cancelled"))
}

const onSave = async () => {
  const valid = await dialogFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning(t("tradingP2P.incompleteForm"))
    return
  }

  try {
    await createP2PTrade({
      tradeType: 2,
      buyerId: Number(dialogForm.buyerId),
      quantity: Number(dialogForm.quantity),
      // 2026-11-03：P2P 无单价维度，后端按 1 处理。不传，避免误以为它在生效。
      remark: dialogForm.remark || "",
    })

    dialogVisible.value = false
    ElMessage.success(`${t('tradingP2P.createSuccess')} ${t('nextSteps.afterTradeCreated')}`)
    await loadTrades()
  } catch (error) {
    ElMessage.error(t("tradingP2P.createFailed") + ": " + ((error as Error).message || ""))
  }
}

const onCancelTrade = async (row: TradeResponse) => {
  if (row.status !== 0) {
    ElMessage.warning(t("tradingP2P.canOnlyCancelPending"))
    return
  }

  try {
    await ElMessageBox.confirm(t("ordersManage.confirmCancel"), t("ordersManage.cancelTrade"), {
      confirmButtonText: t("common.confirm"),
      cancelButtonText: t("common.cancel"),
      type: "warning",
    })

    await cancelTrade(row.id)
    ElMessage.success(t("tradingP2P.cancelSuccess"))
    await loadTrades()
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(t("tradingP2P.cancelFailed") + ": " + ((error as Error).message || ""))
    } else {
      ElMessage.info(t("common.cancelled"))
    }
  }
}

const onConfirmTrade = async (row: TradeResponse) => {
  if (row.status !== 0) {
    ElMessage.warning(t("tradingP2P.canOnlyConfirmPending"))
    return
  }

  try {
    await ElMessageBox.confirm(t("tradingP2P.confirmTradePrompt"), t("common.confirm"), {
      confirmButtonText: t("common.confirm"),
      cancelButtonText: t("common.cancel"),
      type: "warning",
    })

    // W3：命中风控时交易停在待审核、资产仍在冻结态。
    // 必须据此区分提示语——否则用户看到「交易完成」会以为配额与碳币已到账。
    const result = await confirmTrade(row.id)
    if (result?.auditStatus === 1) {
      ElMessage.warning(t("tradingP2P.confirmPendingAudit"))
    } else {
      ElMessage.success(t("tradingP2P.confirmSuccess"))
    }
    await loadTrades()
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(t("tradingP2P.confirmFailed") + ": " + ((error as Error).message || ""))
    } else {
      ElMessage.info(t("common.cancelled"))
    }
  }
}

onMounted(() => {
  loadTrades()
})

/** 首屏统计条：仅由已加载的本页交易列表推导，不新增接口请求 */
const p2pStats = computed(() => {
  const rows = tableData.value
  const pending = rows.filter((r) => r.status === 0).length
  const completed = rows.filter((r) => r.status === 2).length
  const volume = rows.reduce((sum, r) => sum + (Number(r.quantity) || 0), 0)
  const bestPrice = rows.reduce((max, r) => Math.max(max, Number(r.unitPrice) || 0), 0)

  return [
    {
      label: t('tradingP2P.statTotal'),
      value: total.value,
      unit: t('tradingP2P.unitDeals'),
      delta: t('tradingP2P.statTotalHint'),
      tone: 'neu' as const,
      icon: CircleCheck,
    },
    {
      label: t('tradingP2P.statPending'),
      value: pending,
      unit: t('tradingP2P.unitDeals'),
      delta: t('tradingP2P.statPendingHint'),
      tone: pending > 0 ? ('neu' as const) : ('pos' as const),
      icon: Clock,
    },
    {
      label: t('tradingP2P.statVolume'),
      value: Number(volume.toFixed(2)),
      unit: t('common.unit_ton'),
      delta: t('tradingP2P.statVolumeHint', { count: completed }),
      tone: 'neu' as const,
      icon: Histogram,
    },
    {
      label: t('tradingP2P.statBestPrice'),
      value: Number(bestPrice.toFixed(2)),
      unit: t('tradingP2P.unitPricePerTon'),
      delta: t('tradingP2P.statBestPriceHint'),
      tone: 'neu' as const,
      icon: PriceTag,
    },
  ]
})
</script>

<template>
  <section class="p2p-page pen-enter">
    <PageHeader
      :title="t('menu.p2pTrading')"
      :description="t('tradingP2P.pageDescription')"
      :crumbs="[t('tradingP2P.breadcrumbTrading'), t('tradingP2P.breadcrumbP2P')]"
    />

    <PageHint :text="t('pageHints.tradingP2P')" />

    <StatStrip :items="p2pStats" :loading="loading" />

    <FilterBar :aria-label="t('tradingP2P.filterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('tradingP2P.colCounterpartyName') }}</span>
        <el-input v-model="searchForm.keyword" :placeholder="t('common.enterKeyword')" clearable />
      </label>

      <label class="pen-field">
        <span class="pen-field__label">{{ t('tradingP2P.identityFilterLabel') }}</span>
        <el-select v-model="searchForm.identity" :placeholder="t('tradingP2P.identityFilterPlaceholder')" clearable>
          <el-option v-for="item in identityOptions" :key="item.value" :label="t(item.label)" :value="item.value" />
        </el-select>
      </label>

      <label class="pen-field">
        <span class="pen-field__label">{{ t('tradingP2P.colTradeNo') }}</span>
        <el-input v-model="searchForm.tradeNo" :placeholder="t('common.enterKeyword')" clearable />
      </label>

      <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
      <el-tooltip :disabled="admitted" :content="admissionBlockMessage" placement="top">
        <span>
          <el-button
            type="primary"
            :disabled="!admitted"
            data-testid="p2p-create-trade"
            @click="openAddDialog"
          >
            {{ t('tradingP2P.dialogCreate') }}
          </el-button>
        </span>
      </el-tooltip>
    </FilterBar>

    <DataPanel :title="t('tradingP2P.panelTitle')" :meta="t('common.total', { count: total })">
      <el-table :data="tableData" v-loading="loading" :empty-text="t('tradingP2P.emptyText')">
        <el-table-column :label="t('tradingMarket.colIndex')" width="80">
          <template #default="scope">
            {{ (page - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="tradeNo" :label="t('tradingP2P.colTradeNo')" min-width="160" />
        <el-table-column prop="buyerName" :label="t('tradingP2P.colBuyerName')" min-width="140" />
        <el-table-column prop="sellerName" :label="t('tradingP2P.colSellerName')" min-width="140" />
        <el-table-column prop="quantity" :label="t('tradingP2P.colQuantity')" min-width="110">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" :label="t('tradingP2P.colUnitPrice')" min-width="110">
          <template #default="{ row }">{{ formatNumber(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="t('tradingP2P.colTotalAmount')" min-width="120">
          <template #default="{ row }">{{ formatNumber(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="statusText" :label="t('tradingP2P.colStatus')" min-width="100">
          <template #default="{ row }">
            <StatusTag domain="trade" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('tradingP2P.colCreateTime')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('tradingP2P.colOperation')" width="120" fixed="right">
          <template #default="{ row }">
            <div v-if="row.status === 0" class="action-buttons">
              <el-button link type="primary" @click="onConfirmTrade(row)">{{ t('common.confirm') }}</el-button>
              <el-button link type="danger" @click="onCancelTrade(row)">{{ t('ordersManage.btnCancel') }}</el-button>
            </div>
            <span v-else class="action-placeholder">-</span>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </template>
    </DataPanel>

    <el-dialog v-model="dialogVisible" :title="t('tradingP2P.dialogCreate')" width="520px" destroy-on-close>
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="100px" scroll-to-error>
        <el-form-item :label="t('tradingP2P.colBuyerId')" prop="buyerId">
          <el-select
            v-model="dialogForm.buyerId"
            filterable
            remote
            reserve-keyword
            :remote-method="buyerSearch"
            :loading="buyerOptionsLoading"
            :placeholder="t('tradingP2P.buyerSelectPlaceholder')"
            style="width: 100%"
          >
            <el-option
              v-for="option in buyerOptions"
              :key="option.userId"
              :label="option.label"
              :value="option.userId"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('tradingP2P.colQuantity')" prop="quantity">
          <el-input-number v-model="dialogForm.quantity" :min="0.01" :precision="2" :step="1" style="width: 100%" :placeholder="t('tradingP2P.enterQuantity')" />
          <span class="field-hint">{{ t('tradingP2P.quantityHintCoin') }}</span>
        </el-form-item>

        <!-- 2026-11-03：P2P 改为「碳币换碳币」，1 枚换 1 枚，无价格维度。
             单价输入已移除——填了后端也按 1 处理，展示它只会误导。 -->

        <div v-if="Number(dialogForm.quantity) > 0" class="p2p-estimate-card">
          <div class="p2p-estimate-label">{{ t('tradingP2P.estimatedTotal') }}</div>
          <div class="p2p-estimate-val">{{ formatNumber(Number(dialogForm.quantity)) }} <small>{{ t('common.unit_coin') || '碳币' }}</small></div>
        </div>

        <el-form-item :label="t('ordersManage.labelRemark')" prop="remark">
          <el-input v-model="dialogForm.remark" type="textarea" :rows="3" :placeholder="t('ordersManage.labelRemark') + ' (' + t('common.optional') + ')'" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="onCancel">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="onSave">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.p2p-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.action-buttons {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.action-placeholder {
  color: var(--pen-faint);
  font-size: var(--fs-caption);
}

.p2p-estimate-card {
  margin: calc(-1 * var(--space-1)) 0 var(--space-4) 100px;
  padding: var(--space-2) var(--space-3);
  background: var(--pen-sunken);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.p2p-estimate-label {
  font-size: var(--fs-sub);
  color: var(--pen-muted);
}

.p2p-estimate-val {
  font-size: var(--fs-h1);
  font-weight: 500;
  color: var(--pen-ink);
  font-variant-numeric: tabular-nums;
}
</style>
