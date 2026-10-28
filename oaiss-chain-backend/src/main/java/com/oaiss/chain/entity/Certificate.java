package com.oaiss.chain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 数字证书实体
 * 用于 CA 数字证书体系的应用层证书管理
 */
@Entity
@Table(name = "certificate", indexes = {
    @Index(name = "idx_cert_user_deleted", columnList = "user_id,is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Certificate extends BaseEntity {

    @Column(name = "serial_number", nullable = false, length = 64, unique = true)
    private String serialNumber;

    @Column(name = "subject_dn", nullable = false, length = 500)
    private String subjectDn;

    @Column(name = "issuer_dn", nullable = false, length = 500)
    private String issuerDn;

    @Column(name = "certificate_pem", nullable = false, columnDefinition = "TEXT")
    private String certificatePem;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "certificate_type", nullable = false, length = 20)
    private String certificateType;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "not_before", nullable = false)
    private LocalDateTime notBefore;

    @Column(name = "not_after", nullable = false)
    private LocalDateTime notAfter;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 200)
    private String revokeReason;
}
