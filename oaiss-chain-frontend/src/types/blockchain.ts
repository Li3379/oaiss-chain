export interface BlockchainStatusResponse {
  connected: boolean
  channel?: string
  peers?: number
  orderers?: number
  mode?: string
  mspId?: string
  chaincode?: string
  caEnabled?: boolean
  timestamp?: string
}

export interface BlockchainBlockResponse {
  blockNumber: number
  /** Fabric 区块头 dataHash（命名沿用历史契约，实际为 dataHash 而非整块哈希） */
  blockHash: string
  previousHash?: string
  txCount?: number
  timestamp?: string
  blockType?: string
  channel?: string
}

/**
 * 链上交易记录。字段全部来自 Fabric 区块信封解析，不含数据库推导值。
 *
 * 注意：`status` 取自区块元数据的 TRANSACTIONS_FILTER（交易校验码），
 * 不再是写死的 `VALID`；`senderMsp` / `endorsers` 来自签名头与背书列表。
 */
export interface BlockchainTransactionResponse {
  txHash: string
  txId?: string
  status?: string
  validationCode?: number
  blockNumber?: number
  timestamp?: string
  timestampNanos?: number
  channelId?: string
  /** ChannelHeader.type：3 = ENDORSER_TRANSACTION */
  type?: string | number
  /** 交易发起者所属组织 MSP */
  senderMsp?: string
  /** 背书节点所属组织 MSP 列表 */
  endorsers?: string[]
  /** 本次调用的链码函数名，如 CreateCarbonReportV2 */
  function?: string
  /** 链码入参（不含签名等敏感字段） */
  args?: string[]
  chaincodeName?: string
}
