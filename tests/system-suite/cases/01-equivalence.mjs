/**
 * 测试集 01: 功能等价类划分测试 (Equivalence Partitioning) - 20 Cases
 * 覆盖有效输入等价类与无效输入等价类
 */
export const equivalenceCases = [
  {
    id: 'TC-EQ-001',
    dimension: '功能等价类',
    title: '有效类-企业用户凭据正确登录签发有效Token',
    method: 'POST',
    endpoint: '/auth/login',
    role: 'anonymous',
    input: { username: 'enterprise001', password: 'admin123' },
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => (res.data?.data?.accessToken || res.data?.data?.token) ? true : '未返回有效 JWT Token'
  },
  {
    id: 'TC-EQ-002',
    dimension: '功能等价类',
    title: '无效类-错误密码登录被安全拦截',
    method: 'POST',
    endpoint: '/auth/login',
    role: 'anonymous',
    input: { username: 'enterprise001', password: 'wrong-password-999' },
    expectedStatus: 400,
    expectedCode: 2001
  },
  {
    id: 'TC-EQ-003',
    dimension: '功能等价类',
    title: '无效类-空用户名提交登录被参数校验拦截',
    method: 'POST',
    endpoint: '/auth/login',
    role: 'anonymous',
    input: { username: '', password: 'admin123' },
    expectedStatus: 400
  },
  {
    id: 'TC-EQ-004',
    dimension: '功能等价类',
    title: '有效类-审核员角色合法凭据登录',
    method: 'POST',
    endpoint: '/auth/login',
    role: 'anonymous',
    input: { username: 'reviewer001', password: 'admin123' },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-005',
    dimension: '功能等价类',
    title: '有效类-系统管理员角色合法凭据登录',
    method: 'POST',
    endpoint: '/auth/login',
    role: 'anonymous',
    input: { username: 'admin', password: 'admin123' },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-006',
    dimension: '功能等价类',
    title: '有效类-发电企业25参数国标模型测算',
    method: 'POST',
    endpoint: '/carbon/calculate/power-generation',
    role: 'enterprise001',
    input: {
      enterpriseName: '火电一厂',
      reportingYear: 2026,
      rawCoalFc: 1000,
      rawCoalNcv: 26700,
      rawCoalCc: 27.4,
      rawCoalOf: 0.98,
      carbonateConsumed: 50,
      desulfEmissionFactor: 0.44,
      desulfConversionRate: 0.90
    },
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => res.data?.data?.totalEmission > 0 ? true : '未计算出有效排放量'
  },
  {
    id: 'TC-EQ-007',
    dimension: '功能等价类',
    title: '无效类-发电模型计算缺少燃料参数被拦截',
    method: 'POST',
    endpoint: '/carbon/calculate/power-generation',
    role: 'enterprise001',
    input: { enterpriseName: '火电测试厂' }, // 缺少必填参数
    expectedStatus: 400
  },
  {
    id: 'TC-EQ-008',
    dimension: '功能等价类',
    title: '有效类-电网企业9参数国标模型测算',
    method: 'POST',
    endpoint: '/carbon/calculate/power-grid',
    role: 'enterprise001',
    input: {
      enterpriseName: '电网一厂',
      reportingYear: 2026,
      transmissionVolume: 50000,
      lineLossRate: 0.05,
      gridEmissionFactor: 0.5810
    },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-009',
    dimension: '功能等价类',
    title: '有效类-燃煤碳氧化率实测反推计算',
    method: 'POST',
    endpoint: '/carbon/calculate/power-generation/oxidation-rate',
    role: 'enterprise001',
    input: {
      slagWeight: 15.0,
      slagCarbonContent: 0.02,
      flyAshWeight: 85.0,
      flyAshCarbonContent: 0.03,
      coalConsumption: 1000.0,
      netCalorificValue: 20000.0,
      carbonContent: 25.0
    },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-010',
    dimension: '功能等价类',
    title: '有效类-企业正常查询自身碳币账户资产',
    method: 'GET',
    endpoint: '/carbon-coin/account',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => res.data?.data?.balance !== undefined ? true : '未返回balance字段'
  },
  {
    id: 'TC-EQ-011',
    dimension: '功能等价类',
    title: '有效类-企业获取自身已登记的公钥凭据',
    method: 'GET',
    endpoint: '/signature/keypair',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-012',
    dimension: '功能等价类',
    title: '有效类-企业查询自身信用评分与等级',
    method: 'GET',
    endpoint: '/credit/my-score',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-013',
    dimension: '功能等价类',
    title: '有效类-企业正常提交碳币充值',
    method: 'POST',
    endpoint: '/carbon-coin/recharge?userId=2',
    role: 'admin',
    input: { amount: 500.00, remark: '系统测试有效充值' },
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-014',
    dimension: '功能等价类',
    title: '无效类-向不存在的目标用户转账碳币被拦截',
    method: 'POST',
    endpoint: '/carbon-coin/transfer',
    role: 'enterprise001',
    input: { counterpartId: 99999999, amount: 10.00, remark: '转账给不存在的用户' },
    expectedStatus: 400,
    expectedCode: 4003
  },
  {
    id: 'TC-EQ-015',
    dimension: '功能等价类',
    title: '有效类-审核员拉取待审核碳核算报告任务队列',
    method: 'GET',
    endpoint: '/reviewer/reports/pending?page=1&size=10',
    role: 'reviewer001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-016',
    dimension: '功能等价类',
    title: '有效类-第三方监管拉取公信力宏观监控大屏数据',
    method: 'GET',
    endpoint: '/third-party/statistics',
    role: 'thirdparty001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-017',
    dimension: '功能等价类',
    title: '有效类-管理员分页拉取全局审计日志列表',
    method: 'GET',
    endpoint: '/admin/statistics',
    role: 'admin',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-018',
    dimension: '功能等价类',
    title: '有效类-查询联盟链浏览器最新区块列表',
    method: 'GET',
    endpoint: '/blockchain/blocks/latest?page=1&size=5',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-019',
    dimension: '功能等价类',
    title: '有效类-公开大厅检索碳中和项目列表',
    method: 'GET',
    endpoint: '/carbon-neutral/projects?page=1&size=10',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-EQ-020',
    dimension: '功能等价类',
    title: '有效类-获取当前在线用户会话停留时长统计',
    method: 'GET',
    endpoint: '/user/stay-time',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  }
];
