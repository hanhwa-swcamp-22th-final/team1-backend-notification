package com.conk.notification.query.dto;

import java.time.LocalDateTime;

/**
 * 알림 목록/실시간 알림 공통 필드 구조다.
 */
public class NotificationListItemResponse {

    private final String notificationId;
    private final String type;
    private final String title;
    private final String message;
    private final boolean isRead;
    private final LocalDateTime createdAt;

    public NotificationListItemResponse(
            String notificationId,
            String type,
            String title,
            String message,
            boolean isRead,
            LocalDateTime createdAt
    ) {
        this.notificationId = notificationId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean getIsRead() {
        return isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
