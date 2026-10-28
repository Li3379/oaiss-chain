package com.oaiss.chain.service;

import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.repository.EnterpriseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnterpriseServiceTest {

    @Mock
    private EnterpriseRepository enterpriseRepository;

    @InjectMocks
    private EnterpriseService enterpriseService;

    private Enterprise enterprise;

    @BeforeEach
    void setUp() {
        enterprise = Enterprise.builder()
                .userId(20L)
                .enterpriseName("Carbon Zero Ltd")
                .creditCode("123456789012345678")
                .contactPerson("Old Contact")
                .contactPhone("010-0000")
                .carbonQuota(new BigDecimal("100.00"))
                .carbonUsed(new BigDecimal("25.50"))
                .carbonTradable(new BigDecimal("74.50"))
                .build();
        enterprise.setId(8L);
        enterprise.setDeleted(false);
    }

    @Test
    @DisplayName("getEnterpriseInfo returns enterprise for current user")
    void getEnterpriseInfo_returnsEnterprise() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(20L)).thenReturn(Optional.of(enterprise));

        Enterprise result = enterpriseService.getEnterpriseInfo(20L);

        assertEquals("Carbon Zero Ltd", result.getEnterpriseName());
    }

    @Test
    @DisplayName("getEnterpriseInfo throws when enterprise is missing")
    void getEnterpriseInfo_whenMissing_throws() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(20L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> enterpriseService.getEnterpriseInfo(20L));
    }

    @Test
    @DisplayName("getQuotaInfo computes remaining and usage rate")
    void getQuotaInfo_computesRemainingAndUsageRate() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(20L)).thenReturn(Optional.of(enterprise));

        Map<String, Object> result = enterpriseService.getQuotaInfo(20L);

        assertEquals(new BigDecimal("100.00"), result.get("totalQuota"));
        assertEquals(new BigDecimal("25.50"), result.get("usedQuota"));
        assertEquals(new BigDecimal("74.50"), result.get("tradableQuota"));
        assertEquals("Carbon Zero Ltd", result.get("enterpriseName"));
        assertEquals(new BigDecimal("74.50"), result.get("remainingQuota"));
        assertEquals(new BigDecimal("25.50"), result.get("usageRate"));
    }

    @Test
    @DisplayName("getQuotaInfo keeps usage rate at zero when quota is zero")
    void getQuotaInfo_whenQuotaZero_returnsZeroUsageRate() {
        enterprise.setCarbonQuota(BigDecimal.ZERO);
        enterprise.setCarbonUsed(new BigDecimal("5.00"));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(20L)).thenReturn(Optional.of(enterprise));

        Map<String, Object> result = enterpriseService.getQuotaInfo(20L);

        assertEquals(BigDecimal.ZERO, result.get("usageRate"));
        assertEquals(new BigDecimal("-5.00"), result.get("remainingQuota"));
    }

    @Test
    @DisplayName("updateContact only applies non-blank fields")
    void updateContact_onlyAppliesNonBlankFields() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(20L)).thenReturn(Optional.of(enterprise));

        enterpriseService.updateContact(20L, "  ", "020-123456");

        assertEquals("Old Contact", enterprise.getContactPerson());
        assertEquals("020-123456", enterprise.getContactPhone());
        verify(enterpriseRepository).save(enterprise);
    }

    @Test
    @DisplayName("getEnterpriseById rejects deleted enterprise")
    void getEnterpriseById_rejectsDeletedEnterprise() {
        enterprise.setDeleted(true);
        when(enterpriseRepository.findById(8L)).thenReturn(Optional.of(enterprise));

        assertThrows(RuntimeException.class, () -> enterpriseService.getEnterpriseById(8L));
    }

    @Test
    @DisplayName("getQuotaInfo throws when enterprise is missing")
    void getQuotaInfo_whenMissing_throws() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(20L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> enterpriseService.getQuotaInfo(20L));
    }

    @Test
    @DisplayName("getEnterpriseById throws when enterprise not found")
    void getEnterpriseById_whenNotFound_throws() {
        when(enterpriseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> enterpriseService.getEnterpriseById(99L));
    }

    @Test
    @DisplayName("updateContact handles null contactPerson and contactPhone")
    void updateContact_handlesNullValues() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(20L)).thenReturn(Optional.of(enterprise));

        enterpriseService.updateContact(20L, null, null);

        assertEquals("Old Contact", enterprise.getContactPerson());
        assertEquals("010-0000", enterprise.getContactPhone());
        verify(enterpriseRepository).save(enterprise);
    }

    @Test
    @DisplayName("updateContact applies valid non-blank values")
    void updateContact_appliesValidValues() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(20L)).thenReturn(Optional.of(enterprise));

        enterpriseService.updateContact(20L, "New Contact", "13800000000");

        assertEquals("New Contact", enterprise.getContactPerson());
        assertEquals("13800000000", enterprise.getContactPhone());
        verify(enterpriseRepository).save(enterprise);
    }
}
