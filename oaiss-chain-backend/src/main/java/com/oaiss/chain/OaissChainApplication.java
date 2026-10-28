package com.oaiss.chain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 双碳链动系统——基于区块链的可信碳核算与交易平台
 * OAISS Chain Backend Application
 * 
 * @author OAISS Team
 * @version 1.0.0
 */
@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
@EnableCaching
public class OaissChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(OaissChainApplication.class, args);
    }
}
