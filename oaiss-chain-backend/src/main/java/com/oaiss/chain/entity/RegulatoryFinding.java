package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 监管线索（Regulatory Finding）。
 *
 * <p>存在理由：原始需求要求监管「进行数据的定期核查」，但系统里既没有核查任务、
 * 也没有核查记录——监管只有两只能看的表，没有任何"发现异常"的系统能力。
 * 本实体把分散在审核驳回、信誉事件、额度偏差等处的数据汇成可处置的清单。
 *
 * <p><b>只产出、不处置</b>：线索是监管的"发现"，处置走独立的监督意见。
 * 两者混在一起会让「监管不干预市场」这条定位失效——线索本身不改变任何业务状态。
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "regulatory_finding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegulatoryFinding extends BaseEntity {

    // ==================== 规则代码 ====================

    /** 连续成交驳回：同企业在窗口期内被驳回次数达阈值 */
    public static final String RULE_REPEATED_REJECTION = "REPEATED_REJECTION";
    /** 额度异常：已用配额超过持有配额 */
    public static final String RULE_QUOTA_OVERDRAFT = "QUOTA_OVERDRAFT";
    /**
     * 超排缺口：报告的实排超过该报告算出的配额。
     *
     * <p>取代 {@link #RULE_QUOTA_OVERDRAFT}——那个判据是「企业余额口径」，
     * 配额退出资产层后恒不触发。新判据按报告算，是唯一仍能反映
     * 「排放超过许可」的地方。
     */
    public static final String RULE_QUOTA_SHORTFALL = "QUOTA_SHORTFALL";
    /** 信誉骤降：窗口期内信誉分跌幅达阈值 */
    public static final String RULE_CREDIT_PLUNGE = "CREDIT_PLUNGE";
    /** 洗售拦截：撮合引擎曾拦截该企业的自成交 */
    public static final String RULE_WASH_TRADING_BLOCKED = "WASH_TRADING_BLOCKED";

    // ==================== 严重等级 ====================

    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    /**
     * 企业ID
     */
    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    /**
     * 触发规则代码
     */
    @Column(name = "rule_code", nullable = false, length = 32)
    private String ruleCode;

    /**
     * 严重等级（LOW / MEDIUM / HIGH / CRITICAL）
     */
    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    /**
     * 触发依据（人类可读，含实际数值——监管需要知道"凭什么"）
     */
    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    /**
     * 是否已处置。未处置的同一规则不再重复产出（幂等）。
     */
    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    /**
     * 处置人（监管用户ID）
     */
    @Column(name = "resolved_by")
    private Long resolvedBy;

    /**
     * 处置时间
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * 处置说明
     */
    @Column(name = "resolve_note", length = 500)
    private String resolveNote;
}