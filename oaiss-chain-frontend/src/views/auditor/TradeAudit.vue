<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：审核员端 - 成交审核工作台 (Trade Audit Workbench)
 * 【路由地址】：/auditor/trade-audit
 * 【访问权限】：REVIEWER（主审）、ADMIN（兜底）
 * 【界面可见功能】：
 *   1. 待审核成交工单列表（默认待审，可切换已通过/已驳回复盘）
 *   2. 风险理由展示：分流器命中的规则与依据数值，供审核员作为判断输入
 *   3. 通过 / 驳回裁决（驳回须选理由 + 填意见）
 *   4. 裁决留痕提示：通过后才结算并上链，驳回则解冻资产并扣信誉分
 * 【对应后端 Controller & API】：
 *   - TradeAuditController:
 *     * GET  /api/v1/trade-audit                    -> listPendingAudits()
 *     * POST /api/v1/trade-audit/{id}/approve       -> approveTrade()
 *     * POST /api/v1/trade-audit/{id}/reject        -> rejectTrade()
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Warning } from '@element-plus/icons-vue'
import PageHeader from '../../components/pen/PageHeader.vue'
import PageHint from '../../components/PageHint.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import {
  AUDIT_STATUS,
  REJECT_REASONS,
  approveTrade,
  listPendingAudits,
  rejectTrade,
  type RejectReason,
  type TradeAuditRecord,
} from '../../api/tradeAudit'
import { formatDateTime } from '../../utils/format'
import { parseRiskReasons } from '../../utils/tradeAudit'

const { t } = useI18n()

const records = ref<TradeAuditRecord[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const filter = reactive<{ status: number }>({ status: AUDIT_STATUS.PENDING })

/** 裁决弹窗 */
const dialogVisible = ref(false)
const dialogMode = ref<'approve' | 'reject'>('approve')
const activeRecord = ref<TradeAuditRecord | null>(null)
const submitting = ref(false)
const form = reactive<{ remark: string; reason: RejectReason }>({
  remark: '',
  reason: 'OTHER',
})

const statusOptions = computed(() => [
  { label: t('tradeAudit.statusPending'), value: AUDIT_STATUS.PENDING },
  { label: t('tradeAudit.statusApproved'), value: AUDIT_STATUS.APPROVED },
  { label: t('tradeAudit.statusRejected'), value: AUDIT_STATUS.REJECTED },
])

const reasonOptions = computed(() =>
  REJECT_REASONS.map((code) => ({
    label: t(`tradeAudit.reason.${code}`),
    value: code,
  })),
)

const load = async () => {
  try {
    loading.value = true
    const result = await listPendingAudits({
      pageNum: page.value,
      pageSize: pageSize.value,
      status: filter.status,
    })
    records.value = result?.items || []
    total.value = result?.total || 0
  } catch {
    ElMessage.error(t('tradeAudit.loadFailed'))
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  page.value = 1
  load()
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  page.value = 1
  load()
}

const onCurrentChange = (p: number) => {
  page.value = p
  load()
}

const openDialog = (mode: 'approve' | 'reject', record: TradeAuditRecord) => {
  dialogMode.value = mode
  activeRecord.value = record
  form.remark = ''
  form.reason = 'OTHER'
  dialogVisible.value = true
}

const submit = async () => {
  const record = activeRecord.value
  if (!record) return
  const remark = form.remark.trim()
  if (!remark) {
    ElMessage.warning(t('tradeAudit.remarkRequired'))
    return
  }

  // 通过意味着真实结算与上链（不可逆），二次确认避免误点
  if (dialogMode.value === 'approve') {
    try {
      await ElMessageBox.confirm(
        t('tradeAudit.approveConfirm'),
        t('tradeAudit.approveConfirmTitle'),
        { type: 'warning' },
      )
    } catch {
      return
    }
  }

  try {
    submitting.value = true
    if (dialogMode.value === 'approve') {
      await approveTrade(record.tradeId, remark)
      ElMessage.success(t('tradeAudit.approveSuccess'))
    } else {
      await rejectTrade(record.tradeId, remark, form.reason)
      ElMessage.success(t('tradeAudit.rejectSuccess'))
    }
    dialogVisible.value = false
    await load()
  } catch {
    ElMessage.error(t('tradeAudit.actionFailed'))
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="trade-audit-page pen-enter">
    <PageHeader
      :title="t('tradeAudit.title')"
      :description="t('tradeAudit.description')"
      :crumbs="[t('tradeAudit.breadcrumbAudit'), t('tradeAudit.breadcrumbTradeAudit')]"
    />

    <PageHint :text="t('tradeAudit.hint')" />

    <FilterBar :aria-label="t('tradeAudit.filterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('tradeAudit.colAuditStatus') }}</span>
        <el-select v-model="filter.status" style="width: 160px" @change="onSearch">
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </label>
      <el-button type="primary" @click="onSearch">{{ t('common.search') }}</el-button>
      <el-button @click="load">{{ t('common.refresh') }}</el-button>
    </FilterBar>

    <DataPanel
      :title="t('tradeAudit.tableTitle')"
      :meta="t('tradeAudit.panelMeta', { total })"
      :icon="Warning"
    >
      <el-table :data="records" v-loading="loading">
        <el-table-column :label="t('common.colIndex')" width="80">
          <template #default="scope">
            {{ (page - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="tradeId" :label="t('tradeAudit.colTradeId')" min-width="110" />
        <el-table-column :label="t('tradeAudit.colRiskReasons')" min-width="280">
          <template #default="{ row }">
            <div class="reason-cell">
              <el-tag
                v-for="(reason, idx) in parseRiskReasons(row.riskReasons)"
                :key="idx"
                type="warning"
                size="small"
              >
                {{ reason }}
              </el-tag>
              <span v-if="parseRiskReasons(row.riskReasons).length === 0" class="reason-none">
                {{ t('tradeAudit.noRiskReason') }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('tradeAudit.colAuditStatus')" min-width="110">
          <template #default="{ row }">
            <el-tag :type="row.auditStatus === AUDIT_STATUS.APPROVED ? 'success' : row.auditStatus === AUDIT_STATUS.REJECTED ? 'danger' : 'warning'">
              {{ statusOptions.find((o) => o.value === row.auditStatus)?.label ?? '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditRemark" :label="t('tradeAudit.colAuditRemark')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditRemark || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('tradeAudit.colCreatedAt')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" min-width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === AUDIT_STATUS.PENDING">
              <el-button link type="primary" :icon="CircleCheck" @click="openDialog('approve', row)">
                {{ t('tradeAudit.approve') }}
              </el-button>
              <el-button link type="danger" :icon="CircleClose" @click="openDialog('reject', row)">
                {{ t('tradeAudit.reject') }}
              </el-button>
            </template>
            <span v-else class="reason-none">-</span>
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
          @current-change="onCurrentChange"
        />
      </template>
    </DataPanel>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'approve' ? t('tradeAudit.approveTitle') : t('tradeAudit.rejectTitle')"
      width="520px"
    >
      <el-form label-position="top">
        <el-form-item v-if="dialogMode === 'reject'" :label="t('tradeAudit.colRejectReason')">
          <el-select v-model="form.reason" style="width: 100%">
            <el-option
              v-for="opt in reasonOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('tradeAudit.colAuditRemark')" required>
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="4"
            :maxlength="500"
            show-word-limit
            :placeholder="t('tradeAudit.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          :type="dialogMode === 'approve' ? 'primary' : 'danger'"
          :loading="submitting"
          @click="submit"
        >
          {{ dialogMode === 'approve' ? t('tradeAudit.approve') : t('tradeAudit.reject') }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.trade-audit-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.reason-cell {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.reason-none {
  color: var(--pen-faint);
  font-size: var(--fs-caption);
}
</style>
