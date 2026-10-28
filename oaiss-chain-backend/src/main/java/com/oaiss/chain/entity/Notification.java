package com.oaiss.chain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 站内通知实体
 * 用于 P2P 交易通知、审核结果通知等站内消息
 */
@Entity
@Table(name = "notification", indexes = {
    @Index(name = "idx_notif_user_deleted", columnList = "user_id,is_deleted"),
    @Index(name = "idx_notif_user_read_deleted", columnList = "user_id,is_read,is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
}
