<script setup lang="ts">
/**
 * 统计条：对照 pen 设计稿行 Row > frame Stat 结构
 * ─ 每卡：标签(10.5px/500, 弱色) + 图标 → 数值(25px/500) + 单位(11px) → 增量行(11px)
 * ─ 卡片：白底 + 1px 发丝线 + 2px 圆角 + 内边距 13/15、纵向 gap 4
 *
 * 数值支持自动千分位；增量 tone 决定颜色（正向绿/负向红/中性灰）。
 */
import { computed, type Component } from 'vue'
import { formatNumber } from '../../utils/format'

export interface StatItem {
  /** 卡片标签（已 i18n 化） */
  label: string
  /** 主数值，数字会自动千分位；字符串原样输出 */
  value: number | string
  /** 单位后缀，如 tCO2e / 份 / 碳币 */
  unit?: string
  /** 底部增量说明，如「同比 -4.2%」 */
  delta?: string
  /** 增量语义色：pos 绿 / neg 红 / neu 灰（默认 neu） */
  tone?: 'pos' | 'neg' | 'neu'
  /** 头部右侧线性图标 */
  icon?: Component
}

const props = defineProps<{
  items: StatItem[]
  /** 加载中显示骨架，避免数值闪跳 */
  loading?: boolean
}>()

const displayValue = (v: number | string): string =>
  typeof v === 'number' ? formatNumber(v) : v

const toneClass = (tone?: StatItem['tone']): string => {
  if (tone === 'pos') return 'is-pos'
  if (tone === 'neg') return 'is-neg'
  return 'is-neu'
}

const cols = computed(() => props.items.length)
</script>

<template>
  <div class="pen-stat-strip" :style="{ '--stat-cols': cols }">
    <div v-for="(item, i) in items" :key="i" class="pen-stat">
      <div class="pen-stat__top">
        <span class="pen-stat__label">{{ item.label }}</span>
        <el-icon v-if="item.icon" :size="15" class="pen-stat__icon" aria-hidden="true">
          <component :is="item.icon" />
        </el-icon>
      </div>

      <div v-if="loading" class="pen-stat__skeleton" aria-hidden="true" />
      <div v-else class="pen-stat__valrow">
        <span class="pen-stat__value">{{ displayValue(item.value) }}</span>
        <span v-if="item.unit" class="pen-stat__unit">{{ item.unit }}</span>
      </div>

      <p v-if="item.delta && !loading" class="pen-stat__delta" :class="toneClass(item.tone)">
        {{ item.delta }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.pen-stat-strip {
  display: grid;
  grid-template-columns: repeat(var(--stat-cols, 4), minmax(0, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.pen-stat {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: 13px 15px;
  background: var(--pen-surface);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  transition: border-color var(--motion-base) var(--motion-ease);
}

.pen-stat:hover {
  border-color: var(--pen-muted);
}

.pen-stat__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.pen-stat__label {
  font-size: var(--fs-caption);
  font-weight: 500;
  line-height: 1.4;
  color: var(--pen-faint);
}

.pen-stat__icon {
  color: var(--pen-faint);
  flex-shrink: 0;
}

.pen-stat__valrow {
  display: flex;
  align-items: baseline;
  gap: var(--space-1-5);
  min-width: 0;
}

.pen-stat__value {
  font-size: var(--fs-stat);
  font-weight: 500;
  line-height: 1.2;
  letter-spacing: -0.02em;
  color: var(--pen-ink);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.pen-stat__unit {
  font-size: 11px;
  color: var(--pen-faint);
  flex-shrink: 0;
}

.pen-stat__delta {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
}

.pen-stat__delta.is-pos {
  color: var(--pen-success);
}

.pen-stat__delta.is-neg {
  color: var(--pen-danger);
}

.pen-stat__delta.is-neu {
  color: var(--pen-faint);
}

.pen-stat__skeleton {
  height: 26px;
  width: 60%;
  border-radius: var(--radius-control);
  background: var(--pen-raised);
  animation: pen-pulse 1.2s ease-in-out infinite;
}

@keyframes pen-pulse {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}

@media (max-width: 1280px) {
  .pen-stat-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .pen-stat-strip {
    grid-template-columns: 1fr;
  }
}
</style>