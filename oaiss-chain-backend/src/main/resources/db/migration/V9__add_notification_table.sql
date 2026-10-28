-- V9: 添加站内通知表
-- 用于 P2P 交易通知、审核结果通知等站内消息

CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    notification_type VARCHAR(50) NOT NULL COMMENT '通知类型: TRADE/CARBON/CERT/SYSTEM',
    related_entity_id BIGINT COMMENT '关联业务ID',
    related_entity_type VARCHAR(50) COMMENT '关联业务类型',
    is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
    INDEX idx_notif_user_read (user_id, is_read, created_at),
    INDEX idx_notif_type (notification_type),
    INDEX idx_notif_entity (related_entity_id, related_entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';
