package com.conk.notification.query.service;

import com.conk.notification.command.domain.aggregate.Notification;
import com.conk.notification.command.domain.enums.NotificationType;
import com.conk.notification.command.infrastructure.repository.NotificationJpaRepository;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import com.conk.notification.query.dto.NotificationListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationQueryService {

    private final NotificationJpaRepository notificationJpaRepository;

    public NotificationQueryService(NotificationJpaRepository notificationJpaRepository) {
        this.notificationJpaRepository = notificationJpaRepository;
    }

    public List<NotificationListItemResponse> findNotifications(
            String accountId,
            int page,
            int size,
            String type,
            Boolean isRead,
            String sort
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                resolveSort(sort)
        );

        NotificationType notificationType = parseNotificationType(type);
        Page<Notification> result = findPage(accountId, notificationType, isRead, pageable);

        return result.getContent().stream()
                .map(this::toResponse)
                .toList();
    }

    public long countUnreadNotifications(String accountId) {
        return notificationJpaRepository.countByAccountIdAndIsReadFalse(accountId);
    }

    private Page<Notification> findPage(
            String accountId,
            NotificationType notificationType,
            Boolean isRead,
            Pageable pageable
    ) {
        if (notificationType != null && isRead != null) {
            return notificationJpaRepository.findByAccountIdAndNotificationTypeAndIsRead(
                    accountId,
                    notificationType,
                    isRead,
                    pageable
            );
        }
        if (notificationType != null) {
            return notificationJpaRepository.findByAccountIdAndNotificationType(accountId, notificationType, pageable);
        }
        if (isRead != null) {
            return notificationJpaRepository.findByAccountIdAndIsRead(accountId, isRead, pageable);
        }
        return notificationJpaRepository.findByAccountId(accountId, pageable);
    }

    private NotificationType parseNotificationType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        try {
            return NotificationType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.INVALID_NOTIFICATION_TYPE,
                    "지원하지 않는 알림 타입입니다: %s".formatted(type)
            );
        }
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("createdAt"));
        }

        String[] tokens = sort.split(",", 2);
        String property = "createdAt".equals(tokens[0]) ? "createdAt" : "createdAt";
        Sort.Direction direction = (tokens.length > 1)
                ? Sort.Direction.fromOptionalString(tokens[1]).orElse(Sort.Direction.DESC)
                : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(direction, property));
    }

    private NotificationListItemResponse toResponse(Notification notification) {
        return new NotificationListItemResponse(
                notification.getNotificationId(),
                notification.getNotificationType().name(),
                notification.getTitle(),
                notification.getMessage(),
                Boolean.TRUE.equals(notification.getIsRead()),
                notification.getCreatedAt()
        );
    }
}
