<script setup lang="ts">
/**
 * 页头：对照 pen 设计稿 PageHead 结构
 * ─ 左：面包屑（10.5px）→ 标题（24px/500）→ 一句话职责（12.5px）
 * ─ 右：主次操作按钮（34px 高；主操作墨色填充，次操作描边）
 *
 * 设计源规格：gap 24px、左列纵向 gap 8px、右列按钮 gap 8px、底部对齐。
 */
import { computed } from 'vue'

export interface PageHeaderAction {
  /** 按钮文案（已 i18n 化的字符串） */
  label: string
  /** primary = 墨色填充主操作；default = 描边次操作 */
  variant?: 'primary' | 'default'
  /** 图标组件（Lucide/EP 线性图标），不传则纯文字 */
  icon?: unknown
  disabled?: boolean
  loading?: boolean
}

const props = defineProps<{
  title: string
  description?: string
  /** 面包屑各段文案（已 i18n 化） */
  crumbs?: string[]
  actions?: PageHeaderAction[]
}>()

const emit = defineEmits<{ action: [index: number] }>()

const hasCrumbs = computed(() => Array.isArray(props.crumbs) && props.crumbs.length > 0)
</script>

<template>
  <header class="pen-page-head">
    <div class="pen-page-head__left">
      <nav v-if="hasCrumbs" class="pen-crumbs" aria-label="breadcrumb">
        <template v-for="(crumb, i) in crumbs" :key="i">
          <span v-if="i > 0" class="pen-crumbs__sep" aria-hidden="true">/</span>
          <span class="pen-crumbs__item" :class="{ 'is-current': i === crumbs!.length - 1 }">{{ crumb }}</span>
        </template>
      </nav>

      <h1 class="pen-page-head__title">{{ title }}</h1>

      <p v-if="description" class="pen-page-head__desc">{{ description }}</p>
    </div>

    <div v-if="actions?.length" class="pen-page-head__right">
      <button
        v-for="(action, i) in actions"
        :key="i"
        type="button"
        class="pen-btn"
        :class="action.variant === 'primary' ? 'pen-btn--primary' : 'pen-btn--ghost'"
        :disabled="action.disabled || action.loading"
        @click="emit('action', i)"
      >
        <span v-if="action.loading" class="pen-btn__spinner" aria-hidden="true" />
        <span v-else-if="action.icon" class="pen-btn__icon" aria-hidden="true">
          <component :is="action.icon" />
        </span>
        <span>{{ action.label }}</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.pen-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-6);
  margin-bottom: var(--space-5);
}

.pen-page-head__left {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
  flex: 1 1 auto;
}

.pen-crumbs {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--fs-caption);
  line-height: 1.4;
  color: var(--pen-faint);
}

.pen-crumbs__sep {
  color: var(--pen-border);
}

.pen-crumbs__item.is-current {
  color: var(--pen-muted);
}

.pen-page-head__title {
  margin: 0;
  font-size: var(--fs-h1);
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: -0.01em;
  color: var(--pen-ink);
}

.pen-page-head__desc {
  margin: 0;
  font-size: var(--fs-sub);
  line-height: 1.6;
  color: var(--pen-muted);
}

.pen-page-head__right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-shrink: 0;
}

/* 设计稿按钮：34px 高、0 14px 内边距、2px 圆角 */
.pen-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1-5);
  height: 34px;
  padding: 0 14px;
  border-radius: var(--radius-control);
  border: 1px solid transparent;
  font-family: inherit;
  font-size: var(--fs-body);
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--motion-ease),
    border-color var(--motion-fast) var(--motion-ease),
    color var(--motion-fast) var(--motion-ease);
}

.pen-btn--primary {
  background: var(--pen-action);
  color: var(--color-on-inverse);
}

.pen-btn--primary:hover:not(:disabled) {
  background: var(--pen-action-hover);
}

.pen-btn--primary:active:not(:disabled) {
  background: var(--pen-action-active);
}

.pen-btn--ghost {
  background: var(--pen-surface);
  border-color: var(--pen-border);
  color: var(--pen-ink);
}

.pen-btn--ghost:hover:not(:disabled) {
  background: var(--pen-raised);
}

.pen-btn--ghost:active:not(:disabled) {
  background: var(--pen-raised-active);
}

.pen-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pen-btn:focus-visible {
  outline: 2px solid var(--pen-ink);
  outline-offset: 2px;
}

.pen-btn__icon {
  display: inline-flex;
  font-size: 14px;
}

.pen-btn__spinner {
  width: 12px;
  height: 12px;
  border: 1.5px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: pen-spin 0.7s linear infinite;
}

@keyframes pen-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .pen-page-head {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-3);
  }

  .pen-page-head__title {
    font-size: 20px;
  }

  .pen-page-head__right {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>