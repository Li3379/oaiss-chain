package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 信誉评分表
 * 存储企业信誉评分信息
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "credit_score")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditScore extends BaseEntity {

    /**
     * 企业ID（关联Enterprise）
     */
    @Column(name = "enterprise_id", nullable = false, unique = true)
    private Long enterpriseId;

    /**
     * 当前信誉分（0-100）
     */
    @Column(name = "score", nullable = false)
    @Builder.Default
    private Integer score = 100;

    /**
     * 信誉等级（EXCELLENT/GOOD/WARNING/DANGER/FROZEN）
     */
    @Column(name = "level", length = 20)
    @Builder.Default
    private String level = "EXCELLENT";

    /**
     * 是否限制交易
     */
    @Column(name = "trade_restricted", nullable = false)
    @Builder.Default
    private Boolean tradeRestricted = false;

    /**
     * 是否冻结账户
     */
    @Column(name = "account_frozen", nullable = false)
    @Builder.Default
    private Boolean accountFrozen = false;

    /**
     * 最近评估时间
     */
    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;
}
