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
  blockHash: string
  previousHash?: string
  txCount?: number
  timestamp?: string
  blockType?: string
  miner?: string
}

export interface BlockchainTransactionResponse {
  txHash: string
  txId?: string
  status?: string
  blockNumber?: number
  timestamp?: string
  channelId?: string
  type?: string | number
  fromAddress?: string
  toAddress?: string
  amount?: number | string
}
