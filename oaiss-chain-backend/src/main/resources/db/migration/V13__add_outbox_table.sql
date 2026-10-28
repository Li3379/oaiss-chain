-- V13__add_outbox_table.sql
-- 区块链可靠存证与最终一致性 (Blockchain Outbox)

CREATE TABLE `outbox_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `message_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '全局唯一消息ID',
    `aggregate_type` VARCHAR(32) NOT NULL COMMENT '业务聚合类型（REPORT, TRADE）',
    `aggregate_id` BIGINT NOT NULL COMMENT '业务聚合实体ID',
    `topic` VARCHAR(64) NOT NULL COMMENT '消息主题/操作函数名',
    `payload` TEXT NOT NULL COMMENT '待上链业务JSON载荷',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '消息状态（PENDING-待发送, SENT-已上链, FAILED-重试中, DEAD-死信）',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `next_retry_at` DATETIME NULL COMMENT '下次允许重试时间',
    `error_message` TEXT NULL COMMENT '最近一次上链异常信息',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标识（0-正常, 1-删除）',
    INDEX `idx_outbox_status_retry` (`status`, `next_retry_at`),
    INDEX `idx_outbox_aggregate` (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区块链事务发件箱表';
