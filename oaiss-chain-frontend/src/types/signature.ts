/** RSA密钥对响应 */
export interface RsaKeyPairResponse {
  id: number
  userId: number
  publicKey: string
  createdAt: string
  expiresAt: string
  keyStatus: number
  keyStatusText: string
  keyVersion: number
  keyUsage: number
}

/** 签名结果 */
export interface SignatureResult {
  signature: string
  algorithm: string
  timestamp: string
  valid: boolean | null
  signerId: number
}

/** 签名验证请求 */
export interface SignatureVerifyRequest {
  reportId: number
  signatureData: string
  reportData: string
  signerId?: number
}
