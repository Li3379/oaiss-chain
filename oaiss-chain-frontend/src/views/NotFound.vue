<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()

function goHome() {
  router.push('/official-home')
}

function goBack() {
  router.back()
}
</script>

<template>
  <div class="not-found">
    <div class="content">
      <h1 class="code">404</h1>
      <p class="desc">{{ t('notFound.message') }}</p>
      <div class="actions">
        <el-button type="primary" @click="goHome">{{ t('notFound.goHome') }}</el-button>
        <el-button @click="goBack">{{ t('notFound.goBack') }}</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--color-raised);
}

.content {
  text-align: center;
}

.code {
  font-size: 120px;
  font-weight: 700;
  color: var(--color-accent);
  margin: 0;
  line-height: 1;
}

.desc {
  font-size: var(--text-lg);
  color: var(--color-text-muted);
  margin: 20px 0 32px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
