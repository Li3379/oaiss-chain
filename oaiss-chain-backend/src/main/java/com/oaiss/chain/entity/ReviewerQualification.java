package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 审核员资质表
 * 存储审核员获得的审核资质
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "reviewer_qualification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerQualification extends BaseEntity {

    /**
     * 审核员ID（关联reviewer表）
     */
    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    /**
     * 资质类型
     */
    @Column(name = "qualification_type", nullable = false, length = 100)
    private String qualificationType;

    /**
     * 资质编号
     */
    @Column(name = "certificate_no", nullable = false, length = 50)
    private String certificateNo;

    /**
     * 发证机构
     */
    @Column(name = "issuing_authority", length = 200)
    private String issuingAuthority;

    /**
     * 获得日期
     */
    @Column(name = "issued_date")
    private java.time.LocalDate issuedDate;

    /**
     * 有效期至
     */
    @Column(name = "expiry_date")
    private java.time.LocalDate expiryDate;

    /**
     * 资质状态（1-有效, 2-已吊销）
     * @see com.oaiss.chain.enums.QualificationStatusEnum
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 1;
}
