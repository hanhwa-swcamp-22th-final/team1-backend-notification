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

import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
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
    @DisplayName("Redis INCR 결과를 그대로 반환한다")
    void increment_returnsIncrementedCount() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("notification:unread:1001")).willReturn(5L);

        long result = unreadCountService.increment("1001");

        assertThat(result).isEqualTo(5L);
    }

    @Test
    @DisplayName("Redis가 null을 반환하면 0을 반환한다")
    void increment_returnsZero_whenRedisReturnsNull() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("notification:unread:1001")).willReturn(null);

        long result = unreadCountService.increment("1001");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("Redis 장애 시 BusinessException을 던진다")
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
    @DisplayName("키가 존재하면 파싱된 카운트를 반환한다")
    void getCount_returnsCount_whenKeyExists() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001")).willReturn("3");

        long result = unreadCountService.getCount("1001");

        assertThat(result).isEqualTo(3L);
    }

    @Test
    @DisplayName("키가 없으면 0을 반환한다")
    void getCount_returnsZero_whenKeyNotExists() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001")).willReturn(null);

        long result = unreadCountService.getCount("1001");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("Redis 장애 시 0을 반환하고 예외를 전파하지 않는다")
    void getCount_returnsZero_whenRedisThrows() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001"))
                .willThrow(new RuntimeException("Redis timeout"));

        long result = unreadCountService.getCount("1001");

        assertThat(result).isEqualTo(0L);
    }

    // =============================================
    // getCachedCount()
    // =============================================

    @Test
    @DisplayName("캐시된 값이 있으면 파싱된 카운트를 반환한다")
    void getCachedCount_returnsParsedCount_whenCacheExists() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001")).willReturn("7");

        Long result = unreadCountService.getCachedCount("1001");

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("캐시된 값이 없으면 null을 반환한다")
    void getCachedCount_returnsNull_whenCacheDoesNotExist() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001")).willReturn(null);

        Long result = unreadCountService.getCachedCount("1001");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("캐시 조회 중 예외가 발생하면 null을 반환한다")
    void getCachedCount_returnsNull_whenRedisThrows() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001"))
                .willThrow(new RuntimeException("Redis timeout"));

        Long result = unreadCountService.getCachedCount("1001");

        assertThat(result).isNull();
    }

    // =============================================
    // getOrInitialize()
    // =============================================

    @Test
    @DisplayName("캐시된 값이 있으면 DB를 조회하지 않고 그대로 반환한다")
    void getOrInitialize_returnsCachedCount_whenCacheExists() {
        LongSupplier dbCountSupplier = mock(LongSupplier.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001")).willReturn("9");

        long result = unreadCountService.getOrInitialize("1001", dbCountSupplier);

        assertThat(result).isEqualTo(9L);
        then(dbCountSupplier).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("캐시된 값이 없으면 DB 값을 저장한 뒤 반환한다")
    void getOrInitialize_setsDbCount_whenCacheDoesNotExist() {
        LongSupplier dbCountSupplier = mock(LongSupplier.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("notification:unread:1001")).willReturn(null);
        given(dbCountSupplier.getAsLong()).willReturn(6L);

        long result = unreadCountService.getOrInitialize("1001", dbCountSupplier);

        assertThat(result).isEqualTo(6L);
        then(valueOperations).should().set("notification:unread:1001", "6");
    }

    // =============================================
    // setCount()
    // =============================================

    @Test
    @DisplayName("양수 카운트는 Redis에 저장한다")
    void setCount_storesCount_whenCountIsPositive() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

        unreadCountService.setCount("1001", 3L);

        then(valueOperations).should().set("notification:unread:1001", "3");
    }

    @Test
    @DisplayName("0 이하 카운트는 Redis 키를 삭제한다")
    void setCount_deletesKey_whenCountIsZeroOrLess() {
        unreadCountService.setCount("1001", 0L);

        then(stringRedisTemplate).should().delete("notification:unread:1001");
    }

    @Test
    @DisplayName("카운트 저장 중 예외가 발생해도 예외를 전파하지 않는다")
    void setCount_doesNotThrow_whenRedisThrows() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        doThrow(new RuntimeException("Redis timeout"))
                .when(valueOperations).set("notification:unread:1001", "3");

        assertThatCode(() -> unreadCountService.setCount("1001", 3L))
                .doesNotThrowAnyException();
    }

    // =============================================
    // reset()
    // =============================================

    @Test
    @DisplayName("Redis DEL 명령을 호출한다")
    void reset_deletesRedisKey() {
        unreadCountService.reset("1001");

        then(stringRedisTemplate).should().delete("notification:unread:1001");
    }

    @Test
    @DisplayName("Redis 장애 시 예외를 전파하지 않는다")
    void reset_doesNotThrow_whenRedisThrows() {
        doThrow(new RuntimeException("Redis timeout"))
                .when(stringRedisTemplate).delete("notification:unread:1001");

        // 예외가 전파되지 않아야 한다
        unreadCountService.reset("1001");
    }
}
