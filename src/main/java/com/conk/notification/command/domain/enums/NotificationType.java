package com.conk.notification.command.domain.enums;

/**
 * 알림 유형을 정의하는 enum
 *
 * DB의 notification_type 컬럼(VARCHAR 50)에 문자열로 저장된다.
 * 새로운 알림 유형이 필요할 경우 여기에 추가하고,
 * 해당 Kafka 토픽과 Consumer 메서드도 함께 추가해야 한다.
 */
public enum NotificationType {

    /**
     * 작업 배정 알림
     * 발행 서비스: wms-service
     * 수신 대상: WH_WORKER (작업을 배정받은 창고 작업자)
     * 예시 메시지: "작업 3건이 배정되었습니다"
     */
    TASK_ASSIGNED,

    /**
     * 입고예정 등록 알림
     * 발행 서비스: wms-service
     * 수신 대상: WH_MANAGER (ASN 등록 시 선택한 창고의 관리자)
     * 예시 메시지: "입고예정 2건이 등록되었습니다"
     */
    ASN_CREATED
}
