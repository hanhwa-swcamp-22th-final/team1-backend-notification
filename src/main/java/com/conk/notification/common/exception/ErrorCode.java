package com.conk.notification.common.exception;

import org.springframework.http.HttpStatus;

/**
 * notification-service 전반에서 사용하는 비즈니스 에러 코드와 메시지를 모아둔 enum이다.
 */
// 공통 비즈니스 에러 코드 모음.
// NTF-001~099: 요청 유효성 오류 (400)
// NTF-301~399: Kafka / 외부 연동 오류
// NTF-500~: 공통 시스템 오류
public enum ErrorCode {

    // 400 Bad Request — 요청 유효성
    MISSING_ACCOUNT_ID(HttpStatus.BAD_REQUEST, "NTF-001", "accountId가 필요합니다."),

    // 400 / 502 / 500 — Kafka / 외부 연동 오류
    INVALID_KAFKA_MESSAGE(HttpStatus.BAD_REQUEST, "NTF-301", "Kafka 메시지 형식이 올바르지 않습니다."),
    RECIPIENT_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY, "NTF-302", "알림 수신자 조회에 실패했습니다."),
    NOTIFICATION_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NTF-303", "알림 저장에 실패했습니다."),
    REDIS_DISPATCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NTF-304", "실시간 알림 후속 처리에 실패했습니다."),

    // 공통 시스템 오류
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "NTF-500", "서버 내부에서 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
