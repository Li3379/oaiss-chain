<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/**
 * 页面容器：pen 版式承载
 * ─ 页头（面包屑 / 标题 / 职责说明 / 右上操作）沿用 PageHeader 规格
 * ─ 内容区纵向节奏 18px（设计稿 Main 的 gap）
 *
 * 保持既有对外契约不变（title/description + default slot），
 * 额外可选 crumbs / actions 插槽向 pen 版式对齐。
 */
const { t } = useI18n()

defineProps({
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
  /** 面包屑各段（已 i18n 化）；不传则不渲染 */
  crumbs: {
    type: Array as () => string[],
    default: () => [],
  },
})
</script>

<template>
  <section class="page-container">
    <header class="page-head">
      <div class="page-head__left">
        <nav v-if="crumbs.length" class="page-crumbs" aria-label="breadcrumb">
          <template v-for="(crumb, i) in crumbs" :key="i">
            <span v-if="i > 0" class="page-crumbs__sep" aria-hidden="true">/</span>
            <span class="page-crumbs__item" :class="{ 'is-current': i === crumbs.length - 1 }">{{ crumb }}</span>
          </template>
        </nav>
        <h1 class="page-title">{{ title }}</h1>
        <p class="page-description">{{ description || t('pageContainer.pageHint', { title }) }}</p>
      </div>

      <div v-if="$slots.actions" class="page-head__right">
        <slot name="actions" />
      </div>
    </header>

    <slot />
  </section>
</template>

<style scoped>
.page-container {
  min-height: calc(100vh - 112px);
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-6);
}

.page-head__left {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
  flex: 1 1 auto;
}

.page-head__right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.page-crumbs {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--fs-caption);
  line-height: 1.4;
  color: var(--pen-faint);
}

.page-crumbs__sep {
  color: var(--pen-border);
}

.page-crumbs__item.is-current {
  color: var(--pen-muted);
}

.page-title {
  margin: 0;
  color: var(--pen-ink);
  font-size: var(--fs-h1);
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.page-description {
  margin: 0;
  color: var(--pen-muted);
  font-size: var(--fs-sub);
  line-height: 1.6;
}

@media (max-width: 768px) {
  .page-container {
    min-height: calc(100vh - 128px);
    gap: var(--space-3);
  }

  .page-head {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-3);
  }

  .page-title {
    font-size: 20px;
  }
}
</style>