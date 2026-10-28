<script setup lang="ts">
import { useRouter } from 'vue-router'

/**
 * 空态引导：告诉用户"还没有数据时下一步做什么"
 * 空态只说"暂无数据"是新手最容易卡住的地方，这里补一个明确的行动入口。
 * - 传 to：点击跳转该路由
 * - 只传 actionText：点击抛出 action 事件，由父页面决定行为（如打开创建对话框）
 */
const props = defineProps<{
  description: string
  actionText?: string
  to?: string
  icon?: string
}>()

const emit = defineEmits<{ action: [] }>()

const router = useRouter()

function onAction() {
  if (props.to) {
    void router.push(props.to)
    return
  }
  emit('action')
}
</script>

<template>
  <el-empty :description="description" class="empty-hint">
    <el-button v-if="actionText" type="primary" plain @click="onAction">
      <span v-if="icon" class="empty-hint__icon" aria-hidden="true">{{ icon }}</span>
      {{ actionText }}
    </el-button>
    <slot v-else />
  </el-empty>
</template>

<style scoped>
.empty-hint {
  padding: 18px 0;
}

.empty-hint__icon {
  margin-right: 6px;
}
</style>
