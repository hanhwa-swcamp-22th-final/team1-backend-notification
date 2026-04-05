package com.conk.notification.command.infrastructure.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 작업 배정 Kafka 이벤트 DTO
 *
 * wms-service가 "wms.task.assigned" 토픽에 발행하는 메시지의 JSON 구조를 정의한다.
 * Kafka에서 JSON 문자열을 수신한 뒤 ObjectMapper로 이 클래스로 역직렬화한다.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true):
 *   발행 측(WMS)이 필드를 추가하더라도 이 클래스에 없는 필드는 무시하고 정상 처리된다.
 *   버전 간 호환성을 위해 항상 붙여두는 것이 좋다.
 *
 * 발행 측(wms-service)이 전송해야 하는 JSON 예시:
 * {
 *   "workerId": "1001",
 *   "roleId": "ROLE_WH_WORKER",
 *   "assignedCount": 3,
 *   "tenantId": "tenant-001",
 *   "timestamp": "2026-04-05T10:00:00"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskAssignedEvent {

    /**
     * 작업을 배정받은 창고 작업자(WH_WORKER)의 accountId
     * member-service Account.accountId를 문자열로 전달한다.
     */
    private String workerId;

    /**
     * 수신자의 역할 ID
     * notification 테이블의 role_id 컬럼에 저장된다.
     */
    private String roleId;

    /** 배정된 작업 건수 */
    private int assignedCount;

    /** 테넌트 ID (데이터 격리용, 로깅 및 추적에 활용) */
    private String tenantId;

    /** 이벤트 발생 시각 (wms-service에서 세팅) */
    private LocalDateTime timestamp;

    // Jackson이 역직렬화할 때 기본 생성자가 필요하다
    public TaskAssignedEvent() {}

    public String getWorkerId() { return workerId; }
    public String getRoleId() { return roleId; }
    public int getAssignedCount() { return assignedCount; }
    public String getTenantId() { return tenantId; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public void setAssignedCount(int assignedCount) { this.assignedCount = assignedCount; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
