package com.conk.notification.command.application.service;

import com.conk.notification.command.application.dto.CreateNotificationCommand;
import com.conk.notification.command.application.event.NotificationCreatedEvent;
import com.conk.notification.command.domain.aggregate.Notification;
import com.conk.notification.command.domain.enums.NotificationType;
import com.conk.notification.command.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCommandService 단위 테스트")
class NotificationCommandServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationCommandService notificationCommandService;

    @Test
    @DisplayName("알림 저장 후 AFTER_COMMIT용 이벤트를 발행한다")
    void createNotification_publishesNotificationCreatedEvent() {
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CreateNotificationCommand command = new CreateNotificationCommand(
                "1001",
                "ROLE_WH_WORKER",
                NotificationType.TASK_ASSIGNED,
                "작업 배정",
                "작업 3건이 배정되었습니다."
        );

        notificationCommandService.createNotification(command);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();

        ArgumentCaptor<NotificationCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());

        NotificationCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getNotificationId()).isEqualTo(saved.getNotificationId());
        assertThat(event.getAccountId()).isEqualTo("1001");
        assertThat(event.getType()).isEqualTo("TASK_ASSIGNED");
        assertThat(event.getTitle()).isEqualTo("작업 배정");
        assertThat(event.getMessage()).isEqualTo("작업 3건이 배정되었습니다.");
    }
}
