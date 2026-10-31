package com.oaiss.chain.service;

import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.EnterpriseAdmissionRepository;
import com.oaiss.chain.repository.ReviewerQualificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 证书门禁单元测试。
 *
 * <p>这一层的语义是「吊销必须真的挡住写操作」。改动前全仓没有任何服务读取
 * 准入证书状态，吊销是孤岛写入，因此这里的每条用例都在钉住一个此前不成立的行为。</p>
 */
@ExtendWith(MockitoExtension.class)
class CertificateGateServiceTest {

    @Mock
    private EnterpriseAdmissionRepository enterpriseAdmissionRepository;

    @Mock
    private ReviewerQualificationRepository reviewerQualificationRepository;

    @InjectMocks
    private CertificateGateService certificateGateService;

    private EnterpriseAdmission active;
    private EnterpriseAdmission revoked;

    @BeforeEach
    void setUp() {
        active = EnterpriseAdmission.builder()
                .enterpriseId(1L)
                .certificateNo("EA-20260101-000001")
                .issuedDate(LocalDate.now().minusMonths(1))
                .expiryDate(LocalDate.now().plusMonths(11))
                .status(1)
                .build();

        revoked = EnterpriseAdmission.builder()
                .enterpriseId(1L)
                .certificateNo("EA-20260101-000002")
                .issuedDate(LocalDate.now().minusMonths(2))
                .expiryDate(LocalDate.now().plusMonths(10))
                .status(2)
                .build();
    }

    @Test
    @DisplayName("有效证书-放行")
    void isEnterpriseAdmitted_whenActive_returnsTrue() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(active));

        assertTrue(certificateGateService.isEnterpriseAdmitted(1L));
    }

    @Test
    @DisplayName("无有效证书-拒绝（默认关闭，不延续信誉分的默认放行）")
    void isEnterpriseAdmitted_whenNoActiveCertificate_returnsFalse() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.empty());

        assertFalse(certificateGateService.isEnterpriseAdmitted(1L));
    }

    @Test
    @DisplayName("证书已过期-拒绝")
    void isEnterpriseAdmitted_whenExpired_returnsFalse() {
        active.setExpiryDate(LocalDate.now().minusDays(1));
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(active));

        assertFalse(certificateGateService.isEnterpriseAdmitted(1L));
    }

    @Test
    @DisplayName("到期日为当天-仍放行（到期日含当天）")
    void isEnterpriseAdmitted_whenExpiresToday_returnsTrue() {
        active.setExpiryDate(LocalDate.now());
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(active));

        assertTrue(certificateGateService.isEnterpriseAdmitted(1L));
    }

    @Test
    @DisplayName("到期日为空-视为长期有效，放行（兼容历史数据）")
    void isEnterpriseAdmitted_whenExpiryNull_returnsTrue() {
        active.setExpiryDate(null);
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(active));

        assertTrue(certificateGateService.isEnterpriseAdmitted(1L));
    }

    @Test
    @DisplayName("企业ID为空-直接拒绝，不做隐式放行")
    void isEnterpriseAdmitted_whenEnterpriseIdNull_returnsFalse() {
        assertFalse(certificateGateService.isEnterpriseAdmitted(null));
    }

    @Test
    @DisplayName("断言门禁-有证放行不抛异常")
    void assertEnterpriseAdmitted_whenActive_doesNotThrow() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(active));

        assertDoesNotThrow(() -> certificateGateService.assertEnterpriseAdmitted(1L));
    }

    @Test
    @DisplayName("断言门禁-已吊销抛业务异常，错误码可被前端识别")
    void assertEnterpriseAdmitted_whenRevoked_throwsBusinessException() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> certificateGateService.assertEnterpriseAdmitted(1L));

        assertTrue(ex.getCode() >= 4000);
        assertNotNullMessage(ex);
    }

    @Test
    @DisplayName("审核员资格证-有效放行")
    void isReviewerQualified_whenActive_returnsTrue() {
        ReviewerQualification qualification = ReviewerQualification.builder()
                .reviewerId(9L)
                .qualificationType("CARBON_AUDIT")
                .certificateNo("RQ-20260101-000001")
                .issuedDate(LocalDate.now().minusMonths(1))
                .expiryDate(LocalDate.now().plusMonths(11))
                .status(1)
                .build();
        when(reviewerQualificationRepository.findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc(9L, 1))
                .thenReturn(Optional.of(qualification));

        assertTrue(certificateGateService.isReviewerQualified(9L));
    }

    @Test
    @DisplayName("审核员资格证-无有效证拒绝")
    void isReviewerQualified_whenMissing_returnsFalse() {
        when(reviewerQualificationRepository.findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc(9L, 1))
                .thenReturn(Optional.empty());

        assertFalse(certificateGateService.isReviewerQualified(9L));
    }

    @Test
    @DisplayName("审核员资格证-过期拒绝")
    void isReviewerQualified_whenExpired_returnsFalse() {
        ReviewerQualification qualification = ReviewerQualification.builder()
                .reviewerId(9L)
                .qualificationType("CARBON_AUDIT")
                .certificateNo("RQ-20260101-000002")
                .issuedDate(LocalDate.now().minusYears(2))
                .expiryDate(LocalDate.now().minusDays(1))
                .status(1)
                .build();
        when(reviewerQualificationRepository.findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc(9L, 1))
                .thenReturn(Optional.of(qualification));

        assertFalse(certificateGateService.isReviewerQualified(9L));
    }

    @Test
    @DisplayName("断言审核员门禁-未持证抛业务异常")
    void assertReviewerQualified_whenMissing_throwsBusinessException() {
        when(reviewerQualificationRepository.findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc(9L, 1))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> certificateGateService.assertReviewerQualified(9L));

        assertNotNullMessage(ex);
    }

    private void assertNotNullMessage(BusinessException ex) {
        assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank(),
                "门禁异常必须带可读消息，否则前端只能显示空白");
    }
}
