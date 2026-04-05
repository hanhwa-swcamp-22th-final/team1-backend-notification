package com.conk.notification.command.application.dto;

import com.conk.notification.command.domain.enums.NotificationType;

/**
 * 알림 생성 커맨드 DTO
 *
 * Kafka Consumer에서 이벤트를 수신한 뒤,
 * NotificationCommandService에 전달하기 위한 내부 데이터 전달 객체이다.
 *
 * 외부(HTTP 요청 등)에서 직접 사용되지 않으며,
 * Kafka Consumer → Service 사이의 내부 인터페이스 역할을 한다.
 */
public class CreateNotificationCommand {

    /** 알림을 받을 계정의 ID (member-service Account.accountId를 문자열로) */
    private final String accountId;

    /** 알림을 받을 계정의 역할 ID (member-service Role.roleId) */
    private final String roleId;

    /** 알림 유형 (TASK_ASSIGNED / ASN_CREATED / ORDER_REGISTERED) */
    private final NotificationType notificationType;

    /** 알림 제목 (짧은 요약) */
    private final String title;

    /** 알림 본문 메시지 */
    private final String message;

    public CreateNotificationCommand(
            String accountId,
            String roleId,
            NotificationType notificationType,
            String title,
            String message
    ) {
        this.accountId = accountId;
        this.roleId = roleId;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
    }

    public String getAccountId() { return accountId; }
    public String getRoleId() { return roleId; }
    public NotificationType getNotificationType() { return notificationType; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
}
