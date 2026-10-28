<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：管理员端 - 全平台用户与角色权限管控 (System Users)
 * 【路由地址】：/admin/system/users
 * 【访问权限】：ROLE_ADMIN (系统管理员)
 * 【界面可见功能】：
 *   1. 用户筛选工具栏：按角色（企业/审核员/第三方/管理员）、状态（启用/禁用）检索
 *   2. 用户列表表格：展示用户ID、用户名、绑定邮箱、角色徽章、启用状态与注册时间
 *   3. 账号启停操作：点击【启用】或【禁用】开关，二次确认后控制账号登录权限
 *   4. 用户类型与安全策略说明（PageHint / StatusTag）
 * 【对应后端 Controller & API】：
 *   - AdminController:
 *     * GET /api/v1/admin/users                 -> getUserList() 分页查询全量用户
 *     * PUT /api/v1/admin/users/{userId}/status -> updateUserStatus() 修改账号启停状态
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, updateUserStatus } from '../../api/admin'
import { formatDateTime } from '../../utils/format'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'

const { t } = useI18n()

type UserRow = {
  id: number
  username?: string
  email?: string
  userType: number
  status: number
  createdAt?: string
}

const searchForm = reactive<{
  userType: '' | number
  status: '' | number
}>({
  userType: '',
  status: '',
})

const userTypeOptions = computed(() => [
  { label: t('systemUsers.typeAll'), value: '' },
  { label: t('systemUsers.typeEnterprise'), value: 1 },
  { label: t('systemUsers.typeAuditor'), value: 2 },
  { label: t('systemUsers.typeThirdParty'), value: 3 },
  { label: t('systemUsers.typeAdmin'), value: 4 },
])

const statusOptions = computed(() => [
  { label: t('systemUsers.typeAll'), value: '' },
  { label: t('systemUsers.statusEnabled'), value: 1 },
  { label: t('systemUsers.statusDisabled'), value: 0 },
])

const userList = ref<UserRow[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const userTypeMap = computed<Record<number, string>>(() => ({
  1: t('systemUsers.typeEnterprise'),
  2: t('systemUsers.typeAuditor'),
  3: t('systemUsers.typeThirdParty'),
  4: t('systemUsers.typeAdmin'),
}))

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      ...(searchForm.userType !== '' && { userType: searchForm.userType }),
      ...(searchForm.status !== '' && { status: searchForm.status }),
    }
    const response = await getUserList(params)
    userList.value = response.items || []
    total.value = response.total || 0
  } catch {
    ElMessage.error(t('systemUsers.loadFailed'))
  } finally {
    loading.value = false
  }
}

const onQuery = () => {
  currentPage.value = 1
  fetchData()
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const onCurrentChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

const beforeStatusChange = async (row: UserRow): Promise<boolean> => {
  const newStatus = row.status === 1 ? 0 : 1

  try {
    await ElMessageBox.confirm(
      newStatus === 1 ? t('systemUsers.confirmEnable') : t('systemUsers.confirmDisable'),
      newStatus === 1 ? t('systemUsers.enableUser') : t('systemUsers.disableUser'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )

    await updateUserStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? t('systemUsers.enableSuccess') : t('systemUsers.disableSuccess'))
    row.status = newStatus
    await fetchData()
    return true
  } catch (error) {
    if (error === 'cancel') {
      return false
    }
    return false
  }
}

const handleStatusToggle = async (row: UserRow) => {
  await beforeStatusChange(row)
}

const getStatusType = (status: number) => (status === 1 ? 'success' : 'danger')

const getStatusText = (status: number) => (
  status === 1 ? t('systemUsers.statusEnabled') : t('systemUsers.statusDisabled')
)

onMounted(() => {
  fetchData()
})
</script>

<template>
  <section class="users-page">
    <el-card class="section-card" shadow="never">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>{{ t('systemUsers.breadcrumbSystem') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ t('systemUsers.breadcrumbUsers') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </el-card>

    <PageHint :text="t('pageHints.systemUsers')" />

    <el-card class="section-card" shadow="never">
      <el-form :inline="true" class="search-form">
        <el-form-item :label="t('systemUsers.colUserType')">
          <el-select v-model="searchForm.userType" :placeholder="t('systemUsers.typeAll')" style="width: 140px" clearable>
            <el-option v-for="item in userTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('common.status')">
          <el-select v-model="searchForm.status" :placeholder="t('systemUsers.typeAll')" style="width: 140px" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="section-card" shadow="never">
      <el-table :data="userList" border v-loading="loading">
        <el-table-column prop="username" :label="t('systemUsers.colUsername')" min-width="120" />
        <el-table-column prop="email" :label="t('systemUsers.colEmail')" min-width="180" />
        <el-table-column prop="userType" :label="t('systemUsers.colUserType')" min-width="120">
          <template #default="{ row }">
            {{ userTypeMap[row.userType] || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" min-width="100">
          <template #default="{ row }">
            <StatusTag :type="getStatusType(row.status)" :text="getStatusText(row.status)" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('common.createTime')" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="140" fixed="right">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :before-change="() => beforeStatusChange(row)"
              inline-prompt
              :active-text="t('systemUsers.statusEnabled')"
              :inactive-text="t('systemUsers.statusDisabled')"
              style="--el-switch-on-color: var(--color-accent);"
            />
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="onSizeChange"
          @current-change="onCurrentChange"
        />
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.users-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
}

.pagination-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
