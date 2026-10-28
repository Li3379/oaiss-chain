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
    @DisplayName("测试链码调用")
    void testInvokeChaincode() {
        String txHash = blockchainService.invokeChaincode(
                "carbon-channel",
                "carbon-report-cc",
                "commitReport",
                "arg1", "arg2"
        );

        assertNotNull(txHash);
        assertTrue(txHash.startsWith("tx_mock_"));
        assertTrue(txHash.contains("_"));
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
    @DisplayName("测试碳报告上链")
    void testCommitReportToChain() {
        Long reportId = 1L;
        String reportData = "{\"reportNo\":\"CR20240101001\",\"totalEmission\":1000.00}";

        String txHash = blockchainService.commitReportToChain(reportId, reportData);

        assertNotNull(txHash);
        assertTrue(txHash.startsWith("tx_mock_"));
    }

    @Test
    @DisplayName("测试交易记录上链")
    void testCommitTradeToChain() {
        Long tradeId = 1L;
        String tradeData = "{\"tradeNo\":\"TX20240101001\",\"quantity\":100.00}";

        String txHash = blockchainService.commitTradeToChain(tradeId, tradeData);

        assertNotNull(txHash);
        assertTrue(txHash.startsWith("tx_mock_"));
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

    @Test
    @DisplayName("测试交易查询")
    void testQueryTransaction() {
        String txHash = "tx_mock_1234567890_abc12345";

        String txInfo = blockchainService.queryTransaction(txHash);

        assertNotNull(txInfo);
        assertTrue(txInfo.contains("txHash"));
        assertTrue(txInfo.contains("status"));
        assertTrue(txInfo.contains("VALID"));
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
    @DisplayName("测试交易列表分页")
    void testListTransactions() {
        Page<Map<String, Object>> page = blockchainService.listTransactions(2, 3);

        assertEquals(3, page.getContent().size());
        assertEquals(100, page.getTotalElements());
        assertEquals(1, page.getNumber());
        assertEquals(3, page.getSize());
        assertTrue(page.getContent().get(0).containsKey("txHash"));
        assertTrue(page.getContent().get(0).containsKey("blockNumber"));
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
