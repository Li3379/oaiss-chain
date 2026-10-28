/**
 * ==============================================================================
 * 【API 模块】：非对称加密数字签名与机密数据信封加解密 (Digital Signature API)
 * 【对应后端】：com.oaiss.chain.controller.DigitalSignatureController (/signature)
 * 【主要服务页面】：
 *   - 个人中心密钥管理: views/enterprise/UserProfile.vue (/enterprise/user/profile)
 *   - 碳核算填报防篡改: views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload)
 *   - 核查员验签核验: views/auditor/AuditList.vue (/auditor/audit/list)
 * ==============================================================================
 */
import request from './request'
import type { RsaKeyPairResponse, SignatureResult, SignatureVerifyRequest } from '../types'

/**
 * 【业务功能】：为当前企业或审核员生成专属 RSA-2048 非对称密钥对（带密码保护私钥与公钥指纹）
 * 【对应前端页面】：UserProfile.vue (/enterprise/user/profile)
 * 【界面触发操作】：在“数字签名证书”卡片点击【重新生成密钥对】或初始化证书
 * 【后端对应端点】：DigitalSignatureController -> POST /signature/keypair/generate
 */
export function generateKeyPair(): Promise<RsaKeyPairResponse> {
  return request.post('/signature/keypair/generate')
}

/**
 * 【业务功能】：查询当前用户的公钥证书元数据、指纹及证书就绪状态
 * 【对应前端页面】：UserProfile.vue, CarbonUpload.vue (/enterprise/carbon/upload)
 * 【界面触发操作】：填报页面加载时检查企业证书是否就绪（绿标提示）
 * 【后端对应端点】：DigitalSignatureController -> GET /signature/keypair
 */
export function getKeyPair(options?: { suppressErrorMessage?: boolean }): Promise<RsaKeyPairResponse> {
  return request.get('/signature/keypair', options)
}

/**
 * 【业务功能】：撤销并销毁当前用户的密钥对（密钥注销/轮换）
 * 【对应前端页面】：UserProfile.vue (/enterprise/user/profile)
 * 【界面触发操作】：在证书管理卡片点击【吊销/作废密钥】并确认
 * 【后端对应端点】：DigitalSignatureController -> DELETE /signature/keypair
 */
export function deleteKeyPair(): Promise<void> {
  return request.delete('/signature/keypair')
}

/**
 * 【业务功能】：使用当前企业私钥对碳核算报告原始摘要生成数字签名（抗抵赖凭据）
 * 【对应前端页面】：CarbonUpload.vue (/enterprise/carbon/upload)
 * 【界面触发操作】：提交碳核算报告时，系统后台调用对报告哈希进行签名
 * 【后端对应端点】：DigitalSignatureController -> POST /signature/sign
 */
export function signData(data: { data: string }): Promise<SignatureResult> {
  return request.post('/signature/sign', data)
}

/**
 * 【业务功能】：使用申报企业公钥验证数字签名真实性（防篡改检测）
 * 【对应前端页面】：AuditList.vue (/auditor/audit/list)
 * 【界面触发操作】：核查员打开报告审核抽屉，点击【验证数字签名】按钮，检测数据是否被篡改
 * 【后端对应端点】：DigitalSignatureController -> POST /signature/verify
 */
export function verifySignature(data: SignatureVerifyRequest): Promise<{ valid: boolean }> {
  return request.post('/signature/verify', data)
}

/**
 * 【业务功能】：使用审核员公钥加密企业核心生产商业机密数据（非对称加密）
 * 【对应前端页面】：CarbonUpload.vue
 * 【界面触发操作】：上传敏感凭证时加密封包
 * 【后端对应端点】：DigitalSignatureController -> POST /signature/encrypt
 */
export function encryptData(data: string, reviewerId: number): Promise<string> {
  if (!data) return Promise.reject(new Error('待加密数据不能为空'))
  if (!reviewerId) return Promise.reject(new Error('审核员ID不能为空'))
  return request.post('/signature/encrypt', data, {
    headers: { 'Content-Type': 'text/plain' },
    params: { reviewerId }
  })
}

/**
 * 【业务功能】：审核员使用私钥解密查看受保护的企业敏感工艺参数
 * 【对应前端页面】：AuditList.vue
 * 【界面触发操作】：核查员查阅敏感凭证时调用解密
 * 【后端对应端点】：DigitalSignatureController -> POST /signature/decrypt
 */
export function decryptData(encryptedData: string): Promise<string> {
  if (!encryptedData) return Promise.reject(new Error('加密数据不能为空'))
  return request.post('/signature/decrypt', encryptedData, {
    headers: { 'Content-Type': 'text/plain' }
  })
}
