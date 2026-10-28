package com.oaiss.chain.config;

import com.oaiss.chain.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket STOMP 鉴权与订阅隔离测试")
class WebSocketAuthConfigTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketAuthConfig webSocketAuthConfig;
    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        webSocketAuthConfig = new WebSocketAuthConfig(jwtTokenProvider);
        interceptor = webSocketAuthConfig.createAuthChannelInterceptor();
    }

    @Test
    @DisplayName("CONNECT 携带有效 Token 时鉴权成功并绑定 Principal")
    void connect_withValidToken_setsAuthenticatedUser() {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(101L);
        when(jwtTokenProvider.getUsernameFromToken("valid-token")).thenReturn("enterprise001");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("101", resultAccessor.getUser().getName());
    }

    @Test
    @DisplayName("CONNECT 携带无效 Token 时抛出 401 Unauthorized 异常")
    void connect_withInvalidToken_throwsException() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("Unauthorized: invalid token"));
    }

    @Test
    @DisplayName("CONNECT 缺失 Authorization 头时拒绝连接")
    void connect_missingAuthHeader_throwsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("Unauthorized: missing token"));
    }

    @Test
    @DisplayName("SUBSCRIBE 未认证会话订阅时抛出 Unauthorized 异常")
    void subscribe_unauthenticatedUser_throwsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/notifications");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("Unauthorized: unauthenticated subscription"));
    }

    @Test
    @DisplayName("SUBSCRIBE 订阅公共广播主题 /topic/system-notifications 放行")
    void subscribe_publicTopic_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("101", null, Collections.emptyList()));
        accessor.setDestination("/topic/system-notifications");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("SUBSCRIBE 订阅标准用户自身私有队列 /user/queue/notifications 放行")
    void subscribe_standardUserQueue_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("101", null, Collections.emptyList()));
        accessor.setDestination("/user/queue/notifications");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("SUBSCRIBE 订阅自身显式标识路径 /user/101/queue/notifications 放行")
    void subscribe_ownExplicitUserQueue_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("101", null, Collections.emptyList()));
        accessor.setDestination("/user/101/queue/notifications");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("SUBSCRIBE 跨租户订阅他人私有队列 /user/202/queue/notifications 严格阻断")
    void subscribe_crossTenantUserQueue_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("101", null, Collections.emptyList()));
        accessor.setDestination("/user/202/queue/notifications");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("Forbidden: cannot subscribe to another user's queue"));
    }

    @Test
    @DisplayName("SUBSCRIBE 直接订阅通用原始队列 /queue/notifications 严格阻断")
    void subscribe_rawQueueDirect_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("101", null, Collections.emptyList()));
        accessor.setDestination("/queue/notifications");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("Forbidden: direct subscription to raw queue is restricted"));
    }

    @Test
    @DisplayName("SUBSCRIBE 订阅自身 Broker 内部队列 /queue/notifications-user101 放行")
    void subscribe_ownRawBrokerQueue_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("101", null, Collections.emptyList()));
        accessor.setDestination("/queue/notifications-user101");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("SUBSCRIBE 试图偷窥他人 Broker 内部队列 /queue/notifications-user202 阻断")
    void subscribe_otherRawBrokerQueue_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("101", null, Collections.emptyList()));
        accessor.setDestination("/queue/notifications-user202");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("Forbidden: direct subscription to raw queue is restricted"));
    }

    @Test
    @DisplayName("SUBSCRIBE 非法前缀目标拒绝订阅")
    void subscribe_invalidDestinationPrefix_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("101", null, Collections.emptyList()));
        accessor.setDestination("/admin/secret-channel");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("Forbidden: invalid destination prefix"));
    }
}
