package com.oaiss.chain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import org.springframework.data.domain.Page;

import static org.junit.jupiter.api.Assertions.*;

/**
 * * BlockchainService 单元测试
 * 
 * * @author OAISS Team
 */
@ExtendWith(MockitoExtension.class)
class MockBlockchainServiceTest {

    private MockBlockchainService blockchainService;

    @BeforeEach
    void setUp() {
        blockchainService = new MockBlockchainService();
    }

    @Test
    @DisplayName("W0：Mock 模式不得伪造交易哈希（历史上 tx_mock_ 会被当成真实存证）")
    void testInvokeChaincode() {
        String txHash = blockchainService.invokeChaincode(
                "carbon-channel",
                "carbon-report-cc",
                "commitReport",
                "arg1", "arg2"
        );

        // 语义已反转：此前断言 txHash 以 tx_mock_ 开头，等于把「伪造存证」写进契约。
        // 现在必须断言 null——调用方据此让报告停留在「审核通过」而非「已上链」。
        assertNull(txHash, "Mock 模式必须不产出哈希，否则会污染 blockchain_tx_hash");
    }

    @Test
    @DisplayName("测试链码查询")
    void testQueryChaincode() {
        String result = blockchainService.queryChaincode(
                "carbon-channel",
                "carbon-report-cc",
                "queryReport",
                "reportId"
        );

        assertNotNull(result);
        assertTrue(result.contains("status"));
        assertTrue(result.contains("success"));
    }

    @Test
    @DisplayName("W0：碳报告上链不产出哈希")
    void testCommitReportToChain() {
        Long reportId = 1L;
        String reportData = "{\"reportNo\":\"CR20240101001\",\"totalEmission\":1000.00}";

        String txHash = blockchainService.commitReportToChain(reportId, reportData);

        assertNull(txHash, "Mock 模式不产出 txHash，报告应停留 APPROVED 而非 ON_CHAIN");
    }

    @Test
    @DisplayName("W0：交易记录上链不产出哈希")
    void testCommitTradeToChain() {
        Long tradeId = 1L;
        String tradeData = "{\"tradeNo\":\"TX20240101001\",\"quantity\":100.00}";

        String txHash = blockchainService.commitTradeToChain(tradeId, tradeData);

        assertNull(txHash, "Mock 模式不产出 txHash，避免 trade 记录被误认为已存证");
    }

    @Test
    @DisplayName("测试区块查询")
    void testQueryBlock() {
        Long blockNumber = 100L;

        String blockInfo = blockchainService.queryBlock(blockNumber);

        assertNotNull(blockInfo);
        assertTrue(blockInfo.contains("blockNumber"));
        assertTrue(blockInfo.contains("txCount"));
    }

    /**
     * Mock 模式没有账本：提交只返回哈希，不落存储。因此「链上快照」恒为空，
     * 语义等于「链上查无此报告」。刻意不伪造快照，否则开发环境的对账会呈现假绿。
     */
    @Test
    @DisplayName("测试链上报告快照在 Mock 下为空")
    void testQueryReportSnapshotReturnsNull() {
        assertNull(blockchainService.queryReportSnapshot(1L));
    }

    @Test
    @DisplayName("测试签名验证")
    void testVerifySignature() {
        String data = "test-data-content";
        String signature = "mock-signature";
        String publicKey = "mock-public-key";

        boolean result = blockchainService.verifySignature(data, signature, publicKey);

        assertTrue(result); // Mock环境默认验证通过
    }

    @Test
    @DisplayName("测试连接状态检查")
    void testCheckConnection() {
        Map<String, Object> status = blockchainService.checkConnection();

        assertNotNull(status);
        assertEquals(true, status.get("connected"));
        assertEquals("carbon-channel", status.get("channel"));
        assertEquals(2, status.get("peers"));
        assertEquals(1, status.get("orderers"));
        assertEquals("MOCK", status.get("mode"));
        assertNotNull(status.get("timestamp"));
    }

    @Test
    @DisplayName("W0：链上交易列表返回空页而非编造流水")
    void testListTransactions() {
        Page<Map<String, Object>> page = blockchainService.listTransactions(2, 3);

        // 语义已反转：历史实现编造 size 条 tx_mock_ 流水、总数恒为 100，
        // 区块链浏览器在 Mock 模式下因此显示一屏假交易，演示时会被当成真实存证核对。
        assertTrue(page.getContent().isEmpty(), "Mock 模式必须无链上数据，不得编造流水");
        assertEquals(0, page.getTotalElements());
    }

    @Test
    @DisplayName("测试最新区块列表分页")
    void testListLatestBlocks() {
        Page<Map<String, Object>> page = blockchainService.listLatestBlocks(1, 4);

        assertEquals(4, page.getContent().size());
        assertEquals(10000, page.getTotalElements());
        assertEquals(0, page.getNumber());
        assertEquals(4, page.getSize());
        assertEquals("GENESIS", page.getContent().get(0).get("blockType"));
        assertTrue(page.getContent().get(1).containsKey("blockHash"));
    }
}
