package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 第三方机构表
 * 存储第三方监管机构信息
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "third_party_org")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThirdPartyOrg extends BaseEntity {

    /**
     * 关联用户ID
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * 机构名称
     */
    @Column(name = "org_name", nullable = false, length = 200)
    private String orgName;

    /**
     * 机构编码
     */
    @Column(name = "org_code", nullable = false, unique = true, length = 50)
    private String orgCode;

    /**
     * 机构类型（1-政府部门, 2-行业协会, 3-认证机构, 4-研究机构）
     */
    @Column(name = "org_type", nullable = false)
    private Integer orgType;

    /**
     * 监管范围（JSON数组）
     */
    @Column(name = "supervision_scope", columnDefinition = "TEXT")
    private String supervisionScope;

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
     * 机构地址
     */
    @Column(name = "address", length = 500)
    private String address;

    /**
     * 数据访问权限级别（1-基础, 2-详细, 3-完整）
     */
    @Column(name = "access_level", nullable = false)
    @Builder.Default
    private Integer accessLevel = 1;

    /**
     * 机构状态（0-禁用, 1-启用）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 1;
}
