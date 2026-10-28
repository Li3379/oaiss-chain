package com.oaiss.chain.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheManager;

/**
 * 缓存配置
 * 优先使用 Redis，不可用时降级为本地内存缓存
 *
 * @author OAISS Team
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    public org.springframework.cache.CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory)
                .transactionAware()
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    public org.springframework.cache.CacheManager localCacheManager() {
        return new ConcurrentMapCacheManager("captcha", "users", "permissions", "tokenBlacklist");
    }
}
