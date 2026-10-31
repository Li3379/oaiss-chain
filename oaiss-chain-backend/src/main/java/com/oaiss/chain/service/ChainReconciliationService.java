package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.OutboxMessage;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.OutboxMessageRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private final CarbonReportRepository carbonReportRepository;
    private final BlockchainServicePort blockchainService;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    /**
     * 对账结果。
     */
    @Getter
    @Builder
    public static class ReconciliationResult {
        /** 本地已标记上链的报告总数（本页） */
        private final int checked;
        /** 库与链一致 */
        private final int matched;
        /** 库有 txHash、链上查无记录 */
        private final int missingOnChain;
        /** 链上有记录、但内容与库不一致 */
        private final int contentMismatch;
        /** 查询链上失败（无法判定，不应算作差异） */
        private final int unverifiable;
        /** 逐条明细，供管理员定位 */
        private final List<Map<String, Object>> details;
        private final long total;
    }

    /**
     * 执行对账：逐条比对本地已上链报告与链上快照。
     *
     * @param page 页码（从1开始）
     * @param size 每页数量
     */
    @Transactional(readOnly = true)
    public ReconciliationResult reconcile(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(Math.max(page == null ? 1 : page, 1) - 1, size == null || size < 1 ? 10 : size);
        Page<CarbonReport> reports = carbonReportRepository.findByBlockchainTxHashIsNotNullAndDeletedFalse(pageable);

        int matched = 0;
        int missing = 0;
        int mismatch = 0;
        int unverifiable = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (CarbonReport report : reports.getContent()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reportId", report.getId());
            row.put("reportNo", report.getReportNo());
            row.put("dbTxHash", report.getBlockchainTxHash());

            String snapshot;
            try {
                snapshot = blockchainService.queryReportSnapshot(report.getId());
            } catch (Exception e) {
                // 链上不可达时不能把记录判成「缺失」——那会把一次网络故障
                // 变成一整页虚假差异，逼人去做毫无必要的补救。
                unverifiable++;
                row.put("status", "UNVERIFIABLE");
                row.put("reason", e.getMessage());
                details.add(row);
                continue;
            }

            if (snapshot == null || snapshot.isBlank()) {
                missing++;
                row.put("status", "MISSING_ON_CHAIN");
                row.put("reason", "链上查无该报告的存证记录");
                details.add(row);
                continue;
            }

            BigDecimal chainTotal = readTotalEmission(snapshot);
            row.put("chainTotalEmission", chainTotal == null ? null : chainTotal.toPlainString());
            row.put("dbTotalEmission", report.getTotalEmission() == null ? null : report.getTotalEmission().toPlainString());

            if (chainTotal != null && report.getTotalEmission() != null
                    && chainTotal.compareTo(plain(report.getTotalEmission())) != 0) {
                mismatch++;
                row.put("status", "CONTENT_MISMATCH");
                row.put("reason", "链上总量与本地记录不一致");
            } else {
                matched++;
                row.put("status", "MATCHED");
            }
            details.add(row);
        }

        return ReconciliationResult.builder()
                .checked(reports.getNumberOfElements())
                .matched(matched)
                .missingOnChain(missing)
                .contentMismatch(mismatch)
                .unverifiable(unverifiable)
                .details(details)
                .total(reports.getTotalElements())
                .build();
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

    /** 从链上快照 JSON 读取 totalEmission（链码以字符串存 BigDecimal）。 */
    private BigDecimal readTotalEmission(String snapshot) {
        try {
            JsonNode node = objectMapper.readTree(snapshot);
            if (!node.hasNonNull("totalEmission")) {
                return null;
            }
            String raw = node.get("totalEmission").asText();
            return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
        } catch (Exception e) {
            log.debug("[RECONCILE] Could not read totalEmission from chain snapshot: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
