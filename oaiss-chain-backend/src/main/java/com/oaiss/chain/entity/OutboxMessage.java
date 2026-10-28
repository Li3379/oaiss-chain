package com.oaiss.chain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 区块链事务发件箱实体
 * 用于保证业务事务与联盟链存证之间的 100% 最终一致性
 *
 * @author OAISS Team
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox_message")
@EqualsAndHashCode(callSuper = true)
public class OutboxMessage extends BaseEntity {

    /**
     * 全局唯一消息ID (UUID)
     */
    @Column(name = "message_id", nullable = false, unique = true, length = 64)
    private String messageId;

    /**
     * 业务聚合类型（REPORT, TRADE）
     */
    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    /**
     * 业务聚合实体ID (reportId, tradeId)
     */
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    /**
     * 消息主题或智能合约函数名
     */
    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    /**
     * 待上链业务JSON载荷
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * 消息状态（PENDING-待发送, SENT-已上链, FAILED-重试中, DEAD-死信）
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * 已重试次数
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 下次允许重试时间
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * 最近一次上链异常信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
