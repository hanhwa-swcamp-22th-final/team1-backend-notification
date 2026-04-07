package com.conk.notification.common.exception;

// 잠시 후 재시도하면 회복될 수 있는 Kafka 처리 실패를 표현한다.
// 예: member-service 장애, 일시적인 Redis 장애, 외부 시스템 timeout
public class RetryableKafkaException extends BusinessException {

    public RetryableKafkaException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }
}
