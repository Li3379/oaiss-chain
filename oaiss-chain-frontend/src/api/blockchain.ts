/**
 * ==============================================================================
 * 【API 模块】：超级账本（Hyperledger Fabric）区块链浏览器与存证溯源 (Blockchain API)
 * 【对应后端】：com.oaiss.chain.controller.BlockchainController (/blockchain)
 * 【主要服务页面】：
 *   - 区块链浏览器: views/enterprise/Blockchain.vue (/enterprise/blockchain/browser)
 *   - 官网首页链状态: views/OfficialHome.vue (/official-home)
 *   - 审核员上链补救: views/admin/VerifyList.vue (/auditor/verify/list)
 * ==============================================================================
 */
import request from './request'
import type {
  BlockchainBlockResponse,
  BlockchainStatusResponse,
  BlockchainTransactionResponse,
  PageRequest,
  PageResponse,
} from '../types'

/**
 * 【业务功能】：探活底层 Hyperledger Fabric 网络健康度（通道、节点状态、最新区块高度与智能合约版本）
 * 【对应前端页面】：Blockchain.vue (/enterprise/blockchain/browser), OfficialHome.vue
 * 【界面触发操作】：区块链浏览器顶部“网络状态监控卡片”及官网首页底层节点运行指示灯
 * 【后端对应端点】：BlockchainController -> GET /blockchain/status
 */
export function getStatus(): Promise<BlockchainStatusResponse> {
  return request.get('/blockchain/status')
}

/**
 * 【业务功能】：分页查询链上所有碳报告存证与交易结算事务流水列表
 * 【对应前端页面】：Blockchain.vue (/enterprise/blockchain/browser)
 * 【界面触发操作】：切换至“交易存证流”Tab查看全网提交上链的交易ID、发起方及时间戳
 * 【后端对应端点】：BlockchainController -> GET /blockchain/transactions
 */
export function getTransactions(params?: PageRequest): Promise<PageResponse<BlockchainTransactionResponse>> {
  return request.get('/blockchain/transactions', { params })
}

/**
 * 【业务功能】：查询底层区块链最新打包生成的区块列表（出块高度、区块哈希、包含交易数）
 * 【对应前端页面】：Blockchain.vue (/enterprise/blockchain/browser)
 * 【界面触发操作】：浏览器左侧“最新区块高度瀑布流”实时展示最新出块动态
 * 【后端对应端点】：BlockchainController -> GET /blockchain/blocks/latest
 */
export function getLatestBlocks(params?: PageRequest): Promise<PageResponse<BlockchainBlockResponse>> {
  return request.get('/blockchain/blocks/latest', { params })
}

/**
 * 【业务功能】：根据区块高度（Block Number）查询具体区块的头哈希、前序哈希与完整交易列表
 * 【对应前端页面】：Blockchain.vue (/enterprise/blockchain/browser)
 * 【界面触发操作】：点击区块列表中的区块高度，下钻查看该区块内的交易明细
 * 【后端对应端点】：BlockchainController -> GET /blockchain/block/{blockNumber}
 */
export function queryBlock(blockNumber: number): Promise<BlockchainBlockResponse | Record<string, unknown>> {
  return request.get(`/blockchain/block/${blockNumber}`)
}
