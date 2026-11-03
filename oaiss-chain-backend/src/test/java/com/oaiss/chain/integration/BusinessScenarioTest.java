package com.oaiss.chain.integration;

import com.oaiss.chain.H2IntegrationTest;
import com.oaiss.chain.dto.*;
import com.oaiss.chain.entity.*;
import com.oaiss.chain.enums.*;
import com.oaiss.chain.repository.*;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full-service integration tests simulating real business scenarios.
 * Covers end-to-end flows across all major modules.
 */
class BusinessScenarioTest extends H2IntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private EnterpriseRepository enterpriseRepository;
    @Autowired private CarbonReportRepository carbonReportRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CarbonCoinAccountRepository carbonCoinAccountRepository;
    @Autowired private CarbonCoinTransactionRepository carbonCoinTransactionRepository;
    @Autowired private CreditScoreRepository creditScoreRepository;
    @Autowired private CreditEventRepository creditEventRepository;
    @Autowired private CarbonNeutralProjectRepository carbonNeutralProjectRepository;
    @Autowired private RsaKeyPairRepository rsaKeyPairRepository;
    @Autowired private EmissionRatingRepository emissionRatingRepository;
    @Autowired private ReviewerRepository reviewerRepository;
    @Autowired private ThirdPartyOrgRepository thirdPartyOrgRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private CarbonService carbonService;
    @Autowired private TradeService tradeService;
    @Autowired private CarbonCoinService carbonCoinService;
    @Autowired private CreditScoreService creditScoreService;
    @Autowired private CarbonNeutralProjectService carbonNeutralProjectService;
    @Autowired private DigitalSignatureService digitalSignatureService;
    @Autowired private EmissionRatingService emissionRatingService;
    @Autowired private EnterpriseAdmissionRepository enterpriseAdmissionRepository;
    @Autowired private ReviewerQualificationRepository reviewerQualificationRepository;

    private User enterpriseUser;
    private User reviewerUser;
    private User adminUser;
    private User thirdPartyUser;
    private User buyerUser;
    private Enterprise enterprise;
    private Enterprise buyerEnterprise;
    private Reviewer reviewerRecord;

    @BeforeEach
    void setUp() {
        carbonReportRepository.deleteAll();
        transactionRepository.deleteAll();
        carbonCoinTransactionRepository.deleteAll();
        carbonCoinAccountRepository.deleteAll();
        creditEventRepository.deleteAll();
        creditScoreRepository.deleteAll();
        carbonNeutralProjectRepository.deleteAll();
        rsaKeyPairRepository.deleteAll();
        emissionRatingRepository.deleteAll();
        enterpriseAdmissionRepository.deleteAll();
        reviewerQualificationRepository.deleteAll();
        enterpriseRepository.deleteAll();
        reviewerRepository.deleteAll();
        thirdPartyOrgRepository.deleteAll();
        userRepository.deleteAll();

        enterpriseUser = createUser("enterprise001", "Test123456", 1, "enterprise@example.com");
        reviewerUser = createUser("reviewer001", "Test123456", 2, "reviewer@example.com");
        adminUser = createUser("admin001", "Test123456", 4, "admin@example.com");
        thirdPartyUser = createUser("thirdparty001", "Test123456", 3, "thirdparty@example.com");
        buyerUser = createUser("enterprise002", "Test123456", 1, "buyer@example.com");

        enterprise = createEnterprise(enterpriseUser, "Test Enterprise", "91110000MA00ABCD12",
                new BigDecimal("10000"), new BigDecimal("5000"), new BigDecimal("5000"));
        buyerEnterprise = createEnterprise(buyerUser, "Buyer Enterprise", "91110000MA00ABCD13",
                new BigDecimal("8000"), new BigDecimal("2000"), new BigDecimal("6000"));

        // 真实系统里审核员有独立的 reviewer 档案（种子数据即如此）；测试夹具此前缺失，
        // 因为旧版审核路径不查资格证，缺了也不影响。门禁生效后必须补齐。
        reviewerRecord = reviewerRepository.save(Reviewer.builder()
                .userId(reviewerUser.getId())
                .name(reviewerUser.getRealName() == null ? reviewerUser.getUsername() : reviewerUser.getRealName())
                .qualificationNo("RQ-SEED-001")
                .organization("OAISS管理中心")
                .status(1)
                .build());

        // 证书门禁（2026-10-31）生效后，未持准入证书的企业不得提交报告、挂单、成交或充值。
        // 这些用例验证的是「合规主体」的业务闭环，因此测试夹具必须像真实系统一样先签发证书；
        // 否则它们测到的其实是「门禁拒绝」而非业务本身。
        grantAdmission(enterprise);
        grantAdmission(buyerEnterprise);
        grantReviewerQualification(reviewerUser);
    }

    /**
     * 为交易结算路径预置碳币账户。
     *
     * <p>结算走 buyQuota/sellQuota，要求账户已存在（找不到即 4002）；
     * 真实系统由注册流程建账，夹具按需补建。刻意不放进全局 setUp：
     * 部分用例（如充值用例）以「零余额」为前提，全局建账会改变它们的断言基线。</p>
     */
    private void ensureCoinAccount(Long userId, String balance) {
        if (carbonCoinAccountRepository.findByUserIdAndDeletedFalse(userId).isPresent()) {
            return;
        }
        carbonCoinAccountRepository.save(CarbonCoinAccount.builder()
                .userId(userId)
                .balance(new BigDecimal(balance))
                .totalRecharged(new BigDecimal(balance))
                .totalSpent(BigDecimal.ZERO)
                .status(1)
                .build());
    }

    /**
     * 建立健康的信誉分（>= TradeAuditProperties.minCreditScore，默认 40）。
     *
     * <p>W3 起 P2P 确认与拍卖同口径走风控分流：无信誉记录会被按「最低分」处理，
     * 从而命中 LOW_CREDIT 转人工。验证「正常成交路径」的用例必须先备好信誉分。</p>
     *
     * <p><b>参数是 enterpriseId 不是 userId</b>：CreditScore 按企业维度存储，
     * {@code getScoresByUserIds} 内部会先做 userId → enterpriseId 的两跳转换。
     * 用 userId 建记录会导致查不到而仍然转审（此处踩过一次）。</p>
     */
    private void ensureHealthyCredit(Long enterpriseId) {
        if (creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId).isPresent()) {
            return;
        }
        creditScoreRepository.save(CreditScore.builder()
                .enterpriseId(enterpriseId)
                .score(80)
                .level("GOOD")
                .tradeRestricted(false)
                .accountFrozen(false)
                .build());
    }

    private void grantAdmission(Enterprise target) {
        enterpriseAdmissionRepository.save(EnterpriseAdmission.builder()
                .enterpriseId(target.getId())
                .certificateNo("EA-TEST-" + target.getId())
                .issuedDate(java.time.LocalDate.now())
                .expiryDate(java.time.LocalDate.now().plusYears(1))
                .status(QualificationStatusEnum.ACTIVE.getCode())
                .build());
    }

    private void grantReviewerQualification(User user) {
        reviewerQualificationRepository.save(ReviewerQualification.builder()
                .reviewerId(reviewerRecord.getId())
                .qualificationType("碳排放审核资质")
                .certificateNo("RQ-TEST-" + reviewerRecord.getId())
                .issuedDate(java.time.LocalDate.now())
                .expiryDate(java.time.LocalDate.now().plusYears(1))
                .status(QualificationStatusEnum.ACTIVE.getCode())
                .build());
    }

    @Nested
    @DisplayName("Carbon Report Lifecycle")
    class CarbonReportLifecycle {

        @Test
        @DisplayName("Full lifecycle: draft -> submit -> approve")
        void fullReportLifecycle_Draft_Submit_Approve() {
            JwtUserDetails enterpriseDetails = createJwtDetails(enterpriseUser, "ENTERPRISE");
            CarbonReportRequest createRequest = CarbonReportRequest.builder()
                    .accountingPeriod("2025-Q1")
                    .title("Q1 2025 Carbon Report")
                    .reportType(1)
                    .emissionData("{\"scope1\":1000,\"scope2\":500,\"scope3\":200}")
                    .calculationMethod("GB/T 32150-2015")
                    .build();

            CarbonReportResponse draft = carbonService.createReport(enterpriseDetails, createRequest);
            assertNotNull(draft);
            assertEquals(ReportStatusEnum.DRAFT.getCode(), draft.getStatus());

            CarbonReportResponse submitted = carbonService.submitReport(enterpriseDetails, draft.getId());
            assertEquals(ReportStatusEnum.SUBMITTED.getCode(), submitted.getStatus());

            JwtUserDetails reviewerDetails = createJwtDetails(reviewerUser, "REVIEWER");
            ReviewRequest reviewRequest = ReviewRequest.builder()
                    .reportId(draft.getId())
                    .reviewResult(ReportStatusEnum.APPROVED.getCode())
                    .reviewComment("Approved")
                    .build();

            // W0 语义变更：Mock 模式不再伪造 txHash（历史上写入 tx_mock_ 假存证，
            // 实测在库里留下 11 份，对账页把它们当成已上链）。
            // 现在审核通过后，上链失败被 autoCommitToChain 捕获，报告停留在 APPROVED(3)，
            // 由 /carbon/certify 幂等补救入口重试。这才是「有链就上、没链不假装」。
            CarbonReportResponse approved = carbonService.reviewReport(reviewerDetails, reviewRequest);
            assertEquals(ReportStatusEnum.APPROVED.getCode(), approved.getStatus(),
                    "无真实 Fabric 时报告应停在审核通过，不得被 Mock 假装成已上链");

            CarbonReport savedReport = carbonReportRepository.findById(draft.getId()).orElse(null);
            assertNotNull(savedReport);
            assertEquals(ReportStatusEnum.APPROVED.getCode(), savedReport.getStatus());
            assertNull(savedReport.getBlockchainTxHash(),
                    "Mock 模式必须不产出任何 txHash，否则与真实存证无法区分");
        }

        @Test
        @DisplayName("Report rejected then resubmitted")
        void reportRejected_ThenResubmit() {
            JwtUserDetails enterpriseDetails = createJwtDetails(enterpriseUser, "ENTERPRISE");
            CarbonReportRequest createRequest = CarbonReportRequest.builder()
                    .accountingPeriod("2025-Q2")
                    .title("Q2 2025 Carbon Report")
                    .reportType(1)
                    .emissionData("{\"scope1\":800,\"scope2\":400,\"scope3\":150}")
                    .calculationMethod("GB/T 32150-2015")
                    .build();

            CarbonReportResponse draft = carbonService.createReport(enterpriseDetails, createRequest);
            carbonService.submitReport(enterpriseDetails, draft.getId());

            JwtUserDetails reviewerDetails = createJwtDetails(reviewerUser, "REVIEWER");
            ReviewRequest rejectRequest = ReviewRequest.builder()
                    .reportId(draft.getId())
                    .reviewResult(ReportStatusEnum.REJECTED.getCode())
                    .reviewComment("Incomplete data")
                    .build();

            CarbonReportResponse rejected = carbonService.reviewReport(reviewerDetails, rejectRequest);
            assertEquals(ReportStatusEnum.REJECTED.getCode(), rejected.getStatus());

            CarbonReportResponse resubmitted = carbonService.submitReport(enterpriseDetails, draft.getId());
            assertEquals(ReportStatusEnum.SUBMITTED.getCode(), resubmitted.getStatus());
        }

        @Test
        @DisplayName("Non-enterprise user cannot create report")
        void nonEnterpriseUser_CannotCreateReport() {
            JwtUserDetails reviewerDetails = createJwtDetails(reviewerUser, "REVIEWER");
            CarbonReportRequest request = CarbonReportRequest.builder()
                    .accountingPeriod("2025-Q1")
                    .title("Test")
                    .reportType(1)
                    .emissionData("{\"scope1\":100}")
                    .build();

            assertThrows(Exception.class, () -> carbonService.createReport(reviewerDetails, request));
        }
    }

    @Nested
    @DisplayName("Carbon Trading")
    class CarbonTradingScenario {

        @Test
        @DisplayName("P2P trade: create -> confirm -> complete")
        void p2pTradeFullFlow_Create_Confirm_Complete() {
            JwtUserDetails sellerDetails = createJwtDetails(enterpriseUser, "ENTERPRISE");

            // W3 起 P2P 确认与拍卖同口径走风控分流：无信誉记录会被按「最低分」处理，
            // 从而命中 LOW_CREDIT 转人工审核（这是正确行为，不是缺陷）。
            // 本用例要验证的是「正常成交路径」，因此先给双方建立健康的信誉分。
            ensureHealthyCredit(enterprise.getId());
            ensureHealthyCredit(buyerEnterprise.getId());

            // 2026-11-03：P2P 改为「转让碳币」，卖方**挂单时**就冻结碳币，
            // 所以账户必须在 createP2PTrade 之前备好——原来放在 confirm 之前，
            // 那时挂单已经因为余额不足失败了。
            ensureCoinAccount(enterpriseUser.getId(), "10000.00");
            ensureCoinAccount(buyerUser.getId(), "0.00");

            TradeRequest tradeRequest = TradeRequest.builder()
                    .tradeType(TradeTypeEnum.P2P.getCode())
                    .buyerId(buyerUser.getId())
                    .quantity(new BigDecimal("100"))
                    .unitPrice(new BigDecimal("50.00"))
                    .build();

            TradeResponse trade = tradeService.createP2PTrade(sellerDetails, tradeRequest);
            assertNotNull(trade);
            assertEquals(TradeStatusEnum.PENDING.getCode(), trade.getStatus());

            TradeResponse confirmed = tradeService.confirmTrade(trade.getId(), buyerUser.getId());
            assertEquals(TradeStatusEnum.COMPLETED.getCode(), confirmed.getStatus());

            Transaction savedTx = transactionRepository.findById(trade.getId()).orElse(null);
            assertNotNull(savedTx);
            assertEquals(TradeStatusEnum.COMPLETED.getCode(), savedTx.getStatus());
        }

        @Test
        @DisplayName("Seller can cancel pending trade")
        void sellerCanCancelPendingTrade() {
            // 2026-11-03：挂单要冻结碳币，卖方必须有余额
            ensureCoinAccount(enterpriseUser.getId(), "10000.00");
            JwtUserDetails sellerDetails = createJwtDetails(enterpriseUser, "ENTERPRISE");
            TradeRequest tradeRequest = TradeRequest.builder()
                    .tradeType(TradeTypeEnum.P2P.getCode())
                    .buyerId(buyerUser.getId())
                    .quantity(new BigDecimal("50"))
                    .unitPrice(new BigDecimal("45.00"))
                    .build();

            TradeResponse trade = tradeService.createP2PTrade(sellerDetails, tradeRequest);
            TradeResponse cancelled = tradeService.cancelTrade(trade.getId(), sellerDetails);
            assertEquals(TradeStatusEnum.CANCELLED.getCode(), cancelled.getStatus());
        }

        @Test
        @DisplayName("Cannot trade with self")
        void cannotTradeWithSelf() {
            JwtUserDetails sellerDetails = createJwtDetails(enterpriseUser, "ENTERPRISE");
            TradeRequest tradeRequest = TradeRequest.builder()
                    .tradeType(TradeTypeEnum.P2P.getCode())
                    .buyerId(enterpriseUser.getId())
                    .quantity(new BigDecimal("100"))
                    .unitPrice(new BigDecimal("50"))
                    .build();

            assertThrows(Exception.class, () -> tradeService.createP2PTrade(sellerDetails, tradeRequest));
        }
    }

    @Nested
    @DisplayName("Carbon Coin")
    class CarbonCoinScenario {

        @Test
        @DisplayName("Full flow: recharge -> balance")
        void carbonCoinFullFlow_Recharge_Balance() {
            // Create accounts first
            carbonCoinService.getOrCreateAccount(enterpriseUser.getId());
            carbonCoinService.getOrCreateAccount(buyerUser.getId());

            CarbonCoinRechargeRequest rechargeRequest = new CarbonCoinRechargeRequest();
            rechargeRequest.setAmount(new BigDecimal("10000"));
            rechargeRequest.setRemark("Initial recharge");

            carbonCoinService.recharge(enterpriseUser.getId(), rechargeRequest);

            CarbonCoinAccount account = carbonCoinAccountRepository.findByUserIdAndDeletedFalse(enterpriseUser.getId()).orElse(null);
            assertNotNull(account);
            assertEquals(0, new BigDecimal("10000").compareTo(account.getBalance()));





            List<CarbonCoinTransaction> transactions = carbonCoinTransactionRepository.findAll();
            assertTrue(transactions.size() >= 1);
        }
    }

    @Nested
    @DisplayName("Credit Score")
    class CreditScoreScenario {

        @Test
        @DisplayName("Full flow: init -> deduct -> bonus")
        void creditScoreFullFlow_Init_Deduct_Bonus() {
            CreditScore score = CreditScore.builder()
                    .enterpriseId(enterprise.getId())
                    .score(80)
                    .level("GOOD")
                    .build();
            creditScoreRepository.save(score);

            creditScoreService.deductPoints(enterprise.getId(), 1, "Incomplete data", adminUser.getId(), null);

            CreditScore updatedScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterprise.getId()).orElse(null);
            assertNotNull(updatedScore);
            assertTrue(updatedScore.getScore() < 80, "Score should decrease after deduction");

            creditScoreService.addBonusPoints(enterprise.getId(), 10, "Active participation", adminUser.getId());

            CreditScore finalScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterprise.getId()).orElse(null);
            assertNotNull(finalScore);
            assertTrue(finalScore.getScore() > updatedScore.getScore(), "Score should increase after bonus");

            List<CreditEvent> events = creditEventRepository.findAll();
            assertTrue(events.size() >= 2);
        }
    }

    @Nested
    @DisplayName("Carbon Neutral Project")
    class CarbonNeutralProjectScenario {

        @Test
        @DisplayName("Full lifecycle: create -> submit -> approve -> implement")
        void projectFullLifecycle_Create_Submit_Review_Start() {
            JwtUserDetails enterpriseDetails = createJwtDetails(enterpriseUser, "ENTERPRISE");
            CarbonNeutralProjectRequest createRequest = CarbonNeutralProjectRequest.builder()
                    .projectName("Forestry Carbon Sink")
                    .projectType(1)
                    .description("Afforestation project in Inner Mongolia")
                    .expectedReduction(new BigDecimal("5000"))
                    .location("Inner Mongolia")
                    .build();

            CarbonNeutralProjectResponse project = carbonNeutralProjectService.createProject(enterpriseDetails, createRequest);
            assertNotNull(project);
            assertEquals(0, project.getStatus());

            CarbonNeutralProjectResponse submitted = carbonNeutralProjectService.submitForReview(enterpriseDetails, project.getId());
            assertEquals(1, submitted.getStatus());

            JwtUserDetails reviewerDetails = createJwtDetails(reviewerUser, "REVIEWER");
            CarbonNeutralProjectResponse reviewed = carbonNeutralProjectService.reviewProject(reviewerDetails, project.getId(), true, "Approved");
            assertEquals(2, reviewed.getStatus());

            CarbonNeutralProjectResponse started = carbonNeutralProjectService.startImplementation(enterpriseDetails, project.getId());
            assertEquals(3, started.getStatus());

            CarbonNeutralProject savedProject = carbonNeutralProjectRepository.findById(project.getId()).orElse(null);
            assertNotNull(savedProject);
            assertEquals(3, savedProject.getStatus());
        }

        @Test
        @DisplayName("Project rejected then resubmitted")
        void projectRejected_CanResubmit() {
            JwtUserDetails enterpriseDetails = createJwtDetails(enterpriseUser, "ENTERPRISE");
            CarbonNeutralProjectRequest createRequest = CarbonNeutralProjectRequest.builder()
                    .projectName("Energy Retrofit")
                    .projectType(4)
                    .description("Factory energy retrofit")
                    .expectedReduction(new BigDecimal("2000"))
                    .location("Shanghai")
                    .build();

            CarbonNeutralProjectResponse project = carbonNeutralProjectService.createProject(enterpriseDetails, createRequest);
            carbonNeutralProjectService.submitForReview(enterpriseDetails, project.getId());

            JwtUserDetails reviewerDetails = createJwtDetails(reviewerUser, "REVIEWER");
            CarbonNeutralProjectResponse rejected = carbonNeutralProjectService.reviewProject(reviewerDetails, project.getId(), false, "Needs more detail");
            assertEquals(6, rejected.getStatus());

            CarbonNeutralProjectResponse resubmitted = carbonNeutralProjectService.submitForReview(enterpriseDetails, project.getId());
            assertEquals(1, resubmitted.getStatus());
        }
    }

    @Nested
    @DisplayName("Digital Signature")
    class DigitalSignatureScenario {

        @Test
        @DisplayName("Full flow: generate key -> sign -> verify")
        void signatureFullFlow_Generate_Sign_Verify() {
            RsaKeyPairResponse keyPair = digitalSignatureService.generateKeyPair(enterpriseUser.getId());
            assertNotNull(keyPair);
            assertNotNull(keyPair.getPublicKey());

            String reportData = "{\"reportId\":\"RPT-2025-001\",\"totalEmission\":1700,\"period\":\"2025-Q1\"}";
            SignatureResult signResult = digitalSignatureService.signReport(enterpriseUser.getId(), reportData);
            assertNotNull(signResult);
            assertNotNull(signResult.getSignature());

            boolean isValid = digitalSignatureService.verifySignature(enterpriseUser.getId(), reportData, signResult.getSignature());
            assertTrue(isValid);

            boolean isTampered = digitalSignatureService.verifySignature(enterpriseUser.getId(),
                    "{\"reportId\":\"RPT-2025-001\",\"totalEmission\":9999,\"period\":\"2025-Q1\"}",
                    signResult.getSignature());
            assertFalse(isTampered);
        }
    }

    @Nested
    @DisplayName("Emission Rating")
    class EmissionRatingScenario {

        @Test
        @DisplayName("Full flow: generate rating -> query history -> ranking")
        void emissionRatingFullFlow_Generate_History_Ranking() {
            EmissionRating rating = emissionRatingService.rateEnterprise(
                    enterprise.getId(), "2025",
                    new BigDecimal("5000"), new BigDecimal("10000"),
                    adminUser.getId());
            assertNotNull(rating);
            assertNotNull(rating.getRatingLevel());

            List<EmissionRating> history = emissionRatingService.getEnterpriseRatings(enterprise.getId());
            assertFalse(history.isEmpty());

            List<EmissionRating> rankings = emissionRatingService.getIndustryRanking("2025");
            assertNotNull(rankings);
        }
    }

    @Nested
    @DisplayName("Cross-Service Business Flow")
    class CrossServiceBusinessFlow {

        @Test
        @DisplayName("Full chain: report -> trade -> coin -> credit")
        void fullBusinessChain_Report_Trade_Coin_Credit() {
            // Step 1: Create and approve carbon report
            JwtUserDetails enterpriseDetails = createJwtDetails(enterpriseUser, "ENTERPRISE");
            CarbonReportRequest reportRequest = CarbonReportRequest.builder()
                    .accountingPeriod("2025-Q1")
                    .title("Annual Carbon Report")
                    .reportType(2)
                    .emissionData("{\"scope1\":1200,\"scope2\":600,\"scope3\":300}")
                    .calculationMethod("GB/T 32150-2015")
                    .build();

            CarbonReportResponse report = carbonService.createReport(enterpriseDetails, reportRequest);
            carbonService.submitReport(enterpriseDetails, report.getId());

            JwtUserDetails reviewerDetails = createJwtDetails(reviewerUser, "REVIEWER");
            ReviewRequest approveRequest = ReviewRequest.builder()
                    .reportId(report.getId())
                    .reviewResult(ReportStatusEnum.APPROVED.getCode())
                    .reviewComment("Approved")
                    .build();
            carbonService.reviewReport(reviewerDetails, approveRequest);

            // Step 2: Create P2P trade
            // W3：无信誉记录会被按最低分处理而转人工审核，本用例验证完整业务链，
            // 故先备好双方健康信誉分。
            ensureHealthyCredit(enterprise.getId());
            ensureHealthyCredit(buyerEnterprise.getId());

            // 2026-11-03：卖方**挂单时**就要冻结碳币，账户必须提前备好
            ensureCoinAccount(enterpriseUser.getId(), "20000.00");
            ensureCoinAccount(buyerUser.getId(), "0.00");

            TradeRequest tradeRequest = TradeRequest.builder()
                    .tradeType(TradeTypeEnum.P2P.getCode())
                    .buyerId(buyerUser.getId())
                    .quantity(new BigDecimal("200"))
                    .unitPrice(new BigDecimal("55.00"))
                    .build();

            TradeResponse trade = tradeService.createP2PTrade(enterpriseDetails, tradeRequest);

            // Step 3: Buyer confirms trade
            tradeService.confirmTrade(trade.getId(), buyerUser.getId());

            // Step 4: 碳币充值已下线（recharge 端点移除）。
            // 此处仅为后续断言准备账户，余额由 ensureCoinAccount 的初始值提供。

            // Step 5: Add credit bonus
            creditScoreService.addBonusPoints(enterprise.getId(), 5, "Completed report and trade", adminUser.getId());

            // Verify final state
            Transaction completedTrade = transactionRepository.findById(trade.getId()).orElse(null);
            assertNotNull(completedTrade);
            assertEquals(TradeStatusEnum.COMPLETED.getCode(), completedTrade.getStatus());

            // 2026-11-03 模型：P2P 是「碳币换碳币」，卖方把冻结的 200 枚转给买方，
            // 自己不再收到货款（无计价关系），故余额 = 20000 − 200 = 19800。
            //
            // 原断言是「收款 11000 + 充值 5000 = 16000」——那属于「用碳币买配额」模型，
            // 卖方卖出配额换回碳币；现在配额不在资产层，这个算式不成立。
            CarbonCoinAccount coinAccount = carbonCoinAccountRepository.findByUserIdAndDeletedFalse(enterpriseUser.getId()).orElse(null);
            assertNotNull(coinAccount);
            assertEquals(0, new BigDecimal("19800").compareTo(coinAccount.getBalance()),
                    "卖方余额应为 20000 − 200（转出的碳币），实际: " + coinAccount.getBalance());

            CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterprise.getId()).orElse(null);
            assertNotNull(creditScore);
            assertTrue(creditScore.getScore() >= 80, "Credit score should be at least 80 after bonus");
        }
    }

    // ==================== Helper Methods ====================

    private User createUser(String username, String password, int userType, String email) {
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .phone("1380013" + String.format("%04d", Math.abs(username.hashCode()) % 10000))
                .realName("Test User " + username)
                .userType(userType)
                .status(1)
                .build();
        return userRepository.save(user);
    }

    private Enterprise createEnterprise(User user, String name, String creditCode,
                                         BigDecimal quota, BigDecimal used, BigDecimal tradable) {
        Enterprise ent = Enterprise.builder()
                .userId(user.getId())
                .enterpriseName(name)
                .creditCode(creditCode)
                .address("123 Test Street")
                .contactPerson("Test Contact")
                .contactPhone("13800138000")
                .industry("Power")
                .carbonQuota(quota)
                .carbonUsed(used)
                .carbonTradable(tradable)
                .certStatus(1)
                .build();
        return enterpriseRepository.save(ent);
    }

    private JwtUserDetails createJwtDetails(User user, String role) {
        return JwtUserDetails.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roles(List.of(role))
                .build();
    }
}
