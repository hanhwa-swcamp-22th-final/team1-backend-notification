package com.conk.notification.query.controller;

import com.conk.notification.command.infrastructure.redis.service.NotificationUnreadCountService;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import com.conk.notification.common.sse.SseEmitterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE(Server-Sent Events) 구독 컨트롤러
 *
 * 프론트엔드가 이 엔드포인트에 연결하면 서버는 HTTP 응답을 닫지 않고
 * 실시간으로 알림 데이터를 push할 수 있다.
 *
 * SSE vs WebSocket:
 *   - SSE: 서버 → 클라이언트 단방향. HTTP 기반. 구현 단순.
 *   - WebSocket: 양방향. 별도 프로토콜. 구현 복잡.
 *   → 알림은 서버 → 클라이언트 단방향이므로 SSE가 적합하다.
 *
 * 엔드포인트:
 *   GET /notifications/sse/subscribe
 *   헤더: X-User-Id (nginx가 JWT 파싱 후 주입)
 *   응답 타입: text/event-stream (SSE 표준 MIME 타입)
 */
@RestController
@RequestMapping("/notifications")
public class NotificationSseController {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseController.class);

    private final SseEmitterManager sseEmitterManager;
    private final NotificationUnreadCountService unreadCountService;

    /**
     * SSE 연결 유지 시간 (밀리초)
     * application.properties의 notification.sse.timeout 값을 주입받는다.
     * 이 시간이 지나면 클라이언트가 자동으로 재연결을 시도한다.
     */
    @Value("${notification.sse.timeout}")
    private long sseTimeout;

    public NotificationSseController(
            SseEmitterManager sseEmitterManager,
            NotificationUnreadCountService unreadCountService
    ) {
        this.sseEmitterManager = sseEmitterManager;
        this.unreadCountService = unreadCountService;
    }

    /**
     * SSE 구독 엔드포인트
     *
     * 프론트엔드 연결 방법 (Vue.js 예시):
     *   const eventSource = new EventSource('/notifications/sse/subscribe', {
     *       headers: { 'X-User-Id': accountId }
     *   });
     *   eventSource.addEventListener('notification', (e) => {
     *       const data = JSON.parse(e.data);
     *       // 알림 표시 처리
     *   });
     *
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE:
     *   응답의 Content-Type을 "text/event-stream"으로 설정한다.
     *   브라우저가 이 Content-Type을 보면 SSE 연결임을 인식하고 연결을 유지한다.
     *
     * @param accountId nginx가 JWT 파싱 후 주입하는 사용자 ID 헤더
     *                  (개발 중에는 쿼리 파라미터로 대체 가능: ?accountId=1001)
     * @return SseEmitter - 연결 유지 객체 (Spring이 응답을 자동으로 스트리밍 모드로 처리)
     */
    @GetMapping(value = "/sse/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestParam(value = "accountId", required = false) String accountIdParam
    ) {
        // nginx auth_request가 미구현된 개발 환경에서는 쿼리 파라미터로 대체
        String resolvedAccountId = (accountId != null) ? accountId : accountIdParam;

        if (resolvedAccountId == null || resolvedAccountId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.MISSING_ACCOUNT_ID,
                    "accountId가 필요합니다. (헤더 X-User-Id 또는 쿼리 파라미터 accountId)"
            );
        }

        log.info("[SSE 구독 요청] accountId={}", resolvedAccountId);

        // SseEmitter 생성: sseTimeout 시간 동안 연결을 유지한다
        SseEmitter emitter = new SseEmitter(sseTimeout);

        // SseEmitterManager에 등록 (Redis Subscriber가 이 Map을 통해 emitter를 찾음)
        sseEmitterManager.register(resolvedAccountId, emitter);

        // 연결 직후 초기 이벤트 전송
        // 이유: SSE는 연결 후 첫 데이터가 올 때까지 브라우저가 연결 여부를 확인하기 어렵다.
        //       즉시 "connected" 이벤트를 보내 연결이 성공했음을 확인시킨다.
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("connected")  // 이벤트 타입 이름
                            .data("SSE 연결 성공. accountId=" + resolvedAccountId)
            );
        } catch (IOException e) {
            log.error("[SSE 초기 이벤트 전송 실패] accountId={}", resolvedAccountId);
            sseEmitterManager.remove(resolvedAccountId);
        }

        return emitter;
    }

    /**
     * 읽지 않은 알림 카운트 조회 엔드포인트
     *
     * 프론트엔드가 페이지 로드 시 현재 읽지 않은 알림 수를 조회하기 위해 사용한다.
     * Redis에서 O(1)으로 빠르게 조회한다.
     *
     * GET /notifications/unread-count
     * 헤더: X-User-Id (nginx 주입)
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestParam(value = "accountId", required = false) String accountIdParam
    ) {
        String resolvedAccountId = (accountId != null) ? accountId : accountIdParam;

        if (resolvedAccountId == null || resolvedAccountId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.MISSING_ACCOUNT_ID,
                    "accountId가 필요합니다. (헤더 X-User-Id 또는 쿼리 파라미터 accountId)"
            );
        }

        long count = unreadCountService.getCount(resolvedAccountId);
        return ResponseEntity.ok(count);
    }

    /**
     * 읽지 않은 알림 카운트 초기화 엔드포인트
     *
     * 사용자가 알림 목록을 열었을 때 호출하여 뱃지 숫자를 0으로 리셋한다.
     *
     * DELETE /notifications/unread-count
     * 헤더: X-User-Id (nginx 주입)
     */
    @DeleteMapping("/unread-count")
    public ResponseEntity<Void> resetUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestParam(value = "accountId", required = false) String accountIdParam
    ) {
        String resolvedAccountId = (accountId != null) ? accountId : accountIdParam;

        if (resolvedAccountId == null || resolvedAccountId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.MISSING_ACCOUNT_ID,
                    "accountId가 필요합니다. (헤더 X-User-Id 또는 쿼리 파라미터 accountId)"
            );
        }

        unreadCountService.reset(resolvedAccountId);
        return ResponseEntity.noContent().build();
    }
}
