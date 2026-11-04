package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.exception.BlockchainException;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.service.BlockchainServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BlockchainController Unit Tests
 * 区块链控制器单元测试
 *
 * @author OAISS Team
 */
@WebMvcTest(controllers = BlockchainController.class)
@AutoConfigureMockMvc(addFilters = false)
class BlockchainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BlockchainServicePort blockchainService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    // ==================== Check Status Tests ====================

    @Nested
    @DisplayName("检查区块链连接状态测试")
    class CheckStatusTests {

        @Test
        @DisplayName("检查区块链连接状态成功")
        void testCheckStatusSuccess() throws Exception {
            // Given
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("connected", true);
            statusData.put("channel", "carbon-channel");
            statusData.put("peers", 2);
            statusData.put("orderers", 1);
            statusData.put("mode", "MOCK");

            when(blockchainService.checkConnection()).thenReturn(statusData);

            // When & Then
            mockMvc.perform(get("/blockchain/status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.connected").value(true))
                    .andExpect(jsonPath("$.data.channel").value("carbon-channel"))
                    .andExpect(jsonPath("$.data.peers").value(2))
                    .andExpect(jsonPath("$.data.orderers").value(1))
                    .andExpect(jsonPath("$.data.mode").value("MOCK"));

            verify(blockchainService, times(1)).checkConnection();
        }

        @Test
        @DisplayName("检查区块链连接状态-连接失败")
        void testCheckStatusConnectionFailed() throws Exception {
            // Given
            when(blockchainService.checkConnection())
                    .thenThrow(BlockchainException.connectionFailed("网络不可达"));

            // When & Then
            mockMvc.perform(get("/blockchain/status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(5000));

            verify(blockchainService, times(1)).checkConnection();
        }

        @Test
        @DisplayName("检查区块链连接状态-返回未连接状态")
        void testCheckStatusNotConnected() throws Exception {
            // Given
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("connected", false);
            statusData.put("channel", null);
            statusData.put("peers", 0);
            statusData.put("orderers", 0);
            statusData.put("mode", "MOCK");

            when(blockchainService.checkConnection()).thenReturn(statusData);

            // When & Then
            mockMvc.perform(get("/blockchain/status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.connected").value(false))
                    .andExpect(jsonPath("$.data.peers").value(0));

            verify(blockchainService, times(1)).checkConnection();
        }

        @Test
        @DisplayName("状态查询端点保留企业角色")
        void testCheckStatusKeepsEnterprise() throws Exception {
            // @WebMvcTest(addFilters=false) 不执行方法级 @PreAuthorize（见 08-PATTERNS.md Trap 6），
            // 因此断言注解字面量而非 HTTP 状态码。
            var handler = BlockchainController.class.getMethod("checkStatus");
            PreAuthorize preAuthorize = handler.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).contains("ENTERPRISE");
        }
    }

    // ==================== Query Block Tests ====================

    @Nested
    @DisplayName("查询区块信息测试")
    class QueryBlockTests {

        @Test
        @DisplayName("查询区块信息成功")
        void testQueryBlockSuccess() throws Exception {
            // Given
            Long blockNumber = 1000L;
            String blockInfo = "{\"blockNumber\":1000,\"txCount\":5,\"timestamp\":\"2024-01-15T10:30:00\"}";

            when(blockchainService.queryBlock(blockNumber)).thenReturn(blockInfo);

            // When & Then
            mockMvc.perform(get("/blockchain/block/{blockNumber}", blockNumber)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(blockInfo));

            verify(blockchainService, times(1)).queryBlock(blockNumber);
        }

        @Test
        @DisplayName("查询区块信息-区块号0")
        void testQueryBlockZero() throws Exception {
            // Given
            Long blockNumber = 0L;
            String blockInfo = "{\"blockNumber\":0,\"txCount\":1,\"timestamp\":\"2024-01-01T00:00:00\"}";

            when(blockchainService.queryBlock(blockNumber)).thenReturn(blockInfo);

            // When & Then
            mockMvc.perform(get("/blockchain/block/{blockNumber}", blockNumber)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(blockInfo));

            verify(blockchainService, times(1)).queryBlock(blockNumber);
        }

        @Test
        @DisplayName("查询区块信息-大区块号")
        void testQueryBlockLargeNumber() throws Exception {
            // Given
            Long blockNumber = 999999999L;
            String blockInfo = "{\"blockNumber\":999999999,\"txCount\":10}";

            when(blockchainService.queryBlock(blockNumber)).thenReturn(blockInfo);

            // When & Then
            mockMvc.perform(get("/blockchain/block/{blockNumber}", blockNumber)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(blockInfo));

            verify(blockchainService, times(1)).queryBlock(blockNumber);
        }

        @Test
        @DisplayName("查询区块信息-区块不存在")
        void testQueryBlockNotFound() throws Exception {
            // Given
            Long blockNumber = 999999L;
            when(blockchainService.queryBlock(blockNumber))
                    .thenThrow(BlockchainException.blockQueryFailed(blockNumber, "区块不存在"));

            // When & Then
            mockMvc.perform(get("/blockchain/block/{blockNumber}", blockNumber)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(5003));

            verify(blockchainService, times(1)).queryBlock(blockNumber);
        }

        @Test
        @DisplayName("查询区块信息-区块链连接异常")
        void testQueryBlockConnectionError() throws Exception {
            // Given
            Long blockNumber = 100L;
            when(blockchainService.queryBlock(blockNumber))
                    .thenThrow(BlockchainException.connectionFailed("节点离线"));

            // When & Then
            mockMvc.perform(get("/blockchain/block/{blockNumber}", blockNumber)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(5000));

            verify(blockchainService, times(1)).queryBlock(blockNumber);
        }

        @Test
        @DisplayName("查询区块信息-无效区块号(负数)")
        void testQueryBlockInvalidBlockNumber() throws Exception {
            // Given - IllegalArgumentException被全局异常处理器处理为400
            Long blockNumber = -1L;
            when(blockchainService.queryBlock(blockNumber))
                    .thenThrow(new IllegalArgumentException("区块号不能为负数"));

            // When & Then - IllegalArgumentException被处理为400
            mockMvc.perform(get("/blockchain/block/{blockNumber}", blockNumber)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verify(blockchainService, times(1)).queryBlock(blockNumber);
        }

        @Test
        @DisplayName("区块查询端点仅 ADMIN/THIRD_PARTY 可达")
        void testQueryBlockRestrictedToAdminAndThirdParty() throws Exception {
            // @WebMvcTest(addFilters=false) 不执行方法级 @PreAuthorize（见 08-PATTERNS.md Trap 6），
            // 因此断言注解字面量而非 HTTP 状态码。
            var handler = BlockchainController.class.getMethod("queryBlock", Long.class);
            PreAuthorize preAuthorize = handler.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).doesNotContain("ENTERPRISE");
            assertThat(preAuthorize.value()).contains("ADMIN");
            assertThat(preAuthorize.value()).contains("THIRD_PARTY");
        }
    }

    // ==================== Query Report Snapshot Tests ====================

    @Nested
    @DisplayName("查询报告链上快照测试")
    class QueryReportSnapshotTests {

        @Test
        @DisplayName("查询报告链上快照成功")
        void testQueryReportSnapshotSuccess() throws Exception {
            Long reportId = 42L;
            String snapshot = "{\"reportId\":\"42\",\"schemaVersion\":\"v2\",\"totalEmission\":\"1234.56\"}";
            when(blockchainService.queryReportSnapshot(reportId)).thenReturn(snapshot);

            mockMvc.perform(get("/blockchain/report/{reportId}", reportId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(snapshot));

            verify(blockchainService, times(1)).queryReportSnapshot(reportId);
        }

        @Test
        @DisplayName("查询报告链上快照-链上无该报告")
        void testQueryReportSnapshotNotFoundOnChain() throws Exception {
            Long reportId = 999L;
            when(blockchainService.queryReportSnapshot(reportId)).thenReturn(null);

            mockMvc.perform(get("/blockchain/report/{reportId}", reportId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(blockchainService, times(1)).queryReportSnapshot(reportId);
        }

        @Test
        @DisplayName("查询报告链上快照-链码调用失败")
        void testQueryReportSnapshotChaincodeFailure() throws Exception {
            Long reportId = 7L;
            when(blockchainService.queryReportSnapshot(reportId))
                    .thenThrow(BlockchainException.chaincodeInvokeFailed("QueryReportHistory", "peer down"));

            mockMvc.perform(get("/blockchain/report/{reportId}", reportId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(5001));

            verify(blockchainService, times(1)).queryReportSnapshot(reportId);
        }

        @Test
        @DisplayName("报告存证查询端点保留企业角色")
        void testQueryReportSnapshotKeepsEnterprise() throws Exception {
            // @WebMvcTest(addFilters=false) 不执行方法级 @PreAuthorize（见 08-PATTERNS.md Trap 6），
            // 因此断言注解字面量而非 HTTP 状态码。
            var handler = BlockchainController.class.getMethod("queryReportSnapshot", Long.class);
            PreAuthorize preAuthorize = handler.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).contains("ENTERPRISE");
        }
    }

    // ==================== Additional Edge Cases ====================

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("状态检查-空响应数据")
        void testCheckStatusEmptyResponse() throws Exception {
            // Given
            Map<String, Object> statusData = new HashMap<>();
            when(blockchainService.checkConnection()).thenReturn(statusData);

            // When & Then
            mockMvc.perform(get("/blockchain/status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").exists());

            verify(blockchainService, times(1)).checkConnection();
        }

        @Test
        @DisplayName("区块查询-空返回值")
        void testQueryBlockEmptyResponse() throws Exception {
            // Given
            Long blockNumber = 1L;
            when(blockchainService.queryBlock(blockNumber)).thenReturn("");

            // When & Then
            mockMvc.perform(get("/blockchain/block/{blockNumber}", blockNumber)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(""));

            verify(blockchainService, times(1)).queryBlock(blockNumber);
        }

        @Test
        @DisplayName("区块查询-包含JSON特殊字符")
        void testQueryBlockWithSpecialJsonChars() throws Exception {
            // Given
            Long blockNumber = 100L;
            String blockInfo = "{\"blockNumber\":100,\"data\":\"value with \\\"quotes\\\" and \\n newline\"}";

            when(blockchainService.queryBlock(blockNumber)).thenReturn(blockInfo);

            // When & Then
            mockMvc.perform(get("/blockchain/block/{blockNumber}", blockNumber)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(blockchainService, times(1)).queryBlock(blockNumber);
        }
    }

    // ==================== List Transactions Tests ====================

    @Nested
    @DisplayName("查询交易列表测试")
    class ListTransactionsTests {

        @Test
        @DisplayName("查询交易列表成功")
        void testListTransactionsSuccess() throws Exception {
            // Given
            Map<String, Object> tx1 = new HashMap<>();
            tx1.put("txHash", "tx_hash_1");
            tx1.put("status", "VALID");
            Map<String, Object> tx2 = new HashMap<>();
            tx2.put("txHash", "tx_hash_2");
            tx2.put("status", "VALID");

            Page<Map<String, Object>> page = new PageImpl<>(List.of(tx1, tx2), PageRequest.of(0, 20), 2);
            when(blockchainService.listTransactions(1, 20)).thenReturn(page);

            // When & Then
            mockMvc.perform(get("/blockchain/transactions")
                            .param("page", "1")
                            .param("size", "20")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].txHash").value("tx_hash_1"));

            verify(blockchainService, times(1)).listTransactions(1, 20);
        }

        @Test
        @DisplayName("查询交易列表-空列表")
        void testListTransactionsEmpty() throws Exception {
            // Given
            Page<Map<String, Object>> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(blockchainService.listTransactions(1, 20)).thenReturn(emptyPage);

            // When & Then
            mockMvc.perform(get("/blockchain/transactions")
                            .param("page", "1")
                            .param("size", "20")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content").isEmpty());

            verify(blockchainService, times(1)).listTransactions(1, 20);
        }

        @Test
        @DisplayName("查询交易列表-自定义分页参数")
        void testListTransactionsCustomPageParams() throws Exception {
            // Given
            Page<Map<String, Object>> page = new PageImpl<>(List.of(), PageRequest.of(2, 10), 25);
            when(blockchainService.listTransactions(3, 10)).thenReturn(page);

            // When & Then
            mockMvc.perform(get("/blockchain/transactions")
                            .param("page", "3")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(blockchainService, times(1)).listTransactions(3, 10);
        }

        @Test
        @DisplayName("交易列表端点对企业角色关闭")
        void testListTransactionsForbiddenForEnterprise() throws Exception {
            // @WebMvcTest(addFilters=false) 不执行方法级 @PreAuthorize（见 08-PATTERNS.md Trap 6），
            // 因此断言注解字面量而非 HTTP 状态码。
            var handler = BlockchainController.class.getMethod(
                    "listTransactions", Integer.class, Integer.class);
            PreAuthorize preAuthorize = handler.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).doesNotContain("ENTERPRISE");
            assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('ADMIN', 'THIRD_PARTY')");
        }
    }

    // ==================== List Latest Blocks Tests ====================

    @Nested
    @DisplayName("获取最新区块列表测试")
    class ListLatestBlocksTests {

        @Test
        @DisplayName("获取最新区块列表成功")
        void testListLatestBlocksSuccess() throws Exception {
            // Given
            Map<String, Object> block1 = new HashMap<>();
            block1.put("blockNumber", 100L);
            block1.put("txCount", 5);
            Map<String, Object> block2 = new HashMap<>();
            block2.put("blockNumber", 99L);
            block2.put("txCount", 3);

            Page<Map<String, Object>> page = new PageImpl<>(List.of(block1, block2), PageRequest.of(0, 20), 2);
            when(blockchainService.listLatestBlocks(1, 20)).thenReturn(page);

            // When & Then
            mockMvc.perform(get("/blockchain/blocks/latest")
                            .param("page", "1")
                            .param("size", "20")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].blockNumber").value(100));

            verify(blockchainService, times(1)).listLatestBlocks(1, 20);
        }

        @Test
        @DisplayName("获取最新区块列表-空列表")
        void testListLatestBlocksEmpty() throws Exception {
            // Given
            Page<Map<String, Object>> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(blockchainService.listLatestBlocks(1, 20)).thenReturn(emptyPage);

            // When & Then
            mockMvc.perform(get("/blockchain/blocks/latest")
                            .param("page", "1")
                            .param("size", "20")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content").isEmpty());

            verify(blockchainService, times(1)).listLatestBlocks(1, 20);
        }

        @Test
        @DisplayName("获取最新区块列表-自定义分页参数")
        void testListLatestBlocksCustomPageParams() throws Exception {
            // Given
            Page<Map<String, Object>> page = new PageImpl<>(List.of(), PageRequest.of(0, 5), 0);
            when(blockchainService.listLatestBlocks(1, 5)).thenReturn(page);

            // When & Then
            mockMvc.perform(get("/blockchain/blocks/latest")
                            .param("page", "1")
                            .param("size", "5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(blockchainService, times(1)).listLatestBlocks(1, 5);
        }

        @Test
        @DisplayName("最新区块列表端点对企业角色关闭")
        void testListLatestBlocksForbiddenForEnterprise() throws Exception {
            // @WebMvcTest(addFilters=false) 不执行方法级 @PreAuthorize（见 08-PATTERNS.md Trap 6），
            // 因此断言注解字面量而非 HTTP 状态码。
            var handler = BlockchainController.class.getMethod(
                    "listLatestBlocks", Integer.class, Integer.class);
            PreAuthorize preAuthorize = handler.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).doesNotContain("ENTERPRISE");
            assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('ADMIN', 'THIRD_PARTY')");
        }
    }
}
