/**
 * 测试集 02: 边界值分析测试 (Boundary Value Analysis) - 18 Cases
 * 覆盖极小值、临界值、负数越界、分页极值与状态边界
 */
export const boundaryCases = [
  {
    id: 'TC-BVA-001',
    dimension: '边界值分析',
    title: '数值下边界-充值最小法定金额0.01碳币',
    method: 'POST',
    endpoint: '/carbon-coin/recharge?userId=2',
    role: 'admin',
    input: { amount: 0.01, remark: '最小充值极值0.01' },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-BVA-002',
    dimension: '边界值分析',
    title: '数值越界拦截-充值0.00碳币被校验拒绝',
    method: 'POST',
    endpoint: '/carbon-coin/recharge?userId=2',
    role: 'admin',
    input: { amount: 0.00, remark: '零金额充值' },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-003',
    dimension: '边界值分析',
    title: '数值越界拦截-充值负数金额被校验拒绝',
    method: 'POST',
    endpoint: '/carbon-coin/recharge?userId=2',
    role: 'admin',
    input: { amount: -50.00, remark: '负数充值' },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-004',
    dimension: '边界值分析',
    title: '转账下边界-转账最小允许金额0.01碳币',
    method: 'POST',
    endpoint: '/carbon-coin/transfer',
    role: 'enterprise001',
    input: { counterpartId: 3, amount: 0.01, remark: '最小转账极值0.01' },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-BVA-005',
    dimension: '边界值分析',
    title: '转账越界拦截-转账0.00碳币被校验拦截',
    method: 'POST',
    endpoint: '/carbon-coin/transfer',
    role: 'enterprise001',
    input: { counterpartId: 3, amount: 0.00, remark: '零金额转账' },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-006',
    dimension: '边界值分析',
    title: '转账越界拦截-转账负数金额被安全拦截',
    method: 'POST',
    endpoint: '/carbon-coin/transfer',
    role: 'enterprise001',
    input: { counterpartId: 3, amount: -100.00, remark: '负数转账套现攻击' },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-007',
    dimension: '边界值分析',
    title: '资金超额边界-转账远超当前账户余额被阻断',
    method: 'POST',
    endpoint: '/carbon-coin/transfer',
    role: 'enterprise001',
    input: { counterpartId: 3, amount: 999999999.00, remark: '超额提现转账' },
    expectedStatus: 400,
    expectedCode: 4005
  },
  {
    id: 'TC-BVA-008',
    dimension: '边界值分析',
    title: '模型参数临界-脱硫转化率恰好为1.0(100%完全转化)',
    method: 'POST',
    endpoint: '/carbon/calculate/power-generation',
    role: 'enterprise001',
    input: {
      enterpriseName: '火电一厂',
      reportingYear: 2026,
      rawCoalFc: 100,
      rawCoalNcv: 26700,
      rawCoalCc: 27.4,
      rawCoalOf: 0.98,
      desulfConversionRate: 1.00 // 刚好边界值 1.0
    },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-BVA-009',
    dimension: '边界值分析',
    title: '模型参数越界-脱硫转化率大于1.0被算法拦截',
    method: 'POST',
    endpoint: '/carbon/calculate/power-generation',
    role: 'enterprise001',
    input: {
      enterpriseName: '火电一厂',
      reportingYear: 2026,
      rawCoalFc: 100,
      rawCoalNcv: 26700,
      rawCoalCc: 27.4,
      rawCoalOf: 0.98,
      desulfConversionRate: 1.01 // 超过 1.0 上限
    },
    expectedStatus: 400,
    expectedCode: 3005
  },
  {
    id: 'TC-BVA-010',
    dimension: '边界值分析',
    title: '模型参数越界-燃煤消耗量为负数被防回归校验拦截',
    method: 'POST',
    endpoint: '/carbon/calculate/power-generation',
    role: 'enterprise001',
    input: {
      enterpriseName: '火电一厂',
      reportingYear: 2026,
      rawCoalFc: -100, // 负数消耗量
      rawCoalNcv: 26700,
      rawCoalCc: 27.4,
      rawCoalOf: 0.98,
      desulfConversionRate: 0.90
    },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-011',
    dimension: '边界值分析',
    title: '分页参数边界-最小页长size=1正常返回单条记录',
    method: 'GET',
    endpoint: '/carbon/reports?page=1&size=1',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => (res.data?.data?.records?.length <= 1 || res.data?.data?.content?.length <= 1) ? true : '返回记录数超出size=1'
  },
  {
    id: 'TC-BVA-012',
    dimension: '边界值分析',
    title: '分页参数边界-常规最大页长size=100正常处理',
    method: 'GET',
    endpoint: '/carbon/reports?page=1&size=100',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-BVA-013',
    dimension: '边界值分析',
    title: '分页参数越界-超大size自动截断至安全上限',
    method: 'GET',
    endpoint: '/carbon/reports?page=1&size=10000',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => {
      const list = res.data?.data?.records || res.data?.data?.content || [];
      return list.length <= 100 ? true : '超大分页未被截断至安全阈值';
    }
  },
  {
    id: 'TC-BVA-014',
    dimension: '边界值分析',
    title: '拍卖挂单边界-挂单价格为0.00被严格阻断',
    method: 'POST',
    endpoint: '/auction/buy',
    role: 'enterprise001',
    input: { direction: 1, quantity: 10.0, price: 0.00 },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-015',
    dimension: '边界值分析',
    title: '拍卖挂单边界-挂单数量为0.00被严格阻断',
    method: 'POST',
    endpoint: '/auction/buy',
    role: 'enterprise001',
    input: { direction: 1, quantity: 0.0, price: 50.00 },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-016',
    dimension: '边界值分析',
    title: '拍卖挂单边界-挂单价格为负数被拦截',
    method: 'POST',
    endpoint: '/auction/sell',
    role: 'enterprise001',
    input: { direction: 2, quantity: 10.0, price: -50.00 },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-017',
    dimension: '边界值分析',
    title: 'P2P交易边界-单价为0.00被校验拦截',
    method: 'POST',
    endpoint: '/trade/p2p',
    role: 'enterprise001',
    input: { buyerId: 2, quantity: 10.0, unitPrice: 0.00, tradeType: 1 },
    expectedStatus: 400
  },
  {
    id: 'TC-BVA-018',
    dimension: '边界值分析',
    title: 'P2P交易边界-配额数量为0.00被校验拦截',
    method: 'POST',
    endpoint: '/trade/p2p',
    role: 'enterprise001',
    input: { buyerId: 2, quantity: 0.00, unitPrice: 50.00, tradeType: 1 },
    expectedStatus: 400
  }
];
