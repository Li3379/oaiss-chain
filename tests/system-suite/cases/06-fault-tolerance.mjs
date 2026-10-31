/**
 * 测试集 06: 异常容错与鲁棒性测试 (Fault Tolerance & Robustness) - 14 Cases
 * 覆盖畸形输入容错、SQL注入/XSS探针净化、资源穿透与大字段防护
 */
export const faultToleranceCases = [
  {
    id: 'TC-ROB-001',
    dimension: '异常容错鲁棒性',
    title: '畸形输入容错-非法JSON格式请求被统一错误信封捕获',
    method: 'POST',
    endpoint: '/carbon/reports',
    role: 'enterprise001',
    input: '{ bad_json: "missing quote }',
    headers: { 'Content-Type': 'application/json' },
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-002',
    dimension: '异常容错鲁棒性',
    title: '数据穿透防护-查询不存在的碳报告返回业务标准错误',
    method: 'GET',
    endpoint: '/carbon/reports/999999999',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400,
    expectedCode: 3000
  },
  {
    id: 'TC-ROB-003',
    dimension: '异常容错鲁棒性',
    title: '数据穿透防护-查询不存在的用户信息安全拦截',
    method: 'GET',
    endpoint: '/user/999999999',
    role: 'admin',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-004',
    dimension: '异常容错鲁棒性',
    title: 'SQL注入探针-在全文搜索框注入经典永真式被安全净化',
    method: 'GET',
    endpoint: "/search/reports?keyword=' OR 1=1--",
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    validate: (res) => res.data?.code === 200 ? true : 'SQL注入探针引发了未捕获异常'
  },
  {
    id: 'TC-ROB-005',
    dimension: '异常容错鲁棒性',
    title: 'SQL注入探针-在用户名字段注入恶意SQL被安全过滤',
    method: 'POST',
    endpoint: '/auth/login',
    role: 'anonymous',
    input: { username: "admin' OR '1'='1", password: 'password' },
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-006',
    dimension: '异常容错鲁棒性',
    title: 'XSS脚本探针-在报告标题中注入script标签被转义存储',
    method: 'POST',
    endpoint: '/carbon/reports',
    role: 'enterprise001',
    input: {
      accountingPeriod: '2026-Q3',
      title: '<script>alert("XSS")</script>',
      reportType: 1,
      emissionData: '{"scope1":10.0}',
      totalEmission: 10.0
    },
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-007',
    dimension: '异常容错鲁棒性',
    title: '大字段溢出防护-超长标题输入被校验长度拦截',
    method: 'POST',
    endpoint: '/carbon/reports',
    role: 'enterprise001',
    input: {
      accountingPeriod: '2026-Q3',
      title: 'A'.repeat(500), // 超过 200 字符限制
      reportType: 1,
      emissionData: '{"scope1":10.0}'
    },
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-008',
    dimension: '异常容错鲁棒性',
    title: '空请求体容错-POST提交空对象安全报错',
    method: 'POST',
    endpoint: '/carbon-coin/recharge?userId=2',
    role: 'admin',
    input: {},
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-009',
    dimension: '异常容错鲁棒性',
    title: '特殊控制字符-在备注中输入换行与制表符正常兼容',
    method: 'POST',
    endpoint: '/carbon-coin/recharge?userId=2',
    role: 'admin',
    input: { amount: 1.00, remark: 'Line1\nLine2\tTabbed\rSpecial' },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-ROB-010',
    dimension: '异常容错鲁棒性',
    title: '状态机异常参数-提交无效空周期被严格拦截',
    method: 'POST',
    endpoint: '/carbon/reports',
    role: 'enterprise001',
    input: {
      accountingPeriod: '', // 空核算周期
      title: '非法参数报告',
      reportType: 1,
      emissionData: '{"scope1":10.0}'
    },
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-011',
    dimension: '异常容错鲁棒性',
    title: '区块链区块查询-查询不存在的超大区块号返回结构化错误',
    method: 'GET',
    endpoint: '/blockchain/block/999999999',
    role: 'enterprise001',
    input: null,
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-012',
    dimension: '异常容错鲁棒性',
    title: '链上快照查询-查询不存在的报告返回空快照而非报错',
    method: 'GET',
    endpoint: '/blockchain/report/999999999',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200
  },
  {
    id: 'TC-ROB-013',
    dimension: '异常容错鲁棒性',
    title: '参数类型错配-向数字字段传入字符串被Spring优雅拒绝',
    method: 'POST',
    endpoint: '/carbon-coin/recharge?userId=2',
    role: 'admin',
    input: { amount: 'NOT_A_NUMBER' },
    expectedStatus: 400
  },
  {
    id: 'TC-ROB-014',
    dimension: '异常容错鲁棒性',
    title: '健康检查接口-高频探测服务存活状态响应正常',
    method: 'GET',
    endpoint: '/actuator/health',
    role: 'anonymous',
    input: null,
    expectedStatus: 200
  }
];
