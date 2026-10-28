package com.oaiss.chain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "cachePreloadExecutor")
    public Executor cachePreloadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("cache-preload-");
        executor.initialize();
        return executor;
    }

    /**
     * 区块链/通知异步任务线程池。
     * 被 NotificationService (@Async("blockchainExecutor")) 与
     * BlockchainEventListener 引用——缺失会导致撮合成交、P2P 通知等
     * 异步路径抛 NoSuchBeanDefinitionException。
     */
    @Bean(name = "blockchainExecutor")
    public Executor blockchainExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("blockchain-async-");
        executor.initialize();
        return executor;
    }
}
