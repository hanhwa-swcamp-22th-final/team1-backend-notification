package com.conk.notification.common.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

// 외부 HTTP 호출에 공통으로 사용할 RestTemplate Bean을 등록한다.
@Configuration
public class HttpClientConfig {

    /**
     * member-service 호출에 사용할 RestTemplate을 생성한다.
     *
     * connect timeout과 read timeout을 명시해
     * Kafka listener 스레드가 외부 API 장애로 장시간 묶이지 않도록 한다.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
