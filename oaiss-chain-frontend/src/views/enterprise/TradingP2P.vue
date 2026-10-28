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
 *   - CreditScoreController:
 *     * GET  /api/v1/credit/ranking     -> getScoreRanking() 获取企业信用等级
 *   - EnterpriseController:
 *     * GET  /api/v1/enterprise/{id}    -> getEnterpriseById() 获取对手企业档案
 * ==============================================================================
 */
import { computed, reactive, ref, onMounted } from "vue"
import { useI18n } from "vue-i18n"
import { ElMessage, ElMessageBox } from "element-plus"
import { getMyTrades, createP2PTrade, cancelTrade, confirmTrade } from "../../api/trade"
import type { TradeIdentityFilter, TradeResponse } from "../../types"
import { formatDateTime } from "../../utils/format"
import PageHint from '../../components/PageHint.vue'
import { formatNumber } from '../../utils/format'
import { getScoreRanking } from "../../api/credit"
import { getEnterpriseById } from "../../api/enterprise"
import { useAppStore } from "../../store"
import StatusTag from '../../components/StatusTag.vue'

const { t } = useI18n()
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
  unitPrice: null as number | null,
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
  unitPrice: [
    { required: true, message: t("tradingP2P.enterPrice"), trigger: "blur" },
    { type: "number", min: 0.01, message: t("tradingP2P.pricePositive"), trigger: "blur" }
  ],
}

const estimatedTotal = computed(() => {
  const q = Number(dialogForm.quantity || 0)
  const p = Number(dialogForm.unitPrice || 0)
  if (!Number.isFinite(q) || !Number.isFinite(p) || q <= 0 || p <= 0) return '0.00'
  return (q * p).toFixed(2)
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
  dialogForm.unitPrice = null
  dialogForm.remark = ""
}

/** 交易对手下拉：信誉排名提供企业名单，逐个取详情映射出 userId（后端 P2P 以用户 ID 定位买方） */
const buyerOptions = ref<Array<{ userId: number; label: string }>>([])
const buyerOptionsLoading = ref(false)

async function loadBuyerOptions(): Promise<void> {
  buyerOptionsLoading.value = true
  try {
    const page = await getScoreRanking({ pageNum: 1, pageSize: 50 })
    const rivals = (page?.items ?? []).filter(
      (item) => item.enterpriseId && item.enterpriseId !== appStore.enterpriseId,
    )
    const options = await Promise.all(
      rivals.map(async (item) => {
        try {
          const enterprise = await getEnterpriseById(item.enterpriseId)
          return {
            userId: enterprise.userId,
            label: `${enterprise.enterpriseName}（用户 ${enterprise.userId}）`,
          }
        } catch {
          return null
        }
      }),
    )
    buyerOptions.value = options.filter(
      (option): option is { userId: number; label: string } => option !== null,
    )
  } catch {
    buyerOptions.value = []
  } finally {
    buyerOptionsLoading.value = false
  }
}

const openAddDialog = () => {
  resetDialogForm()
  dialogVisible.value = true
  void loadBuyerOptions()
}

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
      unitPrice: Number(dialogForm.unitPrice),
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

    await confirmTrade(row.id)
    ElMessage.success(t("tradingP2P.confirmSuccess"))
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
</script>

<template>
  <section class="p2p-page">
    <el-card class="section-card" shadow="never">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>{{ t('tradingP2P.breadcrumbTrading') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ t('tradingP2P.breadcrumbP2P') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </el-card>

    <PageHint :text="t('pageHints.tradingP2P')" />

    <el-card class="section-card" shadow="never">
      <el-form :inline="true" class="search-form">
        <el-form-item :label="t('tradingP2P.colCounterpartyName')">
          <el-input v-model="searchForm.keyword" :placeholder="t('common.enterKeyword')" clearable />
        </el-form-item>

        <el-form-item :label="t('tradingP2P.identityFilterLabel')">
          <el-select v-model="searchForm.identity" :placeholder="t('tradingP2P.identityFilterPlaceholder')" clearable>
            <el-option v-for="item in identityOptions" :key="item.value" :label="t(item.label)" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('tradingP2P.colTradeNo')">
          <el-input v-model="searchForm.tradeNo" :placeholder="t('common.enterKeyword')" clearable />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
          <el-button type="success" plain @click="openAddDialog">{{ t('tradingP2P.dialogCreate') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="section-card" shadow="never">
      <el-table :data="tableData" border v-loading="loading" :empty-text="t('tradingP2P.emptyText')">
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
            <span v-else style="color: var(--color-text-muted); font-size: var(--text-xs);">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
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
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="t('tradingP2P.dialogCreate')" width="520px" destroy-on-close>
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="100px" scroll-to-error>
        <el-form-item :label="t('tradingP2P.colBuyerId')" prop="buyerId">
          <el-select
            v-model="dialogForm.buyerId"
            filterable
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
        </el-form-item>

        <el-form-item :label="t('tradingP2P.colUnitPrice')" prop="unitPrice">
          <el-input-number v-model="dialogForm.unitPrice" :min="0.01" :precision="2" :step="0.01" style="width: 100%" :placeholder="t('tradingP2P.enterPrice')" />
        </el-form-item>

        <div v-if="Number(dialogForm.quantity) > 0 && Number(dialogForm.unitPrice) > 0" class="p2p-estimate-card">
          <div class="p2p-estimate-label">{{ t('tradingP2P.estimatedTotal') }}</div>
          <div class="p2p-estimate-val">{{ estimatedTotal }} <small>{{ t('common.unit_coin') || '碳币' }}</small></div>
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
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
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

.action-buttons {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.p2p-estimate-card {
  margin: -4px 0 16px 100px;
  padding: 8px 12px;
  background: rgba(20, 167, 154, 0.08);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.p2p-estimate-label {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.p2p-estimate-val {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-primary);
}
</style>
