package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信誉事件响应DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
public class CreditEventResponse {

    private Long id;
    private Long enterpriseId;
    private Integer eventType;
    private String eventTypeName;
    private String eventDescription;
    private Integer pointsChanged;
    private Integer scoreBefore;
    private Integer scoreAfter;
    private Long relatedReportId;
    private Long relatedTradeId;
    private Long triggeredBy;
    private String triggeredByName;
    private LocalDateTime triggeredAt;
}
