package com.oaiss.chain.config;

import com.oaiss.chain.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collections;

/**
 * WebSocket STOMP authentication configuration.
 * Validates JWT tokens from the STOMP CONNECT frame header.
 * Rejects unauthenticated WebSocket connections.
 *
 * Note: This is a separate @Configuration class to avoid circular dependency
 * with WebSocketConfig, which handles endpoint registration only.
 *
 * @author OAISS Team
 */
@Configuration
public class WebSocketAuthConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthConfig.class);

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(createAuthChannelInterceptor());
    }

    public ChannelInterceptor createAuthChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                // 1. CONNECT 帧握手鉴权校验
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            if (jwtTokenProvider.validateToken(token)) {
                                String username = jwtTokenProvider.getUsernameFromToken(token);
                                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                                String principalName = userId != null ? String.valueOf(userId) : username;
                                var auth = new UsernamePasswordAuthenticationToken(
                                        principalName, null, Collections.emptyList());
                                if (accessor.isMutable()) {
                                    accessor.setUser(auth);
                                } else {
                                    StompHeaderAccessor mutableAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
                                    mutableAccessor.copyHeaders(accessor.getMessageHeaders());
                                    mutableAccessor.setUser(auth);
                                    mutableAccessor.setLeaveMutable(true);
                                    message = org.springframework.messaging.support.MessageBuilder.createMessage(
                                            message.getPayload(), mutableAccessor.getMessageHeaders());
                                }
                                SecurityContextHolder.getContext().setAuthentication(auth);
                                log.debug("WebSocket authenticated: {} (principal={})", username, principalName);
                            } else {
                                log.warn("WebSocket rejected: invalid JWT token");
                                throw new org.springframework.messaging.MessageDeliveryException("Unauthorized: invalid token");
                            }
                        } catch (Exception e) {
                            log.warn("WebSocket auth failed: {}", e.getMessage());
                            throw new org.springframework.messaging.MessageDeliveryException("Unauthorized: " + e.getMessage());
                        }
                    } else {
                        log.warn("WebSocket CONNECT rejected: missing Authorization header");
                        throw new org.springframework.messaging.MessageDeliveryException("Unauthorized: missing token");
                    }
                }

                // 2. SUBSCRIBE 帧订阅通道隔离与跨租户防穿透校验
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    java.security.Principal user = accessor.getUser();
                    if (user == null || user.getName() == null || user.getName().isBlank()) {
                        log.warn("WebSocket SUBSCRIBE rejected: unauthenticated session");
                        throw new org.springframework.messaging.MessageDeliveryException("Unauthorized: unauthenticated subscription");
                    }

                    String destination = accessor.getDestination();
                    if (destination == null || destination.isBlank()) {
                        log.warn("WebSocket SUBSCRIBE rejected: missing destination for user {}", user.getName());
                        throw new org.springframework.messaging.MessageDeliveryException("Bad Request: missing subscription destination");
                    }

                    String principal = user.getName();

                    // (a) 公共广播主题允许订阅（如 /topic/system-notifications）
                    if (destination.startsWith("/topic/")) {
                        return message;
                    }

                    // (b) 用户私有通道订阅校验（/user/queue/** 或 /user/{targetUserId}/queue/**）
                    if (destination.startsWith("/user/")) {
                        String userSubPath = destination.substring(6); // 截取 "/user/" 之后的路径
                        if (userSubPath.startsWith("queue/") || userSubPath.startsWith("topic/")) {
                            // 标准私有队列：/user/queue/notifications，Spring 将自动绑定到当前会话 Principal
                            return message;
                        }

                        // 显式指定用户目标路径格式：/user/{targetUser}/...
                        int slashIndex = userSubPath.indexOf('/');
                        String targetUser = slashIndex != -1 ? userSubPath.substring(0, slashIndex) : userSubPath;
                        if (!targetUser.equals(principal)) {
                            log.warn("WebSocket cross-tenant SUBSCRIBE blocked: user {} attempted to subscribe to {}",
                                    principal, destination);
                            throw new org.springframework.messaging.MessageDeliveryException(
                                    "Forbidden: cannot subscribe to another user's queue");
                        }
                        return message;
                    }

                    // (c) Broker 原始队列直接订阅校验（如 /queue/**）
                    if (destination.startsWith("/queue/")) {
                        // 仅允许订阅包含当前用户标识的自身队列，禁止跨租户或泛化窃听
                        if (destination.endsWith("-user" + principal) || destination.startsWith("/queue/" + principal + "/")) {
                            return message;
                        }
                        log.warn("WebSocket raw queue SUBSCRIBE blocked: user {} attempted to subscribe to {}",
                                principal, destination);
                        throw new org.springframework.messaging.MessageDeliveryException(
                                "Forbidden: direct subscription to raw queue is restricted");
                    }

                    // (d) 其他非受信前缀拒绝
                    log.warn("WebSocket SUBSCRIBE blocked: invalid destination {} from user {}", destination, principal);
                    throw new org.springframework.messaging.MessageDeliveryException(
                            "Forbidden: invalid destination prefix");
                }

                return message;
            }
        };
    }
}