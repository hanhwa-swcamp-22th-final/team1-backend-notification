package com.conk.notification.command.infrastructure.redis.subscriber;

import com.conk.notification.command.application.dto.SseNotificationPayload;
import com.conk.notification.common.sse.SseEmitterManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Redis Pub/Sub 구독자 (Subscriber)
 *
 * Redis 채널에 발행된 메시지를 수신하여 해당 사용자의 SSE 연결로 push하는 역할이다.
 * RedisConfig에서 "notification:user:*" 패턴을 이 클래스에 등록한다.
 *
 * MessageListener 인터페이스:
 *   Spring Data Redis가 제공하는 Pub/Sub 구독자 인터페이스.
 *   onMessage() 메서드를 구현하면 Redis에 메시지가 도착할 때 자동으로 호출된다.
 *
 * 동작 시점:
 *   Redis PUBLISH "notification:user:1001" "{...json...}"
 *       → onMessage() 자동 호출
 *       → accountId="1001" 추출
 *       → SseEmitterManager에서 해당 SseEmitter 조회
 *       → SseEmitter.send() 로 브라우저에 push
 */
@Component
public class RedisNotificationSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationSubscriber.class);

    private final SseEmitterManager sseEmitterManager;
    private final ObjectMapper objectMapper;

    public RedisNotificationSubscriber(SseEmitterManager sseEmitterManager, ObjectMapper objectMapper) {
        this.sseEmitterManager = sseEmitterManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Redis 채널에 메시지가 도착했을 때 자동으로 호출된다.
     *
     * @param message Redis 메시지 객체
     *                - message.getBody(): 메시지 본문 (byte[])
     *                - message.getChannel(): 발행된 채널 이름 (byte[])
     * @param pattern 구독 패턴 (byte[]) - "notification:user:*" 패턴에 매칭된 실제 채널명
     *                패턴 구독 시 message.getChannel()이 아닌 이 값을 사용해야 정확한 채널명을 얻을 수 있다.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        // byte[] → String 변환
        String channel = new String(message.getChannel()); // 예: "notification:user:1001"
        String payload = new String(message.getBody());    // JSON 문자열

        log.info("[Redis Subscribe] channel={}", channel);

        // 채널명에서 accountId 추출
        // "notification:user:1001" → "1001"
        // "notification:user:" 접두사(19자)를 제거
        String accountId = channel.replace("notification:user:", "");

        // 이 인스턴스에 해당 사용자의 SSE 연결이 있는지 확인
        SseEmitter emitter = sseEmitterManager.get(accountId);

        if (emitter == null) {
            // 이 인스턴스에 해당 사용자의 SSE 연결 없음 → 다른 인스턴스가 처리할 것
            log.debug("[SSE 연결 없음] accountId={} 는 이 인스턴스에 연결되어 있지 않음", accountId);
            return;
        }

        try {
            // JSON 문자열 → SseNotificationPayload 역직렬화
            SseNotificationPayload notificationPayload =
                    objectMapper.readValue(payload, SseNotificationPayload.class);

            // SseEmitter를 통해 브라우저에 이벤트 전송
            // SseEmitter.event(): SSE 이벤트 빌더
            //   .name("notification"): 이벤트 타입명 (프론트에서 addEventListener("notification", ...)로 수신)
            //   .data(object): 전송할 데이터 (자동으로 JSON 직렬화됨)
            emitter.send(
                    SseEmitter.event()
                            .name("notification")       // 이벤트 이름 (프론트에서 구분용)
                            .data(notificationPayload)  // 전송 데이터
            );

            log.info("[SSE Push 완료] accountId={}, type={}", accountId, notificationPayload.getType());

        } catch (IOException e) {
            // 클라이언트가 이미 연결을 끊은 경우 IOException 발생
            log.warn("[SSE Push 실패] accountId={}, 오류={}", accountId, e.getMessage());
            // 죽은 emitter를 Map에서 제거
            sseEmitterManager.remove(accountId);
        }
    }
}
