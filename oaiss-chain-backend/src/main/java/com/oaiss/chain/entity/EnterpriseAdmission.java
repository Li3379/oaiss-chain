package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 企业准入证书
 * 存储企业获得的准入证书
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "enterprise_admission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnterpriseAdmission extends BaseEntity {

    /**
     * 企业ID（关联enterprise表）
     */
    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    /**
     * 证书编号
     */
    @Column(name = "certificate_no", nullable = false, length = 50)
    private String certificateNo;

    /**
     * 签发日期
     */
    @Column(name = "issued_date")
    private LocalDate issuedDate;

    /**
     * 有效期至
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * 证书状态（1-有效, 2-已吊销）
     * @see com.oaiss.chain.enums.QualificationStatusEnum
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 1;
}
