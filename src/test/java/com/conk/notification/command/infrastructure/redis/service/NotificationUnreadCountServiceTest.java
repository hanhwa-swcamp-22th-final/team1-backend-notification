package com.conk.notification.command.infrastructure.redis.service;

import com.conk.notification.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationUnreadCountService 단위 테스트")
class NotificationUnreadCountServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private NotificationUnreadCountService unreadCountService;

    // =============================================
    // increment()
    // =============================================

    @Test
    @DisplayName("increment - Redis INCR 결과를 그대로 반환한다")
    void increment_returnsIncrementedCount() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("notification:unread:1001")).willReturn(5L);

        long result = unreadCountService.increment("1001");

        assertThat(result).isEqualTo(5L);
    }

    @Test
    @DisplayName("increment - Redis가 null을 반환하면 0을 반환한다")
    void increment_returnsZero_whenRedisReturnsNull() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("notification:unread:1001")).willReturn(null);

        long result = unreadCountService.increment("1001");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("increment - Redis 장애 시 BusinessException을 던진다")
    void increment_throwsBusinessException_whenRedisThrows() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("notification:unread:1001"))
                .willThrow(new RuntimeException("Redis connection failed"));

        assertThatThrownBy(() -> unreadCountService.increment("1001"))
                .isInstanceOf(BusinessException.class);
    }

    // =============================================
    // getCount()
    // =============================================

    @Test
    @DisplayName("getCount - 키가 존재하면 파싱된 카운트를 반환한다")
    void getCount_returnsCount_whenKeyExists() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001")).willReturn("3");

        long result = unreadCountService.getCount("1001");

        assertThat(result).isEqualTo(3L);
    }

    @Test
    @DisplayName("getCount - 키가 없으면 0을 반환한다")
    void getCount_returnsZero_whenKeyNotExists() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001")).willReturn(null);

        long result = unreadCountService.getCount("1001");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("getCount - Redis 장애 시 0을 반환하고 예외를 전파하지 않는다")
    void getCount_returnsZero_whenRedisThrows() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001"))
                .willThrow(new RuntimeException("Redis timeout"));

        long result = unreadCountService.getCount("1001");

        assertThat(result).isEqualTo(0L);
    }

    // =============================================
    // reset()
    // =============================================

    @Test
    @DisplayName("reset - Redis DEL 명령을 호출한다")
    void reset_deletesRedisKey() {
        unreadCountService.reset("1001");

        then(stringRedisTemplate).should().delete("notification:unread:1001");
    }

    @Test
    @DisplayName("reset - Redis 장애 시 예외를 전파하지 않는다")
    void reset_doesNotThrow_whenRedisThrows() {
        doThrow(new RuntimeException("Redis timeout"))
                .when(stringRedisTemplate).delete("notification:unread:1001");

        // 예외가 전파되지 않아야 한다
        unreadCountService.reset("1001");
    }
}
