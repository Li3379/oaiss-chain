package com.oaiss.chain.service;

import com.oaiss.chain.entity.Notification;
import com.oaiss.chain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void sendNotification_savesAndReturns() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });
        Notification result = notificationService.sendNotification(
                100L, "Test Title", "Test Content", "TRADE");
        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals("Test Title", result.getTitle());
        assertFalse(result.getIsRead());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void getUserNotifications_returnsPage() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(
                eq(100L), any(PageRequest.class))).thenReturn(page);
        Page<Notification> result = notificationService.getUserNotifications(100L, PageRequest.of(0, 10));
        assertNotNull(result);
    }

    @Test
    void getUnreadCount_returnsCount() {
        when(notificationRepository.countByUserIdAndIsReadFalseAndDeletedFalse(100L)).thenReturn(5L);
        assertEquals(5L, notificationService.getUnreadCount(100L));
    }

    @Test
    void markAsRead_whenFound_setsRead() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setIsRead(false);
        when(notificationRepository.findByIdAndUserIdAndDeletedFalse(1L, 100L))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        notificationService.markAsRead(1L, 100L);
        assertTrue(notification.getIsRead());
    }

    @Test
    void markAsRead_whenNotFound_doesNothing() {
        when(notificationRepository.findByIdAndUserIdAndDeletedFalse(999L, 100L))
                .thenReturn(Optional.empty());
        notificationService.markAsRead(999L, 100L);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendNotificationAsync_savesSuccessfully() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        assertDoesNotThrow(() -> notificationService.sendNotificationAsync(
                100L, "Title", "Content", "REPORT", 1L, "CarbonReport"));
        verify(notificationRepository).save(any(Notification.class));
    }
}