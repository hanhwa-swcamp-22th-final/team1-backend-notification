package com.conk.notification.command.infrastructure.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 주문 등록 Kafka 이벤트 DTO
 *
 * order-service 또는 integration-service가 "order.order.registered" 토픽에
 * 발행하는 메시지의 JSON 구조를 정의한다.
 *
 * 수신자 결정 방식:
 *   - 이벤트에 sellerId 포함
 *   - notification-service가 member-service를 호출하여
 *     해당 sellerId에 연결된 MASTER_ADMIN 계정에게 알림을 발송한다.
 *
 * 발행 측(order/integration-service)이 전송해야 하는 JSON 예시:
 * {
 *   "sellerId": "seller-001",
 *   "tenantId": "tenant-001",
 *   "orderCount": 5,
 *   "timestamp": "2026-04-05T10:00:00"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRegisteredEvent {

    /**
     * 셀러 ID
     * 이 ID로 member-service에서 해당 셀러의 MASTER_ADMIN 계정을 조회한다.
     */
    private String sellerId;

    /** 테넌트 ID (로깅 및 추적용) */
    private String tenantId;

    /** 등록된 주문 건수 */
    private int orderCount;

    /** 이벤트 발생 시각 */
    private LocalDateTime timestamp;

    public OrderRegisteredEvent() {}

    public String getSellerId() { return sellerId; }
    public String getTenantId() { return tenantId; }
    public int getOrderCount() { return orderCount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
