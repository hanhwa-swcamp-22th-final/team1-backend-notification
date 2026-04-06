package com.conk.notification.common.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE Emitter 관리자
 *
 * 현재 서버에 연결되어 있는 모든 SSE 클라이언트(브라우저)를 관리한다.
 * accountId를 키로, SseEmitter 객체를 값으로 보관하는 Map 구조이다.
 *
 * SseEmitter란?
 *   Spring이 제공하는 SSE(Server-Sent Events) 전송 객체.
 *   클라이언트가 GET /notifications/sse/subscribe를 호출하면
 *   서버는 HTTP 응답을 즉시 닫지 않고 SseEmitter를 통해 연결을 유지하며
 *   필요할 때 데이터를 push할 수 있다.
 *
 * ConcurrentHashMap을 사용하는 이유:
 *   Kafka Consumer 스레드와 HTTP 요청 스레드가 동시에 이 Map에 접근할 수 있다.
 *   일반 HashMap은 동시 접근 시 데이터가 손상될 수 있으므로
 *   스레드 안전한(thread-safe) ConcurrentHashMap을 사용한다.
 *
 * 싱글 인스턴스 환경에서는 이것만으로 충분하다.
 * 멀티 인스턴스 환경에서는 Redis Pub/Sub이 인스턴스 간 브로드캐스트를 담당한다.
 */
@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);

    /**
     * accountId → SseEmitter 매핑
     * Key: accountId (사용자 식별자)
     * Value: 해당 사용자의 SSE 연결 객체
     *
     * 한 사용자가 여러 탭을 열면 마지막 연결만 유지된다.
     * (멀티 탭 지원이 필요하면 Map<String, List<SseEmitter>>로 변경 필요)
     */
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * SSE 연결을 등록한다.
     *
     * 클라이언트가 /notifications/sse/subscribe에 접속했을 때 호출된다.
     * 기존 연결이 있으면 새 연결로 교체한다.
     *
     * @param accountId 연결한 사용자의 accountId
     * @param emitter   생성된 SseEmitter 객체
     */
    public void register(String accountId, SseEmitter emitter) {
        // 기존 연결이 있으면 먼저 완료 처리 후 교체
        SseEmitter existing = emitters.get(accountId);
        if (existing != null) {
            existing.complete();
        }

        emitters.put(accountId, emitter);
        log.info("[SSE 등록] accountId={}, 현재 연결 수={}", accountId, emitters.size());

        // SSE 연결이 끊기거나 타임아웃 발생 시 Map에서 자동 제거
        // 이 콜백들을 등록하지 않으면 연결이 끊겨도 Map에 죽은 emitter가 남아있게 됨
        emitter.onCompletion(() -> remove(accountId));
        emitter.onTimeout(() -> {
            log.info("[SSE 타임아웃] accountId={}", accountId);
            remove(accountId);
        });
        emitter.onError(e -> {
            log.warn("[SSE 오류] accountId={}, error={}", accountId, e.getMessage());
            remove(accountId);
        });
    }

    /**
     * SSE 연결을 제거한다.
     *
     * 연결 완료, 타임아웃, 오류 발생 시 자동으로 호출된다.
     *
     * @param accountId 제거할 사용자의 accountId
     */
    public void remove(String accountId) {
        emitters.remove(accountId);
        log.info("[SSE 제거] accountId={}, 남은 연결 수={}", accountId, emitters.size());
    }

    /**
     * 특정 사용자의 SseEmitter를 조회한다.
     *
     * Redis Subscriber가 메시지를 받았을 때 호출하여
     * 해당 사용자의 SSE 연결로 데이터를 push하기 위해 사용한다.
     *
     * @param accountId 조회할 사용자의 accountId
     * @return SseEmitter 객체. 해당 사용자가 연결되어 있지 않으면 null 반환.
     */
    public SseEmitter get(String accountId) {
        return emitters.get(accountId);
    }

    /**
     * 현재 연결된 사용자 수를 반환한다. (모니터링/디버깅용)
     */
    public int getConnectedCount() {
        return emitters.size();
    }
}
