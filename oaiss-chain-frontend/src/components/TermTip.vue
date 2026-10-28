<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * 术语即时翻译：ⓘ 图标，hover/聚焦显示一句大白话
 * 文案来源：i18n `glossary.<term>`（zh/en 同键）
 */
const props = defineProps<{
  term: string
  text?: string
  placement?: 'top' | 'bottom' | 'left' | 'right'
}>()

const { t } = useI18n()

const content = computed(() => {
  if (props.text) return props.text
  const key = `glossary.${props.term}`
  const translated = t(key)
  return translated === key ? '' : translated
})
</script>

<template>
  <el-tooltip
    v-if="content"
    :content="content"
    :placement="placement || 'top'"
    effect="light"
    :show-after="120"
    popper-class="term-tip-popper"
  >
    <span class="term-tip" tabindex="0" role="note" :aria-label="content">ⓘ</span>
  </el-tooltip>
</template>

<style scoped>
.term-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  margin-left: 4px;
  border-radius: 50%;
  border: 1px solid var(--el-color-primary);
  color: var(--el-color-primary);
  font-size: 11px;
  line-height: 1;
  cursor: help;
  vertical-align: text-bottom;
  user-select: none;
}

.term-tip:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}
</style>
