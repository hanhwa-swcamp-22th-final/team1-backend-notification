package com.conk.notification.command.infrastructure.redis.listener;

import com.conk.notification.command.application.dto.SseNotificationPayload;
import com.conk.notification.command.application.event.NotificationCreatedEvent;
import com.conk.notification.command.infrastructure.redis.publisher.NotificationRedisPublisher;
import com.conk.notification.command.infrastructure.redis.service.NotificationUnreadCountService;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRealtimeDispatchListener 단위 테스트")
class NotificationRealtimeDispatchListenerTest {

    @Mock
    private NotificationUnreadCountService unreadCountService;

    @Mock
    private NotificationRedisPublisher redisPublisher;

    @InjectMocks
    private NotificationRealtimeDispatchListener listener;

    @Test
    @DisplayName("commit 이후 unread 증가와 Redis publish를 수행한다")
    void handle_dispatchesUnreadCountAndRedisPublish() {
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
    @DisplayName("Redis 후속 처리 실패는 로깅만 하고 예외를 전파하지 않는다")
    void handle_swallowsRedisDispatchFailure() {
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
