package com.oaiss.chain.service;

import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.QuotaTransaction;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.QuotaTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 配额发放 / 冲正的账本层测试。
 *
 * <p><b>为什么必须单独立类</b>：{@code QuotaAllocationServiceTest} 里
 * {@link QuotaLedgerService} 是 mock，只能验证"调了对的方法"，无法验证
 * **方法内部把哪几个字段改了、改成什么方向**。而配额冲正是全仓第一次让
 * {@code carbon_quota} 下降，方向错一次就是资产错账。
 *
 * <p>本类直接构造真实的 {@code QuotaLedgerService}（repository 用 mock），
 * 断言字段级的前后值。
 *
 * <p>核心不变量：
 * {@code quota == tradable + frozen + used}（在无审核期冻结的场景下）
 */
class QuotaLedgerAllocationTest {

    private EnterpriseRepository enterpriseRepository;
    private QuotaTransactionRepository quotaTransactionRepository;
    private QuotaLedgerService ledgerService;

    private Enterprise enterprise;

    @BeforeEach
    void setUp() {
        enterpriseRepository = mock(EnterpriseRepository.class);
        quotaTransactionRepository = mock(QuotaTransactionRepository.class);
        ledgerService = new QuotaLedgerService(enterpriseRepository, quotaTransactionRepository);

        enterprise = new Enterprise();
        enterprise.setId(10L);
        // 夹具必须满足恒等式 quota = tradable + frozen + used
        enterprise.setCarbonQuota(new BigDecimal("1500.0000"));
        enterprise.setCarbonTradable(new BigDecimal("1000.0000"));
        enterprise.setCarbonUsed(new BigDecimal("500.0000"));
        enterprise.setCarbonFrozen(BigDecimal.ZERO);

        when(enterpriseRepository.findById(10L)).thenReturn(Optional.of(enterprise));
        when(enterpriseRepository.save(any(Enterprise.class))).thenAnswer(i -> i.getArgument(0));
        when(quotaTransactionRepository.save(any(QuotaTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private void assertInvariant() {
        BigDecimal expected = enterprise.getCarbonTradable()
                .add(enterprise.getCarbonFrozen())
                .add(enterprise.getCarbonUsed());
        assertEquals(0, enterprise.getCarbonQuota().compareTo(expected),
                String.format("恒等式破裂：quota=%s，但 tradable+frozen+used=%s",
                        enterprise.getCarbonQuota(), expected));
    }

    // ==================== 发放 ====================

    @Test
    @DisplayName("发放：quota 与 tradable 同增，used 绝不动")
    void increaseForReport_addsQuotaAndTradable_keepsUsed() {
        BigDecimal usedBefore = enterprise.getCarbonUsed();

        ledgerService.increaseForReport(10L, new BigDecimal("200.0000"),
                "发放", 7L, 100L, 1);

        assertEquals(0, new BigDecimal("1700.0000").compareTo(enterprise.getCarbonQuota()),
                "quota 应 1500 → 1700");
        assertEquals(0, new BigDecimal("1200.0000").compareTo(enterprise.getCarbonTradable()),
                "tradable 应 1000 → 1200");
        assertEquals(0, usedBefore.compareTo(enterprise.getCarbonUsed()),
                "used 不得变动");
        assertInvariant();
    }

    @Test
    @DisplayName("发放流水：类型 ALLOCATION、带报告ID、used 前后值相等、幂等键 reportId#seq")
    void increaseForReport_writesLedgerWithReportDimension() {
        ledgerService.increaseForReport(10L, new BigDecimal("200.0000"),
                "发放", 7L, 100L, 3);

        ArgumentCaptor<QuotaTransaction> captor = ArgumentCaptor.forClass(QuotaTransaction.class);
        verify(quotaTransactionRepository).save(captor.capture());
        QuotaTransaction r = captor.getValue();

        assertEquals("ALLOCATION", r.getChangeType());
        assertEquals(Long.valueOf(100L), r.getRelatedReportId());
        assertEquals("100#3", r.getAllocationKey());
        assertEquals(Integer.valueOf(3), r.getAllocationSeq());
        assertEquals("AUDIT", r.getSource());
        // 发放不改 used，故前后值相等
        assertEquals(0, r.getUsedBefore().compareTo(r.getUsedAfter()),
                "发放不应改变 used，used_before 与 used_after 必须相等");
    }

    // ==================== 冲正 ====================

    @Test
    @DisplayName("冲正：quota 与 tradable 同减，used 绝不动，恒等式保持")
    void revokeAllocation_decreasesQuotaAndTradable_keepsUsed() {
        // 先发放 200，再冲正
        ledgerService.increaseForReport(10L, new BigDecimal("200.0000"), "发放", 7L, 100L, 1);
        BigDecimal usedAfterAllocation = enterprise.getCarbonUsed();
        assertEquals(0, new BigDecimal("1700.0000").compareTo(enterprise.getCarbonQuota()));

        ledgerService.revokeAllocation(10L, new BigDecimal("200.0000"),
                "冲正", 7L, 100L);

        assertEquals(0, new BigDecimal("1500.0000").compareTo(enterprise.getCarbonQuota()),
                "quota 应回退到 1500");
        assertEquals(0, new BigDecimal("1000.0000").compareTo(enterprise.getCarbonTradable()),
                "tradable 应回退到 1000");
        assertEquals(0, usedAfterAllocation.compareTo(enterprise.getCarbonUsed()),
                "冲正不得动 used");
        assertInvariant();
    }

    @Test
    @DisplayName("冲正流水：类型 ALLOCATION_REVOKE、allocation_key 为 NULL（可与重发共存）")
    void revokeAllocation_writesLedgerWithNullAllocationKey() {
        ledgerService.increaseForReport(10L, new BigDecimal("200.0000"), "发放", 7L, 100L, 1);
        reset(quotaTransactionRepository);
        when(quotaTransactionRepository.save(any(QuotaTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));

        ledgerService.revokeAllocation(10L, new BigDecimal("200.0000"), "冲正", 7L, 100L);

        ArgumentCaptor<QuotaTransaction> captor = ArgumentCaptor.forClass(QuotaTransaction.class);
        verify(quotaTransactionRepository).save(captor.capture());
        QuotaTransaction r = captor.getValue();

        assertEquals("ALLOCATION_REVOKE", r.getChangeType());
        assertEquals(Long.valueOf(100L), r.getRelatedReportId());
        assertNull(r.getAllocationKey(),
                "冲正行的 allocation_key 必须为 NULL——否则会与重发的新序号冲突");
        assertNull(r.getAllocationSeq());
    }

    @Test
    @DisplayName("冲正时 tradable 不足 → 拒绝，余额不变（不做负余额兜底）")
    void revokeAllocation_rejectsWhenTradableInsufficient() {
        // 企业 tradable 只有 1000，要冲正 1500
        assertThrows(BusinessException.class,
                () -> ledgerService.revokeAllocation(10L, new BigDecimal("1500.0000"),
                        "超额冲正", 7L, 100L));

        assertEquals(0, new BigDecimal("1000.0000").compareTo(enterprise.getCarbonTradable()),
                "失败的冲正不得改变余额");
        verify(quotaTransactionRepository, never()).save(any(QuotaTransaction.class));
    }

    @Test
    @DisplayName("冲正金额必须为正数")
    void revokeAllocation_rejectsNonPositive() {
        assertThrows(BusinessException.class,
                () -> ledgerService.revokeAllocation(10L, BigDecimal.ZERO, "零冲正", 7L, 100L));
        assertThrows(BusinessException.class,
                () -> ledgerService.revokeAllocation(10L, new BigDecimal("-5"), "负冲正", 7L, 100L));
    }

    // ==================== 与既有 rollback 的区别 ====================

    @Test
    @DisplayName("对比：rollback 与 revokeAllocation 方向相反，不可互换")
    void rollbackAndRevokeHaveOppositeDirections() {
        // rollback：撤销交易扣减 → tradable 加、used 减、quota 不动
        enterprise.setCarbonTradable(new BigDecimal("800.0000"));
        enterprise.setCarbonUsed(new BigDecimal("700.0000"));
        enterprise.setCarbonQuota(new BigDecimal("1500.0000"));

        ledgerService.rollback(10L, new BigDecimal("100.0000"), "交易驳回", 7L, "AUCTION", 55L);

        assertEquals(0, new BigDecimal("900.0000").compareTo(enterprise.getCarbonTradable()),
                "rollback 应让 tradable 增加");
        assertEquals(0, new BigDecimal("600.0000").compareTo(enterprise.getCarbonUsed()),
                "rollback 应让 used 减少");
        assertEquals(0, new BigDecimal("1500.0000").compareTo(enterprise.getCarbonQuota()),
                "rollback 不动 quota");

        // revokeAllocation：撤销配额发放 → tradable 减、quota 减、used 不动
        enterprise.setCarbonTradable(new BigDecimal("1000.0000"));
        enterprise.setCarbonUsed(new BigDecimal("500.0000"));
        enterprise.setCarbonQuota(new BigDecimal("1500.0000"));

        ledgerService.revokeAllocation(10L, new BigDecimal("100.0000"), "配额冲正", 7L, 100L);

        assertEquals(0, new BigDecimal("900.0000").compareTo(enterprise.getCarbonTradable()),
                "revoke 应让 tradable 减少");
        assertEquals(0, new BigDecimal("500.0000").compareTo(enterprise.getCarbonUsed()),
                "revoke 不动 used");
        assertEquals(0, new BigDecimal("1400.0000").compareTo(enterprise.getCarbonQuota()),
                "revoke 应让 quota 减少");

        // 两者混用会让 quota 单向偏离：只用 rollback 做冲正，quota 永远不降
        enterprise.setCarbonQuota(new BigDecimal("1500.0000"));
        enterprise.setCarbonTradable(new BigDecimal("900.0000"));
        ledgerService.rollback(10L, new BigDecimal("100.0000"), "误用 rollback 冲正", 7L, "AUDIT", 100L);
        assertEquals(0, new BigDecimal("1500.0000").compareTo(enterprise.getCarbonQuota()),
                "若误用 rollback 代替 revoke，quota 不会下降——这正是它不能复用的原因");
    }
}