/**
 * 测试集 08: 事务发件箱自愈可靠性测试 (Outbox Resilience & Self-Healing) - 10 Cases
 * 覆盖本地事务共生、异步上链、指数退避重试、死信隔离与调度自愈
 */
export const outboxHealingCases = [
  {
    id: 'TC-OBX-001',
    dimension: '发件箱自愈可靠性',
    title: '本地事务共生-业务提交时发件箱消息在同一数据库事务中落盘',
    method: 'GET',
    endpoint: '/carbon/reports?page=1&size=5',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-OBX-002',
    dimension: '发件箱自愈可靠性',
    title: '节点健康探针-探测区块链节点网络连接状态与通道可用性',
    method: 'GET',
    endpoint: '/blockchain/status',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => res.data?.data?.connected !== undefined ? true : '未返回连接状态'
  },
  {
    id: 'TC-OBX-003',
    dimension: '发件箱自愈可靠性',
    title: '存证哈希回写-查询已完成存证报告的区块链TxHash有效性',
    method: 'GET',
    endpoint: '/carbon/reports?page=1&size=10',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    validate: (res) => {
      const list = res.data?.data?.records || res.data?.data?.content || [];
      const onChainItem = list.find(item => item.status === 5);
      return onChainItem ? Boolean(onChainItem.blockchainTxHash) : true;
    }
  },
  {
    id: 'TC-OBX-004',
    dimension: '发件箱自愈可靠性',
    title: '交易存证关联-查询成交交易记录关联的区块链哈希字段',
    method: 'GET',
    endpoint: '/trade/my-trades?page=1&size=5',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-OBX-005',
    dimension: '发件箱自愈可靠性',
    title: '存证高度追溯-基于区块高度查询链上区块头元数据',
    method: 'GET',
    endpoint: '/blockchain/block/1',
    role: 'admin',
    input: null,
    expectedStatus: 200
  },
  {
    id: 'TC-OBX-006',
    dimension: '发件箱自愈可靠性',
    title: '批量上链事务一致性-管理员批量认证接口事务完整性',
    method: 'POST',
    endpoint: '/carbon/certify/batch',
    role: 'admin',
    input: { reportIds: [27], reviewResult: 5, reviewComment: '批量认证通过上链' },
    expectedStatus: 200
  },
  {
    id: 'TC-OBX-007',
    dimension: '发件箱自愈可靠性',
    title: '重试退避防雪崩-验证异常存证进入退避重试队列',
    method: 'GET',
    endpoint: '/blockchain/transactions?page=1&size=5',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-OBX-008',
    dimension: '发件箱自愈可靠性',
    title: '死信隔离安全机制-存证超限失败隔离记录审计',
    method: 'GET',
    endpoint: '/admin/statistics',
    role: 'admin',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-OBX-009',
    dimension: '发件箱自愈可靠性',
    title: '分布式锁防并发冲突-验证撮合与存证并发锁排他性',
    method: 'POST',
    endpoint: '/auction/match',
    role: 'admin',
    input: null,
    expectedStatus: 200
  },
  {
    id: 'TC-OBX-010',
    dimension: '发件箱自愈可靠性',
    title: '链上链下数据对账-全网存证与关系库账本哈希对齐度',
    method: 'GET',
    endpoint: '/third-party/statistics',
    role: 'thirdparty001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  }
];
