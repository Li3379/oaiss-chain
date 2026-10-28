import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: ref('zh-CN'),
  }),
}))

vi.mock('../../api/admin', () => ({
  getEnterpriseAdmissionList: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  issueEnterpriseAdmission: vi.fn(() => Promise.resolve()),
  revokeEnterpriseAdmission: vi.fn(() => Promise.resolve()),
  getReviewerQualificationList: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  issueReviewerQualification: vi.fn(() => Promise.resolve()),
  revokeReviewerQualification: vi.fn(() => Promise.resolve()),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import CertificateManage from '../admin/CertificateManage.vue'
import {
  getEnterpriseAdmissionList,
  getReviewerQualificationList,
  issueEnterpriseAdmission,
  issueReviewerQualification,
  revokeEnterpriseAdmission,
  revokeReviewerQualification,
} from '../../api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'link', 'plain'],
    emits: ['click'],
  },
  'el-table': {
    template: '<table><slot /><slot :row="data[0] || {}" :$index="0" /><slot name="append" /></table>',
    props: ['data', 'border', 'emptyText'],
  },
  'el-table-column': {
    template: '<td><slot :row="row ?? {}" :$index="0" /></td>',
    props: ['prop', 'label', 'minWidth', 'width', 'fixed', 'showOverflowTooltip', 'row'],
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type'] },
  'el-pagination': {
    template: '<div class="el-pagination"></div>',
    props: ['currentPage', 'pageSize', 'background', 'pageSizes', 'layout', 'total'],
    emits: ['size-change', 'current-change', 'update:current-page', 'update:page-size'],
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose'],
    emits: ['update:modelValue'],
  },
  'el-tabs': {
    template: '<div class="el-tabs"><slot /></div>',
    props: ['modelValue'],
    emits: ['update:modelValue', 'tab-change'],
  },
  'el-tab-pane': {
    template: '<div class="el-tab-pane"><slot /></div>',
    props: ['label', 'name'],
  },
  'el-form': { template: '<form @submit.prevent><slot /></form>' },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label'] },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
  },
}

function mountComponent() {
  return mount(CertificateManage, {
    global: {
      stubs,
    },
  })
}

describe('CertificateManage.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads admission certificates on mount with backend paging params', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getEnterpriseAdmissionList).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    wrapper.unmount()
  })

  it('loads reviewer qualifications when switching tabs', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    ;(wrapper.vm as unknown as { onTabChange: (tab: 'qualification') => void }).onTabChange('qualification')
    await flushPromises()

    expect(getReviewerQualificationList).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    wrapper.unmount()
  })

  it('validates enterprise id before issuing admission certificates', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await (wrapper.vm as unknown as { handleIssueAdmission: () => Promise<void> }).handleIssueAdmission()
    await flushPromises()

    expect(issueEnterpriseAdmission).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('certificateManage.enterEnterpriseId')
    wrapper.unmount()
  })

  it('issues admission certificates and refreshes the backend list', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      issueForm: { enterpriseId: string }
      handleIssueAdmission: () => Promise<void>
    }

    vm.issueForm.enterpriseId = '8'
    await vm.handleIssueAdmission()
    await flushPromises()

    expect(issueEnterpriseAdmission).toHaveBeenCalledWith(8)
    expect(ElMessage.success).toHaveBeenCalledWith('certificateManage.issueSuccess')
    expect(getEnterpriseAdmissionList).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('issues reviewer qualifications and refreshes the qualification list', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      issueType: 'qualification'
      issueForm: { reviewerId: string }
      handleIssueQualification: () => Promise<void>
      onTabChange: (tab: 'qualification') => void
    }

    vm.onTabChange('qualification')
    await flushPromises()
    vm.issueForm.reviewerId = '12'
    await vm.handleIssueQualification()
    await flushPromises()

    expect(issueReviewerQualification).toHaveBeenCalledWith(12)
    expect(ElMessage.success).toHaveBeenCalledWith('certificateManage.issueSuccess')
    expect(getReviewerQualificationList).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('revokes admissions after confirmation and refreshes data', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await (wrapper.vm as unknown as {
      handleRevokeAdmission: (row: { enterpriseId: number }) => Promise<void>
    }).handleRevokeAdmission({ enterpriseId: 5 })
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(revokeEnterpriseAdmission).toHaveBeenCalledWith(5)
    expect(ElMessage.success).toHaveBeenCalledWith('certificateManage.revokeSuccess')
    expect(getEnterpriseAdmissionList).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('revokes reviewer qualifications after confirmation and refreshes data', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await (wrapper.vm as unknown as {
      handleRevokeQualification: (row: { reviewerId: number }) => Promise<void>
    }).handleRevokeQualification({ reviewerId: 9 })
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(revokeReviewerQualification).toHaveBeenCalledWith(9)
    expect(ElMessage.success).toHaveBeenCalledWith('certificateManage.revokeSuccess')
    wrapper.unmount()
  })

  it('shows translated load failure when admission query fails', async () => {
    vi.mocked(getEnterpriseAdmissionList).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('certificateManage.loadFailed')
    wrapper.unmount()
  })
})
