package com.conk.notification.command.infrastructure.redis.publisher;

import com.conk.notification.command.application.dto.SseNotificationPayload;
import com.conk.notification.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRedisPublisher 단위 테스트")
class NotificationRedisPublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationRedisPublisher redisPublisher;

    private SseNotificationPayload samplePayload() {
        return new SseNotificationPayload(
                "notification-1",
                "TASK_ASSIGNED",
                "작업 배정",
                "작업 3건이 배정되었습니다.",
                5L
        );
    }

    @Test
    @DisplayName("정상 publish - 올바른 채널에 직렬화된 JSON을 전송한다")
    void publish_sendsSerializedPayloadToCorrectChannel() throws JsonProcessingException {
        SseNotificationPayload payload = samplePayload();
        given(objectMapper.writeValueAsString(payload)).willReturn("{\"notificationId\":\"notification-1\"}");

        redisPublisher.publish("1001", payload);

        then(stringRedisTemplate).should()
                .convertAndSend(eq("notification:user:1001"), eq("{\"notificationId\":\"notification-1\"}"));
    }

    @Test
    @DisplayName("JSON 직렬화 실패 시 BusinessException을 던진다")
    void publish_throwsBusinessException_whenSerializationFails() throws JsonProcessingException {
        SseNotificationPayload payload = samplePayload();
        given(objectMapper.writeValueAsString(payload))
                .willThrow(new JsonProcessingException("serialization error") {});

        assertThatThrownBy(() -> redisPublisher.publish("1001", payload))
                .isInstanceOf(BusinessException.class);

        then(stringRedisTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Redis 전송 실패 시 BusinessException을 던진다")
    void publish_throwsBusinessException_whenRedisThrows() throws JsonProcessingException {
        SseNotificationPayload payload = samplePayload();
        given(objectMapper.writeValueAsString(payload)).willReturn("{\"notificationId\":\"notification-1\"}");
        given(stringRedisTemplate.convertAndSend(anyString(), anyString()))
                .willThrow(new RuntimeException("Redis connection failed"));

        assertThatThrownBy(() -> redisPublisher.publish("1001", payload))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("채널 이름은 'notification:user:{accountId}' 형식이어야 한다")
    void publish_usesCorrectChannelNamingConvention() throws JsonProcessingException {
        SseNotificationPayload payload = samplePayload();
        given(objectMapper.writeValueAsString(payload)).willReturn("{}");

        redisPublisher.publish("user-abc-123", payload);

        then(stringRedisTemplate).should()
                .convertAndSend(eq("notification:user:user-abc-123"), anyString());
    }
}
