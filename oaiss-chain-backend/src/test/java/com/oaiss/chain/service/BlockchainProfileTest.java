package com.oaiss.chain.service;

import com.oaiss.chain.config.FabricProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BlockchainProfileTest {

    @Autowired
    private BlockchainServicePort blockchainService;

    @MockBean
    private MinioService minioService;

    @Test
    void defaultProfile_shouldLoadMockBlockchainService() {
        assertInstanceOf(MockBlockchainService.class, blockchainService,
                "Default profile should load MockBlockchainService");
    }

    @Test
    void mockService_shouldReturnMockConnectionStatus() {
        Map<String, Object> status = blockchainService.checkConnection();

        assertNotNull(status);
        assertEquals("MOCK", status.get("mode"));
    }
}
