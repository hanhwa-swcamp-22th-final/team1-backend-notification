package com.conk.notification.command.infrastructure.kafka.consumer;

import com.conk.notification.command.application.dto.CreateNotificationCommand;
import com.conk.notification.command.application.service.NotificationCommandService;
import com.conk.notification.command.domain.enums.NotificationType;
import com.conk.notification.command.infrastructure.kafka.event.AsnCreatedEvent;
import com.conk.notification.command.infrastructure.kafka.event.TaskAssignedEvent;
import com.conk.notification.common.exception.ErrorCode;
import com.conk.notification.common.exception.NonRetryableKafkaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka 이벤트 Consumer
 *
 * wms-service에서 발행한 Kafka 메시지를 수신하여 알림을 생성하고 DB에 저장하는 역할을 한다.
 *
 * 수신자 결정은 발행 서비스(wms-service)가 담당하며,
 * 이 Consumer는 이벤트에 포함된 수신자 정보를 그대로 사용한다.
 *
 * 처리 흐름:
 *   Kafka 브로커 → 토픽 메시지 수신 → JSON 역직렬화 → 알림 저장
 */
@Component
public class NotificationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);

    private final NotificationCommandService notificationCommandService;
    private final ObjectMapper objectMapper;

    public NotificationKafkaConsumer(
            NotificationCommandService notificationCommandService,
            ObjectMapper objectMapper
    ) {
        this.notificationCommandService = notificationCommandService;
        this.objectMapper = objectMapper;
    }

    // =============================================
    // 1. 작업 배정 알림 (TASK_ASSIGNED)
    // =============================================

    /**
     * wms-service가 발행하는 "wms.task.assigned" 토픽 메시지를 수신한다.
     *
     * 수신자: wms-service가 이벤트에 workerId를 직접 포함한다.
     *
     * @param payload Kafka에서 수신한 JSON 문자열
     */
    @KafkaListener(
            topics = "wms.task.assigned",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTaskAssigned(String payload) {
        log.info("[Kafka 수신] 토픽: wms.task.assigned, payload: {}", payload);

        TaskAssignedEvent event = readValue(payload, TaskAssignedEvent.class, "wms.task.assigned");
        String message = String.format("작업 %d건이 배정되었습니다.", event.getAssignedCount());

        notificationCommandService.createNotification(new CreateNotificationCommand(
                event.getWorkerId(),
                event.getRoleId(),
                NotificationType.TASK_ASSIGNED,
                "작업 배정",
                message
        ));
    }

    // =============================================
    // 2. 입고예정 알림 (ASN_CREATED)
    // =============================================

    /**
     * wms-service가 발행하는 "wms.asn.created" 토픽 메시지를 수신한다.
     *
     * 수신자: wms-service가 ASN 등록 시 선택한 창고의 WH_MANAGER accountId 목록을
     *         이벤트에 직접 포함한다. (외부 서비스 조회 없음)
     *
     * @param payload Kafka에서 수신한 JSON 문자열
     */
    @KafkaListener(
            topics = "wms.asn.created",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAsnCreated(String payload) {
        log.info("[Kafka 수신] 토픽: wms.asn.created, payload: {}", payload);

        AsnCreatedEvent event = readValue(payload, AsnCreatedEvent.class, "wms.asn.created");
        String message = String.format("입고예정 %d건이 등록되었습니다. (예정일: %s)",
                event.getAsnCount(), event.getExpectedDate());

        if (event.getRecipientIds().isEmpty()) {
            log.warn("[알림 발송 없음] ASN_CREATED 이벤트에 수신자가 없습니다. asnId={}", event.getAsnId());
            return;
        }

        for (String accountId : event.getRecipientIds()) {
            notificationCommandService.createNotification(new CreateNotificationCommand(
                    accountId,
                    "ROLE_WH_MANAGER",
                    NotificationType.ASN_CREATED,
                    "입고예정 등록",
                    message
            ));
        }

        log.info("[알림 발송 완료] ASN_CREATED → {}명에게 발송", event.getRecipientIds().size());
    }

    /**
     * Kafka JSON payload를 DTO로 역직렬화한다.
     *
     * 역직렬화 실패는 payload 계약 위반이므로 재시도해도 성공 가능성이 낮다.
     * NonRetryableKafkaException으로 전환해 즉시 재시도 대상에서 제외한다.
     */
    private <T> T readValue(String payload, Class<T> type, String topic) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new NonRetryableKafkaException(
                    ErrorCode.INVALID_KAFKA_MESSAGE,
                    "잘못된 Kafka 메시지입니다. topic=%s".formatted(topic)
            );
        }
    }
}
