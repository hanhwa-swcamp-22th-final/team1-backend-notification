package com.conk.notification.command.infrastructure.repository;

import com.conk.notification.command.domain.aggregate.Notification;
import com.conk.notification.command.domain.enums.NotificationType;
import com.conk.notification.command.domain.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA 기반 알림 레포지토리 구현체
 *
 * JpaRepository를 상속받아 기본적인 CRUD 메서드(save, findById, delete 등)를 자동으로 제공받는다.
 * NotificationRepository(도메인 인터페이스)도 함께 구현하여,
 * 서비스 레이어가 이 JPA 구현에 직접 의존하지 않도록 한다.
 *
 * JpaRepository<Notification, String>:
 *   - 첫 번째 타입: 엔티티 클래스 (Notification)
 *   - 두 번째 타입: Primary Key의 타입 (notification_id가 String이므로 String)
 *
 * Spring Data JPA가 런타임에 이 인터페이스의 구현체를 자동으로 생성한다.
 * 별도의 구현 클래스를 작성하지 않아도 된다.
 */
public interface NotificationJpaRepository
        extends JpaRepository<Notification, String>, NotificationRepository {

    Page<Notification> findByAccountId(String accountId, Pageable pageable);

    Page<Notification> findByAccountIdAndIsRead(String accountId, Boolean isRead, Pageable pageable);

    Page<Notification> findByAccountIdAndNotificationType(
            String accountId,
            NotificationType notificationType,
            Pageable pageable
    );

    Page<Notification> findByAccountIdAndNotificationTypeAndIsRead(
            String accountId,
            NotificationType notificationType,
            Boolean isRead,
            Pageable pageable
    );

    Optional<Notification> findByNotificationIdAndAccountId(String notificationId, String accountId);

    List<Notification> findByAccountIdAndIsReadFalse(String accountId);

    long countByAccountIdAndIsReadFalse(String accountId);
}
