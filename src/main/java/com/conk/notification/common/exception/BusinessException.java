package com.conk.notification.common.exception;

/**
 * 에러 코드와 함께 비즈니스 예외를 전달하기 위한 공통 예외 타입이다.
 */
// 서비스 계층에서 던지는 공통 비즈니스 예외.
// ErrorCode를 함께 보관해 HTTP status / code / message를 전역 핸들러에서 일관되게 만들 수 있다.
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // detail이 있으면 기본 메시지 대신 사용 (예: topic, tenantId, accountId 포함)
    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
