import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('../../api/carbon', () => ({
  getReportList: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  reviewReport: vi.fn(() => Promise.resolve()),
  certifyReport: vi.fn(() => Promise.resolve()),
}))

vi.mock('../../api/blockchain', () => ({
  getStatus: vi.fn(() => Promise.resolve({ status: 'Normal' })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

vi.mock('../../components/PageContainer.vue', () => ({
  default: {
    template: '<div class="page-container"><slot /></div>',
    props: ['title', 'description'],
  },
}))

import VerifyList from '../admin/VerifyList.vue'
import { certifyReport, getReportList } from '../../api/carbon'
import { getStatus } from '../../api/blockchain'
import { ElMessage, ElMessageBox } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-statistic': { template: '<div class="el-statistic"></div>', props: ['title', 'value'] },
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
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
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
  'el-select': {
    template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
    props: ['modelValue', 'placeholder', 'style', 'clearable'],
    emits: ['update:modelValue'],
  },
  'el-option': {
    template: '<option :value="value"><slot /></option>',
    props: ['label', 'value'],
  },
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label', 'span'] },
}

function mountVerifyList() {
  return mount(VerifyList, {
    global: {
      stubs,
    },
    attachTo: document.body,
  })
}

async function flush() {
  await nextTick()
  await nextTick()
}

const baseReport = {
  id: 1,
  reportNo: 'RPT-001',
  enterpriseId: 5,
  enterpriseName: 'Demo',
  accountingPeriod: '2026-Q1',
  title: 'Quarterly Report',
  reportType: 1,
  emissionData: '{}',
  totalEmission: 100,
  scope1Emission: 20,
  scope2Emission: 40,
  scope3Emission: 40,
  calculationMethod: 'GB/T',
  status: 3,
  statusText: '',
  reviewerId: 8,
  reviewerName: 'Auditor',
  reviewComment: '',
  reviewedAt: '',
  signatureData: '',
  blockchainTxHash: '',
  onChainAt: '',
  attachments: '',
  createdAt: '2026-05-31 08:00:00',
  updatedAt: '2026-05-31 08:30:00',
}

describe('VerifyList.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders page container and section cards', async () => {
    const wrapper = mountVerifyList()
    await flush()
    expect(wrapper.find('.page-container').exists()).toBe(true)
    expect(wrapper.find('.section-card').exists()).toBe(true)
    wrapper.unmount()
  })

  it('loads report list and blockchain status on mount with backend paging params', async () => {
    const wrapper = mountVerifyList()
    await flush()

    expect(getReportList).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      keyword: undefined,
      status: undefined,
    })
    expect(getStatus).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('shows translated load error when report query fails', async () => {
    vi.mocked(getReportList).mockRejectedValueOnce(new Error('network'))
    const wrapper = mountVerifyList()
    await flush()
    expect(ElMessage.error).toHaveBeenCalledWith('verifyList.loadFailed')
    wrapper.unmount()
  })

  it('recomputes stats from backend rows and keeps backend total authoritative', async () => {
    vi.mocked(getReportList).mockResolvedValueOnce({
      items: [
        baseReport,
        { ...baseReport, id: 2, reportNo: 'RPT-002', status: 5 },
        { ...baseReport, id: 3, reportNo: 'RPT-003', status: 4 },
      ],
      total: 27,
      page: 1,
      size: 10,
      totalPages: 3,
    })

    const wrapper = mountVerifyList()
    await flush()

    const vm = wrapper.vm as unknown as {
      stats: { pending: number; approved: number; rejected: number }
      total: number
      blockchainHealthy: boolean
    }

    expect(vm.stats).toEqual({ pending: 1, approved: 1, rejected: 1 })
    expect(vm.total).toBe(27)
    expect(vm.blockchainHealthy).toBe(true)
    wrapper.unmount()
  })

  it('forwards search keyword and status filter to the backend', async () => {
    const wrapper = mountVerifyList()
    await flush()

    const vm = wrapper.vm as unknown as {
      keyword: string
      statusFilter: number | ''
      loadReports: () => Promise<void>
    }

    vm.keyword = 'cement'
    vm.statusFilter = 5
    await vm.loadReports()
    await flush()

    expect(getReportList).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      keyword: 'cement',
      status: 5,
    })
    wrapper.unmount()
  })

  it('submits certification approval and refreshes the backend list', async () => {
    vi.mocked(getReportList).mockResolvedValue({
      items: [baseReport],
      total: 1,
    } as never)

    const wrapper = mountVerifyList()
    await flush()

    const vm = wrapper.vm as unknown as {
      onVerify: (row: typeof baseReport, approved: boolean) => Promise<void>
    }

    await vm.onVerify(baseReport, true)
    await flush()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(certifyReport).toHaveBeenCalledWith({
      reportId: 1,
      approved: true,
      comment: 'verifyList.approveSuccess',
    })
    expect(ElMessage.success).toHaveBeenCalledWith('verifyList.btnApprove')
    expect(getReportList).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('suppresses errors for user-cancelled certification dialogs', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

    const wrapper = mountVerifyList()
    await flush()

    const vm = wrapper.vm as unknown as {
      onVerify: (row: typeof baseReport, approved: boolean) => Promise<void>
    }

    await vm.onVerify(baseReport, false)
    await flush()

    expect(certifyReport).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalledWith(expect.stringContaining('verifyList.operationFailed'))
    wrapper.unmount()
  })

  it('shows backend error details when certification fails', async () => {
    vi.mocked(certifyReport).mockRejectedValueOnce(new Error('request failed'))

    const wrapper = mountVerifyList()
    await flush()

    const vm = wrapper.vm as unknown as {
      onVerify: (row: typeof baseReport, approved: boolean) => Promise<void>
    }

    await vm.onVerify(baseReport, false)
    await flush()

    expect(ElMessage.error).toHaveBeenCalledWith('verifyList.operationFailed: request failed')
    wrapper.unmount()
  })
})
