package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonCoinAccount;
import com.oaiss.chain.entity.CarbonCoinTransaction;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonCoinAccountRepository;
import com.oaiss.chain.repository.CarbonCoinTransactionRepository;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 碳币兑换配额测试。
 *
 * <p>模型：1 枚碳币 = 1 吨排放权。企业用碳币换配额后，配额进入
 * {@code carbonTradable}，才可用于履约或挂单。
 *
 * <p>核心不变量：
 * <ol>
 *   <li>扣币与加配额必须原子（同事务）</li>
 *   <li>余额不足要拒绝，不能扣成负数</li>
 *   <li>兑换量必须是正数</li>
 *   <li>流水类型是 REDEEM_QUOTA，可追溯</li>
 * </ol>
 */
class CarbonCoinRedeemTest {

    private CarbonCoinAccountRepository accountRepository;
    private CarbonCoinTransactionRepository transactionRepository;
    private EnterpriseRepository enterpriseRepository;
    private CarbonReportRepository reportRepository;
    private QuotaLedgerService quotaLedgerService;
    private CarbonCoinService service;

    private CarbonCoinAccount account;
    private Enterprise enterprise;

    private static final Long USER_ID = 7L;
    private static final Long ENTERPRISE_ID = 10L;

    @BeforeEach
    void setUp() {
        accountRepository = mock(CarbonCoinAccountRepository.class);
        transactionRepository = mock(CarbonCoinTransactionRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);
        reportRepository = mock(CarbonReportRepository.class);
        quotaLedgerService = mock(QuotaLedgerService.class);

        service = new CarbonCoinService(accountRepository, transactionRepository,
                mock(CertificateGateService.class), enterpriseRepository, reportRepository,
                quotaLedgerService);

        account = CarbonCoinAccount.builder()
                .userId(USER_ID)
                .balance(new BigDecimal("1000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .totalRecharged(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .totalIssued(new BigDecimal("1000.00"))
                .status(1)
                .build();
        account.setId(1L);

        enterprise = Enterprise.builder().userId(USER_ID).enterpriseName("测试企业").build();
        enterprise.setId(ENTERPRISE_ID);
        enterprise.setCarbonTradable(new BigDecimal("500.0000"));

        when(accountRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any(CarbonCoinTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(enterpriseRepository.findByUserId(USER_ID)).thenReturn(Optional.of(enterprise));
    }

    @Test
    @DisplayName("兑换：扣碳币、配额入账（1:1）")
    void redeem_deductsCoinAndCreditsQuota() {
        service.redeemQuota(USER_ID, new BigDecimal("285.80"), null);

        assertEquals(0, new BigDecimal("714.20").compareTo(account.getBalance()),
                "余额应 1000.00 → 714.20");
        verify(quotaLedgerService).increase(eq(ENTERPRISE_ID), eq(new BigDecimal("285.8000")),
                anyString(), eq(USER_ID), anyString(), isNull());
    }

    @Test
    @DisplayName("兑换流水：REDEEM_QUOTA 类型、记录配额量")
    void redeem_writesLedger() {
        service.redeemQuota(USER_ID, new BigDecimal("285.80"), null);

        ArgumentCaptor<CarbonCoinTransaction> captor = ArgumentCaptor.forClass(CarbonCoinTransaction.class);
        verify(transactionRepository).save(captor.capture());
        CarbonCoinTransaction tx = captor.getValue();

        assertEquals(Integer.valueOf(CarbonCoinService.TX_TYPE_REDEEM_QUOTA), tx.getTxType());
        assertNotNull(tx.getRelatedQuota(), "兑换流水应记录换到多少配额");
        assertEquals(0, new BigDecimal("285.80").compareTo(tx.getAmount()));
    }

    @Test
    @DisplayName("余额不足 → 拒绝，不扣币不加配额")
    void redeem_rejectsInsufficientBalance() {
        assertThrows(BusinessException.class,
                () -> service.redeemQuota(USER_ID, new BigDecimal("5000"), null));

        assertEquals(0, new BigDecimal("1000.00").compareTo(account.getBalance()),
                "失败的兑换不得改变余额");
        verify(quotaLedgerService, never()).increase(anyLong(), any(), anyString(),
                anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("兑换量非正 → 拒绝")
    void redeem_rejectsNonPositive() {
        assertThrows(BusinessException.class,
                () -> service.redeemQuota(USER_ID, BigDecimal.ZERO, null));
        assertThrows(BusinessException.class,
                () -> service.redeemQuota(USER_ID, new BigDecimal("-10"), null));
        verify(transactionRepository, never()).save(any(CarbonCoinTransaction.class));
    }

    @Test
    @DisplayName("冻结余额不参与兑换：可用余额不足即拒绝")
    void redeem_onlyUsesAvailableBalance() {
        account.setBalance(new BigDecimal("100.00"));
        account.setFrozenBalance(new BigDecimal("900.00"));

        // 总额 1000 但可用只有 100
        assertThrows(BusinessException.class,
                () -> service.redeemQuota(USER_ID, new BigDecimal("500"), null));
    }
}
