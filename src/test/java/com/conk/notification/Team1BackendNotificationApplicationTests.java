package com.conk.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class Team1BackendNotificationApplicationTests {

    /**
     * 실제 Redis 서버 없이 컨텍스트 로딩 가능하도록 RedisConnectionFactory를 Mock으로 교체한다.
     * Spring Boot auto-configuration은 @ConditionalOnMissingBean(RedisConnectionFactory.class)을 사용하므로
     * Mock Bean이 등록되면 Lettuce 연결 시도 자체가 일어나지 않는다.
     */
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
    }

}
