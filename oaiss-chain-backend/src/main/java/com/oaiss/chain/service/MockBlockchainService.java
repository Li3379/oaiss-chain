package com.oaiss.chain.service;

import com.oaiss.chain.dto.CarbonReportSnapshot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 区块链服务（Mock实现）
 * 
 * 开发阶段模拟Hyperledger Fabric区块链交互
 * 生产环境替换为实际SDK调用
 * 
 * @author OAISS Team
 */
@Slf4j
@Service
@Profile("!fabric")
public class MockBlockchainService implements BlockchainServicePort {

    /**
     * 将数据上链
     *
     * @param channelName 通道名称
     * @param chaincodeName 链码名称
     * @param functionName 函数名称
     * @param args 参数
     * @return 交易哈希
     */
    public String invokeChaincode(String channelName, String chaincodeName,
            String functionName, String... args) {
        log.info("[MOCK] Invoking chaincode: channel={}, chaincode={}, function={}", 
                channelName, chaincodeName, functionName);

        // 模拟交易哈希
        String txHash = "tx_mock_" + System.currentTimeMillis() + "_" + 
                UUID.randomUUID().toString().substring(0, 8);

        log.info("[MOCK] Transaction committed: {}", txHash);
        return txHash;
    }

    /**
     * 从链上查询数据
     *
     * @param channelName 通道名称
     * @param chaincodeName 链码名称
     * @param functionName 函数名称
     * @param args 参数
     * @return 查询结果
     */
    public String queryChaincode(String channelName, String chaincodeName,
            String functionName, String... args) {
        log.info("[MOCK] Querying chaincode: channel={}, chaincode={}, function={}", 
                channelName, chaincodeName, functionName);

        // 模拟返回数据
        return "{\"status\":\"success\",\"data\":{}}";
    }

    /**
     * 将碳报告上链
     *
     * @param reportId 报告ID
     * @param reportData 报告数据
     * @return 交易哈希
     */
    public String commitReportToChain(Long reportId, String reportData) {
        log.info("[MOCK] Committing carbon report to chain: reportId={}", reportId);

        String txHash = invokeChaincode(
                "carbon-channel",
                "carbon-report-cc",
                "commitReport",
                String.valueOf(reportId),
                reportData
        );

        log.info("[MOCK] Report committed: reportId={}, txHash={}", reportId, txHash);
        return txHash;
    }

    /**
     * 存证碳报告完整快照（v2）。
     *
     * <p>Mock 模式下同样生成确定性哈希，并把快照内容一并纳入哈希输入——
     * 这样「快照内容变化 → 哈希变化」的语义在开发环境也可被观察到，
     * 不必等到接入真实 Fabric 才发现快照没进哈希。</p>
     */
    @Override
    public String commitReportSnapshot(CarbonReportSnapshot snapshot) {
        log.info("[MOCK] Committing report snapshot v2: reportId={}, enterpriseId={}",
                snapshot.getReportId(), snapshot.getEnterpriseId());

        String payload = String.join("|",
                String.valueOf(snapshot.getReportId()),
                String.valueOf(snapshot.getEnterpriseId()),
                String.valueOf(snapshot.getAccountingPeriod()),
                String.valueOf(snapshot.getTotalEmission()),
                String.valueOf(snapshot.getScope1Emission()),
                String.valueOf(snapshot.getScope2Emission()),
                String.valueOf(snapshot.getScope3Emission()),
                String.valueOf(snapshot.getSignature()),
                String.valueOf(snapshot.getData()));

        String txHash = invokeChaincode(
                "carbon-channel",
                "carbon-report-cc",
                "CreateCarbonReportV2",
                payload
        );

        log.info("[MOCK] Snapshot committed: reportId={}, txHash={}", snapshot.getReportId(), txHash);
        return txHash;
    }

    /**
     * 将交易记录上链
     *
     * @param tradeId 交易ID
     * @param tradeData 交易数据
     * @return 交易哈希
     */
    public String commitTradeToChain(Long tradeId, String tradeData) {
        log.info("[MOCK] Committing trade to chain: tradeId={}", tradeId);

        String txHash = invokeChaincode(
                "carbon-channel",
                "carbon-trade-cc",
                "commitTrade",
                String.valueOf(tradeId),
                tradeData
        );

        log.info("[MOCK] Trade committed: tradeId={}, txHash={}", tradeId, txHash);
        return txHash;
    }

    /**
     * 查询区块信息
     *
     * @param blockNumber 区块号
     * @return 区块信息（JSON）
     */
    public String queryBlock(Long blockNumber) {
        log.info("[MOCK] Querying block: {}", blockNumber);
        return String.format("{\"blockNumber\":%d,\"txCount\":1,\"timestamp\":\"%s\"}", 
                blockNumber, LocalDateTime.now());
    }

    /**
     * 查询交易信息
     *
     * @param txHash 交易哈希
     * @return 交易信息（JSON）
     */
    public String queryTransaction(String txHash) {
        log.info("[MOCK] Querying transaction: {}", txHash);
        return String.format("{\"txHash\":\"%s\",\"status\":\"VALID\",\"timestamp\":\"%s\"}", 
                txHash, LocalDateTime.now());
    }

    /**
     * 验证签名
     *
     * @param data 原始数据
     * @param signature 签名数据
     * @param publicKey 公钥
     * @return 是否验证通过
     */
    public boolean verifySignature(String data, String signature, String publicKey) {
        log.info("[MOCK] Verifying signature for data length: {}", data.length());
        // Mock环境默认验证通过
        return true;
    }

    /**
     * 检查区块链连接状态
     *
     * @return 连接状态
     */
    public Map<String, Object> checkConnection() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", true);
        status.put("channel", "carbon-channel");
        status.put("peers", 2);
        status.put("orderers", 1);
        status.put("mode", "MOCK");
        status.put("caEnabled", false);
        status.put("timestamp", LocalDateTime.now().toString());
        return status;
    }

    /**
     * 分页查询链上交易列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 交易列表
     */
    public Page<Map<String, Object>> listTransactions(Integer page, Integer size) {
        log.info("[MOCK] Listing transactions: page={}, size={}", page, size);

        List<Map<String, Object>> transactions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Map<String, Object> tx = new HashMap<>();
            tx.put("txHash", "tx_mock_" + System.currentTimeMillis() + "_" + i);
            tx.put("blockNumber", 1000 + (page - 1) * size + i);
            tx.put("status", "VALID");
            tx.put("timestamp", LocalDateTime.now().minusMinutes(i).toString());
            transactions.add(tx);
        }

        return new PageImpl<>(transactions, PageRequest.of(page - 1, size), 100);
    }

    /**
     * 获取最新区块列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 区块列表
     */
    public Page<Map<String, Object>> listLatestBlocks(Integer page, Integer size) {
        log.info("[MOCK] Listing latest blocks: page={}, size={}", page, size);

        List<Map<String, Object>> blocks = new ArrayList<>();
        long baseBlock = 10000 - (long) (page - 1) * size;
        for (int i = 0; i < size && baseBlock - i >= 0; i++) {
            Map<String, Object> block = new HashMap<>();
            block.put("blockNumber", baseBlock - i);
            block.put("blockHash", "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 32));
            block.put("txCount", java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 11));
            block.put("timestamp", LocalDateTime.now().minusMinutes(i * 5).toString());
            block.put("blockType", i == 0 ? "GENESIS" : "REGULAR");
            blocks.add(block);
        }

        return new PageImpl<>(blocks, PageRequest.of(page - 1, size), 10000);
    }
}
