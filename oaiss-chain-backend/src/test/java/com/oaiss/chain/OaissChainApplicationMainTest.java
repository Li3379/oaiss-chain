package com.oaiss.chain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

@DisplayName("OaissChainApplication main tests")
class OaissChainApplicationMainTest {

    @Test
    @DisplayName("main should delegate to SpringApplication.run")
    void main_shouldDelegateToSpringApplication() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            OaissChainApplication.main(new String[]{"--spring.profiles.active=test"});

            springApplication.verify(() -> SpringApplication.run(OaissChainApplication.class,
                    new String[]{"--spring.profiles.active=test"}));
        }
    }
}
