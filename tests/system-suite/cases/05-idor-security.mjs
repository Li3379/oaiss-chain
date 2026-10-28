/**
 * 测试集 05: 安全IDOR水平越权与垂直权限渗透测试 (Security & Access Control) - 16 Cases
 * 覆盖水平越权(IDOR)、垂直越权(RBAC)、未授权访问及特权安全自毁防御
 */
export const idorSecurityCases = [
  {
    id: 'TC-SEC-001',
    dimension: '安全与防越权',
    title: '未授权渗透-匿名缺失Token访问企业核心报告被拦截',
    method: 'GET',
    endpoint: '/carbon/reports?page=1&size=10',
    role: 'anonymous',
    input: null,
    expectedStatus: 401
  },
  {
    id: 'TC-SEC-002',
    dimension: '安全与防越权',
    title: '未授权渗透-篡改伪造伪JWT被鉴权过滤器拦截',
    method: 'GET',
    endpoint: '/carbon/reports?page=1&size=10',
    role: 'anonymous',
    input: null,
    headers: { 'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.fake_signature' },
    expectedStatus: 401
  },
  {
    id: 'TC-SEC-003',
    dimension: '安全与防越权',
    title: '水平越权IDOR-企业查询自身账户放行/尝试穿透他人被拒',
    method: 'GET',
    endpoint: '/carbon-coin/account',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-SEC-004',
    dimension: '安全与防越权',
    title: '水平越权IDOR-企业1尝试确认归属于企业2的交易被阻断',
    method: 'POST',
    endpoint: '/trade/9999/confirm',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-SEC-005',
    dimension: '安全与防越权',
    title: '水平越权IDOR-企业1尝试撤销归属于企业2的拍卖单被阻断',
    method: 'POST',
    endpoint: '/trade/9999/cancel',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-SEC-006',
    dimension: '安全与防越权',
    title: '垂直越权RBAC-普通企业尝试访问用户管理接口被阻断',
    method: 'GET',
    endpoint: '/admin/users?page=1&size=10',
    role: 'enterprise001',
    input: null,
    expectedStatus: 403
  },
  {
    id: 'TC-SEC-007',
    dimension: '安全与防越权',
    title: '垂直越权RBAC-普通企业尝试拉取系统全局审计日志被阻断',
    method: 'GET',
    endpoint: '/admin/audit-logs?page=1&size=10',
    role: 'enterprise001',
    input: null,
    expectedStatus: 403
  },
  {
    id: 'TC-SEC-008',
    dimension: '安全与防越权',
    title: '垂直越权RBAC-普通企业尝试获取平台系统运行参数被阻断',
    method: 'GET',
    endpoint: '/admin/config',
    role: 'enterprise001',
    input: null,
    expectedStatus: 403
  },
  {
    id: 'TC-SEC-009',
    dimension: '安全与防越权',
    title: '垂直越权RBAC-审核员尝试请求系统准入审批被阻断',
    method: 'GET',
    endpoint: '/admin/enterprise-admissions?page=1&size=10',
    role: 'reviewer001',
    input: null,
    expectedStatus: 403
  },
  {
    id: 'TC-SEC-010',
    dimension: '安全与防越权',
    title: '垂直越权RBAC-普通企业尝试调用审核员复核接口被阻断',
    method: 'POST',
    endpoint: '/reviewer/review',
    role: 'enterprise001',
    input: { reportId: 1, approved: true },
    expectedStatus: 403
  },
  {
    id: 'TC-SEC-011',
    dimension: '安全与防越权',
    title: '垂直越权RBAC-审核员无权伪造企业身份挂牌P2P交易',
    method: 'POST',
    endpoint: '/trade/p2p',
    role: 'reviewer001',
    input: { buyerId: 2, quantity: 10.0, unitPrice: 50.0, tradeType: 1 },
    expectedStatus: 403
  },
  {
    id: 'TC-SEC-012',
    dimension: '安全与防越权',
    title: '水平越权IDOR-企业2尝试解密企业1私有数据被阻断',
    method: 'POST',
    endpoint: '/signature/decrypt/enterprise',
    role: 'enterprise002',
    input: 'some-encrypted-data-for-ent1',
    expectedStatus: 400
  },
  {
    id: 'TC-SEC-013',
    dimension: '安全与防越权',
    title: '未授权渗透-匿名调用私钥生成端点被安全拦截',
    method: 'POST',
    endpoint: '/signature/keypair/generate',
    role: 'anonymous',
    input: null,
    expectedStatus: 401
  },
  {
    id: 'TC-SEC-014',
    dimension: '安全与防越权',
    title: '垂直越权RBAC-普通企业尝试触发全网批量认证上链被阻断',
    method: 'POST',
    endpoint: '/carbon/certify/batch',
    role: 'enterprise001',
    input: { reportIds: [1, 2, 3] },
    expectedStatus: 403
  },
  {
    id: 'TC-SEC-015',
    dimension: '安全与防越权',
    title: '行级租户隔离-企业2尝试访问不存在或属于企业1的草稿',
    method: 'GET',
    endpoint: '/carbon/reports/999999',
    role: 'enterprise002',
    input: null,
    expectedStatus: 400,
    expectedCode: 3000
  },
  {
    id: 'TC-SEC-016',
    dimension: '安全与防越权',
    title: '特权自毁防护-管理员账号严禁禁用自身登录权限',
    method: 'POST',
    endpoint: '/admin/users/1/status', // admin自身ID
    role: 'admin',
    input: { status: 0 },
    expectedStatus: 400
  }
];
