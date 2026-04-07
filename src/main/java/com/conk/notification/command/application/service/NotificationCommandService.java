package com.conk.notification.command.application.service;

import com.conk.notification.command.application.dto.CreateNotificationCommand;
import com.conk.notification.command.application.event.NotificationCreatedEvent;
import com.conk.notification.command.domain.aggregate.Notification;
import com.conk.notification.command.domain.repository.NotificationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 저장과 AFTER_COMMIT 후속 처리 예약을 담당하는 커맨드 서비스다.
 *
 * Kafka Consumer로부터 CreateNotificationCommand를 받아 아래 2가지를 순서대로 처리한다:
 *   1. DB 저장 (MySQL) - 알림 이력 영구 보관
 *   2. NotificationCreatedEvent 발행 - AFTER_COMMIT Redis 후속 처리 트리거
 *
 * @Transactional 범위 주의:
 *   이 서비스는 DB 저장까지만 트랜잭션 안에서 책임진다.
 *   Redis unread 증가와 Pub/Sub 발행은 AFTER_COMMIT 리스너에서 처리하므로
 *   DB rollback 시 실시간 알림이 먼저 발송되는 문제를 막을 수 있다.
 */
@Service
@Transactional
public class NotificationCommandService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCommandService.class);

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationCommandService(
            NotificationRepository notificationRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 알림을 생성하고 AFTER_COMMIT 후속 처리를 예약한다.
     *
     * 처리 흐름:
     * 1. Notification 엔티티 생성 → DB 저장
     * 2. AFTER_COMMIT용 NotificationCreatedEvent 발행
     *
     * @param command 알림 생성에 필요한 데이터 (수신자, 유형, 메시지 등)
     */
    public void createNotification(CreateNotificationCommand command) {
        log.info("[알림 생성 시작] type={}, accountId={}", command.getNotificationType(), command.getAccountId());

        // 알림 이력은 항상 DB에 먼저 저장한다.
        Notification notification = Notification.create(
                command.getAccountId(),
                command.getRoleId(),
                command.getNotificationType(),
                command.getTitle(),
                command.getMessage()
        );
        notificationRepository.save(notification);
        log.info("[DB 저장 완료] notificationId={}", notification.getNotificationId());

        // Redis 후속 처리는 AFTER_COMMIT 리스너에서만 실행되도록 이벤트만 발행한다.
        eventPublisher.publishEvent(new NotificationCreatedEvent(
                notification.getNotificationId(),
                notification.getAccountId(),
                notification.getNotificationType().name(),
                notification.getTitle(),
                notification.getMessage()
        ));

        log.info("[알림 생성 완료] notificationId={}", notification.getNotificationId());
    }
}
