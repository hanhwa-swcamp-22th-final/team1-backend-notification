package com.conk.notification.command.infrastructure.redis.publisher;

import com.conk.notification.command.application.dto.SseNotificationPayload;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 발행자 (Publisher)
 *
 * 알림이 DB에 정상 커밋된 직후, 해당 알림을 Redis 채널에 발행한다.
 * 이 메시지를 구독(Subscribe)하고 있는 모든 notification-service 인스턴스가
 * 메시지를 수신하여 해당 사용자의 SSE 연결로 push한다.
 *
 * 채널 네이밍 규칙:
 *   "notification:user:{accountId}"
 *   예: "notification:user:1001"
 *
 * 왜 Redis를 거치는가?
 *   notification-service가 여러 인스턴스로 실행될 때,
 *   사용자의 SSE 연결이 어떤 인스턴스에 있는지 알 수 없다.
 *   Redis Pub/Sub을 사용하면 모든 인스턴스에 메시지가 전달되고,
 *   해당 SSE 연결을 가진 인스턴스만 실제로 push한다.
 */
@Component
public class NotificationRedisPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationRedisPublisher.class);

    /**
     * Redis 채널 이름 접두사
     * 구독자(RedisNotificationSubscriber)의 패턴과 반드시 일치해야 한다.
     */
    private static final String CHANNEL_PREFIX = "notification:user:";

    /**
     * StringRedisTemplate: Redis에 문자열(String) 데이터를 쓰고 읽는 Spring 제공 클라이언트
     * convertAndSend(): 지정한 채널에 메시지를 발행(Publish)하는 메서드
     */
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationRedisPublisher(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 알림 데이터를 Redis 채널에 발행한다.
     *
     * 처리 흐름:
     * 1. SseNotificationPayload를 JSON 문자열로 직렬화
     * 2. "notification:user:{accountId}" 채널에 PUBLISH
     * 3. 해당 채널을 구독하는 모든 인스턴스의 RedisNotificationSubscriber가 수신
     *
     * @param accountId 알림 수신 대상의 accountId
     * @param payload   SSE로 전달할 알림 데이터
     */
    public void publish(String accountId, SseNotificationPayload payload) {
        String channel = CHANNEL_PREFIX + accountId; // "notification:user:1001"

        try {
            String message = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.convertAndSend(channel, message);

            log.info("[Redis Publish] channel={}, payload={}", channel, message);

        } catch (JsonProcessingException e) {
            // payload 직렬화 실패는 개발/계약 문제에 가깝기 때문에 BusinessException으로 전환한다.
            throw new BusinessException(
                    ErrorCode.REDIS_DISPATCH_FAILED,
                    "Redis publish 직렬화 실패 accountId=%s".formatted(accountId)
            );
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.REDIS_DISPATCH_FAILED,
                    "Redis publish 실패 accountId=%s".formatted(accountId)
            );
        }
    }
}
