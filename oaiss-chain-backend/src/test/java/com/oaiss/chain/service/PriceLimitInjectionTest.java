package com.oaiss.chain.service;

import com.oaiss.chain.annotation.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限价接入的源码级断言（AC-10）。
 *
 * <p>**为什么需要独立于单测**：单测里 {@code PriceLimitService} 是 mock，
 * 「被 mock 调用了」只证明测试桩生效，不证明生产代码在 4 个写入口都接上了。
 * 而 `@WebMvcTest` 切片里 Service 被 `@MockBean`，400 永远不会真的产生
 * （见计划 B2）。因此这里用**反射 + 源码文本双检**：
 *
 * <ol>
 *   <li>反射断言 4 个 Service 方法存在（防止方法被重命名后断言失效）</li>
 *   <li>源码文本断言这些方法的**方法体内**出现 {@code priceLimitService.validate(}
 *       且位于 {@code CarbonTradable} / {@code carbonFrozen} 写入之前</li>
 * </ol>
 *
 * <p>第 2 项是关键：限价若排在冻结之后，用户会先被冻结资产再被拒绝。这是计划
 * v1→v2 修正过的顺序缺陷（B6/M2b），必须有断言守住。
 */
class PriceLimitInjectionTest {

    // surefire 的工作目录是模块根（oaiss-chain-backend/），不是仓库根
    private static final String SRC_ROOT = "src/main/java/com/oaiss/chain/service/";
    private static final String TRADE_SERVICE = SRC_ROOT + "TradeService.java";
    private static final String AUCTION_SERVICE = SRC_ROOT + "DoubleAuctionService.java";

    private String readSource(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new IllegalStateException("无法读取源码: " + path, e);
        }
    }

    /** 截取某个方法的方法体（从签名行到成对的收尾大括号） */
    private String methodBody(String source, String signatureFragment) {
        int sigIdx = source.indexOf(signatureFragment);
        assertTrue(sigIdx >= 0, "找不到方法签名: " + signatureFragment);

        int braceStart = source.indexOf('{', sigIdx);
        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(braceStart, i + 1);
            }
        }
        throw new IllegalStateException("方法体括号不匹配: " + signatureFragment);
    }

    @Test
    @DisplayName("AC-10：TradeService 的两个写入口都调用了限价校验")
    void tradeService_bothEntryPointsValidate() {
        String src = readSource(TRADE_SERVICE);

        List<String> entryPoints = new ArrayList<>();
        entryPoints.add("public TradeResponse createP2PTrade(");
        entryPoints.add("public TradeResponse createAuctionOrder(");

        for (String sig : entryPoints) {
            String body = methodBody(src, sig);
            assertTrue(body.contains("priceLimitService.validate("),
                    sig + " 的方法体内缺少 priceLimitService.validate 调用");
        }
    }

    @Test
    @DisplayName("AC-10：拍卖撮合的公共前置 validateOrderRequest 调用了限价校验（覆盖买/卖两个入口）")
    void auctionService_validateOrderRequestValidates() {
        String src = readSource(AUCTION_SERVICE);
        String body = methodBody(src, "private void validateOrderRequest(");

        assertTrue(body.contains("priceLimitService.validate("),
                "validateOrderRequest 内缺少限价校验，双向挂单将都不受限价约束");
    }

    @Test
    @DisplayName("B6/M2b：限价校验必须早于配额冻结（否则先冻结再拒绝）")
    void priceLimit_precedesQuotaFreeze() {
        String src = readSource(TRADE_SERVICE);

        String p2p = methodBody(src, "public TradeResponse createP2PTrade(");
        assertTrue(p2p.indexOf("priceLimitService.validate(") < p2p.indexOf("setCarbonTradable("),
                "createP2PTrade：限价校验必须早于 setCarbonTradable（冻结）");

        String auction = methodBody(src, "public TradeResponse createAuctionOrder(");
        assertTrue(auction.indexOf("priceLimitService.validate(") < auction.indexOf("setCarbonTradable("),
                "createAuctionOrder：限价校验必须早于 setCarbonTradable（冻结）");
    }

    @Test
    @DisplayName("AC-12d：撮合的结算价复核早于 MatchingResult 落库（防孤儿撮合记录）")
    void settlementRecheck_precedesMatchingSave() {
        String src = readSource(AUCTION_SERVICE);
        String body = methodBody(src, "public List<MatchingResultResponse> executeMatching(");

        int recheckIdx = body.indexOf("priceLimitService.validate(settlementPrice");
        int saveIdx = body.indexOf("matchingResultRepository.save(match)");

        assertTrue(recheckIdx >= 0, "executeMatching 内缺少结算价复核");
        assertTrue(saveIdx >= 0, "找不到 MatchingResult 落库点");
        assertTrue(recheckIdx < saveIdx,
                "结算价复核必须在 MatchingResult 落库之前，否则会留下孤儿撮合记录");
    }

    @Test
    @DisplayName("限价被拦下时写审计（可区分「没撮合到」与「撮合到但被限价挡回」）")
    void settlementRecheck_recordsAudit() {
        String src = readSource(AUCTION_SERVICE);

        assertTrue(src.contains("recordPriceLimitBlockedAudit("),
                "缺少限价拦截的审计落库");
        assertTrue(src.contains("PRICE_LIMIT_BLOCKED"),
                "审计动作标识缺失，事后无法按 action 检索");
    }
}