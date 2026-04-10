package com.conk.notification.command.infrastructure.redis.subscriber;

import com.conk.notification.command.application.dto.SseNotificationPayload;
import com.conk.notification.common.sse.SseEmitterManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisNotificationSubscriber 단위 테스트")
class RedisNotificationSubscriberTest {

    @Mock
    private SseEmitterManager sseEmitterManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SseEmitter emitter;

    @InjectMocks
    private RedisNotificationSubscriber redisNotificationSubscriber;

    @Test
    @DisplayName("SSE 연결이 없으면 아무 작업도 하지 않는다")
    void onMessage_doesNothing_whenEmitterDoesNotExist() {
        given(sseEmitterManager.get("1001")).willReturn(null);

        redisNotificationSubscriber.onMessage(message("notification:user:1001", "{\"type\":\"TASK_ASSIGNED\"}"), null);

        then(objectMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("SSE 연결이 있으면 notification 이벤트를 전송한다")
    void onMessage_sendsNotificationEvent_whenEmitterExists() throws Exception {
        SseNotificationPayload payload = new SseNotificationPayload(
                "notification-1",
                "TASK_ASSIGNED",
                "작업 배정",
                "작업 3건이 배정되었습니다.",
                5L
        );
        given(sseEmitterManager.get("1001")).willReturn(emitter);
        given(objectMapper.readValue("{\"type\":\"TASK_ASSIGNED\"}", SseNotificationPayload.class)).willReturn(payload);

        redisNotificationSubscriber.onMessage(message("notification:user:1001", "{\"type\":\"TASK_ASSIGNED\"}"), null);

        then(emitter).should().send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("SSE 전송 중 IOException이 발생하면 emitter를 제거한다")
    void onMessage_removesEmitter_whenSendThrowsIOException() throws Exception {
        SseNotificationPayload payload = new SseNotificationPayload(
                "notification-1",
                "TASK_ASSIGNED",
                "작업 배정",
                "작업 3건이 배정되었습니다.",
                5L
        );
        given(sseEmitterManager.get("1001")).willReturn(emitter);
        given(objectMapper.readValue("{\"type\":\"TASK_ASSIGNED\"}", SseNotificationPayload.class)).willReturn(payload);
        willThrow(new IOException("connection closed")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));

        redisNotificationSubscriber.onMessage(message("notification:user:1001", "{\"type\":\"TASK_ASSIGNED\"}"), null);

        then(sseEmitterManager).should().remove("1001");
    }

    private DefaultMessage message(String channel, String payload) {
        return new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }
}
