package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.OutboxMessage;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.OutboxMessageRepository;
import com.oaiss.chain.util.EmissionDataValidator;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 链上对账与死信管理服务。
 *
 * <h2>为什么需要它</h2>
 * 改造前系统对链只有「写」：DB → outbox → 链码。链上写了什么，本地只能靠
 * {@code carbon_report.blockchain_tx_hash} 这个自填字段来相信。于是有两种情况无人能发现：
 * 本地标了上链但链上其实没有；链上有存证但本地指针被抹掉（驳回分支会这么做）。
 * 而 outbox 超过重试次数转为 DEAD 之后，连一条查询入口都没有——死信是黑洞。
 *
 * <h2>「还原」的边界</h2>
 * Fabric 账本不可修改，所以这里不做任何「改写链上数据」的动作。能做的只有：
 * <ul>
 *   <li><b>对账</b>：把差异分类呈现，让人知道哪份报告需要人工处理；</li>
 *   <li><b>补偿</b>：把 DEAD 死信复位后重投，让「本该上链却没上」的记录重新走正常流程。</li>
 * </ul>
 * 真正的「更正」需要链码追加一条 CORRECTION 记录（需链码升级），不在本服务职责内。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainReconciliationService {

    private static final String STATUS_DEAD = "DEAD";
    private static final String STATUS_PENDING = "PENDING";

    /** 对账分类常量（与前端 status 文案一一对应） */
    static final String STATUS_MATCHED = "MATCHED";
    static final String STATUS_MISMATCH = "CONTENT_MISMATCH";
    static final String STATUS_MISSING = "MISSING_ON_CHAIN";
    static final String STATUS_UNVERIFIABLE = "UNVERIFIABLE";
    static final String STATUS_INSUFFICIENT = "INSUFFICIENT_DATA";
    static final String STATUS_HISTORICAL_V1 = "HISTORICAL_V1";
    static final String STATUS_HISTORICAL_MOCK = "HISTORICAL_MOCK";

    /** 链上快照版本标记：v1 结构只有 6 个 legacy 字段，无金额可比 */
    private static final String SCHEMA_V1 = "v1";

    /** 分页上限：防止同步阻塞入口被 size=100000 放大成十万次链调用 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 历史 Mock 时钟哈希的标记值（链上不存在，已确认，不再纳入缺失告警） */
    public static final String MARK_HISTORICAL_MOCK = "HISTORICAL_MOCK";
    /** Mock 模式下生成的哈希前缀（System.currentTimeMillis + UUID） */
    private static final String MOCK_TX_PREFIX = "tx_mock_";
    private static final String MOCK_MARK_REASON =
            "Mock profile 时期生成：哈希仅存于本地库，从未写入 Fabric 账本，已确认为历史欠账";

    private final CarbonReportRepository carbonReportRepository;
    private final BlockchainServicePort blockchainService;
    private final OutboxMessageRepository outboxMessageRepository;
    private final com.oaiss.chain.repository.TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    /** 可选：用于暴露「无法判定占比」指标。单测构造可为 null。 */
    private final MetricsService metricsService;

    /**
     * 对账结果。
     *
     * <p><b>计数口径</b>：7 类计数与 {@code checkedGlobal} 都是<b>全库</b>口径，
     * 由同一次全量遍历产出；{@code details} 只是当前页的切片。
     * 不变量：{@code matched + insufficientData + historicalV1 + historicalMock
     * + missingOnChain + contentMismatch + unverifiable == checkedGlobal}。</p>
     *
     * <p>历史教训：早前 {@code checked} 是「本页条数」而 {@code total} 是「全库」，
     * 两个口径并列展示，导致管理员在同一统计条上看到对不上的数字。</p>
     */
    @Getter
    @Builder
    public static class ReconciliationResult {
        /** 全库参与对账的报告总数（= 本次遍历条数，含 HISTORICAL_MOCK） */
        private final int checkedGlobal;
        /** 当前页条数（仅供前端标注「本页」） */
        private final int checkedPage;
        /** 哈希已验证且全字段一致 */
        private final int matched;
        /** 链上有快照，但金额/参与字段不足，无法判定是否一致 */
        private final int insufficientData;
        /** 链上为 v1 结构（无金额字段），仅做哈希验证、不做内容比对 */
        private final int historicalV1;
        /** 库有 txHash、链上查无记录 */
        private final int missingOnChain;
        /** 哈希不符或字段不符 */
        private final int contentMismatch;
        /** 查询链上失败（无法判定，不应算作差异） */
        private final int unverifiable;
        /** 已标记为历史 Mock 欠账、不再重复告警的记录数 */
        private final int historicalMock;
        /** 逐条明细（当前页），供管理员定位 */
        private final List<Map<String, Object>> details;
        private final long total;
    }

    /**
     * 单条记录的判定结果（全量遍历的内部载体）。
     *
     * <p>{@code txHashVerified} 是<b>逐条</b>属性，所以不放聚合 DTO：
     * 取值 {@code true} / {@code false} / {@code null}（null = 无法判定）。</p>
     */
    private record RowOutcome(Long reportId, String category, Boolean txHashVerified, Map<String, Object> detail) {
    }

    /** 参与内容比对的 11 个字段（v2 快照）。v1 只有 6 个 legacy 字段，不做内容比对。 */
    private static final int MIN_COMPARABLE_FIELDS = 4;

    /**
     * 执行对账：逐条比对本地已上链报告与链上快照。
     *
     * <p><b>单次全量遍历是唯一数据源</b>：7 类计数与明细都从同一次遍历产出，
     * 明细按 offset 切片得到当前页。这样 {@code sum(7类) == checkedGlobal}
     * 由构造保证，而不是「两个查询碰巧相等」。</p>
     *
     * <p>遍历需要 N 次链调用（N = 全库有 txHash 的报告数）。当前 N≈29 时可接受；
     * N &gt; 500 应改为增量/缓存，届时需重新评估本方法的实现。</p>
     *
     * @param page 页码（从1开始）
     * @param size 每页数量（上限 200，防止同步阻塞入口被放大）
     */
    @Transactional(readOnly = true)
    public ReconciliationResult reconcile(Integer page, Integer size) {
        int pageNum = Math.max(page == null ? 1 : page, 1);
        int pageSize = Math.min(size == null || size < 1 ? 10 : size, MAX_PAGE_SIZE);

        // 全量遍历（含排序，保证分页可复现）
        List<CarbonReport> all = carbonReportRepository
                .findByBlockchainTxHashIsNotNullAndDeletedFalse(Sort.by(Sort.Direction.ASC, "id"));

        int matched = 0, insufficient = 0, historicalV1 = 0;
        int missing = 0, mismatch = 0, unverifiable = 0, historicalMock = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());

        for (int i = 0; i < all.size(); i++) {
            RowOutcome outcome = judge(all.get(i));
            switch (outcome.category()) {
                case STATUS_HISTORICAL_MOCK -> historicalMock++;
                case STATUS_UNVERIFIABLE -> unverifiable++;
                case STATUS_MISSING -> missing++;
                case STATUS_HISTORICAL_V1 -> historicalV1++;
                case STATUS_INSUFFICIENT -> insufficient++;
                case STATUS_MISMATCH -> mismatch++;
                case STATUS_MATCHED -> matched++;
                default -> throw new IllegalStateException("未知对账分类: " + outcome.category());
            }
            if (i >= from && i < to) {
                details.add(outcome.detail());
            }
        }

        if (metricsService != null) {
            metricsService.recordReconciliation(insufficient + unverifiable, all.size());
        }

        return ReconciliationResult.builder()
                .checkedGlobal(all.size())
                .checkedPage(details.size())
                .matched(matched)
                .insufficientData(insufficient)
                .historicalV1(historicalV1)
                .missingOnChain(missing)
                .contentMismatch(mismatch)
                .unverifiable(unverifiable)
                .historicalMock(historicalMock)
                .details(details)
                .total(all.size())
                .build();
    }

    /**
     * 单条记录判定，严格按 9 行判定优先级表（前一条命中即终止）。
     *
     * <p><b>步骤 0（前置计算）</b>：取到快照后立即验哈希，产出 {@code txHashVerified}。
     * 该值是计算值，贯穿后续各行，<b>不在任何行硬编码</b>——
     * 否则「内容不符」分支会先于哈希校验命中，却宣称哈希已验证（假声明）。</p>
     */
    private RowOutcome judge(CarbonReport report) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportId", report.getId());
        row.put("reportNo", report.getReportNo());
        row.put("dbTxHash", report.getBlockchainTxHash());

        // 1. 已标记的历史 Mock 欠账：不查链（必然失败的往返），也不重复告警
        if (report.getChainMark() != null) {
            row.put("status", report.getChainMark());
            row.put("chainMark", report.getChainMark());
            row.put("reason", report.getChainMarkReason());
            row.put("txHashVerified", null);
            return new RowOutcome(report.getId(), STATUS_HISTORICAL_MOCK, null, row);
        }

        // 2. 链不可达 / 快照或 txHash 解析失败 / txHash 字段缺失
        String snapshot;
        try {
            snapshot = blockchainService.queryReportSnapshot(report.getId());
        } catch (Exception e) {
            row.put("status", STATUS_UNVERIFIABLE);
            row.put("reason", e.getMessage());
            row.put("txHashVerified", null);
            return new RowOutcome(report.getId(), STATUS_UNVERIFIABLE, null, row);
        }
        if (snapshot == null || snapshot.isBlank()) {
            // 3. 链上查无该 key
            row.put("status", STATUS_MISSING);
            row.put("reason", "链上查无该报告的存证记录");
            row.put("txHashVerified", false);
            return new RowOutcome(report.getId(), STATUS_MISSING, false, row);
        }

        JsonNode chain;
        try {
            chain = objectMapper.readTree(snapshot);
        } catch (Exception e) {
            row.put("status", STATUS_UNVERIFIABLE);
            row.put("reason", "链上快照无法解析: " + e.getMessage());
            row.put("txHashVerified", null);
            return new RowOutcome(report.getId(), STATUS_UNVERIFIABLE, null, row);
        }

        // ---- 步骤 0：前置计算 txHashVerified ----
        String chainTxHash = text(chain, "txHash");
        Boolean txHashVerified;
        if (chainTxHash == null || chainTxHash.isBlank()) {
            // txHash 字段缺失 → 无法判定（不得填 false，那会把「不知道」说成「哈希为假」）
            row.put("status", STATUS_UNVERIFIABLE);
            row.put("reason", "链上快照缺少 txHash 字段，无法校验哈希");
            row.put("txHashVerified", null);
            return new RowOutcome(report.getId(), STATUS_UNVERIFIABLE, null, row);
        }
        txHashVerified = chainTxHash.equals(report.getBlockchainTxHash());
        row.put("chainTxHash", chainTxHash);
        row.put("txHashVerified", txHashVerified);

        // 4. v1 结构：链上恒无金额字段 → 只验哈希，不做内容比对
        String schemaVersion = text(chain, "schemaVersion");
        if (SCHEMA_V1.equals(schemaVersion)) {
            row.put("status", STATUS_HISTORICAL_V1);
            row.put("reason", "链上为 v1 结构，无金额字段，未做内容比对（哈希已验证=" + txHashVerified + "）");
            return new RowOutcome(report.getId(), STATUS_HISTORICAL_V1, txHashVerified, row);
        }

        // 5/6. 内容可比性判定（先于内容比对：比不了就不许判一致）
        BigDecimal chainTotal = decimal(chain, "totalEmission");
        BigDecimal dbTotal = report.getTotalEmission();
        row.put("chainTotalEmission", chainTotal == null ? null : chainTotal.toPlainString());
        row.put("dbTotalEmission", dbTotal == null ? null : dbTotal.toPlainString());

        List<String> mismatchFields = compareSnapshot(report, chain);
        int comparable = comparableFieldCount(report, chain);

        if (chainTotal == null) {
            row.put("status", STATUS_INSUFFICIENT);
            row.put("reason", "链上 totalEmission 为空，无法比对总量");
            row.put("mismatchFields", mismatchFields);
            return new RowOutcome(report.getId(), STATUS_INSUFFICIENT, txHashVerified, row);
        }
        if (comparable < MIN_COMPARABLE_FIELDS) {
            row.put("status", STATUS_INSUFFICIENT);
            row.put("reason", "可比对字段数 " + comparable + " 少于下限 " + MIN_COMPARABLE_FIELDS);
            row.put("mismatchFields", mismatchFields);
            return new RowOutcome(report.getId(), STATUS_INSUFFICIENT, txHashVerified, row);
        }

        // 7. 内容字段不符
        if (!mismatchFields.isEmpty()) {
            row.put("status", STATUS_MISMATCH);
            row.put("reason", "链上内容与本地记录不一致");
            row.put("mismatchFields", mismatchFields);
            return new RowOutcome(report.getId(), STATUS_MISMATCH, txHashVerified, row);
        }

        // 8. 内容全等但哈希不符（提交后被篡改 blockchain_tx_hash，或链上换了版本）
        if (!txHashVerified) {
            row.put("status", STATUS_MISMATCH);
            row.put("reason", "链上内容一致，但 txHash 与本地记录不符");
            row.put("mismatchFields", List.of("txHash"));
            return new RowOutcome(report.getId(), STATUS_MISMATCH, false, row);
        }

        // 9. 其余：哈希已验证 + 全字段一致
        row.put("status", STATUS_MATCHED);
        return new RowOutcome(report.getId(), STATUS_MATCHED, true, row);
    }

    /**
     * 标记历史 Mock 脏数据：把 {@code tx_mock_*} 哈希的记录标注为「链上不存在，已确认」。
     *
     * <p>只加标记，<b>不改动</b> {@code blockchainTxHash} / {@code status} / {@code onChainAt}：
     * 哈希是历史真相，抹掉就没有审计线索；状态是业务事实，标记无权改写。</p>
     *
     * <p>幂等：已标记的记录不会重复写入，也不会重复计数。</p>
     *
     * @param operatorId 操作人用户ID，可为 null（系统自动标记）
     * @return 本次新标记的记录数（报告 + 交易）
     */
    @Transactional
    public int markHistoricalMockData() {
        return markHistoricalMockData(null);
    }

    @Transactional
    public int markHistoricalMockData(Long operatorId) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String likePrefix = MOCK_TX_PREFIX + "%";

        List<CarbonReport> reports = carbonReportRepository
                .findByBlockchainTxHashLikeAndDeletedFalse(likePrefix).stream()
                .filter(r -> r.getChainMark() == null)
                .toList();
        reports.forEach(r -> {
            r.setChainMark(MARK_HISTORICAL_MOCK);
            r.setChainMarkReason(MOCK_MARK_REASON);
            r.setChainMarkedAt(now);
            r.setChainMarkedBy(operatorId);
        });

        List<com.oaiss.chain.entity.Transaction> trades = transactionRepository
                .findByBlockchainTxHashLikeAndDeletedFalse(likePrefix).stream()
                .filter(t -> t.getChainMark() == null)
                .toList();
        trades.forEach(t -> {
            t.setChainMark(MARK_HISTORICAL_MOCK);
            t.setChainMarkReason(MOCK_MARK_REASON);
            t.setChainMarkedAt(now);
            t.setChainMarkedBy(operatorId);
        });

        if (!reports.isEmpty()) {
            carbonReportRepository.saveAll(reports);
        }
        if (!trades.isEmpty()) {
            transactionRepository.saveAll(trades);
        }

        int total = reports.size() + trades.size();
        log.info("[CHAIN-MARK] Marked {} historical mock records (reports={}, trades={})",
                total, reports.size(), trades.size());
        return total;
    }

    /**
     * 查询死信队列。
     *
     * @param page 页码（从1开始）
     * @param size 每页数量
     */
    @Transactional(readOnly = true)
    public Page<OutboxMessage> listDeadLetters(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(Math.max(page == null ? 1 : page, 1) - 1, size == null || size < 1 ? 10 : size);
        return outboxMessageRepository.findByStatusAndDeletedFalseOrderByCreatedAtDesc(STATUS_DEAD, pageable);
    }

    /**
     * 重放一条死信：复位为 PENDING 并清零重试计数，交给后台调度器重新投递。
     *
     * <p>只接受 DEAD 状态。PENDING/FAILED/SENT 的消息重放会导致同一笔业务
     * 被提交两次——链码对已存在的 key 会报错，但重复提交本身就是事故。</p>
     *
     * @param id 发件箱消息主键
     */
    @Transactional
    public OutboxMessage replayDeadLetter(Long id) {
        OutboxMessage message = outboxMessageRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("error.outbox.notFound"));

        if (!STATUS_DEAD.equals(message.getStatus())) {
            throw BusinessException.of(ErrorCode.PARAM_ERROR, "error.outbox.notDead");
        }

        message.setStatus(STATUS_PENDING);
        message.setRetryCount(0);
        message.setNextRetryAt(null);
        message.setErrorMessage(null);
        outboxMessageRepository.save(message);

        log.info("[OUTBOX-REPLAY] Dead letter re-queued: id={}, aggregate={}#{}",
                id, message.getAggregateType(), message.getAggregateId());
        return message;
    }

    // ==================== 11 字段比对 ====================

    /**
     * 比对 11 个字段，返回<b>不符</b>的字段名列表（空列表 = 全部一致）。
     *
     * <p>字段来源（注意实体没有 process 三字段，必须现算）：</p>
     * <ul>
     *   <li>{@code signature} ← {@code report.getSignatureData()}</li>
     *   <li>{@code desulfEmission} / {@code transmissionLossEmission} / {@code sf6Emission}
     *       ← {@code EmissionDataValidator.parseProcessEmissions(report.getEmissionData())}</li>
     *   <li>其余 7 项直接取自实体 getter</li>
     * </ul>
     *
     * <p>「参与比对」定义：<b>库侧与链侧均非空</b>。仅一侧非空属差异，计入不符列表。</p>
     */
    private List<String> compareSnapshot(CarbonReport report, JsonNode chain) {
        List<String> diffs = new ArrayList<>();

        // 数值类：BigDecimal.compareTo（避免 "150.0" vs "150.0000" 的字符串不等）
        checkDecimal(diffs, "totalEmission", report.getTotalEmission(), decimal(chain, "totalEmission"));
        checkDecimal(diffs, "scope1Emission", report.getScope1Emission(), decimal(chain, "scope1Emission"));
        checkDecimal(diffs, "scope2Emission", report.getScope2Emission(), decimal(chain, "scope2Emission"));
        checkDecimal(diffs, "scope3Emission", report.getScope3Emission(), decimal(chain, "scope3Emission"));

        // 过程排放：库里没有独立列，从 emissionData 原文现算
        BigDecimal[] process = EmissionDataValidator.parseProcessEmissions(
                report.getEmissionData(), objectMapper);
        checkDecimal(diffs, "desulfEmission", process[0], decimal(chain, "desulfEmission"));
        checkDecimal(diffs, "transmissionLossEmission", process[1], decimal(chain, "transmissionLossEmission"));
        checkDecimal(diffs, "sf6Emission", process[2], decimal(chain, "sf6Emission"));

        // 文本类
        checkText(diffs, "reportNo", report.getReportNo(), text(chain, "reportNo"));
        checkText(diffs, "accountingPeriod", report.getAccountingPeriod(), text(chain, "accountingPeriod"));
        checkText(diffs, "signature", report.getSignatureData(), text(chain, "signature"));

        // enterpriseId：链上是 String.valueOf(enterpriseId)，用 Long 归一后比
        String chainEnterprise = text(chain, "enterpriseId");
        if (chainEnterprise != null && !chainEnterprise.isBlank()) {
            if (report.getEnterpriseId() == null
                    || !report.getEnterpriseId().toString().equals(chainEnterprise.trim())) {
                diffs.add("enterpriseId");
            }
        }

        // data（emissionData 原文逐字比对）：堵住「改明细但保持派生量一致」的绕过路径。
        // 链上 data 即提交时的 emissionData 原文，库里也是同一字段——
        // 两侧都非空且不相等即为篡改痕迹。
        checkText(diffs, "data", report.getEmissionData(), text(chain, "data"));

        return diffs;
    }

    /** 可参与比对的字段数（两侧均非空）。 */
    private int comparableFieldCount(CarbonReport report, JsonNode chain) {
        int n = 0;
        if (bothPresent(report.getTotalEmission(), decimal(chain, "totalEmission"))) n++;
        if (bothPresent(report.getScope1Emission(), decimal(chain, "scope1Emission"))) n++;
        if (bothPresent(report.getScope2Emission(), decimal(chain, "scope2Emission"))) n++;
        if (bothPresent(report.getScope3Emission(), decimal(chain, "scope3Emission"))) n++;

        BigDecimal[] process = EmissionDataValidator.parseProcessEmissions(
                report.getEmissionData(), objectMapper);
        if (bothPresent(process[0], decimal(chain, "desulfEmission"))) n++;
        if (bothPresent(process[1], decimal(chain, "transmissionLossEmission"))) n++;
        if (bothPresent(process[2], decimal(chain, "sf6Emission"))) n++;

        if (bothTextPresent(report.getReportNo(), text(chain, "reportNo"))) n++;
        if (bothTextPresent(report.getAccountingPeriod(), text(chain, "accountingPeriod"))) n++;
        if (bothTextPresent(report.getSignatureData(), text(chain, "signature"))) n++;
        if (bothTextPresent(report.getEnterpriseId() == null ? null : report.getEnterpriseId().toString(),
                text(chain, "enterpriseId"))) n++;
        if (bothTextPresent(report.getEmissionData(), text(chain, "data"))) n++;
        return n;
    }

    private void checkDecimal(List<String> diffs, String field, BigDecimal db, BigDecimal chain) {
        if (db == null && chain == null) return;
        if (db == null || chain == null) {
            diffs.add(field); // 仅一侧非空 → 视为差异
            return;
        }
        if (db.compareTo(chain) != 0) diffs.add(field);
    }

    private void checkText(List<String> diffs, String field, String db, String chain) {
        if ((db == null || db.isBlank()) && (chain == null || chain.isBlank())) return;
        if (db == null || chain == null) {
            diffs.add(field);
            return;
        }
        if (!db.trim().equals(chain.trim())) diffs.add(field);
    }

    private boolean bothPresent(BigDecimal a, BigDecimal b) {
        return a != null && b != null;
    }

    private boolean bothTextPresent(String a, String b) {
        return a != null && !a.isBlank() && b != null && !b.isBlank();
    }

    /** 从链上 JSON 取文本字段（缺失/null/空串统一返回 null）。 */
    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    /** 从链上 JSON 取数值字段（缺失/空串/非法统一返回 null）。 */
    private BigDecimal decimal(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
