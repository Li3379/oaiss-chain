import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: ref('zh-CN'),
  }),
}))

vi.mock('../../api/carbon', () => ({
  reviewReport: vi.fn(() => Promise.resolve()),
}))

vi.mock('../../api/reviewer', () => ({
  getPendingReports: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 0, size: 10, totalPages: 0 })),
  getReviewHistory: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 0, size: 10, totalPages: 0 })),
  getMyReviewerQualification: vi.fn(() => Promise.resolve([])),
  getReviewerInfo: vi.fn(() => Promise.resolve(null)),
  getStatistics: vi.fn(() => Promise.resolve(null)),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import AuditList from '../auditor/AuditList.vue'
import { reviewReport } from '../../api/carbon'
import {
  getMyReviewerQualification,
  getPendingReports,
  getReviewHistory,
  getReviewerInfo,
  getStatistics,
} from '../../api/reviewer'
import { ElMessage } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-space': { template: '<div class="el-space"><slot /></div>', props: ['size'] },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
  'el-form': {
    template: '<form @submit.prevent><slot /></form>',
    methods: {
      validate() { return Promise.resolve(true) },
      resetFields() {},
    },
  },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label', 'prop'] },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'placeholder', 'showPassword', 'clearable', 'rows'],
    emits: ['update:modelValue'],
  },
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
  'el-radio-group': {
    template: '<div class="el-radio-group"><slot /></div>',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
  'el-radio': {
    template: '<label class="el-radio"><slot /></label>',
    props: ['label', 'value'],
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
}

function mountComponent() {
  return mount(AuditList, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

const baseReport = {
  id: 10,
  reportNo: 'RPT-010',
  enterpriseId: 5,
  enterpriseName: '',
  accountingPeriod: '2026-Q1',
  title: 'Emission Report',
  reportType: 1,
  emissionData: '{}',
  totalEmission: 78,
  scope1Emission: 20,
  scope2Emission: 30,
  scope3Emission: 28,
  calculationMethod: 'GB/T',
  status: 1,
  statusText: '',
  reviewerId: 7,
  reviewerName: 'Auditor',
  reviewComment: '',
  reviewedAt: '',
  signatureData: '',
  blockchainTxHash: '',
  onChainAt: '',
  attachments: '',
  createdAt: '2026-05-31 09:00:00',
  updatedAt: '2026-05-31 09:30:00',
}

describe('AuditList.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('renders successfully', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('loads pending reports and reviewer summary data on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getPendingReports).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(getMyReviewerQualification).toHaveBeenCalled()
    expect(getReviewerInfo).toHaveBeenCalled()
    expect(getStatistics).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('normalizes reviewer rows, statistics, and identity metadata from backend responses', async () => {
    vi.mocked(getPendingReports).mockResolvedValueOnce({
      items: [{ ...baseReport, status: '1' } as unknown as typeof baseReport],
      total: 12,
      page: 0,
      size: 10,
      totalPages: 2,
    })
    vi.mocked(getMyReviewerQualification).mockResolvedValueOnce([
      {
        id: 3,
        reviewerId: 7,
        qualificationType: 'Carbon Audit',
        certificateNo: 'QUAL-001',
        issuingAuthority: 'Board',
        issuedDate: '2025-01-01',
        expiryDate: '2027-01-01',
        status: 1,
        createdAt: '2025-01-01',
        updatedAt: '2025-01-01',
      },
    ])
    vi.mocked(getReviewerInfo).mockResolvedValueOnce({
      id: 7,
      userId: 9,
      qualificationNo: 'RV-001',
      level: 2,
      organization: 'Climate Org',
      reviewableIndustries: '[]',
      completedReviews: 5,
      status: 1,
      name: 'Li Reviewer',
    })
    vi.mocked(getStatistics).mockResolvedValueOnce({
      completedReviews: 8,
      passedCount: 6,
      rejectedCount: 2,
    })

    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      tableData: Array<{ enterpriseName: string; status: number; statusText: string }>
      total: number
      qualificationStatus: { certificateNo: string } | null
      reviewerIdentityLine: string
      normalizedStatistics: { totalReviews: number; approvedCount: number; rejectedCount: number; approvalRate: string }
    }

    expect(vm.tableData[0]).toMatchObject({
      enterpriseName: 'Enterprise #5',
      status: 1,
      statusText: 'Submitted',
    })
    expect(vm.total).toBe(12)
    expect(vm.qualificationStatus?.certificateNo).toBe('QUAL-001')
    expect(vm.reviewerIdentityLine).toContain('Li Reviewer')
    expect(vm.reviewerIdentityLine).toContain('Climate Org')
    expect(vm.normalizedStatistics).toMatchObject({
      totalReviews: 8,
      approvedCount: 6,
      rejectedCount: 2,
      approvalRate: '75.0%',
    })
    wrapper.unmount()
  })

  it('shows an error message when pending report loading fails', async () => {
    vi.mocked(getPendingReports).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('auditList.loadFailed')
    wrapper.unmount()
  })

  it('loads review history when the tab switches to all reports', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    ;(wrapper.vm as unknown as { activeTab: string }).activeTab = 'all'
    ;(wrapper.vm as unknown as { onTabChange: () => void }).onTabChange()
    await flushPromises()

    expect(getReviewHistory).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    wrapper.unmount()
  })

  it('展示规则预检卡片，且文案明确标注非 AI 模型', async () => {
    vi.mocked(getPendingReports).mockResolvedValue({
      items: [{ ...baseReport, signatureValid: true } as unknown as typeof baseReport],
      total: 1,
      page: 0,
      size: 10,
      totalPages: 1,
    })

    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      openReviewDialog: (row: typeof baseReport) => void
      rulePrecheck: { status: string; statusText: string; findings: string[] }
    }
    vm.openReviewDialog({ ...baseReport, signatureValid: true } as unknown as typeof baseReport)
    await flushPromises()

    const card = wrapper.find('[data-testid="rule-precheck"]')
    expect(card.exists()).toBe(true)
    // i18n 在测试里被 mock 成返回 key：标题必须是 rulePrecheckTitle 而不是任何 AI 文案
    expect(card.text()).toContain('auditList.rulePrecheckTitle')
    expect(card.text()).not.toContain('aiPreAuditTitle')
    expect(vm.rulePrecheck.status).toBe('CHECK_PASS')
    expect(vm.rulePrecheck.findings).toHaveLength(3)

    wrapper.unmount()
  })

  it('submits reviews and refreshes pending data plus statistics', async () => {
    vi.mocked(getPendingReports).mockResolvedValue({
      items: [baseReport],
      total: 1,
      page: 0,
      size: 10,
      totalPages: 1,
    })

    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      openReviewDialog: (row: typeof baseReport) => void
      reviewForm: { reportId: number | null; approved: boolean; comment: string }
      submitReview: () => Promise<void>
    }

    vm.openReviewDialog(baseReport)
    vm.reviewForm.comment = 'Looks good'
    await vm.submitReview()
    await flushPromises()

    expect(reviewReport).toHaveBeenCalledWith({
      reportId: 10,
      approved: true,
      comment: 'Looks good',
    })
    expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('auditList.approveSuccess'))
    expect(getPendingReports).toHaveBeenCalledTimes(2)
    expect(getStatistics).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
