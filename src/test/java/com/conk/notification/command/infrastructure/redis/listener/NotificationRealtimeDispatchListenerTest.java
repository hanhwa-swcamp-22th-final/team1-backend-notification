package com.conk.notification.command.infrastructure.redis.listener;

import com.conk.notification.command.application.dto.SseNotificationPayload;
import com.conk.notification.command.application.event.NotificationCreatedEvent;
import com.conk.notification.command.infrastructure.repository.NotificationJpaRepository;
import com.conk.notification.command.infrastructure.redis.publisher.NotificationRedisPublisher;
import com.conk.notification.command.infrastructure.redis.service.NotificationUnreadCountService;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRealtimeDispatchListener 단위 테스트")
class NotificationRealtimeDispatchListenerTest {

    @Mock
    private NotificationJpaRepository notificationJpaRepository;

    @Mock
    private NotificationUnreadCountService unreadCountService;

    @Mock
    private NotificationRedisPublisher redisPublisher;

    @Test
    @DisplayName("커밋 이후 unread 수를 증가시키고 Redis로 발행한다")
    void handle_dispatchesUnreadCountAndRedisPublish() {
        NotificationRealtimeDispatchListener listener =
                new NotificationRealtimeDispatchListener(unreadCountService, redisPublisher);
        given(unreadCountService.increment("1001")).willReturn(5L);

        // AFTER_COMMIT에서 사용할 이벤트는 불변 클래스 DTO로 전달한다.
        listener.handle(new NotificationCreatedEvent(
                "notification-1",
                "1001",
                "TASK_ASSIGNED",
                "작업 배정",
                "작업 3건이 배정되었습니다."
        ));

        ArgumentCaptor<SseNotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(SseNotificationPayload.class);
        then(redisPublisher).should().publish(org.mockito.ArgumentMatchers.eq("1001"), payloadCaptor.capture());

        SseNotificationPayload payload = payloadCaptor.getValue();
        assertThat(payload.getNotificationId()).isEqualTo("notification-1");
        assertThat(payload.getType()).isEqualTo("TASK_ASSIGNED");
        assertThat(payload.getUnreadCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("repository가 있으면 DB 기준 unread 수를 재계산해 Redis로 발행한다")
    void handle_syncsUnreadCountFromRepository_whenRepositoryExists() {
        NotificationRealtimeDispatchListener listener =
                new NotificationRealtimeDispatchListener(notificationJpaRepository, unreadCountService, redisPublisher);
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 10, 16, 30);
        given(notificationJpaRepository.countByAccountIdAndIsReadFalse("1001")).willReturn(3L);

        listener.handle(new NotificationCreatedEvent(
                "notification-1",
                "1001",
                "TASK_ASSIGNED",
                "작업 배정",
                "작업 3건이 배정되었습니다.",
                createdAt
        ));

        ArgumentCaptor<SseNotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(SseNotificationPayload.class);
        then(unreadCountService).should().setCount("1001", 3L);
        then(unreadCountService).should(never()).increment(anyString());
        then(redisPublisher).should().publish(org.mockito.ArgumentMatchers.eq("1001"), payloadCaptor.capture());

        SseNotificationPayload payload = payloadCaptor.getValue();
        assertThat(payload.getUnreadCount()).isEqualTo(3L);
        assertThat(payload.getCreatedAt()).isEqualTo(createdAt);
        assertThat(payload.getIsRead()).isFalse();
    }

    @Test
    @DisplayName("Redis 후속 처리 실패 시 로깅만 하고 예외를 전파하지 않는다")
    void handle_swallowsRedisDispatchFailure() {
        NotificationRealtimeDispatchListener listener =
                new NotificationRealtimeDispatchListener(unreadCountService, redisPublisher);
        given(unreadCountService.increment("1001"))
                .willThrow(new BusinessException(ErrorCode.REDIS_DISPATCH_FAILED, "redis failed"));

        assertThatCode(() -> listener.handle(new NotificationCreatedEvent(
                "notification-1",
                "1001",
                "TASK_ASSIGNED",
                "작업 배정",
                "작업 3건이 배정되었습니다."
        ))).doesNotThrowAnyException();

        then(redisPublisher).shouldHaveNoInteractions();
    }
}
