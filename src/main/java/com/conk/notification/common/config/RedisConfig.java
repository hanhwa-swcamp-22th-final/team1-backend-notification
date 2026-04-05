package com.conk.notification.common.config;

import com.conk.notification.command.infrastructure.redis.subscriber.RedisNotificationSubscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis 설정 클래스
 *
 * Redis를 사용하기 위한 Bean들을 등록한다.
 * application.properties의 spring.data.redis.* 설정값은
 * Spring Boot Auto Configuration이 자동으로 읽어 RedisConnectionFactory를 생성하므로
 * 별도의 ConnectionFactory 설정은 필요 없다.
 *
 * 등록하는 Bean:
 *   1. ObjectMapper         - JSON 직렬화/역직렬화 공용 객체
 *   2. StringRedisTemplate  - Redis String 연산 (INCR, GET, DEL, PUBLISH)
 *   3. MessageListenerAdapter - RedisNotificationSubscriber를 MessageListener로 래핑
 *   4. RedisMessageListenerContainer - Pub/Sub 구독 채널 등록 및 스레드 관리
 */
@Configuration
public class RedisConfig {

    /**
     * ObjectMapper Bean 등록
     *
     * JSON 변환에 사용하는 Jackson ObjectMapper를 싱글톤 Bean으로 등록한다.
     * NotificationRedisPublisher, RedisNotificationSubscriber 등 여러 클래스에서 공유한다.
     *
     * JavaTimeModule: LocalDateTime 등 Java 8 날짜/시간 타입을 JSON으로 처리하기 위해 필요
     * WRITE_DATES_AS_TIMESTAMPS = false: 날짜를 타임스탬프(숫자) 대신
     *   "2026-04-05T10:00:00" 형태 문자열로 직렬화
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * StringRedisTemplate Bean 등록
     *
     * Redis에 String 타입 데이터를 쓰고 읽는 클라이언트.
     * RedisConnectionFactory는 Spring Boot Auto Configuration이 자동 생성한 것을 주입받는다.
     *
     * StringRedisTemplate vs RedisTemplate:
     *   - StringRedisTemplate: key/value가 모두 String인 경우에 특화. 더 단순하고 가볍다.
     *   - RedisTemplate: 다양한 타입(Object, List 등)을 저장할 때 사용. 직렬화 설정 필요.
     *   → 이 프로젝트에서는 String(JSON) 위주로 사용하므로 StringRedisTemplate으로 충분하다.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * MessageListenerAdapter Bean 등록
     *
     * RedisNotificationSubscriber(우리가 만든 구독자)를 Spring의 MessageListener 인터페이스로 래핑한다.
     * "onMessage"는 메시지 수신 시 호출할 메서드 이름으로,
     * RedisNotificationSubscriber.onMessage()를 가리킨다.
     *
     * 왜 Adapter를 쓰는가?
     *   RedisMessageListenerContainer에 등록하기 위해
     *   MessageListener 인터페이스 구현이 필요하다.
     *   Adapter 패턴으로 기존 클래스를 변경 없이 인터페이스에 맞게 감싼다.
     */
    @Bean
    public MessageListenerAdapter messageListenerAdapter(RedisNotificationSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    /**
     * RedisMessageListenerContainer Bean 등록
     *
     * Redis Pub/Sub 구독을 관리하는 컨테이너.
     * 별도 스레드에서 Redis 채널을 상시 감시하다가 메시지가 오면 MessageListener에게 전달한다.
     *
     * PatternTopic("notification:user:*"):
     *   와일드카드(*)를 사용하여 "notification:user:1001", "notification:user:1002" 등
     *   모든 사용자 채널을 한 번에 구독한다.
     *   특정 사용자가 연결할 때마다 채널을 추가 등록할 필요가 없어서 효율적이다.
     *
     * addMessageListener(listener, topic):
     *   - listener: 메시지를 처리할 MessageListener (위에서 등록한 Adapter)
     *   - topic: 구독할 채널 패턴
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();

        // Redis 서버 연결 설정 주입
        container.setConnectionFactory(connectionFactory);

        // "notification:user:*" 패턴의 모든 채널 구독 등록
        // Publisher가 "notification:user:1001"에 PUBLISH하면
        // 이 Container가 수신하여 messageListenerAdapter.onMessage()를 호출한다
        container.addMessageListener(
                messageListenerAdapter,
                new PatternTopic("notification:user:*") // 와일드카드 패턴 구독
        );

        return container;
    }
}
