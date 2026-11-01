package com.oaiss.chain.service;

import com.oaiss.chain.dto.CarbonReportSnapshot;

import org.springframework.data.domain.Page;

import java.util.Map;

public interface BlockchainServicePort {

    /**
     * <b>禁调黑名单</b>：链码里这三个函数是恒返回 VALID/空数组的桩，不碰账本——
     * {@code GetTransactionByID} / {@code QueryBlock} / {@code ListTransactions}。
     * 严禁经由通用 {@link #invokeChaincode} / {@link #queryChaincode} 调用它们：
     * 对不存在的交易号也会得到「已验证」的假结论。
     * 真实区块/交易数据由实现类自行解析区块事件流产出。
     */
    String invokeChaincode(String channelName, String chaincodeName, String functionName, String... args);

    String queryChaincode(String channelName, String chaincodeName, String functionName, String... args);

    /**
     * 兼容旧签名：只存 emissionData 原文，链上无主体归属（v1）。
     *
     * @deprecated 用 {@link #commitReportSnapshot} 存完整快照，
     *             v1 记录无法回答「这份报告属于哪家企业」。
     */
    @Deprecated
    String commitReportToChain(Long reportId, String reportData);

    /**
     * 存证碳报告**完整快照**（v2）：含企业归属、核算周期、总量、三范围、签名与原文明细。
     *
     * <p>v1 只存 emissionData 原文且 EnterpriseID 从未赋值，链上无法独立验证主体。
     * v2 使链上记录能回答「谁、什么时候、报了多少」。</p>
     */
    String commitReportSnapshot(CarbonReportSnapshot snapshot);

    String commitTradeToChain(Long tradeId, String tradeData);

    String queryBlock(Long blockNumber);

    /**
     * 按报告 ID 读回链上 v2 快照（链码 {@code QueryReportHistory} 最新一版）。
     *
     * @return 快照 JSON 原文；链上无该报告时返回 {@code null}
     */
    String queryReportSnapshot(Long reportId);

    boolean verifySignature(String data, String signature, String publicKey);

    Map<String, Object> checkConnection();

    Page<Map<String, Object>> listTransactions(Integer page, Integer size);

    Page<Map<String, Object>> listLatestBlocks(Integer page, Integer size);
}