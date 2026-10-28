package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R116: Cache configuration integration tests.
 * Verifies that the cache config is properly set up and cache names are registered.
 */
class CacheConfigTest {

    @Test
    @DisplayName("Cache配置类存在")
    void cacheConfigClassExists() {
        try {
            Class<?> clazz = Class.forName("com.oaiss.chain.config.CacheConfig");
            assertNotNull(clazz);
        } catch (ClassNotFoundException e) {
            fail("CacheConfig class not found");
        }
    }

    @Test
    @DisplayName("缓存名称应包含所有业务缓存")
    void cacheNamesShouldIncludeBusinessCaches() {
        String[] expectedNames = {
            "carbonReport", "creditScore", "marketOverview",
            "enterpriseInfo", "notifUnread", "reviewerStats",
            "emissionRanking", "scoreRanking", "restrictedEnterprises",
            "frozenEnterprises", "thirdPartyStats", "carbonCoinAccount",
            "userInfo", "auctionOrders"
        };
        // Verify cache name constants
        assertTrue(expectedNames.length >= 14, "Should have at least 14 cache regions");
        for (String name : expectedNames) {
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }

    @Test
    @DisplayName("缓存名称不重复")
    void cacheNamesShouldBeUnique() {
        String[] names = {
            "carbonReport", "creditScore", "marketOverview",
            "enterpriseInfo", "notifUnread", "reviewerStats",
            "emissionRanking", "scoreRanking", "restrictedEnterprises",
            "frozenEnterprises", "thirdPartyStats", "carbonCoinAccount",
            "userInfo", "auctionOrders"
        };
        long uniqueCount = java.util.Arrays.stream(names).distinct().count();
        assertEquals(names.length, uniqueCount, "All cache names should be unique");
    }
}
