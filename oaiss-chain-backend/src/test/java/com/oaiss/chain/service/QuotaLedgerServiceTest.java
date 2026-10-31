package com.oaiss.chain.service;

import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.QuotaTransaction;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.QuotaTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S1 接缝：配额变动账本。
 *
 * <p>存在理由：配额变动此前只内联在 {@code TradeService}（12 处）与
 * {@code DoubleAuctionService}（11 处）的交易结算路径里，直接在
 * {@code Enterprise} 的 {@code carbonQuota/carbonUsed/carbonTradable/carbonFrozen}
 * 四个字段上就地加减。后果是「这 500 吨是被哪一笔交易、哪一次冻结动的」
 * 在系统里无法事后回答——成交审核的裁决依据、额度上限的周期累计、
 * 监管的逐笔归因，三者都建立在这个能力之上。
 *
 * <p>本接缝要保证的三件事：
 * <ol>
 *   <li>每次变动都留痕（冻结/解冻/扣减/增加/驳回回滚各一条）</li>
 *   <li>流水前后值首尾相接（record[i].after == record[i+1].before），可对账</li>
 *   <li>总量守恒（变动增量之和 == 字段实际变化）</li>
 * </ol>
 *
 * <p>会漏掉的：有人绕过本服务直接 set 字段——那由静态门禁覆盖，不在本测试范围。
 */
class QuotaLedgerServiceTest {

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
        enterprise.setCarbonQuota(new BigDecimal("1000.0000"));
        enterprise.setCarbonUsed(BigDecimal.ZERO);
        enterprise.setCarbonTradable(new BigDecimal("1000.0000"));
        enterprise.setCarbonFrozen(BigDecimal.ZERO);

        when(enterpriseRepository.findById(10L)).thenReturn(Optional.of(enterprise));
        when(enterpriseRepository.save(any(Enterprise.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quotaTransactionRepository.save(any(QuotaTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private List<QuotaTransaction> capturedRecords(int expectedCount) {
        ArgumentCaptor<QuotaTransaction> captor = ArgumentCaptor.forClass(QuotaTransaction.class);
        verify(quotaTransactionRepository, times(expectedCount)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("冻结：可交易减少、冻结增加，并留下一条流水")
    void freeze_movesTradableToFrozen_andRecordsLedger() {
        ledgerService.freeze(10L, new BigDecimal("300.0000"), "挂单冻结", 99L, "AUCTION");

        assertEquals(0, new BigDecimal("700.0000").compareTo(enterprise.getCarbonTradable()),
                "可交易应减少 300");
        assertEquals(0, new BigDecimal("300.0000").compareTo(enterprise.getCarbonFrozen()),
                "冻结应增加 300");

        QuotaTransaction record = capturedRecords(1).get(0);
        assertEquals("FREEZE", record.getChangeType());
        assertEquals(0, new BigDecimal("1000.0000").compareTo(record.getTradableBefore()),
                "流水记录的变动前余额应为初始值 1000");
        assertEquals(0, new BigDecimal("700.0000").compareTo(record.getTradableAfter()),
                "流水记录的变动后余额应为 700");
        assertEquals(0, new BigDecimal("300.0000").compareTo(record.getAmount()));
    }

    @Test
    @DisplayName("连冻解扣三段操作：流水前后值首尾相接，且总量守恒")
    void sequentialOperations_produceContiguousLedger() {
        ledgerService.freeze(10L, new BigDecimal("300.0000"), "挂单冻结", 99L, "AUCTION");
        ledgerService.unfreeze(10L, new BigDecimal("300.0000"), "撤单解冻", 99L, "AUCTION");
        ledgerService.transfer(10L, new BigDecimal("200.0000"), "成交扣减", 99L, "AUCTION");

        List<QuotaTransaction> records = capturedRecords(3);

        // 首尾相接：上一条的 after 必须等于下一条的 before
        for (int i = 0; i < records.size() - 1; i++) {
            assertEquals(0, records.get(i).getTradableAfter()
                            .compareTo(records.get(i + 1).getTradableBefore()),
                    "第 " + (i + 1) + " 条与第 " + (i + 2) + " 条流水的可交易余额断裂");
        }

        // 守恒：初始 1000 - 冻结300 + 解冻300 - 扣减200 = 800
        assertEquals(0, new BigDecimal("800.0000").compareTo(enterprise.getCarbonTradable()),
                "可交易余额应为 800");
        assertEquals(0, BigDecimal.ZERO.compareTo(enterprise.getCarbonFrozen()),
                "冻结余额应归零");
        assertEquals(0, new BigDecimal("200.0000").compareTo(enterprise.getCarbonUsed()),
                "已用应累计 200");
    }

    @Test
    @DisplayName("解冻金额超过冻结余额时拒绝，且不留流水")
    void unfreeze_exceedingFrozen_isRejected() {
        ledgerService.freeze(10L, new BigDecimal("100.0000"), "挂单冻结", 99L, "AUCTION");

        assertThrows(com.oaiss.chain.exception.BusinessException.class,
                () -> ledgerService.unfreeze(10L, new BigDecimal("500.0000"), "非法解冻", 99L, "AUCTION"));

        // 只应有冻结那一条
        assertEquals(1, capturedRecords(1).size());
        assertEquals(0, new BigDecimal("100.0000").compareTo(enterprise.getCarbonFrozen()),
                "失败操作不得改变余额");
    }

    @Test
    @DisplayName("可交易不足时冻结被拒绝，且不留流水")
    void freeze_exceedingTradable_isRejected() {
        assertThrows(com.oaiss.chain.exception.BusinessException.class,
                () -> ledgerService.freeze(10L, new BigDecimal("5000.0000"), "超额挂单", 99L, "AUCTION"));

        verify(quotaTransactionRepository, never()).save(any(QuotaTransaction.class));
        assertEquals(0, new BigDecimal("1000.0000").compareTo(enterprise.getCarbonTradable()),
                "失败操作不得改变余额");
    }

    @Test
    @DisplayName("驳回回滚：把已扣减的额度原路退回并留痕")
    void rollback_returnsTransferredQuota_andRecordsLedger() {
        ledgerService.transfer(10L, new BigDecimal("200.0000"), "成交扣减", 99L, "AUCTION");
        ledgerService.rollback(10L, new BigDecimal("200.0000"), "审核驳回回滚", 99L, "AUCTION");

        assertEquals(0, new BigDecimal("1000.0000").compareTo(enterprise.getCarbonTradable()),
                "回滚后可交易应恢复原值");
        assertEquals(0, BigDecimal.ZERO.compareTo(enterprise.getCarbonUsed()),
                "回滚后已用应归零");

        QuotaTransaction record = capturedRecords(2).get(1);
        assertEquals("ROLLBACK", record.getChangeType());
    }
}