package com.conk.notification.command.infrastructure.redis.listener;

import com.conk.notification.command.application.dto.SseNotificationPayload;
import com.conk.notification.command.application.event.NotificationCreatedEvent;
import com.conk.notification.command.infrastructure.repository.NotificationJpaRepository;
import com.conk.notification.command.infrastructure.redis.publisher.NotificationRedisPublisher;
import com.conk.notification.command.infrastructure.redis.service.NotificationUnreadCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 저장 커밋 이후 Redis 후속 처리를 담당하는 리스너다.
 *
 * DB commit이 끝난 뒤에만 unread 증가와 Pub/Sub 발행을 수행해
 * DB에는 없는데 브라우저에는 보이는 유령 알림을 막는다.
 */
@Component
public class NotificationRealtimeDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRealtimeDispatchListener.class);

    private final NotificationJpaRepository notificationJpaRepository;
    private final NotificationUnreadCountService unreadCountService;
    private final NotificationRedisPublisher redisPublisher;

    public NotificationRealtimeDispatchListener(
            NotificationUnreadCountService unreadCountService,
            NotificationRedisPublisher redisPublisher
    ) {
        this(null, unreadCountService, redisPublisher);
    }

    @Autowired
    public NotificationRealtimeDispatchListener(
            NotificationJpaRepository notificationJpaRepository,
            NotificationUnreadCountService unreadCountService,
            NotificationRedisPublisher redisPublisher
    ) {
        this.notificationJpaRepository = notificationJpaRepository;
        this.unreadCountService = unreadCountService;
        this.redisPublisher = redisPublisher;
    }

    /**
     * 알림 저장 트랜잭션이 정상 커밋된 뒤 Redis unread 증가와 실시간 publish를 수행한다.
     *
     * commit 이후 단계이므로 여기서 예외를 다시 던지면
     * 이미 저장된 알림에 대해 Kafka 재처리가 일어나 중복 저장 위험이 있다.
     * 따라서 후속 처리 실패는 로깅만 하고 종료한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        try {
            long unreadCount;
            if (notificationJpaRepository != null) {
                unreadCount = notificationJpaRepository.countByAccountIdAndIsReadFalse(event.getAccountId());
                unreadCountService.setCount(event.getAccountId(), unreadCount);
            } else {
                unreadCount = unreadCountService.increment(event.getAccountId());
            }

            redisPublisher.publish(
                    event.getAccountId(),
                    new SseNotificationPayload(
                            event.getNotificationId(),
                            event.getType(),
                            event.getTitle(),
                            event.getMessage(),
                            false,
                            event.getCreatedAt(),
                            unreadCount
                    )
            );
        } catch (Exception e) {
            log.error("[실시간 알림 후속 처리 실패] notificationId={}", event.getNotificationId(), e);
        }
    }
}
