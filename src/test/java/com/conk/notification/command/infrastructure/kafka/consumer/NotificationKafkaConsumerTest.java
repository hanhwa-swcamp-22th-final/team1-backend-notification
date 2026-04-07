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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

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
    @DisplayName("TASK_ASSIGNED 이벤트는 알림 생성 커맨드를 서비스에 전달한다")
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
    @DisplayName("ASN_CREATED recipientIds가 비어 있으면 알림 생성 없이 종료한다")
    void consumeAsnCreated_skipsWhenRecipientsAreEmpty() {
        consumer.consumeAsnCreated("""
                {"asnId":"ASN-1","recipientIds":[],"asnCount":2,"expectedDate":"2026-04-10"}
                """);

        then(notificationCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ASN_CREATED는 recipientIds 수만큼 알림을 생성한다")
    void consumeAsnCreated_createsNotificationsForAllRecipients() {
        consumer.consumeAsnCreated("""
                {"asnId":"ASN-1","recipientIds":["2001","2002"],"asnCount":2,"expectedDate":"2026-04-10"}
                """);

        then(notificationCommandService).should(times(2))
                .createNotification(any(CreateNotificationCommand.class));
    }

    @Test
    @DisplayName("ASN_CREATED 알림의 roleId는 ROLE_WH_MANAGER로 고정된다")
    void consumeAsnCreated_setsRoleIdToWhManager() {
        consumer.consumeAsnCreated("""
                {"asnId":"ASN-1","recipientIds":["2001"],"asnCount":1,"expectedDate":"2026-04-10"}
                """);

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        then(notificationCommandService).should().createNotification(captor.capture());

        assertThat(captor.getValue().getRoleId()).isEqualTo("ROLE_WH_MANAGER");
        assertThat(captor.getValue().getAccountId()).isEqualTo("2001");
    }
}
