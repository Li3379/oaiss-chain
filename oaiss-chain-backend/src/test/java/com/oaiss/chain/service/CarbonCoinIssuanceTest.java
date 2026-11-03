package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonCoinAccount;
import com.oaiss.chain.entity.CarbonCoinTransaction;
import com.oaiss.chain.entity.CarbonReport;
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
 * 碳币发放与回收测试。
 *
 * <p>核心不变量：
 * <ol>
 *   <li><b>入账对象正确</b>：币必须发给报告所属企业的 userId，不是审核员</li>
 *   <li><b>幂等</b>：同一报告不重复发放；驳回重提后按新序号可再发</li>
 *   <li><b>发行累计不因回收减少</b>：totalIssued 记的是发行史实</li>
 *   <li><b>回收不改 totalIssued</b>：只减 balance</li>
 * </ol>
 */
class CarbonCoinIssuanceTest {

    private CarbonCoinAccountRepository accountRepository;
    private CarbonCoinTransactionRepository transactionRepository;
    private EnterpriseRepository enterpriseRepository;
    private CarbonReportRepository reportRepository;
    private CertificateGateService certificateGateService;
    private CarbonCoinService service;

    private Enterprise enterprise;
    private CarbonReport report;
    private CarbonCoinAccount account;

    private static final Long REPORT_ID = 100L;
    private static final Long ENTERPRISE_ID = 10L;
    private static final Long OWNER_USER_ID = 7L;
    private static final Long OPERATOR_ID = 99L; // 审核员，不应收到币

    @BeforeEach
    void setUp() {
        accountRepository = mock(CarbonCoinAccountRepository.class);
        transactionRepository = mock(CarbonCoinTransactionRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);
        reportRepository = mock(CarbonReportRepository.class);
        certificateGateService = mock(CertificateGateService.class);

        service = new CarbonCoinService(accountRepository, transactionRepository,
                certificateGateService, enterpriseRepository, reportRepository,
                mock(QuotaLedgerService.class));

        enterprise = Enterprise.builder().userId(OWNER_USER_ID).enterpriseName("测试企业").build();
        enterprise.setId(ENTERPRISE_ID);

        report = CarbonReport.builder()
                .reportNo("CR2026Q1")
                .enterpriseId(ENTERPRISE_ID)
                .status(5)
                .scope1Emission(new BigDecimal("700.0000"))
                .build();
        report.setId(REPORT_ID);

        account = CarbonCoinAccount.builder()
                .userId(OWNER_USER_ID)
                .balance(new BigDecimal("1000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .totalRecharged(new BigDecimal("1000.00"))
                .totalSpent(BigDecimal.ZERO)
                .totalIssued(BigDecimal.ZERO)
                .status(1)
                .build();
        account.setId(1L);

        when(reportRepository.findByIdAndDeletedFalse(REPORT_ID)).thenReturn(Optional.of(report));
        when(enterpriseRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(enterprise));
        when(accountRepository.findByUserIdAndDeletedFalse(OWNER_USER_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(CarbonCoinAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any(CarbonCoinTransaction.class))).thenAnswer(i -> i.getArgument(0));
        // 回收需要「该报告已发放多少」——默认无发放记录（发放用例各自覆盖）
        when(transactionRepository.findByRelatedReportIdAndDeletedFalseOrderByIdAsc(REPORT_ID))
                .thenReturn(java.util.List.of());
    }

    /** 构造一条 ISSUE 流水，供回收用例的「已发放量」查询使用 */
    private CarbonCoinTransaction issueRow(String amount) {
        return CarbonCoinTransaction.builder()
                .txNo("CCT-TEST")
                .userId(OWNER_USER_ID)
                .txType(CarbonCoinService.TX_TYPE_ISSUE)
                .amount(new BigDecimal(amount))
                .relatedReportId(REPORT_ID)
                .relatedSeq(1)
                .build();
    }

    // ==================== 发放 ====================

    @Test
    @DisplayName("发放：余额与发行累计同增，且入账是企业的 userId")
    void issue_increasesBalanceAndTotalIssued() {
        when(transactionRepository.countByRelatedReportIdAndRelatedSeqIsNotNull(REPORT_ID)).thenReturn(0L);

        service.issueForReport(REPORT_ID, new BigDecimal("285.8024"), OPERATOR_ID);

        assertEquals(0, new BigDecimal("1285.80").compareTo(account.getBalance()),
                "余额应 1000.00 → 1285.80");
        assertEquals(0, new BigDecimal("285.80").compareTo(account.getTotalIssued()),
                "发行累计应 0 → 285.80");

        // 关键：验证币进了企业账户，而不是审核员账户
        verify(accountRepository).findByUserIdAndDeletedFalse(OWNER_USER_ID);
        verify(accountRepository, never()).findByUserIdAndDeletedFalse(OPERATOR_ID);
    }

    @Test
    @DisplayName("发放流水：ISSUE 类型、带报告ID与序号")
    void issue_writesLedgerWithReportDimension() {
        when(transactionRepository.countByRelatedReportIdAndRelatedSeqIsNotNull(REPORT_ID)).thenReturn(0L);

        service.issueForReport(REPORT_ID, new BigDecimal("285.8024"), OPERATOR_ID);

        ArgumentCaptor<CarbonCoinTransaction> captor = ArgumentCaptor.forClass(CarbonCoinTransaction.class);
        verify(transactionRepository).save(captor.capture());
        CarbonCoinTransaction tx = captor.getValue();

        assertEquals(Integer.valueOf(CarbonCoinService.TX_TYPE_ISSUE), tx.getTxType());
        assertEquals(Long.valueOf(REPORT_ID), tx.getRelatedReportId());
        assertEquals(Integer.valueOf(1), tx.getRelatedSeq());
        assertEquals(0, new BigDecimal("285.80").compareTo(tx.getAmount()));
    }

    @Test
    @DisplayName("幂等：该报告已发过 → 跳过，余额不变")
    void issue_isIdempotent() {
        when(transactionRepository.countByRelatedReportIdAndRelatedSeqIsNotNull(REPORT_ID)).thenReturn(1L);

        service.issueForReport(REPORT_ID, new BigDecimal("285.8024"), OPERATOR_ID);

        assertEquals(0, new BigDecimal("1000.00").compareTo(account.getBalance()), "余额不应变化");
        verify(transactionRepository, never()).save(any(CarbonCoinTransaction.class));
    }

    @Test
    @DisplayName("驳回后重提再通过：序号递增为 2，可以再发")
    void issue_incrementsSeqAfterRevoke() {
        when(transactionRepository.countByRelatedReportIdAndRelatedSeqIsNotNull(REPORT_ID)).thenReturn(1L);
        // 已有 1 次发放，但这是新一轮（调用方已确认要重发，故 bypass 幂等）

        service.reissueForReport(REPORT_ID, new BigDecimal("100.00"), OPERATOR_ID);

        ArgumentCaptor<CarbonCoinTransaction> captor = ArgumentCaptor.forClass(CarbonCoinTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(Integer.valueOf(2), captor.getValue().getRelatedSeq(), "序号应为 2");
    }

    @Test
    @DisplayName("盈余非正 → 不发放、不写流水")
    void issue_skipsNonPositiveSurplus() {
        service.issueForReport(REPORT_ID, BigDecimal.ZERO, OPERATOR_ID);
        verify(transactionRepository, never()).save(any(CarbonCoinTransaction.class));

        service.issueForReport(REPORT_ID, new BigDecimal("-10"), OPERATOR_ID);
        verify(transactionRepository, never()).save(any(CarbonCoinTransaction.class));
    }

    @Test
    @DisplayName("准入证书门禁：未通过则拒绝发放")
    void issue_requiresAdmission() {
        doThrow(new BusinessException(7001, "企业未通过准入"))
                .when(certificateGateService).assertEnterpriseAdmittedByUserId(OWNER_USER_ID);

        assertThrows(BusinessException.class,
                () -> service.issueForReport(REPORT_ID, new BigDecimal("100"), OPERATOR_ID));
        verify(transactionRepository, never()).save(any(CarbonCoinTransaction.class));
    }

    // ==================== 回收 ====================

    @Test
    @DisplayName("回收：余额减、发行累计不变")
    void clawback_decreasesBalanceKeepsTotalIssued() {
        account.setBalance(new BigDecimal("1285.80"));
        account.setTotalIssued(new BigDecimal("285.80"));
        when(transactionRepository.findByRelatedReportIdAndDeletedFalseOrderByIdAsc(REPORT_ID))
                .thenReturn(java.util.List.of(issueRow("285.80")));

        service.clawbackForReport(REPORT_ID, OPERATOR_ID);

        assertEquals(0, new BigDecimal("1000.00").compareTo(account.getBalance()),
                "余额应扣回发放量");
        assertEquals(0, new BigDecimal("285.80").compareTo(account.getTotalIssued()),
                "发行累计不得减少——发行是史实");
    }

    @Test
    @DisplayName("回收流水：CLAWBACK 类型、序号为 null")
    void clawback_writesLedgerWithNullSeq() {
        account.setBalance(new BigDecimal("1285.80"));
        account.setTotalIssued(new BigDecimal("285.80"));
        when(transactionRepository.findByRelatedReportIdAndDeletedFalseOrderByIdAsc(REPORT_ID))
                .thenReturn(java.util.List.of(issueRow("285.80")));

        service.clawbackForReport(REPORT_ID, OPERATOR_ID);

        ArgumentCaptor<CarbonCoinTransaction> captor = ArgumentCaptor.forClass(CarbonCoinTransaction.class);
        verify(transactionRepository).save(captor.capture());
        CarbonCoinTransaction tx = captor.getValue();

        assertEquals(Integer.valueOf(CarbonCoinService.TX_TYPE_CLAWBACK), tx.getTxType());
        assertEquals(Long.valueOf(REPORT_ID), tx.getRelatedReportId());
        assertNull(tx.getRelatedSeq(), "回收行 seq 必须为 null，否则与重发的新序号冲突");
    }

    @Test
    @DisplayName("回收：余额不足 → 拒绝（不产生负余额）")
    void clawback_rejectsWhenBalanceInsufficient() {
        account.setBalance(new BigDecimal("10.00"));
        account.setTotalIssued(new BigDecimal("285.80"));
        when(transactionRepository.findByRelatedReportIdAndDeletedFalseOrderByIdAsc(REPORT_ID))
                .thenReturn(java.util.List.of(issueRow("285.80")));

        assertThrows(BusinessException.class,
                () -> service.clawbackForReport(REPORT_ID, OPERATOR_ID));
    }
}
