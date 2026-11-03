package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 碳币交易流水表
 * 记录所有碳币相关的充值、消费流水（4-历史转账遗留值，功能已下线）
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "carbon_coin_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonCoinTransaction extends BaseEntity {

    /**
     * 交易编号
     */
    @Column(name = "tx_no", nullable = false, unique = true, length = 50)
    private String txNo;

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 交易类型（1-充值, 2-购买配额, 3-出售配额；4 为历史转账遗留值）
     *
     * <p>列类型必须显式声明为 TINYINT：V17 迁移把该列从 INT 收紧为 TINYINT，
     * 而 Java 侧仍是 Integer。Hibernate 的 {@code ddl-auto: validate} 会拿
     * 「Integer → INTEGER」去比对实际列，发现是 TINYINT 就判定类型不符并
     * 直接让应用启动失败：
     *
     * <pre>Schema-validation: wrong column type encountered in column [tx_type]
     * found [tinyint], but expecting [integer]</pre>
     *
     * 本地跑 {@code ddl-auto=none} 时不校验，所以这个缺陷只在 CI（docker profile
     * 用 validate）暴露。</p>
     */
    @Column(name = "tx_type", nullable = false, columnDefinition = "TINYINT")
    private Integer txType;

    /**
     * 交易金额（碳币数量）
     */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * 交易前余额
     */
    @Column(name = "balance_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    /**
     * 交易后余额
     */
    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * 关联碳配额数量（出售/购买配额时）
     */
    @Column(name = "related_quota", precision = 15, scale = 4)
    private BigDecimal relatedQuota;

    /**
     * 关联交易ID（可选）
     */
    @Column(name = "related_trade_id")
    private Long relatedTradeId;

    /**
     * 关联碳报告ID（发放 ISSUE / 回收 CLAWBACK 时填写）。
     *
     * <p>用途是让「这份报告发了多少币」可追溯，配合 {@link #relatedSeq}
     * 构成发放幂等键 {@code reportId#seq}。
     */
    @Column(name = "related_report_id")
    private Long relatedReportId;

    /**
     * 该报告的发放序号，从 1 开始；回收行为 null。
     *
     * <p><b>为什么需要序号</b>：报告被驳回后可修改重提并再次通过
     * （{@code ReportStatusEnum.REJECTED.isSubmittable() == true}）。
     * 若幂等键只取 {@code related_report_id}，第二次发放会撞唯一索引，
     * 新盈余被静默丢弃。序号递增解决这个问题。
     *
     * <p>回收行此列留 null——MySQL 唯一索引不约束 NULL，可多行共存。
     */
    @Column(name = "related_seq")
    private Integer relatedSeq;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;
}
