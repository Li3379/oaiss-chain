<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { STATUS_DOMAIN_META, type StatusDomain } from '../utils/status-meta'

/**
 * 状态标签：pen 设计稿的「5px 圆点 + 浅底胶囊 + 11px 文字」
 * ─ 设计源 pill 规格：高 23、内边距 3/8、圆点 5px、字号 11/500、发丝线边框
 * ─ 三重编码保留：颜色（圆点+浅底）、文案（i18n）、无障碍语义（title + 读屏可见文字）
 * ─ iconComponent 仍可覆盖为真实图标（少数需要图标的场景），默认按设计稿渲染圆点
 *
 * 状态枚举与 i18n 键完全不动：status-meta.ts 是单一真相源。
 */
const props = defineProps<{
  status?: number | string | null
  domain?: StatusDomain
  type?: string
  /** 显式图标覆盖：传了就用图标，不传用设计稿圆点 */
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

/** 无域映射时按显式 type 兜底（保持既有调用点行为不变） */
const tone = computed<string>(() => meta.value?.type || props.type || 'info')

const label = computed(() => {
  if (props.text) return props.text
  if (meta.value) {
    const translated = t(meta.value.labelKey)
    if (translated !== meta.value.labelKey) return translated
  }
  return props.status === undefined || props.status === null ? '-' : String(props.status)
})

/** 圆点取各胶囊的深墨色（设计源 pillXxxInk） */
const DOT_COLOR: Record<string, string> = {
  success: 'var(--pen-pill-pos-ink)',
  danger: 'var(--pen-pill-neg-ink)',
  warning: 'var(--pen-pill-warn-ink)',
  primary: 'var(--pen-pill-info-ink)',
  info: 'var(--pen-pill-neu-ink)',
}
</script>

<template>
  <el-tag :type="tone" :size="size" class="status-tag">
    <el-icon class="status-tag__mark" aria-hidden="true">
      <component :is="iconComponent" v-if="iconComponent" />
      <span
        v-else
        class="status-tag__dot"
        :style="{ background: DOT_COLOR[tone] || DOT_COLOR.info }"
      />
    </el-icon>
    <span class="status-tag__text">{{ label }}</span>
  </el-tag>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  height: 23px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-pill);
  font-size: var(--fs-pill);
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  vertical-align: middle;
}

/* el-icon 仅作为圆点/图标的对齐容器，不参与字号缩放 */
.status-tag__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 5px;
  height: 5px;
  font-size: 12px;
  flex-shrink: 0;
}

.status-tag__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-tag__text {
  line-height: 1;
}
</style>