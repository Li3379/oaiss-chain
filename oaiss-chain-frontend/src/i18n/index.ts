import { createI18n } from 'vue-i18n'

/**
 * Dual i18n pattern in this project:
 * - Vue components: use `useI18n().t(key)` (reactive, auto-updates on locale change)
 * - API / non-component files: use `i18n.global.t(key)` (synchronous, no reactivity needed)
 * Always prefer useI18n() in components for locale reactivity.
 */
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export type LocaleType = 'zh-CN' | 'en-US'

const i18n = createI18n({
  legacy: false,
  locale: (localStorage.getItem('locale') as LocaleType) || 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})



// Safe accessor for non-component files - returns key as fallback if i18n not yet initialized
export const t = (key: string, params?: Record<string, unknown>): string => {
  if (!i18n.global) return key
  return i18n.global.t(key, params as any)
}

export default i18n
