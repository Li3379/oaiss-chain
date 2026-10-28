package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 碳中和项目响应
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarbonNeutralProjectResponse {

    private Long id;
    private String projectNo;
    private String projectName;
    private Integer projectType;
    private String projectTypeName;
    private Long ownerId;
    private String ownerName;
    private String description;
    private String location;
    private BigDecimal expectedReduction;
    private BigDecimal actualReduction;
    private BigDecimal investmentAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private String statusText;
    private Integer certStatus;
    private String certStatusText;
    private String certOrg;
    private LocalDate certDate;
    private String certNo;
    private String methodology;
    private Integer accountingPeriod;
    private BigDecimal issuedCredits;
    private BigDecimal usedCredits;
    private BigDecimal availableCredits;
    private String applicationData;
    private String verificationReport;
    private String attachments;
    private String reviewComment;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private String monitoringData;
    private LocalDate lastMonitoringDate;
    private Long verifierId;
    private String verifierName;
    private Integer verificationStatus;
    private String verificationStatusText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
