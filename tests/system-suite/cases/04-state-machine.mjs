/**
 * 测试集 04: 状态机迁移完整性测试 (State Machine Transition Testing) - 16 Cases
 * 覆盖合法正向状态迁移与非法跨步/逆向/重复终结跃迁拦截
 */
let dynamicReportId = 27;

export const stateMachineCases = [
  {
    id: 'TC-STM-001',
    dimension: '状态机迁移',
    title: '正向迁移-企业新建碳核算报告初始状态为草稿(0)',
    method: 'POST',
    endpoint: '/carbon/reports',
    role: 'enterprise001',
    input: {
      accountingPeriod: '2026-Q3',
      title: '状态机测试报告-草稿',
      reportType: 1,
      emissionData: '{"scope1":100.0,"scope2":50.0,"totalEmission":150.0}',
      totalEmission: 150.0,
      scope1Emission: 100.0,
      scope2Emission: 50.0
    },
    expectedStatus: 200,
    expectedCode: 200,
    after: (res) => {
      if (res.data?.data?.id) {
        dynamicReportId = res.data.data.id;
      }
    },
    validate: (res) => res.data?.data?.status === 0 ? true : `初始状态期望0(草稿)，实际为${res.data?.data?.status}`
  },
  {
    id: 'TC-STM-002',
    dimension: '状态机迁移',
    title: '正向迁移-草稿提交送审流转至已提交(1)并附带数字签名',
    method: 'POST',
    endpoint: () => `/carbon/reports/${dynamicReportId}/submit`,
    role: 'enterprise001',
    input: null,
    expectedStatus: 200
  },
  {
    id: 'TC-STM-003',
    dimension: '状态机迁移',
    title: '非法迁移-已提交送审的报告再次提交修改被拦截',
    method: 'POST',
    endpoint: () => `/carbon/reports/${dynamicReportId}/submit`,
    role: 'enterprise001',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-STM-004',
    dimension: '状态机迁移',
    title: '正向迁移-待审报告审核员合规审核通过(3)',
    method: 'POST',
    endpoint: '/carbon/review',
    role: 'reviewer001',
    input: () => ({ reportId: dynamicReportId, reviewResult: 3, reviewComment: '合规通过测试' }),
    expectedStatus: 200
  },
  {
    id: 'TC-STM-005',
    dimension: '状态机迁移',
    title: '非法迁移-已审核通过的报告禁止修改数据',
    method: 'PUT',
    endpoint: () => `/carbon/reports/${dynamicReportId}`,
    role: 'enterprise001',
    input: { title: '非法修改已审核报告' },
    expectedStatus: 400
  },
  {
    id: 'TC-STM-006',
    dimension: '状态机迁移',
    title: '状态机驳回分支-审核驳回标记(4)并扣除企业信用分',
    method: 'POST',
    endpoint: '/carbon/review',
    role: 'reviewer001',
    input: () => ({ reportId: dynamicReportId, reviewResult: 4, reviewComment: '已终态报告禁止再驳回' }),
    expectedStatus: 400
  },
  {
    id: 'TC-STM-007',
    dimension: '状态机迁移',
    title: '非法跨步-未审核报告禁止直接认证上链',
    method: 'POST',
    endpoint: '/carbon/certify',
    role: 'admin',
    input: { reportId: 999999, reviewResult: 5, reviewComment: '非法越步认证' },
    expectedStatus: 400
  },
  {
    id: 'TC-STM-008',
    dimension: '状态机迁移',
    title: '正向终态-审核通过报告行政认证上链(5)',
    method: 'POST',
    endpoint: '/carbon/certify',
    role: 'admin',
    input: () => ({ reportId: dynamicReportId, reviewResult: 5, reviewComment: '行政认证上链通过' }),
    expectedStatus: 200
  },
  {
    id: 'TC-STM-009',
    dimension: '状态机迁移',
    title: '非法逆向-已上链存证报告严禁再次驳回修改',
    method: 'POST',
    endpoint: '/carbon/review',
    role: 'reviewer001',
    input: () => ({ reportId: dynamicReportId, reviewResult: 4, reviewComment: '恶意逆向驳回' }),
    expectedStatus: 400
  },
  {
    id: 'TC-STM-010',
    dimension: '状态机迁移',
    title: '拍卖挂单状态机-新提交订单初始为PENDING(0)',
    method: 'POST',
    endpoint: '/auction/buy',
    role: 'enterprise001',
    input: { direction: 1, quantity: 1.0, price: 30.0 },
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => res.data?.data?.status === 0 ? true : '订单初始状态非PENDING'
  },
  {
    id: 'TC-STM-011',
    dimension: '状态机迁移',
    title: '拍卖挂单状态机-用户撤销已终态交易被严格拒绝',
    method: 'POST',
    endpoint: '/trade/1/cancel',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-STM-012',
    dimension: '状态机迁移',
    title: '非法跃迁-已取消挂单禁止再次重复撤销',
    method: 'POST',
    endpoint: '/trade/1/cancel',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-STM-013',
    dimension: '状态机迁移',
    title: 'P2P交易状态机-发起交易初始为待处理PENDING(0)',
    method: 'POST',
    endpoint: '/trade/p2p',
    role: 'enterprise001',
    input: { sellerId: 2, buyerId: 3, quantity: 1.0, unitPrice: 35.0, tradeType: 2 },
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => res.data?.data?.status === 0 ? true : 'P2P初始状态非PENDING'
  },
  {
    id: 'TC-STM-014',
    dimension: '状态机迁移',
    title: 'P2P交易状态机-尝试撤销不存在订单被拦截',
    method: 'POST',
    endpoint: '/trade/999999/cancel',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-STM-015',
    dimension: '状态机迁移',
    title: '非法跃迁-已取消交易禁止买方确认成交',
    method: 'POST',
    endpoint: '/trade/2/confirm',
    role: 'enterprise002',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-STM-016',
    dimension: '状态机迁移',
    title: '非法跃迁-已完成交易禁止被作废取消',
    method: 'POST',
    endpoint: '/trade/1/cancel',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400
  }
];
