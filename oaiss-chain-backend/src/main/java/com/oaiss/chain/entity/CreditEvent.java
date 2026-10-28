package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 信誉事件表
 * 记录所有信誉分变动事件（审计日志）
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "credit_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditEvent extends BaseEntity {

    /**
     * 企业ID
     */
    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    /**
     * 事件类型（1-数据造假, 2-迟交报告, 3-轻微违规, 4-严重违规, 5-良好行为奖励）
     */
    @Column(name = "event_type", nullable = false)
    private Integer eventType;

    /**
     * 事件描述
     */
    @Column(name = "event_description", columnDefinition = "TEXT")
    private String eventDescription;

    /**
     * 分数变动（正数=奖励，负数=扣分）
     */
    @Column(name = "points_changed", nullable = false)
    private Integer pointsChanged;

    /**
     * 变动前分数
     */
    @Column(name = "score_before", nullable = false)
    private Integer scoreBefore;

    /**
     * 变动后分数
     */
    @Column(name = "score_after", nullable = false)
    private Integer scoreAfter;

    /**
     * 关联碳报告ID（可选）
     */
    @Column(name = "related_report_id")
    private Long relatedReportId;

    /**
     * 关联交易ID（可选）
     */
    @Column(name = "related_trade_id")
    private Long relatedTradeId;

    /**
     * 触发者用户ID
     */
    @Column(name = "triggered_by")
    private Long triggeredBy;

    /**
     * 触发时间
     */
    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;
}
