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

    /** 管理员下市处罚状态（true=禁止碳交易） */
    private Boolean delisted;

    /** 目标用户 userId（受限名单操作定位用） */
    private Long userId;
    private LocalDateTime lastEvaluatedAt;
    private LocalDateTime createdAt;
}
