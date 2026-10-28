package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 企业表
 * 存储企业用户的详细信息
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "enterprise")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enterprise extends BaseEntity {

    /**
     * 关联用户ID
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * 企业名称
     */
    @Column(name = "enterprise_name", nullable = false, length = 200)
    private String enterpriseName;

    /**
     * 统一社会信用代码
     */
    @Column(name = "credit_code", nullable = false, unique = true, length = 18)
    private String creditCode;

    /**
     * 企业地址
     */
    @Column(name = "address", length = 500)
    private String address;

    /**
     * 联系人
     */
    @Column(name = "contact_person", length = 50)
    private String contactPerson;

    /**
     * 联系电话
     */
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /**
     * 所属行业
     */
    @Column(name = "industry", length = 100)
    private String industry;

    /**
     * 企业规模
     */
    @Column(name = "scale", length = 50)
    private String scale;

    /**
     * 碳配额余额（吨CO2当量）
     */
    @Column(name = "carbon_quota", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private java.math.BigDecimal carbonQuota = java.math.BigDecimal.ZERO;

    /**
     * 已使用碳配额
     */
    @Column(name = "carbon_used", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private java.math.BigDecimal carbonUsed = java.math.BigDecimal.ZERO;

    /**
     * 可交易碳配额
     */
    @Column(name = "carbon_tradable", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private java.math.BigDecimal carbonTradable = java.math.BigDecimal.ZERO;

    /**
     * 冻结碳配额（卖单挂单中）
     */
    @Column(name = "carbon_frozen", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private java.math.BigDecimal carbonFrozen = java.math.BigDecimal.ZERO;

    /**
     * 企业营业执照URL
     */
    @Column(name = "license_url", length = 500)
    private String licenseUrl;

    /**
     * 认证状态（0-未认证, 1-认证中, 2-已认证, 3-认证失败）
     */
    @Column(name = "cert_status", nullable = false)
    @Builder.Default
    private Integer certStatus = 0;

    /**
     * 乐观锁版本号
     */
    @Version
    @Builder.Default
    private Long version = 0L;
}
