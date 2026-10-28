-- V10: 添加数字证书表
-- 用于 CA 数字证书体系的应用层证书管理

CREATE TABLE certificate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    serial_number VARCHAR(64) NOT NULL COMMENT '证书序列号',
    subject_dn VARCHAR(500) NOT NULL COMMENT '使用者 DN',
    issuer_dn VARCHAR(500) NOT NULL COMMENT '颁发者 DN',
    certificate_pem TEXT NOT NULL COMMENT 'X.509 PEM 格式证书',
    user_id BIGINT NOT NULL COMMENT '关联用户 ID',
    certificate_type VARCHAR(20) NOT NULL COMMENT '类型: ROOT/ENTERPRISE/REVIEWER',
    status INT NOT NULL DEFAULT 1 COMMENT '1=有效 2=已吊销 3=已过期',
    not_before DATETIME NOT NULL COMMENT '生效时间',
    not_after DATETIME NOT NULL COMMENT '过期时间',
    revoked_at DATETIME COMMENT '吊销时间',
    revoke_reason VARCHAR(200) COMMENT '吊销原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
    INDEX idx_cert_user (user_id),
    INDEX idx_cert_serial (serial_number),
    INDEX idx_cert_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字证书表';
