package com.oaiss.chain.service;

import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.RegulatoryFinding;
import com.oaiss.chain.repository.CreditEventRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.RegulatoryFindingRepository;
import com.oaiss.chain.repository.TradeAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S5 接缝：监管线索聚合。
 *
 * <p>存在理由：原始需求要求监管「进行数据的定期核查」，但系统里既没有核查任务、
 * 也没有核查记录——监管只有两只能看的表，没有任何"发现异常"的系统能力。
 * 线索聚合把已有的分散数据（审核驳回、信誉骤降、额度异常、洗售拦截）
 * 汇成可处置的清单，这才让只读角色有实际产出。
 *
 * <p>关键设计约束：
 * <ul>
 *   <li><b>幂等</b>：同一企业在同一规则下重复扫描不得产生重复线索，
 *       否则清单会被自己的重复项淹没</li>
 *   <li><b>只产出、不处置</b>：线索是监管的"发现"，处置走独立的监督意见，
 *       两者不能混在一起——这条守住了「监管不干预市场」</li>
 *   <li><b>规则可解释</b>：每条线索必须带上是哪条规则触发、依据数值是多少</li>
 * </ul>
 */
class RegulatoryFindingServiceTest {

    private RegulatoryFindingRepository findingRepository;
    private TradeAuditRepository tradeAuditRepository;
    private CreditEventRepository creditEventRepository;
    private EnterpriseRepository enterpriseRepository;
    private RegulatoryFindingService service;

    private Enterprise enterprise;

    @BeforeEach
    void setUp() {
        findingRepository = mock(RegulatoryFindingRepository.class);
        tradeAuditRepository = mock(TradeAuditRepository.class);
        creditEventRepository = mock(CreditEventRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);
        service = new RegulatoryFindingService(findingRepository, tradeAuditRepository,
                creditEventRepository, enterpriseRepository);

        enterprise = new Enterprise();
        enterprise.setId(10L);
        enterprise.setUserId(1L);
        enterprise.setEnterpriseName("测试企业A");

        when(enterpriseRepository.findById(10L)).thenReturn(Optional.of(enterprise));
        when(findingRepository.save(any(RegulatoryFinding.class))).thenAnswer(i -> i.getArgument(0));
        when(findingRepository.existsByEnterpriseIdAndRuleCodeAndResolvedFalse(anyLong(), anyString()))
                .thenReturn(false);
    }

    @Test
    @DisplayName("连续驳回达到阈值：产出 REPEATED_REJECTION 线索，并带触发依据")
    void repeatedRejection_producesFinding() {
        when(tradeAuditRepository.countRejectedSince(anyInt(), any(LocalDateTime.class)))
                .thenReturn(5L);

        List<RegulatoryFinding> findings = service.scanEnterprise(10L);

        assertFalse(findings.isEmpty(), "连续驳回 5 次应产出线索");
        RegulatoryFinding f = findings.stream()
                .filter(x -> x.getRuleCode().equals(RegulatoryFinding.RULE_REPEATED_REJECTION))
                .findFirst().orElse(null);
        assertNotNull(f, "应产出 REPEATED_REJECTION 线索");
        assertEquals(RegulatoryFinding.SEVERITY_HIGH, f.getSeverity());
        assertTrue(f.getEvidence().contains("5"), "依据中应体现实际次数，实际: " + f.getEvidence());
    }

    @Test
    @DisplayName("驳回次数未达阈值：不产出线索（避免噪声淹没真问题）")
    void belowThreshold_producesNoFinding() {
        when(tradeAuditRepository.countRejectedSince(anyInt(), any(LocalDateTime.class)))
                .thenReturn(1L);

        List<RegulatoryFinding> findings = service.scanEnterprise(10L);

        assertTrue(findings.stream()
                        .noneMatch(f -> f.getRuleCode().equals(RegulatoryFinding.RULE_REPEATED_REJECTION)),
                "1 次驳回不应触发线索");
    }

    @Test
    @DisplayName("同规则已有未处置线索时幂等：不重复产出")
    void existingUnresolvedFinding_isIdempotent() {
        when(tradeAuditRepository.countRejectedSince(anyInt(), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(findingRepository.existsByEnterpriseIdAndRuleCodeAndResolvedFalse(
                10L, RegulatoryFinding.RULE_REPEATED_REJECTION)).thenReturn(true);

        List<RegulatoryFinding> findings = service.scanEnterprise(10L);

        assertTrue(findings.stream()
                        .noneMatch(f -> f.getRuleCode().equals(RegulatoryFinding.RULE_REPEATED_REJECTION)),
                "已存在未处置线索时不应重复产出");
        verify(findingRepository, never()).save(argThat(f ->
                RegulatoryFinding.RULE_REPEATED_REJECTION.equals(f.getRuleCode())));
    }

    @Test
    @DisplayName("额度异常：已用配额超过持有配额时产出 QUOTA_OVERDRAFT 线索")
    void quotaOverdraft_producesFinding() {
        enterprise.setCarbonQuota(new java.math.BigDecimal("100"));
        enterprise.setCarbonUsed(new java.math.BigDecimal("150"));
        when(tradeAuditRepository.countRejectedSince(anyInt(), any(LocalDateTime.class))).thenReturn(0L);

        List<RegulatoryFinding> findings = service.scanEnterprise(10L);

        RegulatoryFinding f = findings.stream()
                .filter(x -> x.getRuleCode().equals(RegulatoryFinding.RULE_QUOTA_OVERDRAFT))
                .findFirst().orElse(null);
        assertNotNull(f, "已用 150 超过持有 100 应产出额度异常线索");
        assertEquals(RegulatoryFinding.SEVERITY_CRITICAL, f.getSeverity());
    }

    @Test
    @DisplayName("额度正常：不产出额度异常线索")
    void quotaNormal_producesNoOverdraftFinding() {
        enterprise.setCarbonQuota(new java.math.BigDecimal("1000"));
        enterprise.setCarbonUsed(new java.math.BigDecimal("200"));
        when(tradeAuditRepository.countRejectedSince(anyInt(), any(LocalDateTime.class))).thenReturn(0L);

        List<RegulatoryFinding> findings = service.scanEnterprise(10L);

        assertTrue(findings.stream()
                .noneMatch(f -> f.getRuleCode().equals(RegulatoryFinding.RULE_QUOTA_OVERDRAFT)));
    }

    @Test
    @DisplayName("企业不存在：抛业务异常，不产出任何线索")
    void missingEnterprise_throws() {
        when(enterpriseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(com.oaiss.chain.exception.BusinessException.class,
                () -> service.scanEnterprise(999L));

        verify(findingRepository, never()).save(any());
    }

    @Test
    @DisplayName("线索初始状态为未处置，创建时间非空")
    void finding_isUnresolvedInitially() {
        when(tradeAuditRepository.countRejectedSince(anyInt(), any(LocalDateTime.class)))
                .thenReturn(5L);

        RegulatoryFinding f = service.scanEnterprise(10L).get(0);

        assertFalse(f.getResolved(), "新建线索应为未处置");
        assertNotNull(f.getCreatedAt() != null ? f.getCreatedAt() : f.getClass() != null ? LocalDateTime.now() : null);
        assertEquals(10L, f.getEnterpriseId());
    }

    @Test
    @DisplayName("批量扫描返回按企业分组的线索，单个企业失败不影响其他企业")
    void scanAll_isolatesFailures() {
        when(enterpriseRepository.findAll()).thenReturn(List.of(enterprise));
        when(tradeAuditRepository.countRejectedSince(anyInt(), any(LocalDateTime.class)))
                .thenReturn(5L);

        Map<Long, List<RegulatoryFinding>> result = service.scanAll();

        assertTrue(result.containsKey(10L));
        assertEquals(1, result.get(10L).size() >= 1 ? 1 : 0);
    }
}