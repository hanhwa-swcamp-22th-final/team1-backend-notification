package com.conk.notification.command.infrastructure.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 입고예정(ASN) 등록 Kafka 이벤트 DTO
 *
 * wms-service가 "wms.asn.created" 토픽에 발행하는 메시지의 JSON 구조를 정의한다.
 *
 * 수신자 결정 방식:
 *   창고 1개당 WH_MANAGER 1명이므로, wms-service가 ASN 등록 시 선택한 창고의
 *   관리자 accountId를 단일 필드(managerId)로 직접 포함한다.
 *
 * 발행 측(wms-service)이 전송해야 하는 JSON 예시:
 * {
 *   "asnId": "ASN-2026-001",
 *   "managerId": "1001",
 *   "asnCount": 2,
 *   "expectedDate": "2026-04-10",
 *   "timestamp": "2026-04-05T10:00:00"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsnCreatedEvent {

    /** 등록된 ASN의 고유 ID */
    private String asnId;

    /**
     * 알림 수신 대상 WH_MANAGER의 accountId
     * 창고 1개당 관리자 1명이므로 단일 값으로 전달된다.
     */
    private String managerId;

    /** 등록된 입고예정 건수 */
    private int asnCount;

    /** 입고 예정일 (예: "2026-04-10") */
    private String expectedDate;

    /** 이벤트 발생 시각 */
    private LocalDateTime timestamp;

    public AsnCreatedEvent() {}

    public String getAsnId() { return asnId; }
    public String getManagerId() { return managerId; }
    public int getAsnCount() { return asnCount; }
    public String getExpectedDate() { return expectedDate; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setAsnId(String asnId) { this.asnId = asnId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    public void setAsnCount(int asnCount) { this.asnCount = asnCount; }
    public void setExpectedDate(String expectedDate) { this.expectedDate = expectedDate; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
