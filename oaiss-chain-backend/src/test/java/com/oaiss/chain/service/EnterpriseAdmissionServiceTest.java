package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.EnterpriseAdmissionRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * EnterpriseAdmissionService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class EnterpriseAdmissionServiceTest {

    @Mock
    private EnterpriseAdmissionRepository enterpriseAdmissionRepository;

    @Mock
    private EnterpriseRepository enterpriseRepository;

    @InjectMocks
    private EnterpriseAdmissionService enterpriseAdmissionService;

    private Enterprise testEnterprise;
    private EnterpriseAdmission testAdmission;

    @BeforeEach
    void setUp() {
        testEnterprise = Enterprise.builder()
                .userId(1L)
                .enterpriseName("Test Enterprise")
                .creditCode("91110000MA00000001")
                .build();
        testEnterprise.setId(1L);

        testAdmission = EnterpriseAdmission.builder()
                .enterpriseId(1L)
                .certificateNo("EA-20260515-123456")
                .issuedDate(LocalDate.now())
                .status(1)
                .build();
        testAdmission.setId(1L);
    }

    @Test
    @DisplayName("签发准入证书-成功")
    void testIssueCertificate_success() {
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalse(1L, 1))
                .thenReturn(Optional.empty());
        when(enterpriseAdmissionRepository.existsByCertificateNoAndDeletedFalse(anyString())).thenReturn(false);
        when(enterpriseAdmissionRepository.save(any(EnterpriseAdmission.class))).thenReturn(testAdmission);

        EnterpriseAdmission result = enterpriseAdmissionService.issueCertificate(1L);

        assertNotNull(result);
        assertEquals(1L, result.getEnterpriseId());
        assertEquals(1, result.getStatus());
        assertNotNull(result.getCertificateNo());
        assertTrue(result.getCertificateNo().startsWith("EA-"));
        assertEquals(LocalDate.now(), result.getIssuedDate());

        verify(enterpriseRepository).findById(1L);
        verify(enterpriseAdmissionRepository).findFirstByEnterpriseIdAndStatusAndDeletedFalse(1L, 1);
        verify(enterpriseAdmissionRepository).save(any(EnterpriseAdmission.class));
    }

    @Test
    @DisplayName("签发准入证书-已有有效证书时拒绝重复签发")
    void testIssueCertificate_duplicateActive_throwsException() {
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalse(1L, 1))
                .thenReturn(Optional.of(testAdmission));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseAdmissionService.issueCertificate(1L));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getCode());

        verify(enterpriseAdmissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("签发准入证书-企业不存在时抛出异常")
    void testIssueCertificate_enterpriseNotFound_throwsException() {
        when(enterpriseRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseAdmissionService.issueCertificate(999L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getCode());

        verify(enterpriseAdmissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("吊销准入证书-成功")
    void testRevokeCertificate_success() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(testAdmission));
        when(enterpriseAdmissionRepository.save(any(EnterpriseAdmission.class))).thenReturn(testAdmission);

        enterpriseAdmissionService.revokeCertificate(1L);

        assertEquals(2, testAdmission.getStatus());
        verify(enterpriseAdmissionRepository).save(testAdmission);
    }

    @Test
    @DisplayName("签发准入证书-必须写入有效期，否则「有效期」永远没有意义")
    void testIssueCertificate_setsExpiryDate() {
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalse(1L, 1))
                .thenReturn(Optional.empty());
        when(enterpriseAdmissionRepository.existsByCertificateNoAndDeletedFalse(anyString())).thenReturn(false);
        when(enterpriseAdmissionRepository.save(any(EnterpriseAdmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EnterpriseAdmission result = enterpriseAdmissionService.issueCertificate(1L);

        assertNotNull(result.getExpiryDate(),
                "签发时必须写有效期：该列此前恒为 NULL，导致到期语义形同虚设");
        assertEquals(LocalDate.now().plusYears(1), result.getExpiryDate(),
                "默认有效期为签发日 + 1 年，与 V19 存量回填口径一致");
    }

    @Test
    @DisplayName("吊销准入证书-写入吊销时间与原因（审计留痕）")
    void testRevokeCertificate_recordsRevocationAuditFields() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(testAdmission));
        when(enterpriseAdmissionRepository.save(any(EnterpriseAdmission.class))).thenReturn(testAdmission);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        enterpriseAdmissionService.revokeCertificate(1L, "连续三期报告数据不合规");

        assertEquals(2, testAdmission.getStatus());
        assertEquals("连续三期报告数据不合规", testAdmission.getRevokeReason());
        assertNotNull(testAdmission.getRevokedAt(), "吊销必须留下时间，否则审计无法追溯");
        assertTrue(testAdmission.getRevokedAt().isAfter(before));
    }

    @Test
    @DisplayName("吊销准入证书-原因超长时截断到列宽，不因超长而失败")
    void testRevokeCertificate_truncatesOverlongReason() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(testAdmission));
        when(enterpriseAdmissionRepository.save(any(EnterpriseAdmission.class))).thenReturn(testAdmission);

        String tooLong = "违".repeat(500);
        enterpriseAdmissionService.revokeCertificate(1L, tooLong);

        assertEquals(2, testAdmission.getStatus());
        assertNotNull(testAdmission.getRevokeReason());
        assertTrue(testAdmission.getRevokeReason().length() <= 200);
    }

    @Test
    @DisplayName("吊销准入证书-未填原因时仍可吊销（原因可选）")
    void testRevokeCertificate_withoutReason_succeeds() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.of(testAdmission));
        when(enterpriseAdmissionRepository.save(any(EnterpriseAdmission.class))).thenReturn(testAdmission);

        enterpriseAdmissionService.revokeCertificate(1L);

        assertEquals(2, testAdmission.getStatus());
        assertNotNull(testAdmission.getRevokedAt());
    }

    @Test
    @DisplayName("吊销准入证书-无有效证书时抛出异常")
    void testRevokeCertificate_noActiveCert_throwsException() {
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(1L, 1))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseAdmissionService.revokeCertificate(1L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getCode());

        verify(enterpriseAdmissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("查询证书列表-带状态筛选")
    void testListCertificates_withStatusFilter() {
        Page<EnterpriseAdmission> page = new PageImpl<>(List.of(testAdmission));
        when(enterpriseAdmissionRepository.findByStatusAndDeletedFalse(eq(1), any(Pageable.class)))
                .thenReturn(page);

        Page<EnterpriseAdmission> result = enterpriseAdmissionService.listCertificates(1, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(enterpriseAdmissionRepository).findByStatusAndDeletedFalse(eq(1), any(Pageable.class));
        verify(enterpriseAdmissionRepository, never()).findByDeletedFalse(any());
    }

    @Test
    @DisplayName("查询证书列表-不带状态筛选")
    void testListCertificates_withoutStatusFilter() {
        Page<EnterpriseAdmission> page = new PageImpl<>(List.of(testAdmission));
        when(enterpriseAdmissionRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(page);

        Page<EnterpriseAdmission> result = enterpriseAdmissionService.listCertificates(null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(enterpriseAdmissionRepository).findByDeletedFalse(any(Pageable.class));
        verify(enterpriseAdmissionRepository, never()).findByStatusAndDeletedFalse(any(), any());
    }

    @Test
    @DisplayName("查询证书列表-page为null时默认为1")
    void testListCertificates_nullPage_defaultsToOne() {
        Page<EnterpriseAdmission> page = new PageImpl<>(List.of(testAdmission));
        when(enterpriseAdmissionRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(page);

        Page<EnterpriseAdmission> result = enterpriseAdmissionService.listCertificates(null, null, 10);

        assertNotNull(result);
        verify(enterpriseAdmissionRepository).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("查询证书列表-size为null时默认为10")
    void testListCertificates_nullSize_defaultsToTen() {
        Page<EnterpriseAdmission> page = new PageImpl<>(List.of(testAdmission));
        when(enterpriseAdmissionRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(page);

        Page<EnterpriseAdmission> result = enterpriseAdmissionService.listCertificates(null, 1, null);

        assertNotNull(result);
        verify(enterpriseAdmissionRepository).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("签发准入证书-证书编号冲突时重试成功")
    void testIssueCertificate_certNoCollision_retriesSuccessfully() {
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalse(1L, 1))
                .thenReturn(Optional.empty());
        // First call returns true (collision), second call returns false (no collision)
        when(enterpriseAdmissionRepository.existsByCertificateNoAndDeletedFalse(anyString()))
                .thenReturn(true)
                .thenReturn(false);
        when(enterpriseAdmissionRepository.save(any(EnterpriseAdmission.class))).thenReturn(testAdmission);

        EnterpriseAdmission result = enterpriseAdmissionService.issueCertificate(1L);

        assertNotNull(result);
        verify(enterpriseAdmissionRepository, times(2)).existsByCertificateNoAndDeletedFalse(anyString());
    }

    @Test
    @DisplayName("签发准入证书-证书编号多次冲突后抛出异常")
    void testIssueCertificate_certNoCollisionMaxRetries_throwsException() {
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));
        when(enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalse(1L, 1))
                .thenReturn(Optional.empty());
        // All 3 attempts collide
        when(enterpriseAdmissionRepository.existsByCertificateNoAndDeletedFalse(anyString()))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseAdmissionService.issueCertificate(1L));
        assertEquals(ErrorCode.SYSTEM_ERROR, ex.getCode());

        verify(enterpriseAdmissionRepository, times(3)).existsByCertificateNoAndDeletedFalse(anyString());
        verify(enterpriseAdmissionRepository, never()).save(any());
    }

    // ==================== Additional branch coverage tests ====================

    @Test
    @DisplayName("查询证书列表-page小于1时默认为1")
    void testListCertificates_zeroPage() {
        Page<EnterpriseAdmission> page = new PageImpl<>(List.of(testAdmission));
        when(enterpriseAdmissionRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(page);

        Page<EnterpriseAdmission> result = enterpriseAdmissionService.listCertificates(null, 0, 10);

        assertNotNull(result);
        verify(enterpriseAdmissionRepository).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("查询证书列表-size小于1时默认为10")
    void testListCertificates_zeroSize() {
        Page<EnterpriseAdmission> page = new PageImpl<>(List.of(testAdmission));
        when(enterpriseAdmissionRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(page);

        Page<EnterpriseAdmission> result = enterpriseAdmissionService.listCertificates(null, 1, 0);

        assertNotNull(result);
        verify(enterpriseAdmissionRepository).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("查询企业准入证书-成功")
    void testGetMyCertificate_success() {
        when(enterpriseAdmissionRepository.findByEnterpriseIdAndDeletedFalseOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(testAdmission));

        List<EnterpriseAdmission> result = enterpriseAdmissionService.getMyCertificate(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("EA-20260515-123456", result.get(0).getCertificateNo());
    }

    @Test
    @DisplayName("查询企业准入证书-无证书时返回空列表")
    void testGetMyCertificate_empty() {
        when(enterpriseAdmissionRepository.findByEnterpriseIdAndDeletedFalseOrderByCreatedAtDesc(999L))
                .thenReturn(List.of());

        List<EnterpriseAdmission> result = enterpriseAdmissionService.getMyCertificate(999L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
