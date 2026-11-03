import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: ref('zh-CN'),
  }),
}))

vi.mock('../../api/carbonNeutral', () => ({
  getPendingVerification: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 0, size: 10, totalPages: 0 })),
  getProjects: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 0, size: 10, totalPages: 0 })),
  reviewProject: vi.fn(() => Promise.resolve()),
}))

vi.mock('../../api/credit', () => ({
  deductPoints: vi.fn(() => Promise.resolve()),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: {
      confirm: vi.fn(() => Promise.resolve()),
      prompt: vi.fn(() => Promise.resolve({ value: 'documents incomplete' })),
    },
  }
})

import ProjectReview from '../auditor/ProjectReview.vue'
import { getPendingVerification, getProjects, reviewProject } from '../../api/carbonNeutral'
import { deductPoints } from '../../api/credit'
import { ElMessage, ElMessageBox } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
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
  'el-form': { template: '<form @submit.prevent><slot /></form>', props: ['labelWidth'] },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label'] },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'rows', 'placeholder'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'link', 'plain'],
    emits: ['click'],
  },
  'el-tabs': {
    template: '<div class="el-tabs"><slot /></div>',
    props: ['modelValue'],
    emits: ['tab-change', 'update:modelValue'],
  },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description'] },
  'el-select': {
    template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
  'el-option': {
    template: '<option :value="value"><slot /></option>',
    props: ['label', 'value'],
  },
}

function mountComponent() {
  return mount(ProjectReview, {
    global: {
      stubs,
    },
    attachTo: document.body,
  })
}

const baseProject = {
  id: 8,
  projectNo: 'CN-001',
  projectName: 'Forest Restore',
  projectType: 1,
  projectTypeName: 'Afforestation',
  ownerId: 22,
  ownerName: 'Green Corp',
  description: 'restore woodland',
  location: 'Hubei',
  expectedReduction: 128,
  actualReduction: 0,
  investmentAmount: 500000,
  startDate: '2026-01-01',
  endDate: '2026-12-31',
  status: 3,
  statusText: 'Implementing',
  certStatus: 0,
  certStatusText: 'Pending',
  certOrg: '',
  certDate: '',
  certNo: '',
  methodology: 'M-01',
  accountingPeriod: 12,
  issuedCredits: 0,
  usedCredits: 0,
  availableCredits: 0,
  applicationData: '',
  verificationReport: '',
  attachments: '',
  reviewComment: '',
  reviewerId: 0,
  reviewerName: '',
  reviewedAt: '',
  monitoringData: 'telemetry',
  lastMonitoringDate: '',
  verifierId: 91,
  verifierName: 'Verifier',
  verificationStatus: 1,
  verificationStatusText: 'Pending',
  createdAt: '2026-05-30 12:00:00',
  updatedAt: '2026-05-31 12:00:00',
}

describe('ProjectReview.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the review queue (status=1) and the certification queue (status=4) on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getProjects).toHaveBeenCalledWith({ status: 1, pageNum: 1, pageSize: 10 })
    expect(getProjects).toHaveBeenCalledWith({ status: 4, pageNum: 1, pageSize: 10 })
    wrapper.unmount()
  })

  it('approves a pending project and refreshes the review queue', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      approveProject: (row: typeof baseProject) => Promise<void>
    }

    await vm.approveProject({ ...baseProject, status: 1 })
    await flushPromises()

    expect(reviewProject).toHaveBeenCalledWith(8, { approved: true, comment: '' })
    expect(ElMessage.success).toHaveBeenCalledWith('projectReview.reviewApproved')
    wrapper.unmount()
  })

  it('requires a comment when rejecting and sends it to the backend', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      rejectProject: (row: typeof baseProject) => Promise<void>
    }

    vi.mocked(ElMessageBox.prompt).mockResolvedValueOnce({ value: '   ' } as never)
    await vm.rejectProject({ ...baseProject, status: 1 })
    await flushPromises()

    expect(reviewProject).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('projectReview.rejectCommentRequired')

    await vm.rejectProject({ ...baseProject, status: 1 })
    await flushPromises()

    expect(reviewProject).toHaveBeenCalledWith(8, { approved: false, comment: 'documents incomplete' })
    expect(ElMessage.success).toHaveBeenCalledWith('projectReview.reviewRejected')
    wrapper.unmount()
  })

  it('normalizes numeric status fields and keeps backend total authoritative', async () => {
    vi.mocked(getPendingVerification).mockResolvedValueOnce({
      items: [{ ...baseProject, status: '4', verificationStatus: '1' } as unknown as typeof baseProject],
      total: 6,
      page: 0,
      size: 10,
      totalPages: 1,
    })

    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      tableData: Array<{ status: number; verificationStatus: number }>
      total: number
    }

    expect(vm.tableData[0]).toMatchObject({ status: 4, verificationStatus: 1 })
    expect(vm.total).toBe(6)
    wrapper.unmount()
  })

  it('requires a deduction description before sending credit deductions', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      openDeductDialog: (row: typeof baseProject) => void
      submitDeduct: () => Promise<void>
    }

    vm.openDeductDialog(baseProject)
    await vm.submitDeduct()
    await flushPromises()

    expect(deductPoints).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('projectReview.enterDescription')
    wrapper.unmount()
  })

  it('submits credit deductions with the resolved enterprise owner id', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      openDeductDialog: (row: typeof baseProject) => void
      deductForm: { description: string; eventType: number; enterpriseId: number | null }
      submitDeduct: () => Promise<void>
    }

    vm.openDeductDialog(baseProject)
    vm.deductForm.description = 'monitoring anomaly'
    await vm.submitDeduct()
    await flushPromises()

    expect(deductPoints).toHaveBeenCalledWith({
      enterpriseId: 22,
      eventType: 1,
      description: 'monitoring anomaly',
    })
    expect(ElMessage.success).toHaveBeenCalledWith('projectReview.deductSuccess')
    wrapper.unmount()
  })

  it('shows backend deduction errors when the credit request fails', async () => {
    vi.mocked(deductPoints).mockRejectedValueOnce(new Error('credit service unavailable'))

    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      openDeductDialog: (row: typeof baseProject) => void
      deductForm: { description: string }
      submitDeduct: () => Promise<void>
    }

    vm.openDeductDialog(baseProject)
    vm.deductForm.description = 'monitoring anomaly'
    await vm.submitDeduct()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('credit service unavailable')
    wrapper.unmount()
  })

  it('shows translated load failure when the queue request fails', async () => {
    vi.mocked(getPendingVerification).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('projectReview.loadFailed')
    wrapper.unmount()
  })
})
