package com.oaiss.chain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 主应用测试类
 * 
 * @author OAISS Team
 */
@SpringBootTest
@ActiveProfiles("test")
class OaissChainApplicationTests {

    @Test
    void contextLoads() {
        // 验证Spring上下文能够正常加载
    }
}
