<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：管理员端 - 平台底层系统参数与安全配置 (System Config)
 * 【路由地址】：/admin/system/config
 * 【访问权限】：ROLE_ADMIN (系统管理员)
 * 【界面可见功能】：
 *   1. 系统运行配置表格：展示系统名称、区块链连通状态、最大上传限制、会话超时
 *   2. 参数检索过滤栏：支持按参数名称或说明快速搜索
 *   3. 配置参数只读安全视图与规则释义（PageHint）
 * 【对应后端 Controller & API】：
 *   - AdminController:
 *     * GET /api/v1/admin/config      -> getConfig() 获取系统运行配置
 *     * GET /api/v1/admin/permissions -> getPermissions() 获取权限清单
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose, Setting, Tickets } from '@element-plus/icons-vue'
import { getConfig } from '../../api/admin'
import type { AdminConfigResponse } from '../../types'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'

const { t } = useI18n()

type ConfigRow = {
  id: string
  description: string
  name: string
  value: string
  updatedBy: string
}

const searchForm = reactive({
  description: '',
  name: '',
})

const loading = ref(false)
const configList = ref<ConfigRow[]>([])
const page = ref(1)
const pageSize = ref(10)

function normalizeValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return String(value)
}

function buildRows(config: Partial<AdminConfigResponse>): ConfigRow[] {
  const mapping: Array<{ key: string; name: string; description: string }> = [
    { key: 'systemName', name: 'systemName', description: t('systemConfig.descSystemName') },
    { key: 'enableBlockChain', name: 'enableBlockChain', description: t('systemConfig.descEnableBlockchain') },
    { key: 'maxUploadSize', name: 'maxUploadSize', description: t('systemConfig.descMaxUploadSize') },
    { key: 'sessionTimeout', name: 'sessionTimeout', description: t('systemConfig.descSessionTimeout') },
    { key: 'version', name: 'version', description: t('systemConfig.descVersion') },
    { key: 'enableCaptcha', name: 'enableCaptcha', description: t('systemConfig.descEnableCaptcha') },
  ]

  return mapping
    .filter(({ key }) => key in config)
    .map(({ key, name, description }) => ({
      id: key,
      description,
      name,
      value: normalizeValue(config[key]),
      updatedBy: t('systemConfig.updatedByBackend'),
    }))
}

const filteredData = computed(() => {
  const description = searchForm.description.trim().toLowerCase()
  const name = searchForm.name.trim().toLowerCase()

  return configList.value.filter((item) => {
    const descriptionMatch = !description || item.description.toLowerCase().includes(description)
    const nameMatch = !name || item.name.toLowerCase().includes(name)
    return descriptionMatch && nameMatch
  })
})

const total = computed(() => filteredData.value.length)

const pagedData = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

/** 首屏统计条：全部由已加载的配置项推导，不新增接口请求 */
const overviewStats = computed(() => {
  const rows = configList.value
  const enabled = rows.filter((row) => row.value === 'true').length
  const disabled = rows.filter((row) => row.value === 'false').length
  const version = rows.find((row) => row.name === 'version')?.value ?? '-'

  return [
    {
      label: t('systemConfig.statTotalConfigs'),
      value: rows.length,
      unit: t('systemConfig.statUnitItems'),
      delta: t('systemConfig.statTotalHint'),
      tone: 'neu' as const,
      icon: Tickets,
    },
    {
      label: t('systemConfig.statEnabledSwitches'),
      value: enabled,
      unit: t('systemConfig.statUnitSwitches'),
      delta: t('systemConfig.statEnabledHint'),
      tone: enabled > 0 ? ('pos' as const) : ('neu' as const),
      icon: CircleCheck,
    },
    {
      label: t('systemConfig.statDisabledSwitches'),
      value: disabled,
      unit: t('systemConfig.statUnitSwitches'),
      delta: t('systemConfig.statDisabledHint'),
      tone: disabled > 0 ? ('neg' as const) : ('neu' as const),
      icon: CircleClose,
    },
    {
      label: t('systemConfig.statVersion'),
      value: version,
      unit: '',
      delta: t('systemConfig.statVersionHint'),
      tone: 'neu' as const,
      icon: Setting,
    },
  ]
})

const loadConfigs = async () => {
  try {
    loading.value = true
    const result = await getConfig()
    configList.value = buildRows(result)
  } catch {
    ElMessage.error(t('systemConfig.loadFailed'))
    configList.value = []
  } finally {
    loading.value = false
  }
}

const onQuery = () => {
  page.value = 1
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  page.value = 1
}

const onCurrentChange = (current: number) => {
  page.value = current
}

onMounted(() => {
  loadConfigs()
})
</script>

<template>
  <section class="config-page pen-enter">
    <PageHeader
      :title="t('systemConfig.title')"
      :description="t('systemConfig.description')"
      :crumbs="[t('systemConfig.breadcrumbSystem'), t('systemConfig.breadcrumbConfig')]"
    />

    <PageHint :text="t('pageHints.systemConfig')" />

    <StatStrip :items="overviewStats" :loading="loading" />

    <FilterBar :aria-label="t('systemConfig.filterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('systemConfig.searchDesc') }}</span>
        <el-input v-model="searchForm.description" :placeholder="t('systemConfig.enterDesc')" clearable @keyup.enter="onQuery" />
      </label>

      <label class="pen-field">
        <span class="pen-field__label">{{ t('systemConfig.searchName') }}</span>
        <el-input v-model="searchForm.name" :placeholder="t('systemConfig.enterName')" clearable @keyup.enter="onQuery" />
      </label>

      <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
      <el-button @click="loadConfigs">{{ t('common.refresh') }}</el-button>
    </FilterBar>

    <DataPanel
      :title="t('systemConfig.panelTitle')"
      :meta="t('common.total', { count: total })"
    >
      <el-table :data="pagedData" v-loading="loading">
        <el-table-column prop="description" :label="t('systemConfig.searchDesc')" min-width="220" />
        <el-table-column prop="name" :label="t('systemConfig.searchName')" min-width="180" />
        <el-table-column prop="value" :label="t('systemConfig.colConfigValue')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updatedBy" :label="t('systemConfig.colUpdatedBy')" min-width="120" />
      </el-table>

      <template #footer>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="onSizeChange"
          @current-change="onCurrentChange"
        />
      </template>
    </DataPanel>
  </section>
</template>

<style scoped>
.config-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}
</style>
