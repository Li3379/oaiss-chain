package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.oaiss.chain.dto.CarbonReportSnapshot;
import com.oaiss.chain.config.FabricProperties;
import com.oaiss.chain.exception.BlockchainException;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.CloseableIterator;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.protos.common.Block;
import org.hyperledger.fabric.protos.common.ChannelHeader;
import org.hyperledger.fabric.protos.common.Envelope;
import org.hyperledger.fabric.protos.common.Payload;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Profile("fabric")
@RequiredArgsConstructor
public class FabricBlockchainService implements BlockchainServicePort {

    private static final long EVENT_STREAM_START_BLOCK = 0L;
    // Historical block replay from block 0 over a single event stream needs
    // headroom: 2s caused the Fabric peer to RST_STREAM (CANCEL) mid-replay,
    // making /blockchain/blocks and /blockchain/transactions always return
    // empty. 60s covers full replay for realistic local networks; see BUG-05
    // (transactions list may still need a filtered-event redesign for scale).
    private static final long EVENT_STREAM_TIMEOUT_SECONDS = 60L;

    private final Contract carbonContract;
    private final Network fabricNetwork;
    private final FabricProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public String invokeChaincode(String channelName, String chaincodeName, String functionName, String... args) {
        try {
            log.info("[FABRIC] Invoking chaincode: channel={}, chaincode={}, function={}",
                    channelName, chaincodeName, functionName);
            byte[] result = carbonContract.submitTransaction(functionName, args);
            return new String(result, StandardCharsets.UTF_8);
        } catch (EndorseException e) {
            throw BlockchainException.chaincodeInvokeFailed(chaincodeName, e.getMessage());
        } catch (CommitException e) {
            throw BlockchainException.txCommitFailed("invoke", e.getMessage());
        } catch (Exception e) {
            throw BlockchainException.chaincodeInvokeFailed(chaincodeName, e.getMessage());
        }
    }

    @Override
    public String queryChaincode(String channelName, String chaincodeName, String functionName, String... args) {
        try {
            log.info("[FABRIC] Querying chaincode: channel={}, chaincode={}, function={}",
                    channelName, chaincodeName, functionName);
            byte[] result = carbonContract.evaluateTransaction(functionName, args);
            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BlockchainException.chaincodeInvokeFailed(chaincodeName, e.getMessage());
        }
    }

    @Override
    public String commitReportToChain(Long reportId, String reportData) {
        try {
            log.info("[FABRIC] Committing carbon report to chain: reportId={}", reportId);
            byte[] result = carbonContract.submitTransaction(
                    "CreateCarbonReport",
                    String.valueOf(reportId),
                    reportData
            );
            String response = new String(result, StandardCharsets.UTF_8);
            log.info("[FABRIC] Report committed: reportId={}, response={}", reportId, response);
            return extractTxHashFromResponse(response);
        } catch (EndorseException e) {
            throw BlockchainException.smartContractError("CreateCarbonReport", e.getMessage());
        } catch (CommitException e) {
            throw BlockchainException.txCommitFailed(String.valueOf(reportId), e.getMessage());
        } catch (Exception e) {
            throw BlockchainException.txCommitFailed(String.valueOf(reportId), e.getMessage());
        }
    }

    /**
     * 存证碳报告完整快照（v2）。
     *
     * <p>调用链码 {@code CreateCarbonReportV2}，把企业归属、周期、总量、三范围与签名
     * 一并写入链上，使链上记录能独立回答「谁、什么时候、报了多少」。
     * 旧函数 {@code CreateCarbonReport} 保留在链码里，历史记录不受影响。</p>
     */
    @Override
    public String commitReportSnapshot(CarbonReportSnapshot snapshot) {
        try {
            log.info("[FABRIC] Committing report snapshot v2: reportId={}, enterpriseId={}",
                    snapshot.getReportId(), snapshot.getEnterpriseId());
            byte[] result = carbonContract.submitTransaction(
                    "CreateCarbonReportV2",
                    String.valueOf(snapshot.getReportId()),
                    nullSafe(snapshot.getReportNo()),
                    String.valueOf(snapshot.getEnterpriseId()),
                    nullSafe(snapshot.getAccountingPeriod()),
                    nullSafe(snapshot.getTotalEmission()),
                    nullSafe(snapshot.getScope1Emission()),
                    nullSafe(snapshot.getScope2Emission()),
                    nullSafe(snapshot.getScope3Emission()),
                    nullSafe(snapshot.getDesulfEmission()),
                    nullSafe(snapshot.getTransmissionLossEmission()),
                    nullSafe(snapshot.getSf6Emission()),
                    nullSafe(snapshot.getSignature()),
                    nullSafe(snapshot.getData())
            );
            String response = new String(result, StandardCharsets.UTF_8);
            log.info("[FABRIC] Snapshot committed: reportId={}, response={}", snapshot.getReportId(), response);
            return extractTxHashFromResponse(response);
        } catch (EndorseException e) {
            throw BlockchainException.smartContractError("CreateCarbonReportV2", e.getMessage());
        } catch (Exception e) {
            throw BlockchainException.connectionFailed("提交快照到链上失败: " + e.getMessage());
        }
    }

    /** 链码参数不允许 null；空串会被链码原样存下，语义上等同「未提供」。 */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public String commitTradeToChain(Long tradeId, String tradeData) {
        try {
            log.info("[FABRIC] Committing trade to chain: tradeId={}", tradeId);
            byte[] result = carbonContract.submitTransaction(
                    "CreateTradeRecord",
                    String.valueOf(tradeId),
                    tradeData
            );
            String response = new String(result, StandardCharsets.UTF_8);
            log.info("[FABRIC] Trade committed: tradeId={}, response={}", tradeId, response);
            return extractTxHashFromResponse(response);
        } catch (EndorseException e) {
            throw BlockchainException.smartContractError("CreateTradeRecord", e.getMessage());
        } catch (CommitException e) {
            throw BlockchainException.txCommitFailed(String.valueOf(tradeId), e.getMessage());
        } catch (Exception e) {
            throw BlockchainException.txCommitFailed(String.valueOf(tradeId), e.getMessage());
        }
    }

    @Override
    public String queryBlock(Long blockNumber) {
        try {
            log.info("[FABRIC] Querying block: {}", blockNumber);
            Block block = readSingleBlock(blockNumber)
                    .orElseThrow(() -> BlockchainException.blockQueryFailed(blockNumber, "Block not found in event stream"));
            return objectMapper.writeValueAsString(toBlockRecord(block));
        } catch (BlockchainException e) {
            throw e;
        } catch (Exception e) {
            throw BlockchainException.blockQueryFailed(blockNumber, e.getMessage());
        }
    }

    @Override
    public String queryTransaction(String txHash) {
        try {
            log.info("[FABRIC] Querying transaction: {}", txHash);
            return collectBlocksByProbe().stream()
                    .flatMap(block -> extractTransactions(block).stream())
                    .filter(tx -> txHash.equals(tx.get("txHash")))
                    .findFirst()
                    .map(this::toJson)
                    .orElseThrow(() -> BlockchainException.txQueryFailed(txHash, "Transaction not found in event stream"));
        } catch (BlockchainException e) {
            throw e;
        } catch (Exception e) {
            throw BlockchainException.txQueryFailed(txHash, e.getMessage());
        }
    }

    @Override
    public boolean verifySignature(String data, String signature, String publicKey) {
        try {
            log.info("[FABRIC] Verifying signature for data length: {}", data.length());
            byte[] result = carbonContract.evaluateTransaction("VerifySignature", data, signature, publicKey);
            String response = new String(result, StandardCharsets.UTF_8);
            return Boolean.parseBoolean(response);
        } catch (Exception e) {
            log.error("[FABRIC] Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> checkConnection() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", true);
        status.put("channel", props.getChannelName());
        status.put("peers", 1);
        status.put("orderers", 1);
        status.put("mode", "FABRIC");
        status.put("mspId", props.getMspId());
        status.put("chaincode", props.getChaincodeName());
        status.put("caEnabled", props.getCa().isEnabled());
        status.put("timestamp", LocalDateTime.now().toString());
        return status;
    }

    @Override
    public Page<Map<String, Object>> listTransactions(Integer page, Integer size) {
        try {
            log.info("[FABRIC] Listing transactions: page={}, size={}", page, size);
            List<Map<String, Object>> transactions = collectBlocksByProbe().stream()
                    .flatMap(block -> extractTransactions(block).stream())
                    .sorted(Comparator
                            .comparing((Map<String, Object> tx) -> asLong(tx.get("blockNumber"))).reversed()
                            .thenComparing(tx -> String.valueOf(tx.getOrDefault("timestamp", "")), Comparator.reverseOrder()))
                    .toList();
            return toPage(transactions, page, size);
        } catch (Exception e) {
            log.error("[FABRIC] Failed to list transactions: {}", e.getMessage());
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page - 1, size), 0);
        }
    }

    @Override
    public Page<Map<String, Object>> listLatestBlocks(Integer page, Integer size) {
        try {
            log.info("[FABRIC] Listing latest blocks: page={}, size={}", page, size);
            List<Map<String, Object>> blocks = collectBlocksByProbe().stream()
                    .map(this::toBlockRecord)
                    .sorted(Comparator.comparing((Map<String, Object> block) -> asLong(block.get("blockNumber"))).reversed())
                    .toList();
            return toPage(blocks, page, size);
        } catch (Exception e) {
            log.error("[FABRIC] Failed to list blocks: {}", e.getMessage());
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page - 1, size), 0);
        }
    }

    /**
     * Read all blocks one-by-one from block 0 with a fresh short-lived event
     * stream per block. A single long-lived replay stream (startBlock=0) is
     * cancelled by the Fabric peer mid-replay (RST_STREAM / CANCEL), which made
     * block/transaction listings return empty (BUG-05). Per-block streams are
     * reliable because each one closes after its first block.
     */
    private List<Block> collectBlocksByProbe() {
        List<Block> blocks = new ArrayList<>();
        long blockNumber = 0L;
        while (true) {
            Optional<Block> maybe = probeSingleBlock(blockNumber);
            if (maybe.isEmpty()) {
                break;
            }
            blocks.add(maybe.get());
            blockNumber++;
            if (blockNumber > 10_000L) {
                log.warn("[FABRIC] Block probe exceeded sanity limit at {}", blockNumber);
                break;
            }
        }
        return blocks;
    }

    /**
     * Probe one block with a short deadline. Unlike readSingleBlock (60s), a
     * missing block must fail fast so block listing does not stall on the first
     * absent height. Existing blocks return immediately regardless.
     */
    private Optional<Block> probeSingleBlock(Long blockNumber) {
        var request = fabricNetwork.newBlockEventsRequest()
                .startBlock(blockNumber)
                .build();
        try (CloseableIterator<Block> iterator = request.getEvents(
                callOptions -> callOptions.withDeadlineAfter(3L, TimeUnit.SECONDS))) {
            if (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.hasHeader() && block.getHeader().getNumber() == blockNumber) {
                    return Optional.of(block);
                }
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<Block> readSingleBlock(Long blockNumber) {
        var request = fabricNetwork.newBlockEventsRequest()
                .startBlock(blockNumber)
                .build();

        try (CloseableIterator<Block> iterator = request.getEvents(
                callOptions -> callOptions.withDeadlineAfter(EVENT_STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS))) {
            if (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.hasHeader() && block.getHeader().getNumber() == blockNumber) {
                    return Optional.of(block);
                }
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            if (isDeadlineExceeded(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private List<Map<String, Object>> extractTransactions(Block block) {
        List<Map<String, Object>> transactions = new ArrayList<>();
        if (!block.hasData()) {
            return transactions;
        }

        String blockTimestamp = extractBlockTimestamp(block);
        for (ByteString envelopeBytes : block.getData().getDataList()) {
            try {
                Envelope envelope = Envelope.parseFrom(envelopeBytes);
                Payload payload = Payload.parseFrom(envelope.getPayload());
                if (!payload.hasHeader()) {
                    continue;
                }

                ChannelHeader channelHeader = ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
                if (channelHeader.getTxId().isBlank()) {
                    continue;
                }

                Map<String, Object> tx = new LinkedHashMap<>();
                tx.put("txHash", channelHeader.getTxId());
                tx.put("txId", channelHeader.getTxId());
                tx.put("status", "VALID");
                tx.put("blockNumber", block.getHeader().getNumber());
                tx.put("timestamp", channelHeader.hasTimestamp() ? toIsoTimestamp(channelHeader.getTimestamp()) : blockTimestamp);
                tx.put("channelId", channelHeader.getChannelId());
                tx.put("type", channelHeader.getType());
                transactions.add(tx);
            } catch (Exception e) {
                log.debug("[FABRIC] Skipping unparsable transaction envelope in block {}: {}",
                        block.hasHeader() ? block.getHeader().getNumber() : -1, e.getMessage());
            }
        }

        return transactions;
    }

    private Map<String, Object> toBlockRecord(Block block) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("blockNumber", block.hasHeader() ? block.getHeader().getNumber() : -1L);
        record.put("blockHash", block.hasHeader() ? toHex(block.getHeader().getDataHash()) : "");
        record.put("previousHash", block.hasHeader() ? toHex(block.getHeader().getPreviousHash()) : "");
        record.put("txCount", block.hasData() ? block.getData().getDataCount() : 0);
        record.put("timestamp", extractBlockTimestamp(block));
        record.put("blockType", block.hasHeader() && block.getHeader().getNumber() == 0 ? "GENESIS" : "REGULAR");
        record.put("channel", props.getChannelName());
        return record;
    }

    private String extractBlockTimestamp(Block block) {
        if (!block.hasData() || block.getData().getDataCount() == 0) {
            return null;
        }

        for (ByteString envelopeBytes : block.getData().getDataList()) {
            try {
                Envelope envelope = Envelope.parseFrom(envelopeBytes);
                Payload payload = Payload.parseFrom(envelope.getPayload());
                if (!payload.hasHeader()) {
                    continue;
                }
                ChannelHeader channelHeader = ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
                if (channelHeader.hasTimestamp()) {
                    return toIsoTimestamp(channelHeader.getTimestamp());
                }
            } catch (Exception e) {
                log.debug("[FABRIC] Failed to extract block timestamp: {}", e.getMessage());
            }
        }

        return null;
    }

    private String toIsoTimestamp(com.google.protobuf.Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos())
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
                .toString();
    }

    private String toHex(ByteString bytes) {
        if (bytes == null || bytes.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder("0x");
        for (byte value : bytes.toByteArray()) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private <T> Page<T> toPage(List<T> items, Integer page, Integer size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min((safePage - 1) * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());
        return new PageImpl<>(
                items.subList(fromIndex, toIndex),
                PageRequest.of(safePage - 1, safeSize),
                items.size()
        );
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Fabric query result", e);
        }
    }

    private boolean isDeadlineExceeded(Throwable error) {
        return Status.fromThrowable(error).getCode() == Status.Code.DEADLINE_EXCEEDED;
    }

    private String extractTxHashFromResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.has("txHash")) {
                return node.get("txHash").asText();
            }
        } catch (Exception e) {
            log.debug("Could not parse txHash from response, returning raw response");
        }
        return response;
    }
}
