<template>
  <ElConfigProvider :z-index="3000" :locale="elementLocale">
    <ErrorBoundary>
      <router-view />
    </ErrorBoundary>
  </ElConfigProvider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
import i18n from './i18n'
import ErrorBoundary from './components/ErrorBoundary.vue'

const elementLocale = computed(() => {
  return i18n.global.locale.value === 'en-US' ? en : zhCn
})
</script>
