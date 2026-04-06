package com.conk.notification.command.infrastructure.kafka.consumer;

import com.conk.notification.command.application.dto.CreateNotificationCommand;
import com.conk.notification.command.application.service.NotificationCommandService;
import com.conk.notification.command.domain.enums.NotificationType;
import com.conk.notification.command.infrastructure.client.MemberServiceClient;
import com.conk.notification.command.infrastructure.client.MemberServiceClient.MemberAccountInfo;
import com.conk.notification.command.infrastructure.kafka.event.AsnCreatedEvent;
import com.conk.notification.command.infrastructure.kafka.event.OrderRegisteredEvent;
import com.conk.notification.command.infrastructure.kafka.event.TaskAssignedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka 이벤트 Consumer
 *
 * 다른 서비스(wms, order, integration)에서 발행한 Kafka 메시지를 수신하여
 * 알림을 생성하고 DB에 저장하는 역할을 한다.
 *
 * @Component: Spring Bean으로 등록하여 의존성 주입이 가능하게 한다.
 *
 * 처리 흐름:
 *   Kafka 브로커 → 토픽 메시지 수신 → JSON 역직렬화 → 수신자 조회 → 알림 저장
 */
@Component
public class NotificationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);

    private final NotificationCommandService notificationCommandService;
    private final MemberServiceClient memberServiceClient;

    /**
     * ObjectMapper: JSON 문자열 ↔ Java 객체 변환 도구 (Jackson 라이브러리)
     * JavaTimeModule: LocalDateTime 등 Java 8 날짜/시간 타입을 JSON으로 처리하기 위해 등록
     */
    private final ObjectMapper objectMapper;

    public NotificationKafkaConsumer(
            NotificationCommandService notificationCommandService,
            MemberServiceClient memberServiceClient
    ) {
        this.notificationCommandService = notificationCommandService;
        this.memberServiceClient = memberServiceClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule()); // LocalDateTime 처리를 위해 필수
    }

    // =============================================
    // 1. 작업 배정 알림 (TASK_ASSIGNED)
    // =============================================

    /**
     * wms-service가 발행하는 "wms.task.assigned" 토픽 메시지를 수신한다.
     *
     * @KafkaListener 주요 속성:
     *   - topics: 구독할 Kafka 토픽 이름. 여러 개 지정 가능 (쉼표 구분).
     *   - groupId: 이 Consumer가 속할 그룹. 기본값은 application.properties의 group-id를 사용.
     *   - containerFactory: 사용할 컨테이너 팩토리 Bean 이름 (KafkaConsumerConfig에서 등록).
     *
     * 수신자: 이벤트에 workerId가 직접 포함되어 있으므로 member-service 호출 불필요.
     *
     * @param payload Kafka에서 수신한 JSON 문자열
     */
    @KafkaListener(
            topics = "wms.task.assigned",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTaskAssigned(String payload) {
        log.info("[Kafka 수신] 토픽: wms.task.assigned, payload: {}", payload);

        try {
            // JSON 문자열을 TaskAssignedEvent 객체로 변환
            TaskAssignedEvent event = objectMapper.readValue(payload, TaskAssignedEvent.class);

            // 알림 메시지 생성: "작업 N건이 배정되었습니다"
            String message = String.format("작업 %d건이 배정되었습니다.", event.getAssignedCount());

            // 알림 저장 커맨드 생성 및 실행
            // TASK_ASSIGNED는 workerId(특정 작업자)가 이미 이벤트에 포함되어 있음
            notificationCommandService.createNotification(new CreateNotificationCommand(
                    event.getWorkerId(),           // 수신 계정 ID (특정 작업자)
                    event.getRoleId(),             // 역할 ID (WH_WORKER)
                    NotificationType.TASK_ASSIGNED,
                    "작업 배정",                   // 알림 제목
                    message                        // 알림 본문
            ));

        } catch (Exception e) {
            // JSON 파싱 오류 또는 DB 저장 오류 발생 시 로그만 남기고 계속 진행
            // (예외를 다시 던지면 Kafka가 해당 메시지를 재처리하려 할 수 있음)
            // 향후 DLQ(Dead Letter Queue)로 실패 메시지를 별도 관리할 수 있다
            log.error("[Kafka Consumer 오류] wms.task.assigned 처리 실패: {}", e.getMessage(), e);
        }
    }

    // =============================================
    // 2. 입고예정 알림 (ASN_CREATED)
    // =============================================

    /**
     * wms-service가 발행하는 "wms.asn.created" 토픽 메시지를 수신한다.
     *
     * 수신자: 이벤트의 tenantId로 member-service를 조회하여
     *         해당 테넌트의 모든 WH_MANAGER 계정에게 알림을 발송한다.
     *         (1:N 알림 발송 → 여러 건의 notification 레코드 저장)
     *
     * @param payload Kafka에서 수신한 JSON 문자열
     */
    @KafkaListener(
            topics = "wms.asn.created",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAsnCreated(String payload) {
        log.info("[Kafka 수신] 토픽: wms.asn.created, payload: {}", payload);

        try {
            AsnCreatedEvent event = objectMapper.readValue(payload, AsnCreatedEvent.class);

            String message = String.format("입고예정 %d건이 등록되었습니다. (예정일: %s)",
                    event.getAsnCount(), event.getExpectedDate());

            // member-service에서 해당 테넌트의 WH_MANAGER 계정 목록 조회
            // 주의: member-service에 GET /member/accounts/by-tenant?tenantId=&roleId= 엔드포인트가 필요
            List<MemberAccountInfo> managers = memberServiceClient
                    .getAccountsByTenantAndRole(event.getTenantId(), "ROLE_WH_MANAGER");

            if (managers.isEmpty()) {
                log.warn("[알림 발송 없음] tenantId={}의 WH_MANAGER 계정이 없거나 member-service 조회 실패",
                        event.getTenantId());
                return;
            }

            // 조회된 모든 WH_MANAGER에게 각각 알림 저장 (1:N)
            for (MemberAccountInfo manager : managers) {
                notificationCommandService.createNotification(new CreateNotificationCommand(
                        manager.getAccountId(),
                        manager.getRoleId(),
                        NotificationType.ASN_CREATED,
                        "입고예정 등록",
                        message
                ));
            }

            log.info("[알림 발송 완료] ASN_CREATED → {}명에게 발송", managers.size());

        } catch (Exception e) {
            log.error("[Kafka Consumer 오류] wms.asn.created 처리 실패: {}", e.getMessage(), e);
        }
    }

    // =============================================
    // 3. 주문 등록 알림 (ORDER_REGISTERED)
    // =============================================

    /**
     * order/integration-service가 발행하는 "order.order.registered" 토픽 메시지를 수신한다.
     *
     * 수신자: 이벤트의 sellerId로 member-service를 조회하여
     *         해당 셀러의 MASTER_ADMIN 계정에게 알림을 발송한다.
     *
     * @param payload Kafka에서 수신한 JSON 문자열
     */
    @KafkaListener(
            topics = "order.order.registered",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderRegistered(String payload) {
        log.info("[Kafka 수신] 토픽: order.order.registered, payload: {}", payload);

        try {
            OrderRegisteredEvent event = objectMapper.readValue(payload, OrderRegisteredEvent.class);

            String message = String.format("주문 %d건이 등록되었습니다.", event.getOrderCount());

            // member-service에서 해당 셀러의 MASTER_ADMIN 계정 조회
            // 주의: member-service에 GET /member/accounts/by-seller?sellerId=&roleId= 엔드포인트가 필요
            List<MemberAccountInfo> admins = memberServiceClient
                    .getAccountsBySellerAndRole(event.getSellerId(), "ROLE_MASTER_ADMIN");

            if (admins.isEmpty()) {
                log.warn("[알림 발송 없음] sellerId={}의 MASTER_ADMIN 계정이 없거나 member-service 조회 실패",
                        event.getSellerId());
                return;
            }

            for (MemberAccountInfo admin : admins) {
                notificationCommandService.createNotification(new CreateNotificationCommand(
                        admin.getAccountId(),
                        admin.getRoleId(),
                        NotificationType.ORDER_REGISTERED,
                        "주문 등록",
                        message
                ));
            }

            log.info("[알림 발송 완료] ORDER_REGISTERED → {}명에게 발송", admins.size());

        } catch (Exception e) {
            log.error("[Kafka Consumer 오류] order.order.registered 처리 실패: {}", e.getMessage(), e);
        }
    }
}
