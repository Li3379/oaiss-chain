<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import type { Component } from 'vue'

/**
 * 页面级提示条：简练阐明当前业务界面的核心定位与操作指南
 */
defineProps<{
  text: string
  icon?: string | Component
}>()
</script>

<template>
  <aside class="page-hint" role="note">
    <el-icon :size="16" class="page-hint__icon" aria-hidden="true">
      <component :is="icon" v-if="typeof icon === 'object' && icon !== null" />
      <span v-else-if="typeof icon === 'string' && icon.trim()">{{ icon }}</span>
      <InfoFilled v-else />
    </el-icon>
    <span class="page-hint__text">{{ text }}</span>
    <slot />
  </aside>
</template>

<style scoped>
.page-hint {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin-bottom: 0;
  padding: 9px 12px;
  border: 1px solid var(--pen-note-bd);
  border-radius: var(--radius-card);
  background: var(--pen-note-bg);
  color: var(--pen-muted);
  font-size: var(--fs-sub);
  line-height: 1.6;
}

.page-hint__icon {
  flex-shrink: 0;
  margin-top: 1px;
  color: var(--pen-ink);
}

.page-hint__text {
  flex: 1 1 auto;
  min-width: 0;
}
</style>
