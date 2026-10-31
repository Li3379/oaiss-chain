package com.oaiss.chain.service;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.ThirdPartyOrg;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.ThirdPartyOrgRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.security.JwtUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 第三方监管服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThirdPartyServiceTest {

    @Mock
    private ThirdPartyOrgRepository thirdPartyOrgRepository;
    @Mock
    private CarbonReportRepository carbonReportRepository;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ThirdPartyService thirdPartyService;

    private JwtUserDetails currentUser;
    private ThirdPartyOrg testOrg;

    @BeforeEach
    void setUp() {
        currentUser = JwtUserDetails.builder()
                .userId(1L).username("thirdparty").userType(3)
                .roles(List.of("THIRD_PARTY")).enterpriseId(null)
                .enabled(true).build();

        testOrg = ThirdPartyOrg.builder()
                .userId(1L).orgName("测试机构").orgCode("ORG001")
                .orgType(3).accessLevel(2).status(1)
                .contactPerson("张三").contactPhone("13800138000")
                .build();
        testOrg.setId(1L);
    }

    // ==================== getCurrentOrg ====================

    @Test
    @DisplayName("获取当前机构 - 成功")
    void getCurrentOrg_ShouldReturnOrg() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        ThirdPartyOrg org = thirdPartyService.getCurrentOrg(currentUser);
        assertEquals("测试机构", org.getOrgName());
    }

    @Test
    @DisplayName("获取当前机构 - 不存在抛出异常")
    void getCurrentOrg_NotFound_ShouldThrow() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> thirdPartyService.getCurrentOrg(currentUser));
    }

    // ==================== queryCarbonReports ====================

    @Test
    @DisplayName("查询碳报告 - 有过滤条件")
    void queryCarbonReports_WithFilters() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        when(carbonReportRepository.search(anyLong(), anyInt(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        ApiResponse<Page<CarbonReport>> result = thirdPartyService.queryCarbonReports(
                currentUser, 1L, 1, "keyword", 1, 10);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(carbonReportRepository).search(anyLong(), anyInt(), anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("查询碳报告 - 无过滤条件")
    void queryCarbonReports_WithoutFilters() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        when(carbonReportRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        ApiResponse<Page<CarbonReport>> result = thirdPartyService.queryCarbonReports(
                currentUser, null, null, null, 1, 10);

        assertNotNull(result);
        verify(carbonReportRepository).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("查询碳报告 - 机构被禁用抛出异常")
    void queryCarbonReports_OrgDisabled_ShouldThrow() {
        testOrg.setStatus(0);
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        assertThrows(BusinessException.class, () ->
                thirdPartyService.queryCarbonReports(currentUser, null, null, null, 1, 10));
    }

    // ==================== getStatistics ====================

    @Test
    @DisplayName("获取统计数据 - 成功")
    void getStatistics_ShouldReturnStats() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        when(carbonReportRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(carbonReportRepository.findByStatusAndDeletedFalse(eq(1), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(carbonReportRepository.findByStatusAndDeletedFalse(eq(4), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(carbonReportRepository.findByStatusInAndDeletedFalse(anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(transactionRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        ApiResponse<Map<String, Object>> result = thirdPartyService.getStatistics(currentUser);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        Map<String, Object> stats = result.getData();
        assertEquals("测试机构", stats.get("orgName"));
        assertEquals(2, stats.get("accessLevel"));
        assertEquals(0L, stats.get("totalReports"));
        assertEquals(0L, stats.get("pendingReports"));
    }

    @Test
    @DisplayName("获取统计数据 - 含交易维度聚合（笔数/总量/总额）")
    void getStatistics_ShouldIncludeTradeAggregates() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        when(carbonReportRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(carbonReportRepository.findByStatusAndDeletedFalse(anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(carbonReportRepository.findByStatusInAndDeletedFalse(anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Transaction sample = Transaction.builder()
                .tradeNo("TX-1").tradeType(2).sellerId(1L).buyerId(2L)
                .quantity(new java.math.BigDecimal("100.0000"))
                .unitPrice(new java.math.BigDecimal("50.00"))
                .totalAmount(new java.math.BigDecimal("5000.00"))
                .status(2).build();
        when(transactionRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample), PageRequest.of(0, 1), 37L));
        when(transactionRepository.sumTotalQuantity()).thenReturn(new java.math.BigDecimal("125000.5000"));
        when(transactionRepository.sumTotalAmount()).thenReturn(new java.math.BigDecimal("6250025.00"));

        Map<String, Object> stats = thirdPartyService.getStatistics(currentUser).getData();

        assertEquals(37L, stats.get("totalTrades"));
        assertEquals(new java.math.BigDecimal("125000.5000"), stats.get("totalTradeQuantity"));
        assertEquals(new java.math.BigDecimal("6250025.00"), stats.get("totalTradeAmount"));
    }

    @Test
    @DisplayName("获取统计数据 - 交易聚合零数据时兜底为 0 而非 null")
    void getStatistics_TradeAggregatesNullSafe() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        when(carbonReportRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(carbonReportRepository.findByStatusAndDeletedFalse(anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(carbonReportRepository.findByStatusInAndDeletedFalse(anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(transactionRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(transactionRepository.sumTotalQuantity()).thenReturn(null);
        when(transactionRepository.sumTotalAmount()).thenReturn(null);

        Map<String, Object> stats = thirdPartyService.getStatistics(currentUser).getData();

        assertEquals(0L, stats.get("totalTrades"));
        assertEquals(java.math.BigDecimal.ZERO, stats.get("totalTradeQuantity"));
        assertEquals(java.math.BigDecimal.ZERO, stats.get("totalTradeAmount"));
    }

    // ==================== updateContact ====================

    @Test
    @DisplayName("更新联系方式 - 两个字段都更新")
    void updateContact_BothFields() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        when(thirdPartyOrgRepository.save(any())).thenReturn(testOrg);

        ApiResponse<Void> result = thirdPartyService.updateContact(currentUser, "李四", "13900139000");

        assertEquals(200, result.getCode());
        verify(thirdPartyOrgRepository).save(any());
    }

    @Test
    @DisplayName("更新联系方式 - 只更新联系人")
    void updateContact_OnlyContactPerson() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        when(thirdPartyOrgRepository.save(any())).thenReturn(testOrg);

        thirdPartyService.updateContact(currentUser, "王五", null);
        verify(thirdPartyOrgRepository).save(argThat(org -> "王五".equals(org.getContactPerson())));
    }

    @Test
    @DisplayName("更新联系方式 - 只更新电话")
    void updateContact_OnlyContactPhone() {
        when(thirdPartyOrgRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testOrg));
        when(thirdPartyOrgRepository.save(any())).thenReturn(testOrg);

        thirdPartyService.updateContact(currentUser, null, "13700137000");
        verify(thirdPartyOrgRepository).save(argThat(org -> "13700137000".equals(org.getContactPhone())));
    }
}
