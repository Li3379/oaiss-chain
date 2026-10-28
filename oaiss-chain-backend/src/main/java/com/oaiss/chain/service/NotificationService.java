package com.oaiss.chain.service;

import com.oaiss.chain.entity.Notification;
import com.oaiss.chain.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知服务 — 提供同步和异步通知发送能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Async("blockchainExecutor")
    @Transactional
    @CacheEvict(value = "notifUnread", key = "T(String).valueOf(#userId)")

    public void sendNotificationAsync(Long userId, String title, String content,
                                       String notificationType, Long relatedEntityId,
                                       String relatedEntityType) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setNotificationType(notificationType);
            notification.setRelatedEntityId(relatedEntityId);
            notification.setRelatedEntityType(relatedEntityType);
            notification.setIsRead(false);
            notificationRepository.save(notification);
            log.info("Async notification sent: userId={}, type={}", userId, notificationType);
        } catch (Exception e) {
            log.error("Async notification failed: userId={}, type={}", userId, notificationType, e);
        }
    }

    @Transactional
    @CacheEvict(value = "notifUnread", key = "T(String).valueOf(#userId)")

    public Notification sendNotification(Long userId, String title, String content,
                                          String notificationType) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setNotificationType(notificationType);
        notification.setIsRead(false);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    @Cacheable(value = "notifUnread", key = "T(String).valueOf(#userId)", unless = "#result == 0")


    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalseAndDeletedFalse(userId);
    }

    @CacheEvict(value = "notifUnread", key = "T(String).valueOf(#userId)")


    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.findByIdAndUserIdAndDeletedFalse(notificationId, userId)
                .ifPresent(n -> {
                    n.setIsRead(true);
                    notificationRepository.save(n);
                });
    }

    @CacheEvict(value = "notifUnread", key = "T(String).valueOf(#userId)")


    @Transactional
    public void markAllAsRead(Long userId) {
        Page<Notification> unread = notificationRepository
                .findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId, Pageable.unpaged());
        unread.stream().filter(n -> !n.getIsRead()).forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }
    /**
     * R118: Batch notification sender for multiple users.
     * Processes notifications in a separate thread to avoid blocking the request.
     */
    @Async("blockchainExecutor")
    @Transactional
    public void sendBatchNotificationsAsync(List<Long> userIds, String title, String content,
                                             String notificationType) {
        try {
            for (Long userId : userIds) {
                Notification notification = new Notification();
                notification.setUserId(userId);
                notification.setTitle(title);
                notification.setContent(content);
                notification.setNotificationType(notificationType);
                notification.setIsRead(false);
                notificationRepository.save(notification);
            }
            log.info("Batch notifications sent to {} users, type={}", userIds.size(), notificationType);
        } catch (Exception e) {
            log.error("Batch notification failed: {}", e.getMessage(), e);
        }
    }

}
