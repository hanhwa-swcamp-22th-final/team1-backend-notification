package com.conk.notification.command.infrastructure.kafka.consumer;

import com.conk.notification.command.application.dto.CreateNotificationCommand;
import com.conk.notification.command.application.service.NotificationCommandService;
import com.conk.notification.common.exception.NonRetryableKafkaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationKafkaConsumer 단위 테스트")
class NotificationKafkaConsumerTest {

    @Mock
    private NotificationCommandService notificationCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationKafkaConsumer(notificationCommandService, objectMapper);
    }

    @Test
    @DisplayName("잘못된 JSON은 NonRetryableKafkaException으로 변환한다")
    void consumeAsnCreated_throwsNonRetryableException_whenPayloadIsInvalid() {
        assertThatThrownBy(() -> consumer.consumeAsnCreated("not-json"))
                .isInstanceOf(NonRetryableKafkaException.class)
                .hasMessageContaining("wms.asn.created");
    }

    @Test
    @DisplayName("TASK_ASSIGNED 이벤트는 workerId를 accountId로 알림을 생성한다")
    void consumeTaskAssigned_callsNotificationCommandService() {
        consumer.consumeTaskAssigned("""
                {"workerId":"1001","roleId":"ROLE_WH_WORKER","assignedCount":3,"tenantId":"tenant-001"}
                """);

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        then(notificationCommandService).should().createNotification(captor.capture());

        CreateNotificationCommand command = captor.getValue();
        assertThat(command.getAccountId()).isEqualTo("1001");
        assertThat(command.getRoleId()).isEqualTo("ROLE_WH_WORKER");
        assertThat(command.getTitle()).isEqualTo("작업 배정");
        assertThat(command.getMessage()).isEqualTo("작업 3건이 배정되었습니다.");
    }

    @Test
    @DisplayName("ASN_CREATED 이벤트는 managerId를 accountId로 알림을 생성한다")
    void consumeAsnCreated_createsNotificationForManager() {
        consumer.consumeAsnCreated("""
                {"asnId":"ASN-1","managerId":"2001","asnCount":2,"expectedDate":"2026-04-10"}
                """);

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        then(notificationCommandService).should().createNotification(captor.capture());

        CreateNotificationCommand command = captor.getValue();
        assertThat(command.getAccountId()).isEqualTo("2001");
        assertThat(command.getRoleId()).isEqualTo("ROLE_WH_MANAGER");
        assertThat(command.getTitle()).isEqualTo("입고예정 등록");
    }
}
