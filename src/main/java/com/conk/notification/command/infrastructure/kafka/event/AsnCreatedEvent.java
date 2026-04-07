package com.conk.notification.command.infrastructure.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 입고예정(ASN) 등록 Kafka 이벤트 DTO
 *
 * wms-service가 "wms.asn.created" 토픽에 발행하는 메시지의 JSON 구조를 정의한다.
 *
 * 수신자 결정 방식:
 *   wms-service가 ASN 등록 시 선택한 창고의 WH_MANAGER accountId 목록을 직접 포함한다.
 *   notification-service는 수신자를 별도로 조회하지 않고 recipientIds를 그대로 사용한다.
 *
 * 발행 측(wms-service)이 전송해야 하는 JSON 예시:
 * {
 *   "asnId": "ASN-2026-001",
 *   "recipientIds": ["1001", "1002"],
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
     * 알림 수신 대상 계정 ID 목록
     * wms-service가 ASN 등록 시 선택한 창고의 WH_MANAGER accountId를 직접 포함한다.
     */
    private List<String> recipientIds;

    /** 등록된 입고예정 건수 */
    private int asnCount;

    /** 입고 예정일 (예: "2026-04-10") */
    private String expectedDate;

    /** 이벤트 발생 시각 */
    private LocalDateTime timestamp;

    public AsnCreatedEvent() {}

    public String getAsnId() { return asnId; }
    public List<String> getRecipientIds() { return recipientIds != null ? recipientIds : Collections.emptyList(); }
    public int getAsnCount() { return asnCount; }
    public String getExpectedDate() { return expectedDate; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setAsnId(String asnId) { this.asnId = asnId; }
    public void setRecipientIds(List<String> recipientIds) { this.recipientIds = recipientIds; }
    public void setAsnCount(int asnCount) { this.asnCount = asnCount; }
    public void setExpectedDate(String expectedDate) { this.expectedDate = expectedDate; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
