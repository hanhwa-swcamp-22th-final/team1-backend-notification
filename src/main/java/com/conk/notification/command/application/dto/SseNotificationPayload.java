package com.conk.notification.command.application.dto;

/**
 * SSE로 브라우저에 전송하는 알림 데이터 DTO
 *
 * Redis Pub/Sub을 통해 전달된 후 SseEmitter.send()로 브라우저에 push된다.
 * JSON 직렬화되어 전송되므로 getter가 반드시 있어야 한다.
 *
 * 브라우저에서 받는 SSE 이벤트 예시:
 *   data: {"notificationId":"uuid","type":"TASK_ASSIGNED","title":"작업 배정",
 *          "message":"작업 3건이 배정되었습니다.","unreadCount":5}
 */
public class SseNotificationPayload {

    /** 알림 고유 ID (UUID) */
    private final String notificationId;

    /** 알림 유형 문자열 (예: "TASK_ASSIGNED") */
    private final String type;

    /** 알림 제목 */
    private final String title;

    /** 알림 본문 메시지 */
    private final String message;

    /**
     * 읽지 않은 알림 총 개수
     * Redis에서 조회한 값으로 브라우저 뱃지 숫자를 업데이트할 때 사용한다.
     */
    private final long unreadCount;

    public SseNotificationPayload(
            String notificationId,
            String type,
            String title,
            String message,
            long unreadCount
    ) {
        this.notificationId = notificationId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.unreadCount = unreadCount;
    }

    public String getNotificationId() { return notificationId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public long getUnreadCount() { return unreadCount; }
}
