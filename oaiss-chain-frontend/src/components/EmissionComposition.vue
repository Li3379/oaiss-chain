<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatNumber } from '../utils/format'
import {
  buildComposition,
  hasProcessEmission,
  hasUnclassifiedGap,
  type CompositionItem,
} from '../config/emissionComposition'

/**
 * 排放总量「构成清单」。
 *
 * ## 设计意图（D0）
 *
 * 系统的 `total_emission` 含**过程排放**（脱硫 / 线损 / SF6），但数据库只有三个 scope 列。
 * 结果是审核端出现「上方总量 1200、下方明细合计 900」两个对不上的数字，且没有任何解释。
 *
 * 本组件把总量逐项拆开，让用户能自己核对：
 *   范围一 X + 范围二 Y + 范围三 Z + 脱硫 A + 线损 B + SF6 C = 总量
 *
 * ## 展示策略
 * - 过程排放为 0 时不显示对应行（多数存量报告只有三范围，避免空行噪音）
 * - 存在「未归类差额」时单独高亮一行，明确告知这不是丢失数据
 * - 每项带一句白话说明，让不懂术语的用户也能对上
 */
const props = defineProps<{
  /** emissionData JSON 字符串（接口原样） */
  data: string | null | undefined
  /** 后端返回的总量（优先用它作为「权威总量」，与库内一致） */
  authoritativeTotal?: number | null
  /** 默认展开还是折叠 */
  defaultOpen?: boolean
}>()

const { t } = useI18n()

const parsed = computed<Record<string, unknown> | null>(() => {
  const raw = props.data
  if (!raw || typeof raw !== 'string') return null
  try {
    const obj = JSON.parse(raw)
    return obj && typeof obj === 'object' && !Array.isArray(obj)
      ? (obj as Record<string, unknown>)
      : null
  } catch {
    return null
  }
})

const composition = computed(() => buildComposition(parsed.value))

/**
 * 展示用的总量。
 *
 * 优先用后端返回的 `authoritativeTotal`（库内真实值），
 * 因为它可能包含解析器算出的、emissionData 里没显式写的差额。
 * 没有时回落到构成推导值。
 */
const displayTotal = computed(() => {
  const auth = props.authoritativeTotal
  if (typeof auth === 'number' && Number.isFinite(auth) && auth > 0) return auth
  return composition.value.resolvedTotal
})

/** 实际差额 = 权威总量 − 构成之和（用于提示未归类余量） */
const actualGap = computed(() =>
  Number((displayTotal.value - composition.value.sum).toFixed(4)),
)

const showGap = computed(() => Math.abs(actualGap.value) > 0.0001)

const scopeItems = computed(() =>
  composition.value.items.filter((i) => !i.isProcess),
)

const processItems = computed(() =>
  composition.value.items.filter((i) => i.isProcess && i.value > 0),
)

const labelKey = (item: CompositionItem): string => `emissionComposition.${item.key}`
const hintKey = (item: CompositionItem): string => `emissionComposition.hint_${item.key}`
</script>

<template>
  <el-collapse :model-value="defaultOpen === false ? [] : ['composition']" class="composition">
    <el-collapse-item name="composition">
      <template #title>
        <span class="composition__title">{{ t('emissionComposition.title') }}</span>
        <span class="composition__total">{{ formatNumber(displayTotal) }} tCO2e</span>
      </template>

      <ul class="composition__list">
        <!-- 三个范围 -->
        <li v-for="item in scopeItems" :key="item.key" class="composition__row">
          <span class="composition__label">{{ t(labelKey(item)) }}</span>
          <span class="composition__hint">{{ t(hintKey(item)) }}</span>
          <span class="composition__value">{{ formatNumber(item.value) }}</span>
        </li>

        <!-- 过程排放：仅在大于 0 时显示 -->
        <template v-if="hasProcessEmission(composition)">
          <li class="composition__divider">{{ t('emissionComposition.processGroup') }}</li>
          <li v-for="item in processItems" :key="item.key" class="composition__row">
            <span class="composition__label">{{ t(labelKey(item)) }}</span>
            <span class="composition__hint">{{ t(hintKey(item)) }}</span>
            <span class="composition__value">{{ formatNumber(item.value) }}</span>
          </li>
        </template>

        <!-- 未归类差额：只在真的存在时出现，并明确解释 -->
        <li v-if="showGap" class="composition__row composition__row--gap">
          <span class="composition__label">{{ t('emissionComposition.unclassified') }}</span>
          <span class="composition__hint">{{ t('emissionComposition.hint_unclassified') }}</span>
          <span class="composition__value">{{ formatNumber(actualGap) }}</span>
        </li>

        <!-- 合计 -->
        <li class="composition__row composition__row--sum">
          <span class="composition__label">{{ t('emissionComposition.total') }}</span>
          <span class="composition__hint">{{ t('emissionComposition.hint_total') }}</span>
          <span class="composition__value">{{ formatNumber(displayTotal) }}</span>
        </li>
      </ul>

      <p v-if="hasUnclassifiedGap(composition)" class="composition__note">
        {{ t('emissionComposition.gapNote', { gap: formatNumber(composition.gap) }) }}
      </p>
    </el-collapse-item>
  </el-collapse>
</template>

<style scoped>
.composition {
  margin-bottom: var(--space-3);
}

.composition__title {
  font-weight: 500;
}

.composition__total {
  margin-left: var(--space-3);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.composition__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.composition__row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 2fr auto;
  gap: var(--space-3);
  align-items: baseline;
  padding: var(--space-1) 0;
  font-size: 12px;
  line-height: 1.6;
}

.composition__label {
  color: var(--pen-ink-2, var(--pen-ink));
}

.composition__hint {
  color: var(--pen-ink-3, var(--pen-ink));
}

.composition__value {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.composition__divider {
  padding: var(--space-2) 0 var(--space-1);
  font-size: 11px;
  color: var(--pen-ink-3, var(--pen-ink));
  border-top: 1px solid var(--pen-note-bd);
}

.composition__row--gap {
  color: var(--pen-pill-warn-ink);
}

.composition__row--sum {
  margin-top: var(--space-2);
  padding-top: var(--space-2);
  font-weight: 600;
  border-top: 1px solid var(--pen-note-bd);
}

.composition__note {
  margin: var(--space-2) 0 0;
  padding: var(--space-2) var(--space-3);
  font-size: 12px;
  line-height: 1.5;
  color: var(--pen-pill-warn-ink);
  background: var(--pen-pill-warn-bg);
  border: 1px solid var(--pen-pill-warn-bd);
  border-radius: var(--radius-sm, 4px);
}
</style>