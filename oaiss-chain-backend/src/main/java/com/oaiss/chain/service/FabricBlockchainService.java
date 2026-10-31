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
import org.hyperledger.fabric.protos.common.BlockMetadataIndex;
import org.hyperledger.fabric.protos.common.ChannelHeader;
import org.hyperledger.fabric.protos.common.Envelope;
import org.hyperledger.fabric.protos.common.Payload;
import org.hyperledger.fabric.protos.common.SignatureHeader;
import org.hyperledger.fabric.protos.msp.SerializedIdentity;
import org.hyperledger.fabric.protos.peer.ChaincodeActionPayload;
import org.hyperledger.fabric.protos.peer.ChaincodeEndorsedAction;
import org.hyperledger.fabric.protos.peer.ChaincodeInvocationSpec;
import org.hyperledger.fabric.protos.peer.ChaincodeProposalPayload;
import org.hyperledger.fabric.protos.peer.ChaincodeSpec;
import org.hyperledger.fabric.protos.peer.Endorsement;
import org.hyperledger.fabric.protos.peer.TransactionAction;
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
    public String queryReportSnapshot(Long reportId) {
        String historyJson;
        try {
            log.info("[FABRIC] Querying report history: reportId={}", reportId);
            byte[] result = carbonContract.evaluateTransaction("QueryReportHistory", String.valueOf(reportId));
            historyJson = new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BlockchainException.chaincodeInvokeFailed("QueryReportHistory", e.getMessage());
        }

        List<JsonNode> versions = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(historyJson);
            if (root != null && root.isArray()) {
                root.forEach(versions::add);
            }
        } catch (Exception e) {
            throw BlockchainException.chaincodeInvokeFailed("QueryReportHistory",
                    "链上历史无法解析: " + e.getMessage());
        }

        // 链上无该 key 时链码返回空数组；最新一版在末尾（GetHistoryForKey 按时间正序）。
        return versions.isEmpty() ? null : versions.get(versions.size() - 1).toString();
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
        int envelopeIndex = 0;
        for (ByteString envelopeBytes : block.getData().getDataList()) {
            try {
                Envelope envelope = Envelope.parseFrom(envelopeBytes);
                Payload payload = Payload.parseFrom(envelope.getPayload());
                if (!payload.hasHeader()) {
                    envelopeIndex++;
                    continue;
                }

                ChannelHeader channelHeader = ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
                if (channelHeader.getTxId().isBlank()) {
                    envelopeIndex++;
                    continue;
                }

                Map<String, Object> tx = new LinkedHashMap<>();
                tx.put("txHash", channelHeader.getTxId());
                tx.put("txId", channelHeader.getTxId());

                // 交易校验码：取自区块元数据 TRANSACTIONS_FILTER，与 envelopes 下标一一对应。
                // 改造前这里写死 "VALID"——链上明明记录了 INVALID，界面却一律显示验证通过。
                int validationCode = validationCodeAt(block, envelopeIndex);
                tx.put("validationCode", validationCode);
                tx.put("status", validationCode == 0 ? "VALID" : "INVALID");

                tx.put("blockNumber", block.getHeader().getNumber());
                tx.put("timestamp", channelHeader.hasTimestamp() ? toIsoTimestamp(channelHeader.getTimestamp()) : blockTimestamp);
                if (channelHeader.hasTimestamp()) {
                    tx.put("timestampNanos", channelHeader.getTimestamp().getNanos());
                }
                tx.put("channelId", channelHeader.getChannelId());
                tx.put("type", channelHeader.getType());

                // 以下三项此前完全没有读取：发起组织、背书组织、被调用的链码函数。
                // 它们是「这笔交易是谁发起的、谁背书的、干了什么」的答案，就在区块里。
                extractSignatureIdentity(payload).ifPresent(msp -> tx.put("senderMsp", msp));
                List<String> endorsers = extractEndorserMspList(payload);
                if (!endorsers.isEmpty()) {
                    tx.put("endorsers", endorsers);
                }
                extractChaincodeInvocation(payload).ifPresent(invocation -> {
                    tx.put("function", invocation.function);
                    tx.put("args", invocation.args);
                    if (invocation.chaincodeName != null) {
                        tx.put("chaincodeName", invocation.chaincodeName);
                    }
                });

                transactions.add(tx);
            } catch (Exception e) {
                log.debug("[FABRIC] Skipping unparsable transaction envelope in block {}: {}",
                        block.hasHeader() ? block.getHeader().getNumber() : -1, e.getMessage());
            }
            envelopeIndex++;
        }

        return transactions;
    }

    /**
     * 读取指定信封的校验码。
     *
     * <p>Fabric 把每个交易的校验结果按 order 记录在区块元数据 {@code TRANSACTIONS_FILTER}
     * 里（0 = VALID，非 0 为各类无效码）。元数据缺失时按 0 处理——历史区块与测试构造
     * 的区块可能没有该字段，此时「未知」与「有效」在业务上等价。</p>
     */
    private int validationCodeAt(Block block, int index) {
        if (!block.hasMetadata()) {
            return 0;
        }
        var metadataList = block.getMetadata().getMetadataList();
        int filterIndex = BlockMetadataIndex.TRANSACTIONS_FILTER_VALUE;
        if (filterIndex >= metadataList.size()) {
            return 0;
        }
        ByteString filter = metadataList.get(filterIndex);
        if (index < 0 || index >= filter.size()) {
            return 0;
        }
        return filter.byteAt(index);
    }

    /** 从签名头解析交易发起者所属组织 MSP。 */
    private Optional<String> extractSignatureIdentity(Payload payload) {
        try {
            if (payload.getHeader().getSignatureHeader().isEmpty()) {
                return Optional.empty();
            }
            SignatureHeader signatureHeader = SignatureHeader.parseFrom(
                    payload.getHeader().getSignatureHeader());
            if (signatureHeader.getCreator().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(SerializedIdentity.parseFrom(signatureHeader.getCreator()).getMspid());
        } catch (Exception e) {
            log.debug("[FABRIC] Could not parse signature identity: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 从链码动作载荷解析背书节点所属组织 MSP 列表（去重保序）。 */
    private List<String> extractEndorserMspList(Payload payload) {
        List<String> mspIds = new ArrayList<>();
        try {
            ChaincodeEndorsedAction action = extractEndorsedAction(payload);
            if (action == null) {
                return mspIds;
            }
            for (Endorsement endorsement : action.getEndorsementsList()) {
                String mspId = SerializedIdentity.parseFrom(endorsement.getEndorser()).getMspid();
                if (mspId != null && !mspId.isBlank() && !mspIds.contains(mspId)) {
                    mspIds.add(mspId);
                }
            }
        } catch (Exception e) {
            log.debug("[FABRIC] Could not parse endorsers: {}", e.getMessage());
        }
        return mspIds;
    }

    /** 从链码调用规格解析被调用的函数名、入参与链码名。 */
    private Optional<ChaincodeInvocation> extractChaincodeInvocation(Payload payload) {
        try {
            ChaincodeActionPayload actionPayload = extractChaincodeActionPayload(payload);
            if (actionPayload == null) {
                return Optional.empty();
            }
            // ChaincodeProposalPayload.input 才是 ChaincodeInvocationSpec 的序列化；
            // 直接对整个 proposal payload 反序列化会把 input 与 transientMap 一起误读，
            // 解析出「有 chaincodeSpec 但没有入参」的空壳。
            ChaincodeProposalPayload proposalPayload = ChaincodeProposalPayload.parseFrom(
                    actionPayload.getChaincodeProposalPayload());
            ChaincodeInvocationSpec spec = ChaincodeInvocationSpec.parseFrom(proposalPayload.getInput());
            ChaincodeSpec chaincodeSpec = spec.getChaincodeSpec();
            List<String> argList = chaincodeSpec.getInput().getArgsList().stream()
                    .map(ByteString::toStringUtf8)
                    .toList();
            if (argList.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ChaincodeInvocation(
                    argList.get(0),
                    argList,
                    chaincodeSpec.getChaincodeId().getName()));
        } catch (Exception e) {
            log.debug("[FABRIC] Could not parse chaincode invocation: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private ChaincodeEndorsedAction extractEndorsedAction(Payload payload) {
        ChaincodeActionPayload actionPayload = extractChaincodeActionPayload(payload);
        return actionPayload != null ? actionPayload.getAction() : null;
    }

    /**
     * 解析交易动作载荷。
     *
     * <p>区块信封的 payload.data 是可选的：配置交易、空交易没有这一段。
     * 解析失败一律视为「无可读的链码动作」，不影响交易本身进入列表——
     * 只是少显示函数名，而不是丢一笔交易。</p>
     */
    private ChaincodeActionPayload extractChaincodeActionPayload(Payload payload) {
        if (payload.getData().isEmpty()) {
            return null;
        }
        try {
            TransactionAction transactionAction = TransactionAction.parseFrom(payload.getData());
            if (transactionAction.getPayload().isEmpty()) {
                return null;
            }
            return ChaincodeActionPayload.parseFrom(transactionAction.getPayload());
        } catch (Exception e) {
            log.debug("[FABRIC] Could not parse chaincode action payload: {}", e.getMessage());
            return null;
        }
    }

    /** 链码调用要素的轻量载体（避免为三个字段引入 DTO 类）。 */
    private record ChaincodeInvocation(String function, List<String> args, String chaincodeName) {
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
