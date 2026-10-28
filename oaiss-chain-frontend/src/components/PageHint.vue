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
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 10px 16px;
  border: 1px solid rgba(20, 167, 154, 0.24);
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(20, 167, 154, 0.06) 0%, rgba(20, 167, 154, 0.02) 100%);
  color: var(--text-brand-muted, #2f6268);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.page-hint__icon {
  flex-shrink: 0;
  color: var(--el-color-primary, #14a79a);
}

.page-hint__text {
  flex: 1 1 auto;
  min-width: 0;
}
</style>
