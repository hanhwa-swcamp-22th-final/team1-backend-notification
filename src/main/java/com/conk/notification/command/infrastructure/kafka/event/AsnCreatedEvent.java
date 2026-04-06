package com.conk.notification.command.infrastructure.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 입고예정(ASN) 등록 Kafka 이벤트 DTO
 *
 * wms-service가 "wms.asn.created" 토픽에 발행하는 메시지의 JSON 구조를 정의한다.
 *
 * 수신자 결정 방식:
 *   - 이벤트에 tenantId만 포함 (특정 계정을 지정하지 않음)
 *   - notification-service가 member-service를 호출하여
 *     해당 tenantId에 속한 모든 WH_MANAGER 계정에게 알림을 발송한다.
 *
 * 발행 측(wms-service)이 전송해야 하는 JSON 예시:
 * {
 *   "asnId": "ASN-2026-001",
 *   "tenantId": "tenant-001",
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
     * 테넌트 ID
     * 이 ID로 member-service에서 해당 테넌트의 WH_MANAGER 계정 목록을 조회한다.
     */
    private String tenantId;

    /** 등록된 입고예정 건수 */
    private int asnCount;

    /** 입고 예정일 (예: "2026-04-10") */
    private String expectedDate;

    /** 이벤트 발생 시각 */
    private LocalDateTime timestamp;

    public AsnCreatedEvent() {}

    public String getAsnId() { return asnId; }
    public String getTenantId() { return tenantId; }
    public int getAsnCount() { return asnCount; }
    public String getExpectedDate() { return expectedDate; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setAsnId(String asnId) { this.asnId = asnId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setAsnCount(int asnCount) { this.asnCount = asnCount; }
    public void setExpectedDate(String expectedDate) { this.expectedDate = expectedDate; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
