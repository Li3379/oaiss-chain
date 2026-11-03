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
     * <p><b>永不返回假哈希</b>：历史实现返回 {@code tx_mock_xxx}，这个值会被
     * {@code CarbonService} 当作真实存证写进 {@code blockchain_tx_hash}，
     * 报告随即被置为「已上链」，而对账页无法把它和真实 Fabric 交易区分开。
     * 实测库中因此留下了 11 份 {@code tx_mock_} 报告。</p>
     *
     * <p>返回 {@code null} 是刻意的：调用方 {@code CarbonService.autoCommitToChain}
     * 会捕获由此引发的异常、清空 txHash 并让报告停留在「审核通过」，
     * 由 {@code /carbon/certify} 补救入口重试——这是「有链就上、没链不假装」的正确语义。</p>
     *
     * @param channelName 通道名称
     * @param chaincodeName 链码名称
     * @param functionName 函数名称
     * @param args 参数
     * @return 恒为 null（Mock 模式不产生任何可被误认为真实存证的哈希）
     */
    @Override
    public String invokeChaincode(String channelName, String chaincodeName,
            String functionName, String... args) {
        log.warn("[MOCK] 拒绝伪造交易哈希: channel={}, chaincode={}, function={} — "
                        + "Mock 模式不产出 txHash，业务方应停留在未上链状态",
                channelName, chaincodeName, functionName);
        return null;
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
    @Override
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
    @Override
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

        // txHash 恒为 null（见 invokeChaincode 的说明）。这里显式记录「未上链」，
        // 避免日志出现 "txHash=null" 被误读成实现缺陷。
        log.warn("[MOCK] 快照未上链（Mock 模式不产出存证）: reportId={}", snapshot.getReportId());
        return txHash;
    }

    /**
     * 将交易记录上链
     *
     * @param tradeId 交易ID
     * @param tradeData 交易数据
     * @return 交易哈希
     */
    @Override
    public String commitTradeToChain(Long tradeId, String tradeData) {
        log.info("[MOCK] Committing trade to chain: tradeId={}", tradeId);

        String txHash = invokeChaincode(
                "carbon-channel",
                "carbon-trade-cc",
                "commitTrade",
                String.valueOf(tradeId),
                tradeData
        );

        log.warn("[MOCK] 交易未上链（Mock 模式不产出存证）: tradeId={}", tradeId);
        return txHash;
    }

    /**
     * 查询区块信息
     *
     * @param blockNumber 区块号
     * @return 区块信息（JSON）
     */
    @Override
    public String queryBlock(Long blockNumber) {
        log.info("[MOCK] Querying block: {}", blockNumber);
        return String.format("{\"blockNumber\":%d,\"txCount\":1,\"timestamp\":\"%s\"}", 
                blockNumber, LocalDateTime.now());
    }

    /**
     * 按报告 ID 读回链上 v2 快照。
     *
     * <p>Mock 模式没有账本，提交只返回一个哈希、不落任何存储，因此这里恒返回
     * {@code null}（语义 = 链上查无此记录）。刻意不在 Mock 里伪造一份快照：
     * 否则开发环境会看到「对账一致」的假象，而真实 Fabric 上未必一致。</p>
     */
    @Override
    public String queryReportSnapshot(Long reportId) {
        log.info("[MOCK] Query report snapshot: reportId={} (mock keeps no ledger)", reportId);
        return null;
    }

    /**
     * 验证签名
     *
     * @param data 原始数据
     * @param signature 签名数据
     * @param publicKey 公钥
     * @return 是否验证通过
     */
    @Override
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
    @Override
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
    @Override
    public Page<Map<String, Object>> listTransactions(Integer page, Integer size) {
        // 返回空页而非编造的 tx_mock_ 流水。
        // 区块链浏览器在 Mock 模式下应当显示「无链上交易」，而不是一屏假交易——
        // 后者会被演示现场当作真实存证来核对。
        log.warn("[MOCK] 拒绝伪造链上交易列表: page={}, size={} — Mock 模式无链上数据", page, size);
        return new PageImpl<>(List.of(), PageRequest.of(Math.max(page - 1, 0), size), 0);
    }

    /**
     * 获取最新区块列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 区块列表
     */
    @Override
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
