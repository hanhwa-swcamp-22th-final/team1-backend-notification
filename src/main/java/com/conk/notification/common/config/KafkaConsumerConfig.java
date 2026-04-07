package com.conk.notification.common.config;

import com.conk.notification.common.exception.NonRetryableKafkaException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer 설정 클래스
 *
 * @Configuration: 이 클래스가 Spring Bean 설정 파일임을 나타낸다.
 * @EnableKafka: @KafkaListener 어노테이션이 동작하도록 Kafka 리스너 기능을 활성화한다.
 *               이 어노테이션이 없으면 @KafkaListener가 무시된다.
 *
 * Kafka Consumer 동작 구조:
 *   ConsumerFactory → ConsumerFactory로 Consumer 인스턴스 생성
 *   ConcurrentKafkaListenerContainerFactory → @KafkaListener 메서드에 컨테이너를 연결
 *   @KafkaListener → 실제 메시지를 처리하는 메서드
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    // application.properties에서 주입받는 Kafka 브로커 주소
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // application.properties에서 주입받는 Consumer 그룹 ID
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.listener.concurrency:3}")
    private int concurrency;

    /**
     * Kafka Consumer 설정을 담은 Map을 반환한다.
     *
     * 주요 설정:
     * - BOOTSTRAP_SERVERS_CONFIG: Kafka 브로커 접속 주소
     * - GROUP_ID_CONFIG: Consumer 그룹 ID
     *   같은 그룹의 Consumer들은 토픽의 파티션을 나눠서 처리한다.
     *   notification-service는 단독 서비스이므로 고유한 그룹명을 사용한다.
     * - AUTO_OFFSET_RESET_CONFIG: "earliest" = 처음부터 읽기
     *   Consumer가 처음 시작하거나 offset 정보가 없을 때 어디서부터 읽을지 결정한다.
     * - KEY_DESERIALIZER_CLASS_CONFIG / VALUE_DESERIALIZER_CLASS_CONFIG:
     *   Kafka 메시지는 bytes 형태로 전달된다. Java 문자열로 변환하는 역직렬화기를 지정한다.
     *   여기서는 key/value 모두 String으로 받고, Consumer에서 ObjectMapper로 JSON 변환한다.
     */
    private Map<String, Object> consumerConfigs() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return config;
    }

    /**
     * ConsumerFactory Bean 등록
     *
     * ConsumerFactory: 실제 Kafka Consumer 인스턴스를 생성하는 팩토리.
     * ConcurrentKafkaListenerContainerFactory가 이 팩토리를 사용해 Consumer를 만든다.
     *
     * @return Kafka Consumer 생성용 팩토리
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    /**
     * KafkaListenerContainerFactory Bean 등록
     *
     * @KafkaListener 어노테이션이 붙은 메서드들이 이 팩토리를 통해 동작한다.
     * "kafkaListenerContainerFactory"라는 이름으로 등록되며,
     * @KafkaListener의 containerFactory 속성에서 이 이름을 참조한다.
     *
     * setConcurrency(3): 동시에 처리할 Consumer 스레드 수.
     *   토픽의 파티션 수 이하로 설정하는 것이 일반적이다.
     *   지금은 1개 파티션을 가정하므로 1로 설정해도 충분하지만, 확장성을 고려해 3으로 설정.
     *
     * AckMode.RECORD: 메시지 하나를 처리할 때마다 offset을 커밋(확인).
     *   처리 성공 시 offset이 저장되어, 서비스가 재시작해도 처리한 메시지는 다시 읽지 않는다.
     *
     * @return Kafka 리스너 컨테이너 팩토리
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler());

        // 동시에 처리할 스레드 수 (파티션 수와 맞추는 것이 좋음)
        factory.setConcurrency(concurrency);

        // 메시지 1건 처리 완료 시마다 offset 커밋
        // (서비스 재시작 시 이미 처리한 메시지는 다시 소비하지 않음)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        return factory;
    }

    /**
     * Kafka 공통 에러 핸들러.
     *
     * 재시도 정책:
     * - 기본: 1초 -> 2초 -> 4초 backoff로 최대 3회 재시도
     * - NonRetryableKafkaException: 즉시 재시도 중단
     *
     * member-service 장애나 일시적 DB 오류는 재시도 대상으로 두고,
     * 잘못된 payload는 즉시 포기하도록 분리한다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "[Kafka 처리 최종 실패] topic={}, partition={}, offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        ex
                ),
                backOff
        );
        handler.addNotRetryableExceptions(NonRetryableKafkaException.class);
        return handler;
    }
}
