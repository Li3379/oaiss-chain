<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳效评级码与行业减排对标 (Emission Data & Ratings)
 * 【路由地址】：/enterprise/emission/data
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 企业自身碳效码等级面板：展示 A~E 级能效等级徽章与万元产值碳强度得分
 *   2. 行业能效排行榜单：按年份切换查看全行业企业碳排放强度与对标排名
 *   3. AI 未来排碳趋势预测：选择预测月数（未来6/12个月），通过回归模型推演排碳总量
 *   4. 碳效评级标准与计算依据释义（PageHint / TermTip）
 * 【对应后端 Controller & API】：
 *   - EmissionController:
 *     * GET  /api/v1/emission/my-rating      -> getMyRating() 查询当前企业碳效码
 *     * GET  /api/v1/emission/rankings/{year}-> getIndustryRankings() 查询年度行业排名
 *     * POST /api/v1/emission/predict        -> predictEmission() 时序AI排放预测
 *   - EnterpriseController:
 *     * GET  /api/v1/enterprise/info         -> getEnterpriseInfo() 获取企业信息
 * ==============================================================================
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getIndustryRankings, getMyRating, predictEmission } from '../../api/emission'
import { getEnterpriseInfo } from '../../api/enterprise'
import type { CarbonPredictionResponse, EmissionRating, EnterpriseResponse } from '../../types'
import PageContainer from '../../components/PageContainer.vue'
import TermTip from '../../components/TermTip.vue'
import PageHint from '../../components/PageHint.vue'

const { t } = useI18n()

const activeTab = ref('ratings')

const ratings = ref<EmissionRating[]>([])
const ratingsLoading = ref(false)

type RankingRow = EmissionRating & { enterpriseName?: string }

const rankings = ref<RankingRow[]>([])
const rankingsLoading = ref(false)
const rankingsYear = ref(String(new Date().getFullYear()))

const predictLoading = ref(false)
const predictResult = ref<CarbonPredictionResponse | null>(null)
const predictForm = ref({
  enterpriseId: '',
  predictMonths: 6,
})

const enterpriseIdNumber = computed(() => {
  const value = Number(predictForm.value.enterpriseId)
  return Number.isFinite(value) && value > 0 ? value : null
})

const latestPrediction = computed(() => {
  const predictions = predictResult.value?.predictions || []
  return predictions.length > 0 ? predictions[predictions.length - 1] : null
})

const predictionHint = computed(() => t('emissionData.description'))

const loadEnterpriseContext = async () => {
  try {
    const enterprise: EnterpriseResponse = await getEnterpriseInfo()
    if (enterprise.id) {
      predictForm.value.enterpriseId = String(enterprise.id)
    }
  } catch {
    // Keep manual input available if enterprise context is unavailable.
  }
}

const loadRatings = async () => {
  try {
    ratingsLoading.value = true
    ratings.value = await getMyRating()
  } catch {
    ElMessage.error(t('emissionData.loadRatingFailed'))
    ratings.value = []
  } finally {
    ratingsLoading.value = false
  }
}

const loadRankings = async () => {
  try {
    rankingsLoading.value = true
    rankings.value = await getIndustryRankings(Number(rankingsYear.value))
  } catch {
    ElMessage.error(t('emissionData.loadRankingFailed'))
    rankings.value = []
  } finally {
    rankingsLoading.value = false
  }
}

const onPredict = async () => {
  if (!enterpriseIdNumber.value) {
    ElMessage.error(t('emissionData.enterpriseIdRequired'))
    return
  }

  try {
    predictLoading.value = true
    predictResult.value = await predictEmission({
      enterpriseId: enterpriseIdNumber.value,
      predictMonths: predictForm.value.predictMonths,
    })
    ElMessage.success(t('emissionData.predictionComplete'))
  } catch {
    ElMessage.error(t('emissionData.predictionFailed'))
  } finally {
    predictLoading.value = false
  }
}

const getRatingTag = (level?: string) => {
  const map: Record<string, string> = { A: 'success', B: 'primary', C: 'warning', D: 'danger' }
  return level ? (map[level] || 'info') : 'info'
}

function getRankingEnterpriseLabel(row: RankingRow) {
  if (row.enterpriseName) return row.enterpriseName
  return `Enterprise ID ${row.enterpriseId}`
}

onMounted(() => {
  loadEnterpriseContext()
  loadRatings()
  loadRankings()
})
</script>

<template>
  <PageContainer :title="t('emissionData.title')" :description="t('emissionData.description')">
    <PageHint :text="t('pageHints.emissionData')" />
    <section class="emission-page">
      <el-card class="section-card" shadow="never">
        <el-tabs v-model="activeTab">
          <el-tab-pane :label="t('emissionData.tabRating')" name="ratings">
            <el-table :data="ratings" border v-loading="ratingsLoading">
              <el-table-column :label="t('common.operation')" width="80">
                <template #default="scope">{{ scope.$index + 1 }}</template>
              </el-table-column>
              <el-table-column prop="ratingLevel" min-width="100">
                <template #header><span>{{ t('emissionData.colRating') }}<TermTip term="emissionRating" /></span></template>
                <template #default="{ row }">
                  <el-tag :type="getRatingTag(row.ratingLevel)">{{ row.ratingLevel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalEmission" :label="t('emissionData.colTotalEmission')" min-width="140" />
              <el-table-column prop="emissionIntensity" :label="t('emissionData.colIndustryAvg')" min-width="140">
                <template #default="{ row }">{{ row.emissionIntensity ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="ratingScore" :label="t('emissionData.colScore')" min-width="100" />
              <el-table-column prop="ratingYear" :label="t('emissionData.colRatingTime')" min-width="170" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="t('emissionData.tabRanking')" name="rankings">
            <div class="toolbar-row">
              <span>{{ t('emissionData.yearPicker') }}:</span>
              <el-date-picker
                v-model="rankingsYear"
                type="year"
                value-format="YYYY"
                :placeholder="t('emissionData.selectYear')"
                @change="loadRankings"
              />
            </div>
            <el-table :data="rankings" border v-loading="rankingsLoading">
              <el-table-column :label="t('emissionData.colRank')" width="80">
                <template #default="scope">{{ scope.$index + 1 }}</template>
              </el-table-column>
              <el-table-column prop="enterpriseId" :label="t('emissionData.colEnterpriseName')" min-width="200">
                <template #default="{ row }">{{ getRankingEnterpriseLabel(row) }}</template>
              </el-table-column>
              <el-table-column prop="totalEmission" :label="t('emissionData.colTotalEmission')" min-width="140" />
              <el-table-column prop="ratingLevel" min-width="80">
                <template #header><span>{{ t('emissionData.colRating') }}<TermTip term="emissionRating" /></span></template>
                <template #default="{ row }">
                  <el-tag :type="getRatingTag(row.ratingLevel)">{{ row.ratingLevel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="ratingScore" :label="t('emissionData.colScore')" min-width="100" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="t('emissionData.tabPrediction')" name="predict">
            <div class="predict-panel">
              <el-form label-width="140px">
                <el-form-item :label="t('emissionData.enterpriseIdLabel')">
                  <el-input v-model="predictForm.enterpriseId" :placeholder="t('emissionData.enterpriseIdPlaceholder')" />
                </el-form-item>
                <el-form-item :label="t('emissionData.predictionMonths')">
                  <el-input-number v-model="predictForm.predictMonths" :min="1" :max="24" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="predictLoading" @click="onPredict">{{ t('emissionData.tabPrediction') }}</el-button>
                </el-form-item>
              </el-form>

              <el-alert
                :title="predictionHint"
                type="info"
                :closable="false"
                show-icon
                class="prediction-alert"
              />

              <el-descriptions
                v-if="predictResult"
                :title="t('emissionData.predictionResult')"
                :column="2"
                border
                class="prediction-result"
              >
                <el-descriptions-item label="Enterprise ID">{{ predictResult.enterpriseId }}</el-descriptions-item>
                <el-descriptions-item :label="t('emissionData.confidence')">{{ predictResult.confidence }}%</el-descriptions-item>
                <el-descriptions-item label="Prediction Summary" :span="2">
                  {{ predictResult.message || '-' }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('emissionData.predictedEmission')">
                  {{ latestPrediction ? `${latestPrediction.predictedEmission} tCO2e` : '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="Generated At">
                  {{ predictResult.generatedAt || '-' }}
                </el-descriptions-item>
              </el-descriptions>

              <el-table
                v-if="predictResult?.predictions?.length"
                :data="predictResult.predictions"
                border
                class="prediction-table"
              >
                <el-table-column prop="period" label="Prediction Period" min-width="140" />
                <el-table-column prop="predictedEmission" :label="t('emissionData.predictedEmission')" min-width="180">
                  <template #default="{ row }">{{ row.predictedEmission }} tCO2e</template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </section>
  </PageContainer>
</template>

<style scoped>
.emission-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.toolbar-row {
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.predict-panel {
  padding: 20px 0;
  max-width: 720px;
}

.prediction-alert {
  margin-top: 8px;
}

.prediction-result {
  margin-top: 20px;
}

.prediction-table {
  margin-top: 16px;
}
</style>
