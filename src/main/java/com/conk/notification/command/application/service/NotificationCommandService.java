package com.conk.notification.command.application.service;

import com.conk.notification.command.application.dto.CreateNotificationCommand;
import com.conk.notification.command.application.dto.SseNotificationPayload;
import com.conk.notification.command.domain.aggregate.Notification;
import com.conk.notification.command.domain.repository.NotificationRepository;
import com.conk.notification.command.infrastructure.redis.publisher.NotificationRedisPublisher;
import com.conk.notification.command.infrastructure.redis.service.NotificationUnreadCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 커맨드 서비스
 *
 * 알림 저장과 같은 쓰기(write) 작업을 처리한다.
 * Kafka Consumer로부터 CreateNotificationCommand를 받아 아래 3가지를 순서대로 처리한다:
 *   1. DB 저장 (MySQL) - 알림 이력 영구 보관
 *   2. Redis Pub/Sub 발행 - SSE push 트리거 (브라우저 실시간 알림)
 *   3. Redis 읽지 않은 카운트 증가 - 프론트 뱃지 숫자 업데이트
 *
 * @Transactional 범위 주의:
 *   DB 저장은 트랜잭션 안에서 처리되지만,
 *   Redis 발행은 트랜잭션 외부 동작이다.
 *   DB 저장 성공 후 Redis가 실패해도 알림 이력은 보존된다.
 *   (향후 DB 저장 실패 시 Redis 발행을 막으려면 @TransactionalEventListener 사용 고려)
 */
@Service
@Transactional
public class NotificationCommandService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCommandService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationRedisPublisher redisPublisher;
    private final NotificationUnreadCountService unreadCountService;

    public NotificationCommandService(
            NotificationRepository notificationRepository,
            NotificationRedisPublisher redisPublisher,
            NotificationUnreadCountService unreadCountService
    ) {
        this.notificationRepository = notificationRepository;
        this.redisPublisher = redisPublisher;
        this.unreadCountService = unreadCountService;
    }

    /**
     * 알림을 생성하고 실시간 push까지 처리한다.
     *
     * 처리 흐름:
     * 1. Notification 엔티티 생성 → DB 저장
     * 2. Redis 읽지 않은 카운트 +1
     * 3. Redis Pub/Sub 발행 → RedisNotificationSubscriber → SseEmitter.send() → 브라우저
     *
     * @param command 알림 생성에 필요한 데이터 (수신자, 유형, 메시지 등)
     */
    public void createNotification(CreateNotificationCommand command) {
        log.info("[알림 생성 시작] type={}, accountId={}", command.getNotificationType(), command.getAccountId());

        // ① DB 저장
        Notification notification = Notification.create(
                command.getAccountId(),
                command.getRoleId(),
                command.getNotificationType(),
                command.getTitle(),
                command.getMessage()
        );
        notificationRepository.save(notification);
        log.info("[DB 저장 완료] notificationId={}", notification.getNotificationId());

        // ② Redis 읽지 않은 카운트 증가 (Redis 장애 시 예외 무시)
        long unreadCount = unreadCountService.increment(command.getAccountId());

        // ③ Redis Pub/Sub 발행 → SSE push 트리거
        // SseNotificationPayload: 브라우저에 전달할 최종 데이터 구성
        SseNotificationPayload payload = new SseNotificationPayload(
                notification.getNotificationId(),
                notification.getNotificationType().name(), // enum → "TASK_ASSIGNED" 문자열
                notification.getTitle(),
                notification.getMessage(),
                unreadCount  // Redis에서 방금 증가된 읽지 않은 알림 수
        );

        // Redis "notification:user:{accountId}" 채널에 발행
        // → RedisNotificationSubscriber가 수신 → SseEmitter.send() 호출
        redisPublisher.publish(command.getAccountId(), payload);

        log.info("[알림 생성 완료] notificationId={}, unreadCount={}", notification.getNotificationId(), unreadCount);
    }
}
