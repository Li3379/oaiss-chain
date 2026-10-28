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

    private User enterpriseUser;
    private User reviewerUser;
    private User adminUser;
    private User thirdPartyUser;
    private User buyerUser;
    private Enterprise enterprise;
    private Enterprise buyerEnterprise;

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

            CarbonReportResponse approved = carbonService.reviewReport(reviewerDetails, reviewRequest);
            assertEquals(ReportStatusEnum.APPROVED.getCode(), approved.getStatus());

            CarbonReport savedReport = carbonReportRepository.findById(draft.getId()).orElse(null);
            assertNotNull(savedReport);
            assertEquals(ReportStatusEnum.APPROVED.getCode(), savedReport.getStatus());
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
        @DisplayName("Full flow: recharge -> transfer -> balance")
        void carbonCoinFullFlow_Recharge_Transfer_Balance() {
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

            CarbonCoinTransferRequest transferRequest = new CarbonCoinTransferRequest();
            transferRequest.setCounterpartId(buyerUser.getId());
            transferRequest.setAmount(new BigDecimal("3000"));
            transferRequest.setRemark("Trade payment");

            carbonCoinService.transfer(enterpriseUser.getId(), transferRequest);

            CarbonCoinAccount sellerAccount = carbonCoinAccountRepository.findByUserIdAndDeletedFalse(enterpriseUser.getId()).orElse(null);
            CarbonCoinAccount buyerAccount = carbonCoinAccountRepository.findByUserIdAndDeletedFalse(buyerUser.getId()).orElse(null);

            assertNotNull(sellerAccount);
            assertNotNull(buyerAccount);
            assertEquals(0, new BigDecimal("7000").compareTo(sellerAccount.getBalance()));
            assertEquals(0, new BigDecimal("3000").compareTo(buyerAccount.getBalance()));

            List<CarbonCoinTransaction> transactions = carbonCoinTransactionRepository.findAll();
            assertTrue(transactions.size() >= 2);
        }

        @Test
        @DisplayName("Transfer fails with insufficient balance")
        void transferFails_InsufficientBalance() {
            carbonCoinService.getOrCreateAccount(enterpriseUser.getId());
            carbonCoinService.getOrCreateAccount(buyerUser.getId());

            CarbonCoinRechargeRequest rechargeRequest = new CarbonCoinRechargeRequest();
            rechargeRequest.setAmount(new BigDecimal("100"));
            rechargeRequest.setRemark("Small recharge");
            carbonCoinService.recharge(enterpriseUser.getId(), rechargeRequest);

            CarbonCoinTransferRequest transferRequest = new CarbonCoinTransferRequest();
            transferRequest.setCounterpartId(buyerUser.getId());
            transferRequest.setAmount(new BigDecimal("500"));
            transferRequest.setRemark("Over transfer");

            assertThrows(Exception.class, () -> carbonCoinService.transfer(enterpriseUser.getId(), transferRequest));
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
            TradeRequest tradeRequest = TradeRequest.builder()
                    .tradeType(TradeTypeEnum.P2P.getCode())
                    .buyerId(buyerUser.getId())
                    .quantity(new BigDecimal("200"))
                    .unitPrice(new BigDecimal("55.00"))
                    .build();

            TradeResponse trade = tradeService.createP2PTrade(enterpriseDetails, tradeRequest);

            // Step 3: Buyer confirms trade
            tradeService.confirmTrade(trade.getId(), buyerUser.getId());

            // Step 4: Recharge carbon coins
            carbonCoinService.getOrCreateAccount(enterpriseUser.getId());
            carbonCoinService.getOrCreateAccount(buyerUser.getId());

            CarbonCoinRechargeRequest rechargeRequest = new CarbonCoinRechargeRequest();
            rechargeRequest.setAmount(new BigDecimal("5000"));
            rechargeRequest.setRemark("Trade reward");
            carbonCoinService.recharge(enterpriseUser.getId(), rechargeRequest);

            // Step 5: Add credit bonus
            creditScoreService.addBonusPoints(enterprise.getId(), 5, "Completed report and trade", adminUser.getId());

            // Verify final state
            Transaction completedTrade = transactionRepository.findById(trade.getId()).orElse(null);
            assertNotNull(completedTrade);
            assertEquals(TradeStatusEnum.COMPLETED.getCode(), completedTrade.getStatus());

            CarbonCoinAccount coinAccount = carbonCoinAccountRepository.findByUserIdAndDeletedFalse(enterpriseUser.getId()).orElse(null);
            assertNotNull(coinAccount);
            assertEquals(0, new BigDecimal("5000").compareTo(coinAccount.getBalance()));

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
