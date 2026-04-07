package com.conk.notification.command.application.event;

/**
 * 알림 저장 완료 후 AFTER_COMMIT 후속 처리를 트리거하기 위한 이벤트다.
 *
 * DB 트랜잭션 안에서는 Notification 엔티티 저장까지만 수행하고,
 * Redis unread 증가와 Pub/Sub 발행은 이 이벤트를 기준으로 트랜잭션 커밋 이후에 실행한다.
 */
public class NotificationCreatedEvent {

    private final String notificationId;
    private final String accountId;
    private final String type;
    private final String title;
    private final String message;

    public NotificationCreatedEvent(
            String notificationId,
            String accountId,
            String type,
            String title,
            String message
    ) {
        this.notificationId = notificationId;
        this.accountId = accountId;
        this.type = type;
        this.title = title;
        this.message = message;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getAccountId() {
        return accountId;
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
}
