package com.oaiss.chain.controller;

import com.oaiss.chain.entity.Notification;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private JwtUserDetails testUser;

    @BeforeEach
    void setUp() {
        testUser = JwtUserDetails.builder()
                .userId(100L)
                .username("testuser")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    @Test
    void listNotifications_returnsPage() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationService.getUserNotifications(eq(100L), any())).thenReturn(page);
        var response = notificationController.listNotifications(testUser, 1, 20);
        assertNotNull(response);
        assertEquals(200, response.getCode());
    }

    @Test
    void getUnreadCount_returnsCount() {
        when(notificationService.getUnreadCount(100L)).thenReturn(3L);
        var response = notificationController.getUnreadCount(testUser);
        assertNotNull(response);
        assertEquals(3L, response.getData());
    }

    @Test
    void markAsRead_delegatesToService() {
        doNothing().when(notificationService).markAsRead(1L, 100L);
        var response = notificationController.markAsRead(testUser, 1L);
        assertNotNull(response);
        assertEquals(200, response.getCode());
    }

    @Test
    void markAllAsRead_delegatesToService() {
        doNothing().when(notificationService).markAllAsRead(100L);
        var response = notificationController.markAllAsRead(testUser);
        assertNotNull(response);
        assertEquals(200, response.getCode());
    }
}