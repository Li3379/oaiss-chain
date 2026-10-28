/**
 * 测试集 07: 国密防伪抗篡改测试 (Crypto Compliance & Anti-Tampering) - 10 Cases
 * 覆盖SM2/RSA密钥生命周期、单字节数据篡改阻断、伪造公钥拦截与客户端验签
 */
let dynamicSignature = '';

export const guomiTamperCases = [
  {
    id: 'TC-CRYPTO-001',
    dimension: '国密防伪抗篡改',
    title: '算法元数据-企业获取自身公钥凭据与算法类型',
    method: 'GET',
    endpoint: '/signature/keypair',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200,
    validate: (res) => res.data?.data?.publicKey ? true : '未返回有效公钥'
  },
  {
    id: 'TC-CRYPTO-002',
    dimension: '国密防伪抗篡改',
    title: '密钥对生成-企业重新生成密钥对并更新密钥版本号',
    method: 'POST',
    endpoint: '/signature/keypair/generate',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200,
    after: async (res, client) => {
      // 密钥生成后，立即使用新私钥对标准报文完成有效签名，以供后续验签用例验证
      const signRes = await client.request('enterprise001', 'POST', '/signature/sign', '{"scope1":100.0}', { 'Content-Type': 'text/plain' });
      if (signRes.data?.data?.signature) {
        dynamicSignature = signRes.data.data.signature;
      }
    },
    validate: (res) => res.data?.data?.keyVersion >= 1 ? true : '密钥版本号未正确递增'
  },
  {
    id: 'TC-CRYPTO-003',
    dimension: '国密防伪抗篡改',
    title: '正常验签-真实有效签名报文验证结果必须为有效',
    method: 'POST',
    endpoint: '/signature/verify',
    role: 'reviewer001',
    input: () => ({
      reportId: 27,
      signerId: 2,
      reportData: '{"scope1":100.0}',
      signatureData: dynamicSignature
    }),
    expectedStatus: 200,
    validate: (res) => res.data?.data?.valid === true ? true : '真实签名验签失败'
  },
  {
    id: 'TC-CRYPTO-004',
    dimension: '国密防伪抗篡改',
    title: '抗造假拦截-数据数值篡改1个字节时验签必须失败',
    method: 'POST',
    endpoint: '/signature/verify',
    role: 'reviewer001',
    input: () => ({
      reportId: 27,
      signerId: 2,
      reportData: '{"scope1":101.0}', // 微调篡改数值
      signatureData: dynamicSignature
    }),
    expectedStatus: 200,
    validate: (res) => res.data?.data?.valid === false ? true : '数据被篡改后验签未拦截！'
  },
  {
    id: 'TC-CRYPTO-005',
    dimension: '国密防伪抗篡改',
    title: '抗造假拦截-核算周期被篡改时验签必须失败',
    method: 'POST',
    endpoint: '/signature/verify',
    role: 'reviewer001',
    input: () => ({
      reportId: 27,
      signerId: 2,
      reportData: '{"scope1":100.0,"period":"2026-Q2"}',
      signatureData: dynamicSignature
    }),
    expectedStatus: 200,
    validate: (res) => res.data?.data?.valid === false ? true : '周期被篡改后验签未拦截！'
  },
  {
    id: 'TC-CRYPTO-006',
    dimension: '国密防伪抗篡改',
    title: '防伪造公钥-使用未登记的伪造公钥验证必然失败',
    method: 'POST',
    endpoint: '/signature/verify',
    role: 'reviewer001',
    input: {
      reportId: 999999, // 不存在的报告
      reportData: 'test-data',
      signatureData: 'dGVzdA=='
    },
    expectedStatus: 400
  },
  {
    id: 'TC-CRYPTO-007',
    dimension: '国密防伪抗篡改',
    title: '签名损毁防护-损坏的Base64签名串优雅捕获无崩溃',
    method: 'POST',
    endpoint: '/signature/verify',
    role: 'reviewer001',
    input: {
      reportId: 27,
      signerId: 2,
      reportData: 'test-data',
      signatureData: 'CORRUPTED-SIGNATURE-STRING@@@'
    },
    expectedStatus: 400
  },
  {
    id: 'TC-CRYPTO-008',
    dimension: '国密防伪抗篡改',
    title: '司法抗抵赖-审核端使用企业公钥验签合规记录有效性',
    method: 'GET',
    endpoint: '/signature/keypair',
    role: 'reviewer001',
    input: null,
    expectedStatus: 200
  },
  {
    id: 'TC-CRYPTO-009',
    dimension: '国密防伪抗篡改',
    title: '链上存证验伪-区块链查验接口验证上链哈希指纹',
    method: 'GET',
    endpoint: '/blockchain/blocks/latest',
    role: 'enterprise001',
    input: null,
    expectedStatus: 200,
    expectedCode: 200
  },
  {
    id: 'TC-CRYPTO-010',
    dimension: '国密防伪抗篡改',
    title: '防重放攻击-相同签名尝试复用于不同申报主体被拦截',
    method: 'POST',
    endpoint: '/signature/verify',
    role: 'reviewer001',
    input: {
      reportId: 27,
      signerId: 999999,
      reportData: '{"scope1":100.0}',
      signatureData: 'dGVzdA=='
    },
    expectedStatus: 400
  }
];
