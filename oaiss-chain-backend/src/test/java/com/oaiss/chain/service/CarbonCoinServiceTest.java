package com.oaiss.chain.service;

import com.oaiss.chain.dto.CarbonCoinAccountResponse;
import com.oaiss.chain.dto.CarbonCoinRechargeRequest;
import com.oaiss.chain.entity.CarbonCoinAccount;
import com.oaiss.chain.entity.CarbonCoinTransaction;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonCoinAccountRepository;
import com.oaiss.chain.repository.CarbonCoinTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CarbonCoinService 单元测试
 * CarbonCoinService Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class CarbonCoinServiceTest {

    @Mock
    private CarbonCoinAccountRepository accountRepository;

    @Mock
    private CarbonCoinTransactionRepository transactionRepository;

    @Mock
    private CertificateGateService certificateGateService;

    @InjectMocks
    private CarbonCoinService carbonCoinService;

    private CarbonCoinAccount testAccount;

    @BeforeEach
    void setUp() {
        // 默认放行：既有用例验证的是合规主体的账户行为
        lenient().doNothing().when(certificateGateService).assertEnterpriseAdmittedByUserId(anyLong());

        testAccount = CarbonCoinAccount.builder()
                .userId(1L)
                .balance(new BigDecimal("1000.00"))
                .totalRecharged(new BigDecimal("2000.00"))
                .totalSpent(new BigDecimal("1000.00"))
                .status(1)
                .build();
        testAccount.setId(1L);
    }

    @Test
    @DisplayName("获取或创建账户-已存在")
    void testGetOrCreateAccountExisting() {
        // Given
        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));

        // When
        CarbonCoinAccountResponse response = carbonCoinService.getOrCreateAccount(1L);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals(new BigDecimal("1000.00"), response.getBalance());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("获取或创建账户-新建")
    void testGetOrCreateAccountNew() {
        // Given
        CarbonCoinAccount newAccount = CarbonCoinAccount.builder()
                .userId(3L)
                .balance(BigDecimal.ZERO)
                .totalRecharged(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .status(1)
                .build();
        newAccount.setId(3L);

        when(accountRepository.findByUserIdAndDeletedFalse(3L)).thenReturn(Optional.empty());
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenReturn(newAccount);

        // When
        CarbonCoinAccountResponse response = carbonCoinService.getOrCreateAccount(3L);

        // Then
        assertNotNull(response);
        assertEquals(3L, response.getUserId());
        verify(accountRepository, times(1)).save(any(CarbonCoinAccount.class));
    }

    @Test
    @DisplayName("充值碳币成功")
    void testRechargeSuccess() {
        // Given
        CarbonCoinRechargeRequest request = new CarbonCoinRechargeRequest();
        request.setAmount(new BigDecimal("500.00"));
        request.setRemark("测试充值");

        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenReturn(testAccount);
        when(transactionRepository.save(any(CarbonCoinTransaction.class))).thenReturn(new CarbonCoinTransaction());

        // When
        CarbonCoinAccountResponse response = carbonCoinService.recharge(1L, request);

        // Then
        assertNotNull(response);
        verify(accountRepository, times(1)).save(any(CarbonCoinAccount.class));
        verify(transactionRepository, times(1)).save(any(CarbonCoinTransaction.class));
    }

    @Test
    @DisplayName("充值碳币失败-账户已禁用")
    void testRechargeFailAccountDisabled() {
        // Given
        testAccount.setStatus(0);
        CarbonCoinRechargeRequest request = new CarbonCoinRechargeRequest();
        request.setAmount(new BigDecimal("500.00"));

        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));

        // When & Then
        assertThrows(BusinessException.class, () -> carbonCoinService.recharge(1L, request));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("购买碳配额成功")
    void testBuyQuotaSuccess() {
        // Given
        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenReturn(testAccount);
        when(transactionRepository.save(any(CarbonCoinTransaction.class))).thenReturn(new CarbonCoinTransaction());

        // When
        CarbonCoinAccountResponse response = carbonCoinService.buyQuota(
                1L, new BigDecimal("100.00"), new BigDecimal("10.00"), 1L);

        // Then
        assertNotNull(response);
        verify(accountRepository, times(1)).save(any(CarbonCoinAccount.class));
        verify(transactionRepository, times(1)).save(any(CarbonCoinTransaction.class));
    }

    @Test
    @DisplayName("购买碳配额失败-余额不足")
    void testBuyQuotaFailInsufficientBalance() {
        // Given
        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));

        // When & Then - trying to buy more than balance (1000)
        assertThrows(BusinessException.class, () -> 
            carbonCoinService.buyQuota(1L, new BigDecimal("2000.00"), new BigDecimal("100.00"), 1L));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("出售碳配额成功")
    void testSellQuotaSuccess() {
        // Given
        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenReturn(testAccount);
        when(transactionRepository.save(any(CarbonCoinTransaction.class))).thenReturn(new CarbonCoinTransaction());

        // When
        CarbonCoinAccountResponse response = carbonCoinService.sellQuota(
                1L, new BigDecimal("100.00"), new BigDecimal("10.00"), 1L);

        // Then
        assertNotNull(response);
        verify(accountRepository, times(1)).save(any(CarbonCoinAccount.class));
        verify(transactionRepository, times(1)).save(any(CarbonCoinTransaction.class));
    }

    @DisplayName("查询交易流水")
    void testGetTransactions() {
        // Given
        CarbonCoinTransaction tx1 = CarbonCoinTransaction.builder()
                .userId(1L)
                .txType(1)
                .amount(new BigDecimal("100.00"))
                .build();
        tx1.setId(1L);
        CarbonCoinTransaction tx2 = CarbonCoinTransaction.builder()
                .userId(1L)
                .txType(2)
                .amount(new BigDecimal("50.00"))
                .build();
        tx2.setId(2L);

        Page<CarbonCoinTransaction> page = new PageImpl<>(Arrays.asList(tx1, tx2));
        when(transactionRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(page);

        // When
        Page<CarbonCoinTransaction> result = carbonCoinService.getTransactions(1L, null, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
    }

    @Test
    @DisplayName("查询交易流水-按类型筛选")
    void testGetTransactionsByType() {
        // Given
        CarbonCoinTransaction tx = CarbonCoinTransaction.builder()
                .userId(1L)
                .txType(1)
                .amount(new BigDecimal("100.00"))
                .build();
        tx.setId(1L);

        Page<CarbonCoinTransaction> page = new PageImpl<>(Arrays.asList(tx));
        when(transactionRepository.findByUserIdAndTxTypeAndDeletedFalseOrderByCreatedAtDesc(eq(1L), eq(1), any(PageRequest.class)))
                .thenReturn(page);

        // When
        Page<CarbonCoinTransaction> result = carbonCoinService.getTransactions(1L, 1, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getContent().get(0).getTxType());
    }

    // ==================== 资产冻结/解冻/扣划测试 (Escrow) ====================

    @Test
    @DisplayName("freezeBalance-成功冻结碳币资金")
    void testFreezeBalanceSuccess() {
        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenReturn(testAccount);

        CarbonCoinAccountResponse response = carbonCoinService.freezeBalance(1L, new BigDecimal("300.00"));

        assertNotNull(response);
        assertEquals(new BigDecimal("700.00"), testAccount.getBalance());
        assertEquals(new BigDecimal("300.00"), testAccount.getFrozenBalance());
        verify(accountRepository, times(1)).save(testAccount);
    }

    @Test
    @DisplayName("freezeBalance-余额不足抛出异常")
    void testFreezeBalanceInsufficient() {
        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));

        assertThrows(BusinessException.class, () ->
                carbonCoinService.freezeBalance(1L, new BigDecimal("5000.00")));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("unfreezeBalance-成功解冻碳币并返还可用余额")
    void testUnfreezeBalanceSuccess() {
        testAccount.setBalance(new BigDecimal("700.00"));
        testAccount.setFrozenBalance(new BigDecimal("300.00"));

        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenReturn(testAccount);

        CarbonCoinAccountResponse response = carbonCoinService.unfreezeBalance(1L, new BigDecimal("200.00"));

        assertNotNull(response);
        assertEquals(new BigDecimal("900.00"), testAccount.getBalance());
        assertEquals(new BigDecimal("100.00"), testAccount.getFrozenBalance());
        verify(accountRepository, times(1)).save(testAccount);
    }

    @Test
    @DisplayName("deductFrozenBalance-撮合成交扣除冻结资金并记录流水")
    void testDeductFrozenBalanceSuccess() {
        testAccount.setBalance(new BigDecimal("700.00"));
        testAccount.setFrozenBalance(new BigDecimal("300.00"));

        when(accountRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenReturn(testAccount);
        when(transactionRepository.save(any(CarbonCoinTransaction.class))).thenReturn(new CarbonCoinTransaction());

        CarbonCoinAccountResponse response = carbonCoinService.deductFrozenBalance(
                1L, new BigDecimal("200.00"), new BigDecimal("10.00"), 88L);

        assertNotNull(response);
        assertEquals(new BigDecimal("100.00"), testAccount.getFrozenBalance());
        assertEquals(new BigDecimal("1200.00"), testAccount.getTotalSpent());
        verify(accountRepository, times(1)).save(testAccount);
        verify(transactionRepository, times(1)).save(any(CarbonCoinTransaction.class));
    }

    @Test
    @DisplayName("碳币充值-企业准入证书失效时拒绝，且不产生任何资金变动")
    void testRechargeBlockedWhenAdmissionInvalid() {
        doThrow(com.oaiss.chain.exception.BusinessException.of(
                com.oaiss.chain.constant.ErrorCode.ADMISSION_REQUIRED, "error.admission.required"))
                .when(certificateGateService).assertEnterpriseAdmittedByUserId(1L);

        assertThrows(com.oaiss.chain.exception.BusinessException.class,
                () -> carbonCoinService.recharge(1L, new com.oaiss.chain.dto.CarbonCoinRechargeRequest()));

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}
