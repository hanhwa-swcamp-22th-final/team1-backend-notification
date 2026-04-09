package com.conk.notification.command.application.service;

import com.conk.notification.command.domain.aggregate.Notification;
import com.conk.notification.command.infrastructure.repository.NotificationJpaRepository;
import com.conk.notification.command.infrastructure.redis.service.NotificationUnreadCountService;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationReadService {

    private final NotificationJpaRepository notificationJpaRepository;
    private final NotificationUnreadCountService unreadCountService;

    public NotificationReadService(
            NotificationJpaRepository notificationJpaRepository,
            NotificationUnreadCountService unreadCountService
    ) {
        this.notificationJpaRepository = notificationJpaRepository;
        this.unreadCountService = unreadCountService;
    }

    @Transactional
    public void markAsRead(String notificationId, String accountId) {
        Notification notification = notificationJpaRepository.findByNotificationIdAndAccountId(notificationId, accountId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOTIFICATION_NOT_FOUND,
                        "알림을 찾을 수 없습니다. notificationId=%s".formatted(notificationId)
                ));

        boolean changed = notification.markAsRead();
        if (!changed) {
            return;
        }

        syncUnreadCount(accountId);
    }

    @Transactional
    public void markAllAsRead(String accountId) {
        List<Notification> unreadNotifications = notificationJpaRepository.findByAccountIdAndIsReadFalse(accountId);
        if (unreadNotifications.isEmpty()) {
            unreadCountService.setCount(accountId, 0L);
            return;
        }

        unreadNotifications.forEach(Notification::markAsRead);
        syncUnreadCount(accountId);
    }

    private void syncUnreadCount(String accountId) {
        long unreadCount = notificationJpaRepository.countByAccountIdAndIsReadFalse(accountId);
        unreadCountService.setCount(accountId, unreadCount);
    }
}
