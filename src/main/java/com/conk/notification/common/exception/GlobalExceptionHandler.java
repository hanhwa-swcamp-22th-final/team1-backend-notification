package com.conk.notification.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 컨트롤러 밖으로 나온 예외를 notification-service 공통 응답 형식으로 통일한다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 비즈니스 예외 처리.
     *
     * ErrorCode에 정의된 status / code / message를 그대로 응답에 반영한다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("BusinessException [{}]: {}", errorCode.getCode(), ex.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(false, errorCode.getCode(), ex.getMessage()));
    }

    /**
     * 그 외 예측하지 못한 시스템 예외를 500으로 응답한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unhandled Exception", ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(false, errorCode.getCode(), errorCode.getMessage()));
    }

    // 에러 응답의 최소 공통 구조다.
    public static class ErrorResponse {
        private final boolean success;
        private final String code;
        private final String message;

        public ErrorResponse(boolean success, String code, String message) {
            this.success = success;
            this.code = code;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
