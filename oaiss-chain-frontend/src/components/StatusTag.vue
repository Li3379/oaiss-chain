<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheck, CircleClose, Clock, InfoFilled, Tools } from '@element-plus/icons-vue'
import { STATUS_DOMAIN_META, type StatusDomain } from '../utils/status-meta'

/**
 * 状态三重编码标签：图标 + 颜色 + 文字
 * - domain 提供域内统一色板与图标（报告/交易/项目）
 * - text 显式覆盖（页面有角色特定措辞时使用）
 * - 无 text 时优先 i18n 文案（修复英文界面直出后端中文 statusText 的问题）
 *
 * 图标统一使用 Element Plus SVG 图标组件（UI/UX 规范：图标用 SVG，不用 emoji）。
 */
const props = defineProps<{
  status?: number | string | null
  domain?: StatusDomain
  type?: string
  /** 图标组件覆盖（默认取域内 meta） */
  iconComponent?: Component
  text?: string
  size?: 'small' | 'default' | 'large'
}>()

const { t } = useI18n()

const meta = computed(() => {
  if (!props.domain) return undefined
  const code = Number(props.status)
  if (Number.isNaN(code)) return undefined
  return STATUS_DOMAIN_META[props.domain][code]
})

/** 无域映射时的兜底图标：让"颜色+文字"升级为"图标+颜色+文字"三重编码 */
const FALLBACK_ICON: Record<string, Component> = {
  info: InfoFilled,
  warning: Clock,
  primary: Tools,
  success: CircleCheck,
  danger: CircleClose,
}

const tagType = computed(() => meta.value?.type || props.type || 'info')
const tagIcon = computed<Component | undefined>(
  () => props.iconComponent ?? meta.value?.icon ?? FALLBACK_ICON[tagType.value],
)

const label = computed(() => {
  if (props.text) return props.text
  if (meta.value) {
    const translated = t(meta.value.labelKey)
    if (translated !== meta.value.labelKey) return translated
  }
  return props.status === undefined || props.status === null ? '-' : String(props.status)
})
</script>

<template>
  <el-tag :type="tagType" :size="size" class="status-tag">
    <el-icon v-if="tagIcon" class="status-tag__icon" aria-hidden="true">
      <component :is="tagIcon" />
    </el-icon>
    <span class="status-tag__text">{{ label }}</span>
  </el-tag>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.status-tag__icon {
  font-size: 13px;
  vertical-align: middle;
}
</style>
