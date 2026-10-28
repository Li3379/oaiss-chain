<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'
import type { LocaleType } from '../i18n'

const { locale } = useI18n()

const languages: { label: string; value: LocaleType }[] = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' },
]

const switchLocale = (lang: LocaleType) => {
  locale.value = lang
  localStorage.setItem('locale', lang)
}
</script>

<template>
  <el-dropdown @command="switchLocale">
    <span class="language-trigger">
      {{ locale === 'zh-CN' ? '中文' : 'English' }}
      <el-icon class="el-icon--right"><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="lang in languages"
          :key="lang.value"
          :command="lang.value"
          :disabled="locale === lang.value"
        >
          {{ lang.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.language-trigger {
  cursor: pointer;
  display: flex;
  align-items: center;
  font-size: var(--text-base);
  color: var(--color-text-muted);
}
.language-trigger:hover {
  color: var(--color-text);
}
</style>
