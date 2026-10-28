<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 履约信用评级与信誉账户中心 (Credit Score)
 * 【路由地址】：/enterprise/credit/score
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 综合信誉分卡片：展示企业信用等级勋章（AAA~D级）、综合信用分与风控状态
 *   2. 履约事件历史表格：展示履约奖惩变动记录（扣分/加分原因、变更分值与发生时间）
 *   3. 行业信用排行榜：展示全行业企业信用总分、信用等级与排名天梯榜
 *   4. 信用评级规则白话释义（PageHint / TermTip）
 * 【对应后端 Controller & API】：
 *   - CreditScoreController:
 *     * GET /api/v1/credit/my-score -> getMyScore() 获取企业信用分与等级
 *     * GET /api/v1/credit/history  -> getScoreHistory() 分页获取信誉事件变动流水
 *     * GET /api/v1/credit/ranking  -> getScoreRanking() 获取全行业企业信誉排行榜
 * ==============================================================================
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getMyScore, getScoreHistory, getScoreRanking } from '../../api/credit'
import type { CreditScoreResponse, CreditEventResponse } from '../../types'
import { formatDateTime } from '../../utils/format'
import PageContainer from '../../components/PageContainer.vue'
import TermTip from '../../components/TermTip.vue'
import PageHint from '../../components/PageHint.vue'

const { t } = useI18n()

const scoreData = ref<CreditScoreResponse | null>(null)
const scoreLoading = ref(false)

const historyData = ref<CreditEventResponse[]>([])
const historyLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const rankingData = ref<CreditScoreResponse[]>([])
const rankingLoading = ref(false)

const loadScore = async () => {
  try {
    scoreLoading.value = true
    const result = await getMyScore()
    scoreData.value = result
  } catch {
    ElMessage.error(t('creditScore.loadScoreFailed'))
  } finally {
    scoreLoading.value = false
  }
}

const loadHistory = async () => {
  try {
    historyLoading.value = true
    const result = await getScoreHistory({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
    historyData.value = result?.items || []
    total.value = result?.total || 0
  } catch {
    ElMessage.error(t('creditScore.loadHistoryFailed'))
  } finally {
    historyLoading.value = false
  }
}

const loadRanking = async () => {
  try {
    rankingLoading.value = true
    const result = await getScoreRanking({
      pageNum: 1,
      pageSize: 10,
    })
    rankingData.value = result?.items || []
  } catch {
    ElMessage.error(t('creditScore.loadRankingFailed'))
  } finally {
    rankingLoading.value = false
  }
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadHistory()
}

const onCurrentChange = (page: number) => {
  currentPage.value = page
  loadHistory()
}

const getScoreLevelType = (level?: string) => {
  const map: Record<string, string> = {
    EXCELLENT: 'success',
    GOOD: 'primary',
    WARNING: 'warning',
    DANGER: 'danger',
    FROZEN: 'danger',
  }
  return level ? (map[level] || 'info') : 'info'
}

const getScoreLevelText = (level?: string) => {
  const map: Record<string, string> = {
    EXCELLENT: 'creditScore.levelExcellent',
    GOOD: 'creditScore.levelGood',
    WARNING: 'creditScore.levelWarning',
    DANGER: 'creditScore.levelDanger',
    FROZEN: 'creditScore.levelFrozen',
  }
  return level ? (map[level] ? t(map[level]) : level) : 'N/A'
}

const getEventTypeTag = (type?: number) => {
  const map: Record<number, string> = {
    1: 'danger',
    2: 'warning',
    3: 'warning',
    4: 'danger',
    5: 'success',
  }
  return typeof type === 'number' ? (map[type] || 'info') : 'info'
}

// 事件类型按语言渲染：后端 eventTypeName 固定中文，英文界面需按枚举码翻译；未知码回退后端文案
const EVENT_TYPE_KEYS: Record<number, string> = {
  1: 'creditScore.eventTypeDataFalsification',
  2: 'creditScore.eventTypeLateSubmission',
  3: 'creditScore.eventTypeMinorViolation',
  4: 'creditScore.eventTypeMajorViolation',
  5: 'creditScore.eventTypeBonusGoodBehavior',
}

const getEventTypeLabel = (row: { eventType?: number; eventTypeName?: string }) => {
  const key = typeof row.eventType === 'number' ? EVENT_TYPE_KEYS[row.eventType] : undefined
  return key ? t(key) : (row.eventTypeName || '-')
}

onMounted(() => {
  loadScore()
  loadHistory()
  loadRanking()
})
</script>

<template>
  <PageContainer :title="t('creditScore.title')" :description="t('creditScore.description')">
    <PageHint :text="t('pageHints.creditScore')" />
    <section class="credit-score-page">
      <el-card class="section-card" shadow="never" v-loading="scoreLoading">
        <template #header>
          <span class="card-header">{{ t('creditScore.currentScore') }}<TermTip term="creditScore" /></span>
        </template>
        <div v-if="scoreData" class="score-display">
          <div class="score-main">
            <div class="score-value">{{ scoreData.score || 0 }}</div>
            <el-tag :type="getScoreLevelType(scoreData.level)" size="large" class="score-level">
              {{ getScoreLevelText(scoreData.level) }}
            </el-tag>
          </div>
          <div class="score-meta">
            <span>{{ t('creditScore.updateTime') }}: {{ formatDateTime(scoreData.lastEvaluatedAt) || '-' }}</span>
          </div>
        </div>
        <el-empty v-else :description="t('creditScore.emptyText')" />
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>
          <span class="card-header">{{ t('creditScore.scoreHistory') }}</span>
        </template>
        <el-table :data="historyData" border v-loading="historyLoading">
          <el-table-column :label="t('common.colIndex')" width="80">
            <template #default="scope">
              {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
            </template>
          </el-table-column>
          <el-table-column prop="eventType" :label="t('creditScore.colEventType')" min-width="140">
            <template #default="{ row }">
              <el-tag :type="getEventTypeTag(row.eventType)">
                {{ getEventTypeLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="pointsChanged" :label="t('creditScore.colChangeValue')" min-width="100">
            <template #default="{ row }">
              <span :class="row.pointsChanged >= 0 ? 'positive' : 'negative'">
                {{ row.pointsChanged >= 0 ? '+' : '' }}{{ row.pointsChanged }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="eventDescription" :label="t('creditScore.colReason')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="triggeredAt" :label="t('creditScore.colTime')" min-width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.triggeredAt) }}
            </template>
          </el-table-column>
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

      <el-card class="section-card" shadow="never">
        <template #header>
          <span class="card-header">{{ t('creditScore.scoreRanking') }}</span>
        </template>
        <el-table :data="rankingData" border v-loading="rankingLoading" data-testid="credit-ranking-table">
          <el-table-column :label="t('common.colIndex')" width="80">
            <template #default="scope">
              {{ scope.$index + 1 }}
            </template>
          </el-table-column>
          <el-table-column prop="enterpriseName" :label="t('creditScore.colEnterpriseName')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="score" :label="t('creditScore.colRankingScore')" min-width="100" />
          <el-table-column prop="level" :label="t('creditScore.colRankingLevel')" min-width="120">
            <template #default="{ row }">
              <el-tag :type="getScoreLevelType(row.level)">
                {{ getScoreLevelText(row.level) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="tradeRestricted" :label="t('creditScore.colTradePermission')" min-width="120">
            <template #default="{ row }">
              <el-tag :type="row.tradeRestricted ? 'danger' : 'success'">
                {{ row.tradeRestricted ? t('creditScore.tradeRestricted') : t('creditScore.tradeAllowed') }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>
  </PageContainer>
</template>

<style scoped>
.credit-score-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.card-header {
  font-weight: 600;
  font-size: var(--text-md);
}

.score-display {
  padding: 20px 0;
}

.score-main {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
}

.score-value {
  font-size: 48px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1;
}

.score-level {
  font-size: var(--text-lg);
  padding: 8px 16px;
}

.score-meta {
  color: var(--color-text-muted);
  font-size: var(--text-base);
}

.positive {
  color: var(--el-color-success);
  font-weight: 600;
}

.negative {
  color: var(--el-color-danger);
  font-weight: 600;
}

.pagination-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
