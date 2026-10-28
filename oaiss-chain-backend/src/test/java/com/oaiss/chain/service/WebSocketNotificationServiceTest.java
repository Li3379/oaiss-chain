package com.oaiss.chain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketNotificationService webSocketNotificationService;

    @Test
    void pushToUser_sendsToCorrectDestination() {
        webSocketNotificationService.pushToUser(100L, "Test Title", "Test Content", "REPORT_APPROVED");

        verify(messagingTemplate).convertAndSendToUser(
                eq("100"),
                eq("/queue/notifications"),
                any(Map.class)
        );
    }

    @Test
    void pushToUser_handlesMessagingException() {
        doThrow(new RuntimeException("Connection lost"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        webSocketNotificationService.pushToUser(100L, "Test", "Content", "TYPE");
    }

    @Test
    void pushUnreadCount_sendsToCorrectDestination() {
        webSocketNotificationService.pushUnreadCount(100L, 5);

        verify(messagingTemplate).convertAndSendToUser(
                eq("100"),
                eq("/queue/unread-count"),
                any(Map.class)
        );
    }

    @Test
    void pushUnreadCount_handlesMessagingException() {
        doThrow(new RuntimeException("Connection lost"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        webSocketNotificationService.pushUnreadCount(100L, 3);
    }

    @Test
    void broadcastSystemNotification_sendsToTopic() {
        webSocketNotificationService.broadcastSystemNotification("System Alert", "Test message");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/system-notifications"),
                (Object) any(Map.class)
        );
    }

    @Test
    void broadcastSystemNotification_handlesMessagingException() {
        doThrow(new RuntimeException("Broker unavailable"))
                .when(messagingTemplate).convertAndSend(anyString(), (Object) any());

        webSocketNotificationService.broadcastSystemNotification("Title", "Content");
    }
}