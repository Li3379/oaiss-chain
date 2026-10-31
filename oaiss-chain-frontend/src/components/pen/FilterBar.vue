<script setup lang="ts">
/**
 * 筛选条：对照 pen 设计稿行 Filters 结构
 * ─ 每个字段：标签(10.5px/500, 弱色) 在上，控件(36px 高、浅底、发丝线) 在下
 * ─ 末尾按钮组：主操作墨色、次操作描边，与字段底部对齐
 *
 * 用默认插槽放控件（保持各页原有 v-model 与校验逻辑不变），
 * 组件只负责「标签在上 + 底部对齐 + 间距」这套版式。
 */
defineProps<{
  /** 无障碍标签；缺省则不加 landmark */
  ariaLabel?: string
}>()
</script>

<template>
  <section class="pen-filter-bar" :aria-label="ariaLabel">
    <slot />
  </section>
</template>

<style scoped>
.pen-filter-bar {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: var(--space-2-5);
  margin-bottom: var(--space-4);
}

.pen-filter-bar :deep(.pen-field) {
  display: flex;
  flex-direction: column;
  gap: var(--space-1-5);
  min-width: 0;
}

.pen-filter-bar :deep(.pen-field__label) {
  font-size: var(--fs-caption);
  font-weight: 500;
  line-height: 1.4;
  color: var(--pen-faint);
}

.pen-filter-bar :deep(.pen-field__control) {
  height: 36px;
}

.pen-filter-bar :deep(.el-form-item) {
  margin: 0;
}

/* 字段内的输入控件统一 36px 高、浅底 */
.pen-filter-bar :deep(.el-input__wrapper),
.pen-filter-bar :deep(.el-select__wrapper) {
  min-height: 36px;
}

@media (max-width: 768px) {
  .pen-filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .pen-filter-bar :deep(.pen-field) {
    width: 100%;
  }
}
</style>