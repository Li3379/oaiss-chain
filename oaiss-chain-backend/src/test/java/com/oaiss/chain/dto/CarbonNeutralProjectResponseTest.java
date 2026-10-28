package com.oaiss.chain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CarbonNeutralProjectResponse 单元测试
 * CarbonNeutralProjectResponse Unit Tests
 */
class CarbonNeutralProjectResponseTest {

    @Test
    @DisplayName("测试Builder创建对象")
    void testBuilder() {
        CarbonNeutralProjectResponse response = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("P001")
                .projectName("Test Project")
                .projectType(1)
                .projectTypeName("Forest")
                .ownerId(100L)
                .ownerName("Owner Name")
                .description("Description")
                .location("Location")
                .expectedReduction(new BigDecimal("1000.00"))
                .actualReduction(new BigDecimal("950.00"))
                .investmentAmount(new BigDecimal("50000.00"))
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .status(1)
                .statusText("Active")
                .certStatus(1)
                .certStatusText("Certified")
                .certOrg("Cert Org")
                .certDate(LocalDate.of(2024, 6, 1))
                .certNo("CERT001")
                .methodology("Method A")
                .accountingPeriod(12)
                .issuedCredits(new BigDecimal("100.00"))
                .usedCredits(new BigDecimal("50.00"))
                .availableCredits(new BigDecimal("50.00"))
                .applicationData("App Data")
                .verificationReport("Report")
                .attachments("attachment.pdf")
                .reviewComment("Comment")
                .reviewerId(200L)
                .reviewerName("Reviewer")
                .reviewedAt(LocalDateTime.now())
                .monitoringData("Data")
                .lastMonitoringDate(LocalDate.now())
                .verifierId(300L)
                .verifierName("Verifier")
                .verificationStatus(1)
                .verificationStatusText("Verified")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        assertEquals(1L, response.getId());
        assertEquals("P001", response.getProjectNo());
        assertEquals("Test Project", response.getProjectName());
        assertEquals(1, response.getProjectType());
        assertEquals("Forest", response.getProjectTypeName());
        assertEquals(100L, response.getOwnerId());
        assertEquals("Owner Name", response.getOwnerName());
        assertEquals("Description", response.getDescription());
        assertEquals("Location", response.getLocation());
        assertEquals(new BigDecimal("1000.00"), response.getExpectedReduction());
        assertEquals(new BigDecimal("950.00"), response.getActualReduction());
        assertEquals(new BigDecimal("50000.00"), response.getInvestmentAmount());
        assertEquals(LocalDate.of(2024, 1, 1), response.getStartDate());
        assertEquals(LocalDate.of(2024, 12, 31), response.getEndDate());
        assertEquals(1, response.getStatus());
        assertEquals("Active", response.getStatusText());
        assertEquals(1, response.getCertStatus());
        assertEquals("Certified", response.getCertStatusText());
        assertEquals("Cert Org", response.getCertOrg());
        assertEquals(LocalDate.of(2024, 6, 1), response.getCertDate());
        assertEquals("CERT001", response.getCertNo());
        assertEquals("Method A", response.getMethodology());
        assertEquals(12, response.getAccountingPeriod());
        assertEquals(new BigDecimal("100.00"), response.getIssuedCredits());
        assertEquals(new BigDecimal("50.00"), response.getUsedCredits());
        assertEquals(new BigDecimal("50.00"), response.getAvailableCredits());
        assertEquals("App Data", response.getApplicationData());
        assertEquals("Report", response.getVerificationReport());
        assertEquals("attachment.pdf", response.getAttachments());
        assertEquals("Comment", response.getReviewComment());
        assertEquals(200L, response.getReviewerId());
        assertEquals("Reviewer", response.getReviewerName());
        assertNotNull(response.getReviewedAt());
        assertEquals("Data", response.getMonitoringData());
        assertNotNull(response.getLastMonitoringDate());
        assertEquals(300L, response.getVerifierId());
        assertEquals("Verifier", response.getVerifierName());
        assertEquals(1, response.getVerificationStatus());
        assertEquals("Verified", response.getVerificationStatusText());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    @DisplayName("测试无参构造函数")
    void testNoArgsConstructor() {
        CarbonNeutralProjectResponse response = new CarbonNeutralProjectResponse();
        assertNull(response.getId());
        assertNull(response.getProjectNo());
    }

    @Test
    @DisplayName("测试全参构造函数")
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        CarbonNeutralProjectResponse response = new CarbonNeutralProjectResponse(
                1L, "P002", "Project", 1, "Type", 100L, "Owner",
                "Desc", "Loc", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN,
                LocalDate.now(), LocalDate.now(), 1, "Active", 1, "Certified",
                "Org", LocalDate.now(), "NO", "Method", 12,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE,
                "App", "Report", "Attach", "Comment",
                200L, "Reviewer", now, "Data", LocalDate.now(),
                300L, "Verifier", 1, "Verified", now, now
        );

        assertEquals(1L, response.getId());
        assertEquals("P002", response.getProjectNo());
    }

    @Test
    @DisplayName("测试Setter和Getter")
    void testSetterGetter() {
        CarbonNeutralProjectResponse response = new CarbonNeutralProjectResponse();
        
        response.setId(2L);
        response.setProjectNo("P003");
        response.setProjectName("New Project");
        response.setProjectType(2);
        response.setProjectTypeName("Energy");
        response.setOwnerId(200L);
        response.setOwnerName("New Owner");
        response.setDescription("New Description");
        response.setLocation("New Location");
        response.setExpectedReduction(BigDecimal.TEN);
        response.setActualReduction(BigDecimal.ONE);
        response.setInvestmentAmount(BigDecimal.ZERO);
        response.setStatus(0);
        response.setStatusText("Inactive");
        response.setCertStatus(0);
        response.setCertStatusText("Pending");

        assertEquals(2L, response.getId());
        assertEquals("P003", response.getProjectNo());
        assertEquals("New Project", response.getProjectName());
        assertEquals(2, response.getProjectType());
        assertEquals("Energy", response.getProjectTypeName());
        assertEquals(200L, response.getOwnerId());
        assertEquals("New Owner", response.getOwnerName());
        assertEquals("New Description", response.getDescription());
        assertEquals("New Location", response.getLocation());
        assertEquals(BigDecimal.TEN, response.getExpectedReduction());
        assertEquals(BigDecimal.ONE, response.getActualReduction());
        assertEquals(BigDecimal.ZERO, response.getInvestmentAmount());
        assertEquals(0, response.getStatus());
        assertEquals("Inactive", response.getStatusText());
        assertEquals(0, response.getCertStatus());
        assertEquals("Pending", response.getCertStatusText());
    }

    @Test
    @DisplayName("测试Equals和HashCode")
    void testEqualsAndHashCode() {
        CarbonNeutralProjectResponse response1 = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("P001")
                .build();
        
        CarbonNeutralProjectResponse response2 = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("P001")
                .build();

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    @DisplayName("测试ToString")
    void testToString() {
        CarbonNeutralProjectResponse response = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("P001")
                .projectName("Test")
                .build();

        String str = response.toString();
        assertTrue(str.contains("P001"));
        assertTrue(str.contains("Test"));
    }
}
