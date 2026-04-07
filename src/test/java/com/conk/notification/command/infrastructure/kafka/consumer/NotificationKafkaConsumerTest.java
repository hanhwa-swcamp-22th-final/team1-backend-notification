package com.conk.notification.command.infrastructure.kafka.consumer;

import com.conk.notification.command.application.dto.CreateNotificationCommand;
import com.conk.notification.command.application.service.NotificationCommandService;
import com.conk.notification.command.infrastructure.client.MemberServiceClient;
import com.conk.notification.command.infrastructure.client.MemberServiceClient.MemberAccountInfo;
import com.conk.notification.common.exception.ErrorCode;
import com.conk.notification.common.exception.NonRetryableKafkaException;
import com.conk.notification.common.exception.RetryableKafkaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationKafkaConsumer 단위 테스트")
class NotificationKafkaConsumerTest {

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private MemberServiceClient memberServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("잘못된 JSON은 NonRetryableKafkaException으로 변환한다")
    void consumeAsnCreated_throwsNonRetryableException_whenPayloadIsInvalid() {
        NotificationKafkaConsumer consumer =
                new NotificationKafkaConsumer(notificationCommandService, memberServiceClient, objectMapper);

        assertThatThrownBy(() -> consumer.consumeAsnCreated("not-json"))
                .isInstanceOf(NonRetryableKafkaException.class)
                .hasMessageContaining("wms.asn.created");
    }

    @Test
    @DisplayName("member-service 조회 실패는 RetryableKafkaException으로 전파한다")
    void consumeAsnCreated_propagatesRetryableLookupFailure() {
        NotificationKafkaConsumer consumer =
                new NotificationKafkaConsumer(notificationCommandService, memberServiceClient, objectMapper);

        given(memberServiceClient.getManagersByWarehouse("WH-001", "ROLE_WH_MANAGER"))
                .willThrow(new RetryableKafkaException(
                        ErrorCode.RECIPIENT_LOOKUP_FAILED,
                        "lookup failed"
                ));

        assertThatThrownBy(() -> consumer.consumeAsnCreated("""
                {"asnId":"ASN-1","warehouseId":"WH-001","asnCount":2,"expectedDate":"2026-04-10"}
                """))
                .isInstanceOf(RetryableKafkaException.class)
                .hasMessageContaining("lookup failed");
    }

    @Test
    @DisplayName("TASK_ASSIGNED 이벤트는 알림 생성 커맨드를 서비스에 전달한다")
    void consumeTaskAssigned_callsNotificationCommandService() {
        NotificationKafkaConsumer consumer =
                new NotificationKafkaConsumer(notificationCommandService, memberServiceClient, objectMapper);

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
    @DisplayName("ASN_CREATED 수신자 목록이 비어 있으면 알림 생성 없이 종료한다")
    void consumeAsnCreated_skipsWhenRecipientsAreEmpty() {
        NotificationKafkaConsumer consumer =
                new NotificationKafkaConsumer(notificationCommandService, memberServiceClient, objectMapper);

        given(memberServiceClient.getManagersByWarehouse("WH-001", "ROLE_WH_MANAGER"))
                .willReturn(List.of());

        consumer.consumeAsnCreated("""
                {"asnId":"ASN-1","warehouseId":"WH-001","asnCount":2,"expectedDate":"2026-04-10"}
                """);

        then(notificationCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ASN_CREATED는 조회된 관리자 수만큼 알림을 생성한다")
    void consumeAsnCreated_createsNotificationsForAllManagers() {
        NotificationKafkaConsumer consumer =
                new NotificationKafkaConsumer(notificationCommandService, memberServiceClient, objectMapper);

        MemberAccountInfo first = new MemberAccountInfo();
        first.setAccountId("2001");
        first.setRoleId("ROLE_WH_MANAGER");
        MemberAccountInfo second = new MemberAccountInfo();
        second.setAccountId("2002");
        second.setRoleId("ROLE_WH_MANAGER");

        given(memberServiceClient.getManagersByWarehouse("WH-001", "ROLE_WH_MANAGER"))
                .willReturn(List.of(first, second));

        consumer.consumeAsnCreated("""
                {"asnId":"ASN-1","warehouseId":"WH-001","asnCount":2,"expectedDate":"2026-04-10"}
                """);

        then(notificationCommandService).should(org.mockito.Mockito.times(2))
                .createNotification(org.mockito.ArgumentMatchers.any(CreateNotificationCommand.class));
    }
}
