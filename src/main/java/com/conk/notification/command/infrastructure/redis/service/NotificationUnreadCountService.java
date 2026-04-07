package com.conk.notification.command.infrastructure.redis.service;

import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 읽지 않은 알림 카운트 관리 서비스
 *
 * Redis의 String 타입을 카운터로 활용하여
 * 각 사용자의 읽지 않은 알림 수를 관리한다.
 *
 * Redis 키 구조:
 *   "notification:unread:{accountId}"
 *   예: "notification:unread:1001" → "5"
 *
 * DB 조회 없이 Redis에서 O(1)로 카운트를 읽을 수 있어
 * 프론트엔드 뱃지 숫자 표시에 매우 효율적이다.
 *
 * Redis INCR/DECR 명령어 특징:
 *   - 원자적(atomic) 연산: 동시에 여러 스레드가 increment해도 안전하게 처리됨
 *   - 키가 없으면 0으로 초기화 후 증가 (별도 초기화 불필요)
 */
@Service
public class NotificationUnreadCountService {

    private static final Logger log = LoggerFactory.getLogger(NotificationUnreadCountService.class);

    /** Redis 키 접두사 */
    private static final String KEY_PREFIX = "notification:unread:";

    private final StringRedisTemplate stringRedisTemplate;

    public NotificationUnreadCountService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 읽지 않은 알림 카운트를 1 증가시킨다.
     *
     * 새 알림이 저장될 때마다 호출된다.
     * Redis INCR 명령 사용: 원자적으로 +1
     * 키가 존재하지 않으면 0에서 시작해 1이 된다.
     *
     * @param accountId 카운트를 증가시킬 사용자의 accountId
     * @return 증가 후의 카운트 값
     */
    public long increment(String accountId) {
        String key = KEY_PREFIX + accountId; // "notification:unread:1001"

        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            long result = count != null ? count : 0L;
            log.debug("[읽지 않은 알림 증가] accountId={}, count={}", accountId, result);
            return result;

        } catch (Exception e) {
            // AFTER_COMMIT 후속 처리에서 사용할 예외이므로 상위 리스너가 로깅 정책을 결정하도록 넘긴다.
            throw new BusinessException(
                    ErrorCode.REDIS_DISPATCH_FAILED,
                    "Redis unread 증가 실패 accountId=%s".formatted(accountId)
            );
        }
    }

    /**
     * 읽지 않은 알림 카운트를 조회한다.
     *
     * 프론트엔드에서 뱃지 숫자를 표시할 때 호출된다.
     * GET /notifications/unread-count 엔드포인트에서 사용 예정.
     *
     * @param accountId 조회할 사용자의 accountId
     * @return 읽지 않은 알림 수 (Redis 키가 없으면 0 반환)
     */
    public long getCount(String accountId) {
        String key = KEY_PREFIX + accountId;

        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0L;

        } catch (Exception e) {
            log.error("[Redis 카운트 조회 실패] accountId={}, 오류={}", accountId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 읽지 않은 알림 카운트를 0으로 초기화한다.
     *
     * 사용자가 알림 목록을 확인했을 때 호출된다.
     * Redis 키를 삭제하면 다음 getCount() 호출 시 0이 반환된다.
     *
     * @param accountId 초기화할 사용자의 accountId
     */
    public void reset(String accountId) {
        String key = KEY_PREFIX + accountId;

        try {
            // delete(): Redis DEL 명령 → 키 삭제
            stringRedisTemplate.delete(key);
            log.info("[읽지 않은 알림 초기화] accountId={}", accountId);

        } catch (Exception e) {
            log.error("[Redis 카운트 초기화 실패] accountId={}, 오류={}", accountId, e.getMessage());
        }
    }
}
