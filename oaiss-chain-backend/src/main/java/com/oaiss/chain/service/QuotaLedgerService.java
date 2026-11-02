package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.QuotaTransaction;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.QuotaTransactionRepository;
import com.oaiss.chain.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 碳配额变动账本服务。
 *
 * <p>把「改 Enterprise 四个配额字段」收敛为四个语义化操作，每次变动都留一条流水，
 * 使额度变动事后可逐笔归因。成交审核的裁决依据、额度周期上限、监管穿透，
 * 三者都依赖这条能力。
 *
 * <p>约定：
 * <ul>
 *   <li>{@code carbonTradable}：可交易余额，所有变动最终收敛于此</li>
 *   <li>{@code carbonFrozen}：在途占用（挂单冻结 + 待审未结算）</li>
 *   <li>{@code carbonUsed}：已履约消耗</li>
 *   <li>任何操作失败时**不得**改变余额、**不得**写流水（校验先行）</li>
 * </ul>
 *
 * <p>MANDATORY 传播：本服务必须加入调用方事务——交易结算与审核裁决都是
 * 「改余额 + 写流水 + 改交易状态」的原子操作，账本脱离调用方事务会造成
 * 余额变了而流水没写（或反之）的半截状态。
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaLedgerService {

    /** 冻结：可交易 → 在途 */
    public static final String TYPE_FREEZE = "FREEZE";
    /** 解冻：在途 → 可交易 */
    public static final String TYPE_UNFREEZE = "UNFREEZE";
    /** 成交扣减：可交易下降、已用上升 */
    public static final String TYPE_TRANSFER = "TRANSFER";
    /** 买入增加：可交易上升 */
    public static final String TYPE_INCREASE = "INCREASE";
    /** 驳回回滚：把已扣减的额度原路退回 */
    public static final String TYPE_ROLLBACK = "ROLLBACK";

    private final EnterpriseRepository enterpriseRepository;
    private final QuotaTransactionRepository quotaTransactionRepository;

    /**
     * 冻结配额（挂单时锁定）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction freeze(Long enterpriseId, BigDecimal amount, String remark,
                                   Long operatorId, String source) {
        return freeze(enterpriseId, amount, remark, operatorId, source, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction freeze(Long enterpriseId, BigDecimal amount, String remark,
                                   Long operatorId, String source, Long relatedTradeId) {
        requirePositive(amount, "冻结");
        Enterprise enterprise = loadEnterprise(enterpriseId);

        BigDecimal tradableBefore = nz(enterprise.getCarbonTradable());
        BigDecimal frozenBefore = nz(enterprise.getCarbonFrozen());

        // 校验先行：不足即拒绝，不改变任何状态
        if (tradableBefore.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUOTA,
                    "可交易配额不足，当前 " + tradableBefore.toPlainString()
                            + " 吨，本次需要 " + amount.toPlainString() + " 吨");
        }

        BigDecimal tradableAfter = tradableBefore.subtract(amount);
        BigDecimal frozenAfter = frozenBefore.add(amount);

        enterprise.setCarbonTradable(tradableAfter);
        enterprise.setCarbonFrozen(frozenAfter);
        enterpriseRepository.save(enterprise);

        return writeLedger(enterprise, TYPE_FREEZE, amount, tradableBefore, tradableAfter,
                frozenBefore, frozenAfter, source, relatedTradeId, operatorId, remark);
    }

    /**
     * 解冻配额（撤单 / 审核驳回时退回可交易）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction unfreeze(Long enterpriseId, BigDecimal amount, String remark,
                                     Long operatorId, String source) {
        return unfreeze(enterpriseId, amount, remark, operatorId, source, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction unfreeze(Long enterpriseId, BigDecimal amount, String remark,
                                     Long operatorId, String source, Long relatedTradeId) {
        requirePositive(amount, "解冻");
        Enterprise enterprise = loadEnterprise(enterpriseId);

        BigDecimal tradableBefore = nz(enterprise.getCarbonTradable());
        BigDecimal frozenBefore = nz(enterprise.getCarbonFrozen());

        if (frozenBefore.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUOTA,
                    "冻结配额不足，当前冻结 " + frozenBefore.toPlainString()
                            + " 吨，本次要解冻 " + amount.toPlainString() + " 吨");
        }

        BigDecimal tradableAfter = tradableBefore.add(amount);
        BigDecimal frozenAfter = frozenBefore.subtract(amount);

        enterprise.setCarbonTradable(tradableAfter);
        enterprise.setCarbonFrozen(frozenAfter);
        enterpriseRepository.save(enterprise);

        return writeLedger(enterprise, TYPE_UNFREEZE, amount, tradableBefore, tradableAfter,
                frozenBefore, frozenAfter, source, relatedTradeId, operatorId, remark);
    }

    /**
     * 卖方结算（解冻 + 扣减，原子完成）。
     *
     * <p>为什么需要它而不是「解冻 + 扣减」两次调用：卖方的额度在挂单时就被冻结，
     * 成交离开挂单态后这笔冻结已经没有对应的挂单约束了。分两步调用会在中间态留下
     * 「已解冻但未扣减」的可用额度，若此时有并发挂单请求进来，同一批额度会被
     * 二次冻结——这正是两段式审核期间最容易出现的重复占用漏洞。
     *
     * <p>语义：frozen - amount，carbonUsed + amount，tradable 不变
     * （额度从未进入可交易池，直接从冻结转已用）。
     *
     * @param enterpriseId 卖方企业ID
     * @param amount       成交量
     * @param operatorId   操作人（审核通过时为审核员，自动放行时为撮合引擎）
     * @param relatedTradeId 交易ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction settleSell(Long enterpriseId, BigDecimal amount, String remark,
                                       Long operatorId, Long relatedTradeId) {
        requirePositive(amount, "卖方结算");
        Enterprise enterprise = loadEnterprise(enterpriseId);

        BigDecimal tradableBefore = nz(enterprise.getCarbonTradable());
        BigDecimal frozenBefore = nz(enterprise.getCarbonFrozen());

        if (frozenBefore.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUOTA,
                    "冻结配额不足，无法结算：当前冻结 " + frozenBefore.toPlainString()
                            + " 吨，本次结算 " + amount.toPlainString() + " 吨");
        }

        BigDecimal frozenAfter = frozenBefore.subtract(amount);
        enterprise.setCarbonFrozen(frozenAfter);
        enterprise.setCarbonUsed(nz(enterprise.getCarbonUsed()).add(amount));
        enterpriseRepository.save(enterprise);

        return writeLedger(enterprise, "SETTLE_SELL", amount, tradableBefore, tradableBefore,
                frozenBefore, frozenAfter, "AUCTION", relatedTradeId, operatorId, remark);
    }

    /**
     * 成交扣减（卖方侧）：可交易下降、已用上升。
     *
     * <p>注：卖方成交时其额度此前已在挂单期冻结，因此真实调用方应先用
     * {@link #unfreeze} 解冻再调用本方法，或使用 {@link #settleSell}。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction transfer(Long enterpriseId, BigDecimal amount, String remark,
                                     Long operatorId, String source) {
        return transfer(enterpriseId, amount, remark, operatorId, source, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction transfer(Long enterpriseId, BigDecimal amount, String remark,
                                     Long operatorId, String source, Long relatedTradeId) {
        requirePositive(amount, "成交扣减");
        Enterprise enterprise = loadEnterprise(enterpriseId);

        BigDecimal tradableBefore = nz(enterprise.getCarbonTradable());
        BigDecimal frozenBefore = nz(enterprise.getCarbonFrozen());

        if (tradableBefore.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUOTA,
                    "可交易配额不足，当前 " + tradableBefore.toPlainString()
                            + " 吨，本次扣减 " + amount.toPlainString() + " 吨");
        }

        BigDecimal tradableAfter = tradableBefore.subtract(amount);
        enterprise.setCarbonTradable(tradableAfter);
        enterprise.setCarbonUsed(nz(enterprise.getCarbonUsed()).add(amount));
        enterpriseRepository.save(enterprise);

        return writeLedger(enterprise, TYPE_TRANSFER, amount, tradableBefore, tradableAfter,
                frozenBefore, frozenBefore, source, relatedTradeId, operatorId, remark);
    }

    /**
     * 买入增加（买方侧）：配额与可交易同时上升。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction increase(Long enterpriseId, BigDecimal amount, String remark,
                                     Long operatorId, String source, Long relatedTradeId) {
        requirePositive(amount, "买入增加");
        Enterprise enterprise = loadEnterprise(enterpriseId);

        BigDecimal tradableBefore = nz(enterprise.getCarbonTradable());
        BigDecimal frozenBefore = nz(enterprise.getCarbonFrozen());

        BigDecimal tradableAfter = tradableBefore.add(amount);
        enterprise.setCarbonTradable(tradableAfter);
        enterprise.setCarbonQuota(nz(enterprise.getCarbonQuota()).add(amount));
        enterpriseRepository.save(enterprise);

        return writeLedger(enterprise, TYPE_INCREASE, amount, tradableBefore, tradableAfter,
                frozenBefore, frozenBefore, source, relatedTradeId, operatorId, remark);
    }

    // ==================== 配额发放（来源：碳报告审核通过） ====================

    /** 配额发放来源标识 */
    public static final String SOURCE_AUDIT = "AUDIT";

    /** 配额发放流水类型（与买入增加的 INCREASE 区分：来源是报告而非交易） */
    public static final String TYPE_ALLOCATION = "ALLOCATION";

    /** 配额冲正流水类型 */
    public static final String TYPE_ALLOCATION_REVOKE = "ALLOCATION_REVOKE";

    /**
     * 按碳报告发放配额：{@code carbon_quota} 与 {@code carbon_tradable} 同增。
     *
     * <p>与 {@link #increase} 的区别：本方法来自报告审核发放，流水带
     * {@code related_report_id} 与发放幂等键 {@code allocation_key}，
     * 类型记 {@code ALLOCATION} 而非 {@code INCREASE}——监管需要能区分
     * 「企业买的配额」与「按基准线发给它的配额」。
     *
     * <p>{@code carbon_used} 不动：发放是给企业新增许可，与"已经用了多少"无关。
     *
     * @param seq 该报告的发放序号，用于构造唯一键 {@code reportId#seq}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction increaseForReport(Long enterpriseId, BigDecimal amount, String remark,
                                              Long operatorId, Long relatedReportId, int seq) {
        requirePositive(amount, "配额发放");
        Enterprise enterprise = loadEnterprise(enterpriseId);

        BigDecimal tradableBefore = nz(enterprise.getCarbonTradable());
        BigDecimal frozenBefore = nz(enterprise.getCarbonFrozen());
        BigDecimal usedBefore = nz(enterprise.getCarbonUsed());

        BigDecimal tradableAfter = tradableBefore.add(amount);
        enterprise.setCarbonTradable(tradableAfter);
        enterprise.setCarbonQuota(nz(enterprise.getCarbonQuota()).add(amount));
        enterpriseRepository.save(enterprise);

        return writeLedgerWithReport(enterprise, TYPE_ALLOCATION, amount,
                tradableBefore, tradableAfter, frozenBefore, frozenBefore,
                usedBefore, usedBefore,
                SOURCE_AUDIT, relatedReportId, operatorId, remark,
                relatedReportId + "#" + seq, seq);
    }

    /**
     * 配额冲正：{@code carbon_quota} 与 {@code carbon_tradable} **同减**，
     * {@code carbon_used} **绝不动**。
     *
     * <p><b>为什么不复用 {@link #rollback}</b>：那个方法是「撤销一笔交易扣减」的逆操作，
     * 方向完全相反——
     * <pre>
     * rollback():     tradable += amount;  used -= amount;  quota 不动
     * revoke():       tradable -= amount;  used 不动;       quota -= amount
     * </pre>
     * 用 rollback 做配额冲正会造成：{@code tradable} 凭空增加（企业白拿一份可交易额度）、
     * {@code carbon_used} 被扣减（抹掉真实履约消耗）、{@code carbon_quota} 不下降
     * （配额总量永久虚高）。恒等式 {@code quota = tradable + frozen + used} 当场破裂。
     *
     * <p>前置条件：调用方必须已确认该批配额**未被动用**（used 未增长、无冻结）。
     * 本方法只校验 tradable 是否足够。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction revokeAllocation(Long enterpriseId, BigDecimal amount, String remark,
                                             Long operatorId, Long relatedReportId) {
        requirePositive(amount, "配额冲正");
        Enterprise enterprise = loadEnterprise(enterpriseId);

        BigDecimal tradableBefore = nz(enterprise.getCarbonTradable());
        BigDecimal frozenBefore = nz(enterprise.getCarbonFrozen());
        BigDecimal usedBefore = nz(enterprise.getCarbonUsed());

        if (tradableBefore.compareTo(amount) < 0) {
            // 走到这里说明调用方的"未动用"判据有漏（例如内联余额被绕过账本改动）。
            // 不做负余额兜底——那正是「用负余额掩盖问题」的反模式。
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUOTA,
                    String.format("可交易配额不足，无法冲正：当前 %s 吨，需冲正 %s 吨",
                            tradableBefore.toPlainString(), amount.toPlainString()));
        }

        BigDecimal tradableAfter = tradableBefore.subtract(amount);
        BigDecimal quotaBefore = nz(enterprise.getCarbonQuota());
        BigDecimal quotaAfter = quotaBefore.subtract(amount);

        enterprise.setCarbonTradable(tradableAfter);
        enterprise.setCarbonQuota(quotaAfter);
        // carbon_used 不动：发放时就没动它，冲正也不该动
        enterpriseRepository.save(enterprise);

        log.info("配额冲正: enterprise={}, report={}, amount={}, quota {} -> {}, tradable {} -> {}",
                enterpriseId, relatedReportId, amount,
                quotaBefore, quotaAfter, tradableBefore, tradableAfter);

        // 冲正行 allocation_key 留 NULL：MySQL 唯一索引不约束 NULL，
        // 若填 reportId 会与重发的新序号冲突（同一 key 只能发一次，但冲正可以多次）
        return writeLedgerWithReport(enterprise, TYPE_ALLOCATION_REVOKE, amount,
                tradableBefore, tradableAfter, frozenBefore, frozenBefore,
                usedBefore, usedBefore,
                SOURCE_AUDIT, relatedReportId, operatorId, remark, null, null);
    }

    /**
     * 审核驳回回滚：把成交时扣减的额度原路退回。
     *
     * <p>与 {@link #increase} 的区别在语义与账本类型：本方法是撤销一笔**已发生但被否决**的
     * 扣减，因此同时把 {@code carbonUsed} 回退，流水类型记 ROLLBACK，便于监管区分
     * 「正常买入」与「驳回冲正」。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction rollback(Long enterpriseId, BigDecimal amount, String remark,
                                     Long operatorId, String source) {
        return rollback(enterpriseId, amount, remark, operatorId, source, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaTransaction rollback(Long enterpriseId, BigDecimal amount, String remark,
                                     Long operatorId, String source, Long relatedTradeId) {
        requirePositive(amount, "驳回回滚");
        Enterprise enterprise = loadEnterprise(enterpriseId);

        BigDecimal tradableBefore = nz(enterprise.getCarbonTradable());
        BigDecimal frozenBefore = nz(enterprise.getCarbonFrozen());

        BigDecimal tradableAfter = tradableBefore.add(amount);
        enterprise.setCarbonTradable(tradableAfter);
        // 已用回退不得超过原值，防止把从未消耗的额度"回滚"成负已用
        BigDecimal used = nz(enterprise.getCarbonUsed());
        BigDecimal newUsed = used.subtract(amount);
        enterprise.setCarbonUsed(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
        enterpriseRepository.save(enterprise);

        log.info("配额回滚: enterprise={}, amount={}, tradable {} -> {}, source={}",
                enterpriseId, amount, tradableBefore, tradableAfter, source);

        return writeLedger(enterprise, TYPE_ROLLBACK, amount, tradableBefore, tradableAfter,
                frozenBefore, frozenBefore, source, relatedTradeId, operatorId, remark);
    }

    // ==================== 内部方法 ====================

    private Enterprise loadEnterprise(Long enterpriseId) {
        return enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "企业不存在: " + enterpriseId));
    }

    private void requirePositive(BigDecimal amount, String action) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, action + "数量必须为正数");
        }
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private QuotaTransaction writeLedger(Enterprise enterprise, String changeType, BigDecimal amount,
                                         BigDecimal tradableBefore, BigDecimal tradableAfter,
                                         BigDecimal frozenBefore, BigDecimal frozenAfter,
                                         String source, Long relatedTradeId, Long operatorId,
                                         String remark) {
        QuotaTransaction record = QuotaTransaction.builder()
                .txNo(CommonUtils.generateTradeId())
                .enterpriseId(enterprise.getId())
                .changeType(changeType)
                .amount(amount)
                .tradableBefore(tradableBefore)
                .tradableAfter(tradableAfter)
                .frozenBefore(frozenBefore)
                .frozenAfter(frozenAfter)
                .source(source)
                .relatedTradeId(relatedTradeId)
                .operatorId(operatorId)
                .remark(remark)
                .build();
        return quotaTransactionRepository.save(record);
    }

    /**
     * 配额发放/冲正专用流水写入。
     *
     * <p><b>为什么新增而不是给 {@link #writeLedger} 加参数</b>：那个方法有 6 处既有调用点
     * （freeze/unfreeze/settleSell/transfer/increase/rollback ×2），改签名会波及全部；
     * 而发放维度（报告ID、used 快照、幂等键）只有新方法需要。
     *
     * @param usedBefore     变动前 carbon_used
     * @param usedAfter      变动后 carbon_used（发放与冲正都不改 used，故两者相等）
     * @param allocationKey  发放幂等键 {@code reportId#seq}；冲正传 null
     * @param allocationSeq  发放序号；冲正传 null
     */
    private QuotaTransaction writeLedgerWithReport(Enterprise enterprise, String changeType,
                                                   BigDecimal amount,
                                                   BigDecimal tradableBefore, BigDecimal tradableAfter,
                                                   BigDecimal frozenBefore, BigDecimal frozenAfter,
                                                   BigDecimal usedBefore, BigDecimal usedAfter,
                                                   String source, Long relatedReportId,
                                                   Long operatorId, String remark,
                                                   String allocationKey, Integer allocationSeq) {
        QuotaTransaction record = QuotaTransaction.builder()
                .txNo(CommonUtils.generateTradeId())
                .enterpriseId(enterprise.getId())
                .changeType(changeType)
                .amount(amount)
                .tradableBefore(tradableBefore)
                .tradableAfter(tradableAfter)
                .frozenBefore(frozenBefore)
                .frozenAfter(frozenAfter)
                .usedBefore(usedBefore)
                .usedAfter(usedAfter)
                .source(source)
                .relatedReportId(relatedReportId)
                .operatorId(operatorId)
                .remark(remark)
                .allocationKey(allocationKey)
                .allocationSeq(allocationSeq)
                .build();
        return quotaTransactionRepository.save(record);
    }
}