package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardDTO {
    private Long totalUsers;
    private Long activeUsers;
    private Long totalEnterprises;
    private Long totalCarbonReports;
    private Long pendingReviews;
    private Long totalTrades;
}
