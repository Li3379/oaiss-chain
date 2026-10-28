package com.oaiss.chain.service;

import com.oaiss.chain.dto.CarbonCoinAccountResponse;
import com.oaiss.chain.dto.CarbonCoinRechargeRequest;
import com.oaiss.chain.dto.CarbonCoinTransferRequest;
import com.oaiss.chain.entity.CarbonCoinAccount;
import com.oaiss.chain.entity.CarbonCoinTransaction;
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
 * 提供碳币账户管理、充值、消费、转账功能
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

    /**
     * 交易类型
     */
    public static final int TX_TYPE_RECHARGE = 1;
    public static final int TX_TYPE_BUY_QUOTA = 2;
    public static final int TX_TYPE_SELL_QUOTA = 3;
    public static final int TX_TYPE_TRANSFER = 4;

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
     * 碳币转账
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse transfer(Long userId, CarbonCoinTransferRequest request) {
        if (userId.equals(request.getCounterpartId())) {
            throw new BusinessException(4001, "不能向自己转账");
        }

        CarbonCoinAccount fromAccount = getAccountEntity(userId);
        validateAccountActive(fromAccount);
        validateBalance(fromAccount, request.getAmount());

        CarbonCoinAccount toAccount = accountRepository.findByUserIdAndDeletedFalse(request.getCounterpartId())
                .orElseThrow(() -> new BusinessException(4003, "对方账户不存在"));
        validateAccountActive(toAccount);

        // 扣款
        BigDecimal fromBefore = fromAccount.getBalance();
        BigDecimal fromAfter = fromBefore.subtract(request.getAmount());
        fromAccount.setBalance(fromAfter);
        fromAccount.setTotalSpent(fromAccount.getTotalSpent().add(request.getAmount()));

        // 到账
        BigDecimal toBefore = toAccount.getBalance();
        BigDecimal toAfter = toBefore.add(request.getAmount());
        toAccount.setBalance(toAfter);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        saveTransaction(userId, TX_TYPE_TRANSFER, request.getAmount(),
                fromBefore, fromAfter, null, null,
                "转账给用户" + request.getCounterpartId() + (request.getRemark() != null ? ": " + request.getRemark() : ""));

        saveTransaction(request.getCounterpartId(), TX_TYPE_TRANSFER, request.getAmount(),
                toBefore, toAfter, null, null,
                "收到用户" + userId + "转账" + (request.getRemark() != null ? ": " + request.getRemark() : ""));

        log.info("碳币转账: from={}, to={}, amount={}", userId, request.getCounterpartId(), request.getAmount());
        return toResponse(fromAccount);
    }

    /**
     * 冻结碳币（买单挂单时锁定资金）
     */
    @DistributedLock(key = "'carbon:coin:' + #userId", expireTime = 30)
    @Transactional
    public CarbonCoinAccountResponse freezeBalance(Long userId, BigDecimal amount) {
        CarbonCoinAccount account = getAccountEntity(userId);
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
        CarbonCoinAccount account = getAccountEntity(userId);
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
        CarbonCoinTransaction tx = CarbonCoinTransaction.builder()
                .txNo(generateTxNo())
                .userId(userId)
                .txType(txType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .relatedQuota(relatedQuota)
                .relatedTradeId(relatedTradeId)
                .remark(remark)
                .build();
        transactionRepository.save(tx);
    }

    private CarbonCoinAccountResponse toResponse(CarbonCoinAccount account) {
        return CarbonCoinAccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .balance(account.getBalance())
                .frozenBalance(account.getFrozenBalance())
                .totalRecharged(account.getTotalRecharged())
                .totalSpent(account.getTotalSpent())
                .status(account.getStatus())
                .build();
    }
}
