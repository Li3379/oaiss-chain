package com.oaiss.chain.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 签名策略工厂
 * 统一分发国际算法 (RSA) 与国密算法 (SM2)
 *
 * @author OAISS Team
 */
@Component
public class SignatureStrategyFactory {

    private final Map<String, SignatureStrategy> strategyMap = new ConcurrentHashMap<>();

    @Value("${app.security.crypto.default-algorithm:RSA}")
    private String defaultAlgorithm = "RSA";

    public SignatureStrategyFactory(List<SignatureStrategy> strategies) {
        for (SignatureStrategy strategy : strategies) {
            strategyMap.put(strategy.getAlgorithmType().toUpperCase(), strategy);
        }
    }

    /**
     * 根据算法类型获取对应策略（默认回退至全局配置）
     */
    public SignatureStrategy getStrategy(String algorithmType) {
        if (algorithmType == null || algorithmType.isBlank()) {
            algorithmType = (defaultAlgorithm != null && !defaultAlgorithm.isBlank()) ? defaultAlgorithm : "RSA";
        }
        SignatureStrategy strategy = strategyMap.get(algorithmType.toUpperCase());
        if (strategy == null) {
            strategy = strategyMap.get("RSA");
        }
        return strategy;
    }

    /**
     * 获取全局默认签名策略
     */
    public SignatureStrategy getDefaultStrategy() {
        return getStrategy(defaultAlgorithm);
    }
}
