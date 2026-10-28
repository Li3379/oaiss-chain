package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信誉评分响应DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
public class CreditScoreResponse {

    private Long id;
    private Long enterpriseId;
    private String enterpriseName;
    private Integer score;
    private String level;
    private Boolean tradeRestricted;
    private Boolean accountFrozen;
    private LocalDateTime lastEvaluatedAt;
    private LocalDateTime createdAt;
}
