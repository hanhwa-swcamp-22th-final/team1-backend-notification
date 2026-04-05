package com.conk.notification.command.domain.aggregate;

import com.conk.notification.command.domain.enums.NotificationType;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 알림 도메인 엔티티
 *
 * DB 테이블 'notification'에 매핑된다.
 * DDL의 컬럼 구조를 그대로 반영하며,
 * Kafka 이벤트를 수신한 뒤 이 엔티티를 생성해 DB에 저장한다.
 */
@Entity
@Table(name = "notification")
public class Notification {

    /**
     * 알림 고유 ID (Primary Key)
     * UUID 형태의 문자열을 사용한다. (예: "550e8400-e29b-41d4-a716-446655440000")
     * @GeneratedValue를 사용하지 않고 create() 팩토리 메서드에서 직접 생성한다.
     */
    @Id
    @Column(name = "notification_id")
    private String notificationId;

    /**
     * 알림 수신 계정 ID
     * member-service의 Account 엔티티의 accountId를 문자열로 저장한다.
     * (Account.accountId는 Long이지만, 이 서비스에서는 VARCHAR로 처리)
     */
    @Column(name = "account_id", nullable = false)
    private String accountId;

    /**
     * 알림 수신 계정의 역할 ID
     * member-service의 Role 엔티티의 roleId를 그대로 저장한다.
     * (예: "ROLE_WH_WORKER", "ROLE_WH_MANAGER", "ROLE_MASTER_ADMIN")
     */
    @Column(name = "role_id", nullable = false)
    private String roleId;

    /**
     * 알림 유형
     * @Enumerated(EnumType.STRING): DB에 enum 이름을 문자열로 저장
     * (예: "TASK_ASSIGNED", "ASN_CREATED", "ORDER_REGISTERED")
     * EnumType.ORDINAL은 숫자로 저장되므로 순서가 바뀌면 데이터가 깨질 위험이 있어 사용하지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    /**
     * 알림 제목 (짧은 요약)
     * 예: "작업 배정", "입고예정 등록", "주문 등록"
     */
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * 알림 본문 메시지
     * 예: "작업 3건이 배정되었습니다"
     * columnDefinition = "TEXT": 긴 문자열도 저장 가능하도록 TEXT 타입 사용
     */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * 읽음 여부
     * 최초 생성 시 false(읽지 않음), 사용자가 확인 시 true로 변경
     */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    // JPA는 기본 생성자가 반드시 필요 (protected로 외부 직접 생성 방지)
    protected Notification() {}

    /**
     * 알림 생성 팩토리 메서드
     *
     * new Notification() 대신 이 메서드를 통해 생성하도록 강제한다.
     * notificationId(UUID), isRead(false) 등 기본값을 여기서 세팅한다.
     *
     * @param accountId       수신 계정 ID
     * @param roleId          수신 계정의 역할 ID
     * @param notificationType 알림 유형
     * @param title           알림 제목
     * @param message         알림 본문
     * @return 생성된 Notification 엔티티 (아직 DB에 저장되지 않은 상태)
     */
    public static Notification create(
            String accountId,
            String roleId,
            NotificationType notificationType,
            String title,
            String message
    ) {
        Notification notification = new Notification();
        notification.notificationId = UUID.randomUUID().toString(); // 고유 ID 자동 생성
        notification.accountId = accountId;
        notification.roleId = roleId;
        notification.notificationType = notificationType;
        notification.title = title;
        notification.message = message;
        notification.isRead = false; // 최초 생성 시 항상 읽지 않음
        notification.createdBy = "SYSTEM"; // Kafka Consumer가 생성하므로 시스템 처리
        notification.updatedBy = "SYSTEM";
        return notification;
    }

    /**
     * DB INSERT 직전에 자동 실행 (JPA 라이프사이클 콜백)
     * createdAt, updatedAt을 현재 시간으로 세팅한다.
     */
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * DB UPDATE 직전에 자동 실행 (JPA 라이프사이클 콜백)
     * updatedAt을 현재 시간으로 갱신한다.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== Getters ====================
    public String getNotificationId() { return notificationId; }
    public String getAccountId() { return accountId; }
    public String getRoleId() { return roleId; }
    public NotificationType getNotificationType() { return notificationType; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Boolean getIsRead() { return isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
}
