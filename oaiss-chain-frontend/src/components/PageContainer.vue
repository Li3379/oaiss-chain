<script setup lang="ts">
import { useI18n } from 'vue-i18n'

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
})
</script>

<template>
  <section class="page-container">
    <el-card shadow="never" class="page-card">
      <h1 class="page-title">{{ title }}</h1>
      <p class="page-description">{{ description || t('pageContainer.pageHint', { title }) }}</p>
    </el-card>
    <slot />
  </section>
</template>

<style scoped>
.page-container {
  min-height: calc(100vh - 112px);
}

.page-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.page-title {
  margin: 0;
  color: var(--color-text);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.4px;
}

.page-description {
  margin: 14px 0 0;
  color: var(--color-text-muted);
  font-size: var(--text-base);
  line-height: 1.8;
}

@media (max-width: 768px) {
  .page-container {
    min-height: calc(100vh - 128px);
  }

  .page-title {
    font-size: var(--text-xl);
  }
}
</style>
