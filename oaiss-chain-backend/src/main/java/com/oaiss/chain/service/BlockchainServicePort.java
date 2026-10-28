package com.oaiss.chain.service;

import org.springframework.data.domain.Page;

import java.util.Map;

public interface BlockchainServicePort {

    String invokeChaincode(String channelName, String chaincodeName, String functionName, String... args);

    String queryChaincode(String channelName, String chaincodeName, String functionName, String... args);

    String commitReportToChain(Long reportId, String reportData);

    String commitTradeToChain(Long tradeId, String tradeData);

    String queryBlock(Long blockNumber);

    String queryTransaction(String txHash);

    boolean verifySignature(String data, String signature, String publicKey);

    Map<String, Object> checkConnection();

    Page<Map<String, Object>> listTransactions(Integer page, Integer size);

    Page<Map<String, Object>> listLatestBlocks(Integer page, Integer size);
}