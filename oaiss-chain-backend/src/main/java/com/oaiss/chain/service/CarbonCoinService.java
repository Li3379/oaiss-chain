package com.oaiss.chain.service;

import com.oaiss.chain.dto.CarbonCoinAccountResponse;
import com.oaiss.chain.dto.CarbonCoinRechargeRequest;
import com.oaiss.chain.entity.CarbonCoinAccount;
import com.oaiss.chain.entity.CarbonCoinTransaction;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonCoinAccountRepository;
import com.oaiss.chain.repository.CarbonCoinTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 碳币交易服务
 * 提供碳币账户管理、充值、消费功能
 * <p>
 * 设计文档要求（doc03）：
 * - 碳额度与碳币之间的交易市场
 * - 企业可用碳币购买碳配额或出售碳配额获得碳币
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonCoinService {

    private final CarbonCoinAccountRepository accountRepository;
    private final CarbonCoinTransactionRepository transactionRepository;
    /** 证书门禁：充值入口与碳币发放都必须校验 */
    private final CertificateGateService certificateGateService;
    /** 发放时需要「报告 → 企业 → userId」两跳解析，故注入两个 repository */
    private final com.oaiss.chain.repository.EnterpriseRepository enterpriseRepository;
    private final com.oaiss.chain.repository.CarbonReportRepository carbonReportRepository;
    /** 兑换配额时配额要进企业余额，必须走账本（唯一合法写入口） */
    private final QuotaLedgerService quotaLedgerService;

    /**
     * 交易类型
     */
    public static final int TX_TYPE_RECHARGE = 1;
    public static final int TX_TYPE_BUY_QUOTA = 2;
    public static final int TX_TYPE_SELL_QUOTA = 3;
    /** 减排成果发放：审核通过后按盈余 1:1 发放 */
    public static final int TX_TYPE_ISSUE = 5;
    /** 发放回收：报告被驳回时冲减已发的币 */
    public static final int TX_TYPE_CLAWBACK = 6;
    /** 碳币兑换配额：1 枚碳币换 1 吨排放权 */
    public static final int TX_TYPE_REDEEM_QUOTA = 7;

    /** 碳币精度：与 carbon_coin_account.balance 列一致 */
    private static final int COIN_SCALE = 2;

    /**
     * 获取或创建碳币账户
     */
    @Transactional
    public CarbonCoinAccountResponse getOrCreateAccount(Long userId) {
        CarbonCoinAccount account = accountRepository.findByUserIdAndDeletedFalse(userId)
                .orElseGet(() -> {
                    CarbonCoinAccount newAccount = CarbonCoinAccount.builder()
                            .userId(userId)
                            .balance(BigDecimal.ZERO)
                            .totalRecharged(BigDecimal.ZERO)
                            .totalSpent(BigDecimal.ZERO)
                            .status(1)
                            .build();
                    return accountRepository.save(newAccount);
                });
        return toResponse(account);
    }

    /**
     * 充值碳币
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse recharge(Long userId, CarbonCoinRechargeRequest request) {
        // 充值本身是「往系统里注入购买力」，无有效准入证书的主体不应获得这项能力。
        // 企业归属需要一跳解析：碳币账户挂 userId，准入证书挂 enterpriseId。
        certificateGateService.assertEnterpriseAdmittedByUserId(userId);

        CarbonCoinAccount account = getAccountEntity(userId);
        validateAccountActive(account);

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(request.getAmount());

        account.setBalance(balanceAfter);
        account.setTotalRecharged(account.getTotalRecharged().add(request.getAmount()));
        accountRepository.save(account);

        saveTransaction(userId, TX_TYPE_RECHARGE, request.getAmount(),
                balanceBefore, balanceAfter, null, null, request.getRemark());

        log.info("碳币充值成功: userId={}, amount={}, balance={}", userId, request.getAmount(), balanceAfter);
        return toResponse(account);
    }

    /**
     * 购买碳配额（消费碳币）
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse buyQuota(Long userId, BigDecimal amount, BigDecimal quota, Long tradeId) {
        CarbonCoinAccount account = getAccountEntity(userId);
        validateAccountActive(account);
        validateBalance(account, amount);

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        account.setBalance(balanceAfter);
        account.setTotalSpent(account.getTotalSpent().add(amount));
        accountRepository.save(account);

        saveTransaction(userId, TX_TYPE_BUY_QUOTA, amount,
                balanceBefore, balanceAfter, quota, tradeId, "购买碳配额");

        log.info("碳币购买配额: userId={}, coin={}, quota={}", userId, amount, quota);
        return toResponse(account);
    }

    /**
     * 出售碳配额（获得碳币）
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse sellQuota(Long userId, BigDecimal amount, BigDecimal quota, Long tradeId) {
        CarbonCoinAccount account = getAccountEntity(userId);
        validateAccountActive(account);

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        account.setBalance(balanceAfter);
        accountRepository.save(account);

        saveTransaction(userId, TX_TYPE_SELL_QUOTA, amount,
                balanceBefore, balanceAfter, quota, tradeId, "出售碳配额");

        log.info("碳币出售配额: userId={}, coin={}, quota={}", userId, amount, quota);
        return toResponse(account);
    }

    /**
     * 冻结碳币（买单挂单时锁定资金）
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse freezeBalance(Long userId, BigDecimal amount) {
        // 挂单入口：账户不存在时自动创建（余额 0）。
        //
        // 不能用 getAccountEntity——企业第一次挂单时通常还没有碳币账户
        // （从未访问过碳币页），直接抛 4002「碳币账户不存在」会让挂单失败。
        // 自动建账户后，余额为 0 会被 validateBalance 拒成「碳币余额不足」，
        // 那个错误信息才指向真正的原因（企业没有币，不是系统找不到账户）。
        CarbonCoinAccount account = getOrCreateAccountEntity(userId);
        validateAccountActive(account);
        validateBalance(account, amount);

        account.setBalance(account.getBalance().subtract(amount));
        BigDecimal currentFrozen = account.getFrozenBalance() != null ? account.getFrozenBalance() : BigDecimal.ZERO;
        account.setFrozenBalance(currentFrozen.add(amount));
        accountRepository.save(account);

        log.info("碳币资金冻结: userId={}, amount={}, available={}, frozen={}",
                userId, amount, account.getBalance(), account.getFrozenBalance());
        return toResponse(account);
    }

    /**
     * 解冻碳币（撤单时原路退回可用余额）
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse unfreezeBalance(Long userId, BigDecimal amount) {
        // 与 freezeBalance 对称：撤单入口也用自动建账户，
        // 否则「挂单时自动建了账户、撤单时找不到」这种不对称会在边缘场景炸掉。
        CarbonCoinAccount account = getOrCreateAccountEntity(userId);
        validateAccountActive(account);

        BigDecimal currentFrozen = account.getFrozenBalance() != null ? account.getFrozenBalance() : BigDecimal.ZERO;
        if (currentFrozen.compareTo(amount) < 0) {
            amount = currentFrozen; // 防止扣成负数
        }

        account.setFrozenBalance(currentFrozen.subtract(amount));
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("碳币资金解冻: userId={}, amount={}, available={}, frozen={}",
                userId, amount, account.getBalance(), account.getFrozenBalance());
        return toResponse(account);
    }

    /**
     * 扣除已冻结的碳币并完成配额购买结算（撮合成交时实扣）
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse deductFrozenBalance(Long userId, BigDecimal amount, BigDecimal quota, Long tradeId) {
        CarbonCoinAccount account = getAccountEntity(userId);
        validateAccountActive(account);

        BigDecimal currentFrozen = account.getFrozenBalance() != null ? account.getFrozenBalance() : BigDecimal.ZERO;
        if (currentFrozen.compareTo(amount) < 0) {
            // 如果冻结金额不足（极端边缘情况），则从冻结和可用补齐
            account.setFrozenBalance(BigDecimal.ZERO);
            BigDecimal shortfall = amount.subtract(currentFrozen);
            account.setBalance(account.getBalance().subtract(shortfall));
        } else {
            account.setFrozenBalance(currentFrozen.subtract(amount));
        }

        account.setTotalSpent(account.getTotalSpent().add(amount));
        accountRepository.save(account);

        saveTransaction(userId, TX_TYPE_BUY_QUOTA, amount,
                account.getBalance().add(amount), account.getBalance(), quota, tradeId, "撮合成交扣除冻结碳币");

        log.info("碳币冻结扣划: userId={}, amount={}, quota={}, tradeId={}", userId, amount, quota, tradeId);
        return toResponse(account);
    }

    /**
     * 查询交易流水
     */
    @Transactional(readOnly = true)
    public Page<CarbonCoinTransaction> getTransactions(Long userId, Integer txType, Integer page, Integer size) {
        int safePage = Math.max(page, 1);
        PageRequest pageable = PageRequest.of(safePage - 1, size);
        if (txType != null) {
            return transactionRepository.findByUserIdAndTxTypeAndDeletedFalseOrderByCreatedAtDesc(userId, txType, pageable);
        }
        return transactionRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    // ==================== 私有方法 ====================

    private CarbonCoinAccount getAccountEntity(Long userId) {
        return accountRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(4002, "碳币账户不存在"));
    }

    private void validateAccountActive(CarbonCoinAccount account) {
        if (account.getStatus() != 1) {
            throw new BusinessException(4004, "碳币账户已禁用");
        }
    }

    private void validateBalance(CarbonCoinAccount account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(4005, "碳币余额不足");
        }
    }

    private String generateTxNo() {
        return "CCT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private void saveTransaction(Long userId, Integer txType, BigDecimal amount,
                                  BigDecimal balanceBefore, BigDecimal balanceAfter,
                                  BigDecimal relatedQuota, Long relatedTradeId, String remark) {
        saveTransaction(userId, txType, amount, balanceBefore, balanceAfter,
                relatedQuota, relatedTradeId, null, null, remark);
    }

    /**
     * 写流水（含报告维度）。
     *
     * @param relatedReportId 关联报告ID；非发放/回收场景传 null
     * @param relatedSeq      发放序号；回收行必须传 null
     */
    private void saveTransaction(Long userId, Integer txType, BigDecimal amount,
                                  BigDecimal balanceBefore, BigDecimal balanceAfter,
                                  BigDecimal relatedQuota, Long relatedTradeId,
                                  Long relatedReportId, Integer relatedSeq, String remark) {
        CarbonCoinTransaction tx = CarbonCoinTransaction.builder()
                .txNo(generateTxNo())
                .userId(userId)
                .txType(txType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .relatedQuota(relatedQuota)
                .relatedTradeId(relatedTradeId)
                .relatedReportId(relatedReportId)
                .relatedSeq(relatedSeq)
                .remark(remark)
                .build();
        transactionRepository.save(tx);
    }

    // ==================== 减排成果发放（组件：碳币闭环）====================

    /**
     * 按报告盈余发放碳币，**幂等**。
     *
     * <p>事务传播是 {@code MANDATORY}：必须由审核流程的事务驱动，
     * 保证「发币」与「报告状态变更」同生共死。
     *
     * <p><b>入账对象是报告所属企业的 userId</b>，不是调用方（审核员）。
     * 这个解析必须在这里做——由调用方传 userId 极易传错，
     * 而资产错发的后果比「多发/少发」严重得多（钱进了审核员口袋）。
     *
     * @param reportId  报告ID
     * @param surplus   盈余吨数；非正数直接跳过（不发、不写流水、不报错）
     * @param operatorId 触发人（审核员），仅用于日志
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void issueForReport(Long reportId, BigDecimal surplus, Long operatorId) {
        doIssue(reportId, surplus, operatorId, true);
    }

    /**
     * 强制重发：跳过幂等检查，序号递增。
     *
     * <p>用于「报告被驳回 → 修改 → 重新审核通过」的场景。驳回时
     * {@link #clawbackForReport} 会冲减上一轮的发放，但流水行保留，
     * 因此这里必须用新序号，否则会撞 {@code uk_coin_issue} 唯一索引。
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reissueForReport(Long reportId, BigDecimal surplus, Long operatorId) {
        doIssue(reportId, surplus, operatorId, false);
    }

    private void doIssue(Long reportId, BigDecimal surplus, Long operatorId, boolean checkIdempotent) {
        if (surplus == null || surplus.compareTo(BigDecimal.ZERO) <= 0) {
            // 超排或刚好持平：不发币，这是正常路径，不该抛错也不该写流水
            log.info("[COIN] 报告 {} 无盈余（{}），不发放碳币", reportId, surplus);
            return;
        }

        CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> new BusinessException(4006, "碳报告不存在: " + reportId));

        Long ownerUserId = resolveOwnerUserId(report.getEnterpriseId());

        // 门禁：无有效准入证书的主体不应获得减排凭证
        certificateGateService.assertEnterpriseAdmittedByUserId(ownerUserId);

        long priorIssues = transactionRepository.countByRelatedReportIdAndRelatedSeqIsNotNull(reportId);
        if (checkIdempotent && priorIssues > 0) {
            log.info("[COIN] 报告 {} 已发放过碳币（{} 次），幂等跳过", reportId, priorIssues);
            return;
        }

        int seq = (int) priorIssues + 1;
        BigDecimal amount = surplus.setScale(COIN_SCALE, java.math.RoundingMode.HALF_UP);

        CarbonCoinAccount account = getOrCreateAccountEntity(ownerUserId);
        validateAccountActive(account);

        BigDecimal before = account.getBalance();
        BigDecimal after = before.add(amount);
        account.setBalance(after);
        account.setTotalIssued(nz(account.getTotalIssued()).add(amount));
        accountRepository.save(account);

        saveTransaction(ownerUserId, TX_TYPE_ISSUE, amount, before, after,
                null, null, reportId, seq,
                String.format("碳报告 %s 减排盈余发放（第 %d 次）", report.getReportNo(), seq));

        // 参数含 toPlainString()，无论日志级别都会求值；显式守卫避免无谓字符串化
        if (log.isInfoEnabled()) {
            log.info("[COIN] 报告 {} 发放碳币 {} 枚给 userId={}（盈余 {}，序号 {}，操作人 {}）",
                    reportId, amount.toPlainString(), ownerUserId, surplus.toPlainString(), seq, operatorId);
        }
    }

    /**
     * 回收某报告已发放的碳币。
     *
     * <p>传播是 {@code REQUIRES_NEW}：调用方（驳回流程）是「尽力而为」的——
     * 回收失败（余额已被花掉）不得让驳回整体失败。若共用事务，
     * 内层抛出会把外层标记 rollback-only，提交阶段抛
     * {@code UnexpectedRollbackException}，驳回变成 500。
     *
     * <p><b>totalIssued 不减少</b>：发行是史实，回收改的是流通量。
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void clawbackForReport(Long reportId, Long operatorId) {
        CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> new BusinessException(4006, "碳报告不存在: " + reportId));

        // 累计已发放量（ISSUE 行求和）
        BigDecimal issued = transactionRepository
                .findByRelatedReportIdAndDeletedFalseOrderByIdAsc(reportId).stream()
                .filter(tx -> Integer.valueOf(TX_TYPE_ISSUE).equals(tx.getTxType()))
                .map(CarbonCoinTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (issued.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[COIN] 报告 {} 未曾发放碳币，无需回收", reportId);
            return;
        }

        Long ownerUserId = resolveOwnerUserId(report.getEnterpriseId());
        CarbonCoinAccount account = getOrCreateAccountEntity(ownerUserId);

        BigDecimal before = account.getBalance();
        if (before.compareTo(issued) < 0) {
            throw new BusinessException(4005,
                    String.format("碳币余额不足，无法回收：应回收 %s 枚，当前余额 %s 枚",
                            issued.toPlainString(), before.toPlainString()));
        }
        BigDecimal after = before.subtract(issued);
        account.setBalance(after);
        // totalIssued 刻意不动——发行史实不可抹去
        accountRepository.save(account);

        saveTransaction(ownerUserId, TX_TYPE_CLAWBACK, issued, before, after,
                null, null, reportId, null,
                String.format("碳报告 %s 驳回，回收已发碳币", report.getReportNo()));

        if (log.isInfoEnabled()) {
            log.info("[COIN] 报告 {} 回收碳币 {} 枚（userId={}）",
                    reportId, issued.toPlainString(), ownerUserId);
        }
    }

    /**
     * 碳币兑换配额：1 枚碳币换 1 吨排放权。
     *
     * <p>这是碳币的**唯一出口**。碳币不能提现，只能换回配额用于履约或挂单——
     * 若没有这个出口，企业拿到碳币后无处可用，「减排有奖」就只是账面数字。
     *
     * <p>扣币与加配额必须在同一事务：只扣币不加配额=凭空销毁资产，
     * 只加配额不扣币=凭空造币。两者都不可接受。
     *
     * @param userId     兑换人
     * @param amount     兑换枚数（= 换到的吨数）
     * @param operatorId 触发人（企业自助时与 userId 相同）
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse redeemQuota(Long userId, BigDecimal amount, Long operatorId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(4001, "兑换数量必须为正数");
        }

        CarbonCoinAccount account = getAccountEntity(userId);
        validateAccountActive(account);
        // 只校验可用余额：冻结中的币属于在途挂单，不能拿去兑换
        validateBalance(account, amount);

        Enterprise enterprise = enterpriseRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(4007, "当前用户未关联企业"));

        BigDecimal before = account.getBalance();
        BigDecimal after = before.subtract(amount);
        account.setBalance(after);
        account.setTotalSpent(nz(account.getTotalSpent()).add(amount));
        accountRepository.save(account);

        // 配额入账走账本——它是配额字段的唯一合法写入口
        BigDecimal quotaAmount = amount.setScale(4, java.math.RoundingMode.HALF_UP);
        quotaLedgerService.increase(enterprise.getId(), quotaAmount,
                "碳币兑换配额", operatorId == null ? userId : operatorId, "REDEEM", null);

        saveTransaction(userId, TX_TYPE_REDEEM_QUOTA, amount, before, after,
                quotaAmount, null, "碳币兑换配额");

        if (log.isInfoEnabled()) {
            log.info("[COIN] userId={} 兑换 {} 枚碳币 → {} 吨配额", userId,
                    amount.toPlainString(), quotaAmount.toPlainString());
        }
        return toResponse(account);
    }

    /**
     * 转让冻结中的碳币：卖方 frozen → 买方 balance。
     *
     * <p>P2P 成交专用。卖方挂单时已通过 {@link #freezeBalance} 冻结了 N 枚，
     * 成交时这 N 枚直接转给买方——卖方不再收到任何币，因为 P2P 是
     * 「碳币换碳币」，不存在计价关系。
     *
     * <p><b>不能用 {@code buyQuota}/{@code sellQuota} 组合</b>：那两个方法是
     * 「买方扣币 + 卖方进币」的双向计价语义，用在这里会让卖方凭空多收一笔。
     */
    @Transactional
    public void transferFrozen(Long sellerUserId, Long buyerUserId,
                               BigDecimal amount, Long tradeId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(4001, "转让数量必须为正数");
        }

        CarbonCoinAccount seller = getAccountEntity(sellerUserId);
        CarbonCoinAccount buyer = getOrCreateAccountEntity(buyerUserId);

        BigDecimal frozen = nz(seller.getFrozenBalance());
        if (frozen.compareTo(amount) < 0) {
            throw new BusinessException(4008,
                    String.format("冻结碳币不足：已冻结 %s 枚，本次需转让 %s 枚",
                            frozen.toPlainString(), amount.toPlainString()));
        }

        // 卖方：冻结减少（币已转出，不回到可用余额）
        seller.setFrozenBalance(frozen.subtract(amount));
        accountRepository.save(seller);

        BigDecimal buyerBefore = buyer.getBalance();
        BigDecimal buyerAfter = buyerBefore.add(amount);
        buyer.setBalance(buyerAfter);
        accountRepository.save(buyer);

        saveTransaction(sellerUserId, TX_TYPE_SELL_QUOTA, amount,
                nz(seller.getBalance()), nz(seller.getBalance()), null, tradeId,
                String.format("P2P 转让碳币 %s 枚（冻结转出）", amount.toPlainString()));
        saveTransaction(buyerUserId, TX_TYPE_BUY_QUOTA, amount,
                buyerBefore, buyerAfter, null, tradeId,
                String.format("P2P 接收碳币 %s 枚", amount.toPlainString()));

        if (log.isInfoEnabled()) {
            log.info("[COIN] P2P 转让 {} 枚碳币：seller={} → buyer={}（trade={}）",
                    amount.toPlainString(), sellerUserId, buyerUserId, tradeId);
        }
    }

    /** 报告 → 企业 → userId 两跳解析 */
    private Long resolveOwnerUserId(Long enterpriseId) {
        return enterpriseRepository.findById(enterpriseId)
                .map(Enterprise::getUserId)
                .orElseThrow(() -> new BusinessException(4007, "企业不存在: " + enterpriseId));
    }

    /**
     * 取账户，不存在则创建。
     *
     * <p>与 {@link #getOrCreateAccount} 的区别：那个返回 DTO，这个返回实体供内部写入。
     * <b>不能用 getAccountEntity</b>——它找不到就抛 4002，而首份报告审核时
     * 账户还不存在（企业从未访问过碳币页），会直接失败。
     */
    private CarbonCoinAccount getOrCreateAccountEntity(Long userId) {
        return accountRepository.findByUserIdAndDeletedFalse(userId)
                .orElseGet(() -> accountRepository.save(CarbonCoinAccount.builder()
                        .userId(userId)
                        .balance(BigDecimal.ZERO)
                        .frozenBalance(BigDecimal.ZERO)
                        .totalRecharged(BigDecimal.ZERO)
                        .totalSpent(BigDecimal.ZERO)
                        .totalIssued(BigDecimal.ZERO)
                        .status(1)
                        .build()));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private CarbonCoinAccountResponse toResponse(CarbonCoinAccount account) {
        return CarbonCoinAccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .balance(account.getBalance())
                .frozenBalance(account.getFrozenBalance())
                .totalRecharged(account.getTotalRecharged())
                .totalIssued(account.getTotalIssued())
                .totalSpent(account.getTotalSpent())
                .status(account.getStatus())
                .build();
    }
}
