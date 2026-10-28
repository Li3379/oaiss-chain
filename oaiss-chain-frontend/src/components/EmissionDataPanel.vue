<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatNumber } from '../utils/format'
import TermTip from './TermTip.vue'

/**
 * 排放数据结构化展示（ELI5）：把 emissionData JSON 渲染为分范围的明细表。
 *
 * 兼容三种历史形态（DB 实测抽样）：
 * 1. 标准数组：{"scope1":[{"name":"锅炉","activity_data":100,"emission_factor":2.16}]}
 * 2. 标量简写：{"scope1":1,"scope2":2}（旧测试数据直接数值）
 * 3. 无法识别 / 非法 JSON：回退为原始文本，不丢弃任何信息
 *
 * 数值键沿用后端 snake_case（activity_data/emission_factor），展示层再翻译为中文。
 */
const props = defineProps<{
  /** emissionData JSON 字符串（接口原样） */
  data: string | null | undefined
}>()

const { t } = useI18n()

type EmissionRow = {
  name?: unknown
  activity_data?: unknown
  emission_factor?: unknown
}

interface ScopeSection {
  key: 'scope1' | 'scope2' | 'scope3'
  titleKey: string
  glossary: string
  rows: Array<{ name: string; activity: number; factor: number }>
  /** 行小计；标量简写形态下为直接数值 */
  subtotal: number
}

const SCOPES: Array<{ key: 'scope1' | 'scope2' | 'scope3'; titleKey: string; glossary: string }> = [
  { key: 'scope1', titleKey: 'emissionPanel.scope1', glossary: 'scope1' },
  { key: 'scope2', titleKey: 'emissionPanel.scope2', glossary: 'scope2' },
  { key: 'scope3', titleKey: 'emissionPanel.scope3', glossary: 'scope3' },
]

const toNumber = (v: unknown): number => {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

const parsed = computed<Record<string, unknown> | null>(() => {
  const raw = (props.data || '').trim()
  if (!raw) return null
  try {
    const obj = JSON.parse(raw)
    return obj && typeof obj === 'object' && !Array.isArray(obj) ? (obj as Record<string, unknown>) : null
  } catch {
    return null
  }
})

const sections = computed<ScopeSection[]>(() => {
  const obj = parsed.value
  if (!obj) return []
  return SCOPES.map(({ key, titleKey, glossary }) => {
    const value = obj[key]
    if (value === undefined || value === null) return null
    if (Array.isArray(value)) {
      const rows = (value as EmissionRow[]).map((r) => ({
        name: r?.name ? String(r.name) : t('emissionPanel.unnamedSource'),
        activity: toNumber(r?.activity_data),
        factor: toNumber(r?.emission_factor),
      }))
      const subtotal = rows.reduce((sum, r) => sum + r.activity * r.factor, 0)
      return { key, titleKey, glossary, rows, subtotal }
    }
    const scalar = Number(value)
    if (Number.isFinite(scalar)) {
      return { key, titleKey, glossary, rows: [], subtotal: scalar }
    }
    return null
  }).filter((x): x is ScopeSection => x !== null)
})

const total = computed(() =>
  sections.value.reduce((sum, section) => sum + section.subtotal, 0),
)

const isStructured = computed(() => sections.value.length > 0)
const rawText = computed(() => (props.data || '').trim())
</script>

<template>
  <div class="emission-panel">
    <template v-if="isStructured">
      <div v-for="section in sections" :key="section.key" class="emission-panel__scope">
        <div class="emission-panel__scope-title">
          <span>{{ t(section.titleKey) }}</span>
          <TermTip :term="section.glossary" />
        </div>
        <el-table v-if="section.rows.length > 0" :data="section.rows" size="small" border>
          <el-table-column prop="name" :label="t('emissionPanel.colSource')" min-width="140" />
          <el-table-column :label="t('emissionPanel.colActivity')" min-width="110" align="right">
            <template #default="{ row }">{{ formatNumber(row.activity, '-', 4) }}</template>
          </el-table-column>
          <el-table-column :label="t('emissionPanel.colFactor')" min-width="110" align="right">
            <template #default="{ row }">{{ formatNumber(row.factor, '-', 4) }}</template>
          </el-table-column>
          <el-table-column :label="t('emissionPanel.colSubtotal')" min-width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.activity * row.factor, '-', 4) }}</template>
          </el-table-column>
        </el-table>
        <div class="emission-panel__subtotal">
          {{ t('emissionPanel.subtotal') }}:
          <strong>{{ formatNumber(section.subtotal, '-', 4) }} tCO₂e</strong>
        </div>
      </div>

      <el-alert
        v-if="sections.length > 1"
        class="emission-panel__total"
        type="success"
        :closable="false"
      >
        <template #title>
          {{ t('emissionPanel.total') }}:
          <strong>{{ formatNumber(total, '-', 4) }} tCO₂e</strong>
        </template>
      </el-alert>
    </template>

    <div v-else-if="rawText" class="emission-panel__raw">
      <div class="emission-panel__raw-hint">{{ t('emissionPanel.rawHint') }}</div>
      <pre>{{ rawText }}</pre>
    </div>

    <div v-else class="emission-panel__raw">{{ t('emissionPanel.empty') }}</div>
  </div>
</template>

<style scoped>
.emission-panel {
  width: 100%;
}

.emission-panel__scope {
  margin-bottom: 14px;
}

.emission-panel__scope-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--text-primary);
}

.emission-panel__subtotal {
  margin-top: 6px;
  text-align: right;
  color: var(--text-secondary);
}

.emission-panel__total strong {
  font-size: var(--text-md);
}

.emission-panel__raw-hint {
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
  margin-bottom: 4px;
}

.emission-panel__raw pre {
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--bg-subtle, #f5f7fa);
  border-radius: 6px;
  padding: 8px;
  margin: 0;
  font-size: var(--text-xs);
}
</style>
