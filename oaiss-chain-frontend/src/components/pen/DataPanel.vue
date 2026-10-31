<script setup lang="ts">
/**
 * 数据面板：对照 pen 设计稿 frame xxxPanel 结构
 * ─ 面板头 44px：图标 + 标题(10.5px/500) + 元信息(条数等) → 右侧工具栏按钮
 * ─ 面板体：直接放表格/图表，去掉多余的内层卡片嵌套
 *
 * 设计源规格：height 44、padding [0,16]、上下发丝线、标题与元信息 gap 9。
 */
import { useSlots, type Component } from 'vue'

defineProps<{
  /** 面板标题（已 i18n 化） */
  title: string
  /** 标题后的元信息，如「共 24 条 · 按时间倒序」 */
  meta?: string
  /** 标题左侧线性图标 */
  icon?: Component
  /** 去掉面板头（纯内容容器场景） */
  bare?: boolean
}>()

const slots = useSlots()
</script>

<template>
  <section class="pen-panel">
    <header v-if="!bare" class="pen-panel__head">
      <div class="pen-panel__head-left">
        <el-icon v-if="icon" :size="15" class="pen-panel__icon" aria-hidden="true">
          <component :is="icon" />
        </el-icon>
        <span class="pen-panel__title">{{ title }}</span>
        <span v-if="meta" class="pen-panel__meta">{{ meta }}</span>
      </div>

      <div v-if="slots.actions" class="pen-panel__head-right">
        <slot name="actions" />
      </div>
    </header>

    <div class="pen-panel__body">
      <slot />
    </div>

    <footer v-if="slots.footer" class="pen-panel__foot">
      <slot name="footer" />
    </footer>
  </section>
</template>

<style scoped>
.pen-panel {
  background: var(--pen-surface);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.pen-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  height: 44px;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--pen-border);
}

.pen-panel__head-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.pen-panel__icon {
  color: var(--pen-faint);
  flex-shrink: 0;
}

.pen-panel__title {
  font-size: var(--fs-caption);
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--pen-muted);
  white-space: nowrap;
}

.pen-panel__meta {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pen-panel__head-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.pen-panel__body {
  min-width: 0;
}

.pen-panel__foot {
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--pen-border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

/* 面板内的表格与卡片贴合边缘（设计稿表格直接撑满面板） */
.pen-panel__body :deep(.el-table) {
  font-size: var(--fs-body);
}

.pen-panel__body :deep(.el-table th.el-table__cell:first-child .cell),
.pen-panel__body :deep(.el-table td.el-table__cell:first-child .cell) {
  padding-left: var(--space-4);
}

.pen-panel__body :deep(.el-table th.el-table__cell:last-child .cell),
.pen-panel__body :deep(.el-table td.el-table__cell:last-child .cell) {
  padding-right: var(--space-4);
}

/* 嵌套卡片在面板内不再叠加边框 */
.pen-panel__body :deep(.el-card) {
  border: none;
  border-radius: 0;
}
</style>