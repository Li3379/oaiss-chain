package com.oaiss.chain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket-based notification push service.
 * Pushes real-time notifications to specific users via STOMP destinations.
 * Falls back gracefully if WebSocket is unavailable (client polling catches up).
 *
 * @author OAISS Team
 */
@Service
public class WebSocketNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketNotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Push a notification to a specific user.
     *
     * @param userId  target user ID
     * @param title   notification title
     * @param content notification content
     * @param type    notification type (e.g., REPORT_APPROVED, TRADE_COMPLETED)
     */
    public void pushToUser(Long userId, String title, String content, String type) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("content", content);
            payload.put("type", type);
            payload.put("timestamp", LocalDateTime.now().toString());
            payload.put("isRead", false);

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    payload
            );
            log.debug("Pushed notification to user {}: {}", userId, title);
        } catch (Exception e) {
            log.warn("Failed to push WebSocket notification to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Push unread count update to a specific user.
     *
     * @param userId      target user ID
     * @param unreadCount new unread count
     */
    public void pushUnreadCount(Long userId, long unreadCount) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("unreadCount", unreadCount);
            payload.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/unread-count",
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to push unread count to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Broadcast a system-wide notification to all connected clients.
     *
     * @param title   notification title
     * @param content notification content
     */
    public void broadcastSystemNotification(String title, String content) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("content", content);
            payload.put("type", "SYSTEM");
            payload.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend("/topic/system-notifications", payload);
            log.info("Broadcast system notification: {}", title);
        } catch (Exception e) {
            log.warn("Failed to broadcast system notification: {}", e.getMessage());
        }
    }
}