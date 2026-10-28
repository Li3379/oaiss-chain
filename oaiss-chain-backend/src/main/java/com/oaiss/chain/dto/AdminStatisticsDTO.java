package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatisticsDTO {
    private Long totalUsers;
    private Long activeUsers;
    private Long totalEnterprises;
    private Long totalReviewers;
    private Long totalThirdParties;
    private Long totalAdmins;
    private Long totalCarbonReports;
    private Long totalTrades;
    private Long totalCarbonCoins;
    private Long pendingReviews;
}
