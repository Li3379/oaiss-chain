package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 审核员表
 * 存储审核员的资质信息
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "reviewer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reviewer extends BaseEntity {

    /**
     * 关联用户ID
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * 审核员资质编号
     */
    @Column(name = "qualification_no", nullable = false, unique = true, length = 50)
    private String qualificationNo;

    /**
     * 审核员级别（1-初级, 2-中级, 3-高级）
     */
    @Column(name = "level", nullable = false)
    @Builder.Default
    private Integer level = 1;

    /**
     * 所属机构
     */
    @Column(name = "organization", length = 200)
    private String organization;

    /**
     * 可审核行业（JSON数组）
     */
    @Column(name = "reviewable_industries", columnDefinition = "TEXT")
    private String reviewableIndustries;

    /**
     * 已完成审核数
     */
    @Column(name = "completed_reviews", nullable = false)
    @Builder.Default
    private Integer completedReviews = 0;

    /**
     * 审核员状态（0-禁用, 1-启用）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 1;

    /**
     * 审核员姓名（非持久化，用于前端展示）
     */
    @Transient
    private String name;
}
