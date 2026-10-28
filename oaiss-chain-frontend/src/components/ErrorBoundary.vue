<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const error = ref<Error | null>(null)
const info = ref('')

onErrorCaptured((err: unknown, _instance, errorInfo: string) => {
  console.error('[ErrorBoundary]', err, errorInfo)
  error.value = err instanceof Error ? err : new Error(String(err))
  info.value = errorInfo
  return false
})

const onRetry = () => {
  error.value = null
  info.value = ''
}
</script>

<template>
  <div v-if="error" class="error-boundary">
    <div class="error-content">
      <h2>{{ t('errorBoundary.title') }}</h2>
      <p class="error-message">{{ error.message || t('errorBoundary.unknownError') }}</p>
      <p v-if="info" class="error-info">{{ info }}</p>
      <el-button type="primary" @click="onRetry">{{ t('errorBoundary.retry') }}</el-button>
    </div>
  </div>
  <slot v-else />
</template>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 40px;
}

.error-content {
  text-align: center;
}

.error-content h2 {
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
}

.error-message {
  margin-bottom: 8px;
  color: var(--el-color-danger);
}

.error-info {
  margin-bottom: 16px;
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}
</style>
