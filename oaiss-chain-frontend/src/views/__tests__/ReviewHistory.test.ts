import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: ref('zh-CN'),
  }),
}))

vi.mock('../../api/reviewer', () => ({
  getReviewHistory: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 0, size: 10, totalPages: 0 })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import ReviewHistory from '../auditor/ReviewHistory.vue'
import { getReviewHistory } from '../../api/reviewer'
import { ElMessage } from 'element-plus'

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
}

function mountComponent() {
  return mount(ReviewHistory, {
    global: {
      stubs,
    },
  })
}

describe('ReviewHistory.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads reviewer history on mount with backend paging params', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getReviewHistory).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    wrapper.unmount()
  })

  it('normalizes enterprise name and review result from backend rows', async () => {
    vi.mocked(getReviewHistory).mockResolvedValueOnce({
      items: [
        {
          id: 1,
          reportNo: 'RPT-001',
          enterpriseId: 9,
          enterpriseName: '',
          accountingPeriod: '2026-Q1',
          title: 'Quarterly Report',
          reportType: 1,
          emissionData: '{}',
          totalEmission: 18,
          scope1Emission: 6,
          scope2Emission: 7,
          scope3Emission: 5,
          calculationMethod: 'GB/T',
          status: 5,
          statusText: 'On Chain',
          reviewerId: 12,
          reviewerName: 'Auditor',
          reviewComment: 'approved',
          reviewedAt: '2026-05-31 10:00:00',
          signatureData: '',
          blockchainTxHash: '',
          onChainAt: '',
          attachments: '',
          createdAt: '2026-05-31 09:00:00',
          updatedAt: '2026-05-31 10:00:00',
        },
      ],
      total: 9,
      page: 0,
      size: 10,
      totalPages: 1,
    })

    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      tableData: Array<{ enterpriseName: string; reviewResult?: number }>
      total: number
    }

    expect(vm.tableData[0]).toMatchObject({
      enterpriseName: '-',
      reviewResult: 3,
    })
    expect(vm.total).toBe(9)
    wrapper.unmount()
  })

  it('requests the selected page when pagination changes', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await (wrapper.vm as unknown as { onCurrentChange: (page: number) => void }).onCurrentChange(3)
    await flushPromises()

    expect(getReviewHistory).toHaveBeenNthCalledWith(2, { pageNum: 3, pageSize: 10 })
    wrapper.unmount()
  })

  it('shows translated error when loading fails', async () => {
    vi.mocked(getReviewHistory).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('reviewHistory.loadFailed')
    wrapper.unmount()
  })
})
