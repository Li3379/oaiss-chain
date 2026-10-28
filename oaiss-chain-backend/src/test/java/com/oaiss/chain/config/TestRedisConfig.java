package com.oaiss.chain.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.mockito.Mockito.mock;

/**
 * Provides RedisTemplate beans for slice tests.
 * Uses a mock connection factory to avoid requiring a running Redis server.
 *
 * @TestConfiguration：Spring Boot 组件扫描自动排除测试配置类——此前普通
 * @Configuration 被 @SpringBootTest 全量扫描拾取，与主 RedisConfig 的
 * redisScriptTemplate 同名冲突（BeanDefinitionOverrideException，预置缺陷）。
 */
@TestConfiguration
public class TestRedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    @Bean
    public RedisTemplate<String, Long> redisScriptTemplate() {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericToStringSerializer<>(Long.class));
        template.setHashValueSerializer(new GenericToStringSerializer<>(Long.class));
        template.afterPropertiesSet();
        return template;
    }
}