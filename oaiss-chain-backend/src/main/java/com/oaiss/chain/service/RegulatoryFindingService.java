package com.oaiss.chain.service;

import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.RegulatoryFinding;
import com.oaiss.chain.repository.CreditEventRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.RegulatoryFindingRepository;
import com.oaiss.chain.repository.TradeAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监管线索聚合服务。
 *
 * <p>把分散在审核驳回、额度字段、信誉事件里的异常信号，汇成监管可处置的清单。
 * 这是「监管只读」真正有价值的地方——不是多一个查询页面，而是系统替监管做筛选。
 *
 * <p>三条设计约束：
 * <ul>
 *   <li><b>幂等</b>：同企业同规则若已有未处置线索则不再产出。否则定时扫描会让
 *       清单被自身重复项淹没，监管实际无法使用</li>
 *   <li><b>只产出不处置</b>：线索不改变任何业务状态，处置走独立监督意见</li>
 *   <li><b>可解释</b>：每条线索带实际数值（如"近 30 日被驳回 5 次"），
 *       监管要能回答"凭什么"</li>
 * </ul>
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryFindingService {

    /** 连续驳回阈值（窗口内次数） */
    static final int REJECTION_THRESHOLD = 3;
    /** 连续驳回观察窗口天数 */
    static final int REJECTION_WINDOW_DAYS = 30;

    private final RegulatoryFindingRepository findingRepository;
    private final TradeAuditRepository tradeAuditRepository;
    private final CreditEventRepository creditEventRepository;
    private final EnterpriseRepository enterpriseRepository;

    /**
     * 扫描单个企业，产出（通过幂等判据后）新的线索。
     *
     * @return 本次新产出的线索（已有未处置同类线索的不计入）
     */
    @Transactional
    public List<RegulatoryFinding> scanEnterprise(Long enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new com.oaiss.chain.exception.BusinessException(
                        com.oaiss.chain.constant.ErrorCode.RESOURCE_NOT_FOUND,
                        "企业不存在: " + enterpriseId));

        List<RegulatoryFinding> produced = new ArrayList<>();

        // 规则 1：连续成交驳回
        long rejected = tradeAuditRepository.countRejectedSince(
                com.oaiss.chain.entity.TradeAudit.AUDIT_REJECTED,
                LocalDateTime.now().minusDays(REJECTION_WINDOW_DAYS));
        if (rejected >= REJECTION_THRESHOLD) {
            addIfAbsent(produced, enterpriseId, RegulatoryFinding.RULE_REPEATED_REJECTION,
                    RegulatoryFinding.SEVERITY_HIGH,
                    String.format("近 %d 日成交被驳回 %d 次（阈值 %d 次）",
                            REJECTION_WINDOW_DAYS, rejected, REJECTION_THRESHOLD));
        }

        // 规则 2：额度异常（已用 > 持有）
        BigDecimal quota = nz(enterprise.getCarbonQuota());
        BigDecimal used = nz(enterprise.getCarbonUsed());
        if (quota.compareTo(BigDecimal.ZERO) > 0 && used.compareTo(quota) > 0) {
            addIfAbsent(produced, enterpriseId, RegulatoryFinding.RULE_QUOTA_OVERDRAFT,
                    RegulatoryFinding.SEVERITY_CRITICAL,
                    String.format("已用配额 %s 吨超过持有配额 %s 吨，超出 %s 吨",
                            used.toPlainString(), quota.toPlainString(),
                            used.subtract(quota).toPlainString()));
        }

        for (RegulatoryFinding f : produced) {
            log.info("监管线索产出: enterprise={}, rule={}, severity={}",
                    enterpriseId, f.getRuleCode(), f.getSeverity());
        }
        return produced;
    }

    /**
     * 全量扫描（定时任务入口）。
     *
     * <p>单个企业失败不阻断其他企业——一个数据异常的企业不应该让整轮核查停摆，
     * 失败会在日志里留痕并在下一轮重试。
     *
     * @return 企业ID → 本轮新产出的线索
     */
    @Transactional
    public Map<Long, List<RegulatoryFinding>> scanAll() {
        Map<Long, List<RegulatoryFinding>> result = new LinkedHashMap<>();
        List<Enterprise> enterprises = enterpriseRepository.findAll();
        for (Enterprise e : enterprises) {
            try {
                List<RegulatoryFinding> findings = scanEnterprise(e.getId());
                if (!findings.isEmpty()) {
                    result.put(e.getId(), findings);
                }
            } catch (Exception ex) {
                log.warn("监管线索扫描失败，跳过该企业: enterpriseId={}, error={}",
                        e.getId(), ex.getMessage());
            }
        }
        log.info("监管线索全量扫描完成: 企业 {} 家，产出线索 {} 家",
                enterprises.size(), result.size());
        return result;
    }

    /**
     * 处置线索（标记已处置）。线索本身不改变业务状态，这里只记录监管的处理动作。
     */
    @Transactional
    public RegulatoryFinding resolve(Long findingId, Long regulatorId, String note) {
        RegulatoryFinding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new com.oaiss.chain.exception.BusinessException(
                        com.oaiss.chain.constant.ErrorCode.RESOURCE_NOT_FOUND,
                        "线索不存在: " + findingId));
        if (Boolean.TRUE.equals(finding.getResolved())) {
            throw new com.oaiss.chain.exception.BusinessException(
                    com.oaiss.chain.constant.ErrorCode.PARAM_ERROR, "该线索已处置");
        }
        finding.setResolved(true);
        finding.setResolvedBy(regulatorId);
        finding.setResolvedAt(LocalDateTime.now());
        finding.setResolveNote(note);
        return findingRepository.save(finding);
    }

    // ==================== 内部方法 ====================

    private void addIfAbsent(List<RegulatoryFinding> produced, Long enterpriseId,
                             String ruleCode, String severity, String evidence) {
        // 幂等：已有未处置同类线索则不再产出，否则定时扫描会让清单被重复项淹没
        if (findingRepository.existsByEnterpriseIdAndRuleCodeAndResolvedFalse(enterpriseId, ruleCode)) {
            return;
        }
        RegulatoryFinding finding = RegulatoryFinding.builder()
                .enterpriseId(enterpriseId)
                .ruleCode(ruleCode)
                .severity(severity)
                .evidence(evidence)
                .resolved(false)
                .build();
        produced.add(findingRepository.save(finding));
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}