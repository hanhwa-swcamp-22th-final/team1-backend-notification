package com.conk.notification.common.exception;

// 재시도해도 성공 가능성이 낮은 Kafka 처리 실패를 표현한다.
// 예: JSON 역직렬화 실패, 필수 필드 누락, 계약과 다른 payload
public class NonRetryableKafkaException extends BusinessException {

    public NonRetryableKafkaException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }
}
