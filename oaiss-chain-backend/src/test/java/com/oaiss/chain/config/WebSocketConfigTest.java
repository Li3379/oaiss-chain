package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketConfig 消息代理与端点配置测试")
class WebSocketConfigTest {

    @Mock
    private MessageBrokerRegistry messageBrokerRegistry;

    @Mock
    private StompEndpointRegistry stompEndpointRegistry;

    @Mock
    private StompWebSocketEndpointRegistration endpointRegistration;

    @Test
    @DisplayName("configureMessageBroker 正确启用 /topic 与 /queue 并设置前缀")
    void configureMessageBroker_enablesBrokersAndPrefixes() {
        WebSocketConfig config = new WebSocketConfig();
        config.configureMessageBroker(messageBrokerRegistry);

        verify(messageBrokerRegistry).enableSimpleBroker("/topic", "/queue");
        verify(messageBrokerRegistry).setApplicationDestinationPrefixes("/app");
        verify(messageBrokerRegistry).setUserDestinationPrefix("/user");
    }

    @Test
    @DisplayName("registerStompEndpoints 注册 /ws 并收敛为指定的跨域白名单")
    void registerStompEndpoints_setsSpecificAllowedOrigins() {
        WebSocketConfig config = new WebSocketConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:5173,http://127.0.0.1:5173");

        when(stompEndpointRegistry.addEndpoint("/ws")).thenReturn(endpointRegistration);
        when(endpointRegistration.setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")).thenReturn(endpointRegistration);

        config.registerStompEndpoints(stompEndpointRegistry);

        verify(stompEndpointRegistry).addEndpoint("/ws");
        verify(endpointRegistration).setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173");
        verify(endpointRegistration).withSockJS();
    }
}
