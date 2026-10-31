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
}