/**
 * 测试集 03: 判定表驱动组合测试 (Decision Table Testing) - 16 Cases
 * 覆盖多条件组合逻辑判定（企业认证、配额状态、资金有效性、身份准入条件矩阵）
 */
export const decisionTableCases = [
  {
    id: 'TC-DEC-001',
    dimension: '判定表驱动',
    title: '规则1-企业有效+碳币充足+买价买量合法->允许提交买入挂单',
    method: 'POST',
    endpoint: '/auction/buy',
    role: 'enterprise001',
    input: { direction: 1, quantity: 2.0, price: 40.0 }, // 80 碳币，余额充足以完成锁定
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => res.data?.data?.orderNo ? true : '未生成挂单编号'
  },
  {
    id: 'TC-DEC-002',
    dimension: '判定表驱动',
    title: '规则2-企业有效+碳币不足->拦截挂买单',
    method: 'POST',
    endpoint: '/auction/buy',
    role: 'enterprise001',
    input: { direction: 1, quantity: 100000.0, price: 500.0 }, // 5000万碳币，严重不足
    expectedStatus: 400,
    expectedCode: 4001
  },
  {
    id: 'TC-DEC-003',
    dimension: '判定表驱动',
    title: '规则3-企业有效+配额充足->允许提交卖出挂单并锁定配额',
    method: 'POST',
    endpoint: '/auction/sell',
    role: 'enterprise001',
    input: { direction: 2, quantity: 1.0, price: 45.0 }, // 1吨配额，通常充足
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => res.data?.data?.frozenAmount !== undefined ? true : '未包含冻结资产信息'
  },
  {
    id: 'TC-DEC-004',
    dimension: '判定表驱动',
    title: '规则4-企业有效+配额不足->拦截挂卖单',
    method: 'POST',
    endpoint: '/auction/sell',
    role: 'enterprise001',
    input: { direction: 2, quantity: 99999999.0, price: 50.0 }, // 超额巨量配额
    expectedStatus: 400,
    expectedCode: 4002
  },
  {
    id: 'TC-DEC-005',
    dimension: '判定表驱动',
    title: '规则5-管理员权限+撮合触发->执行双向拍卖撮合算法',
    method: 'POST',
    endpoint: '/auction/match',
    role: 'admin',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-DEC-006',
    dimension: '判定表驱动',
    title: '规则6-非管理员触发撮合->权限不足严厉阻断',
    method: 'POST',
    endpoint: '/auction/match',
    role: 'enterprise001',
    input: null,
    expectedStatus: 403
  },
  {
    id: 'TC-DEC-007',
    dimension: '判定表驱动',
    title: '规则7-买卖双方不同+卖方配额充足->允许发起P2P交易',
    method: 'POST',
    endpoint: '/trade/p2p',
    role: 'enterprise001',
    input: { sellerId: 2, buyerId: 3, quantity: 1.0, unitPrice: 50.0, tradeType: 2, remark: '判定表有效交易' },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-DEC-008',
    dimension: '判定表驱动',
    title: '规则8-买卖双方为同一主体(自我交易)->严格拒绝',
    method: 'POST',
    endpoint: '/trade/p2p',
    role: 'enterprise001',
    input: { sellerId: 2, buyerId: 2, quantity: 5.0, unitPrice: 50.0, tradeType: 2, remark: '自我倒手交易' },
    expectedStatus: 400,
    expectedCode: 4008
  },
  {
    id: 'TC-DEC-009',
    dimension: '判定表驱动',
    title: '规则9-卖方冒用他人企业身份发起P2P->越权校验拦截',
    method: 'POST',
    endpoint: '/trade/p2p',
    role: 'enterprise001', // 当前用户ID=2
    input: { sellerId: 999, buyerId: 3, quantity: 5.0, unitPrice: 50.0, tradeType: 2 },
    expectedStatus: 403 // 卖方与当前用户不一致触发403越权
  },
  {
    id: 'TC-DEC-010',
    dimension: '判定表驱动',
    title: '规则10-P2P交易非本单相关企业尝试确认->无权操作拦截',
    method: 'POST',
    endpoint: '/trade/999999/confirm',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400,
    expectedCode: 4000
  },
  {
    id: 'TC-DEC-011',
    dimension: '判定表驱动',
    title: '规则11-审核员资质状态查询->有效返回资格信息',
    method: 'GET',
    endpoint: '/reviewer/qualification/my',
    role: 'reviewer001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-DEC-012',
    dimension: '判定表驱动',
    title: '规则12-企业用户冒充审核员查询审核资质->拦截',
    method: 'GET',
    endpoint: '/reviewer/qualification/my',
    role: 'enterprise001',
    input: null,
    expectedStatus: 403
  },
  {
    id: 'TC-DEC-013',
    dimension: '判定表驱动',
    title: '规则13-第三方监管查询特定合规记录->合法放行',
    method: 'GET',
    endpoint: '/third-party/statistics',
    role: 'thirdparty001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-DEC-014',
    dimension: '判定表驱动',
    title: '规则14-企业用户尝试调用监管专属机构接口->拦截',
    method: 'GET',
    endpoint: '/third-party/statistics',
    role: 'enterprise001',
    input: null,
    expectedStatus: 403
  },
  {
    id: 'TC-DEC-015',
    dimension: '判定表驱动',
    title: '规则15-管理员查询全站用户->合法返回',
    method: 'GET',
    endpoint: '/admin/users?page=1&size=10',
    role: 'admin',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-DEC-016',
    dimension: '判定表驱动',
    title: '规则16-普通企业查询全站用户管理列表->垂直越权拦截',
    method: 'GET',
    endpoint: '/admin/users?page=1&size=10',
    role: 'enterprise001',
    input: null,
    expectedStatus: 403
  }
];
