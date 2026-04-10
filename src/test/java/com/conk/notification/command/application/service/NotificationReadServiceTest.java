package com.conk.notification.command.application.service;

import com.conk.notification.command.domain.aggregate.Notification;
import com.conk.notification.command.domain.enums.NotificationType;
import com.conk.notification.command.infrastructure.redis.service.NotificationUnreadCountService;
import com.conk.notification.command.infrastructure.repository.NotificationJpaRepository;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationReadService 단위 테스트")
class NotificationReadServiceTest {

    @Mock
    private NotificationJpaRepository notificationJpaRepository;

    @Mock
    private NotificationUnreadCountService unreadCountService;

    @InjectMocks
    private NotificationReadService notificationReadService;

    @Test
    @DisplayName("알림이 없으면 BusinessException을 던진다")
    void markAsRead_throwsBusinessException_whenNotificationDoesNotExist() {
        given(notificationJpaRepository.findByNotificationIdAndAccountId("notification-1", "1001"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationReadService.markAsRead("notification-1", "1001"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("읽지 않은 알림을 읽음 처리하면 unread 수를 동기화한다")
    void markAsRead_syncsUnreadCount_whenNotificationChanges() {
        Notification notification = notification(false);
        given(notificationJpaRepository.findByNotificationIdAndAccountId("notification-1", "1001"))
                .willReturn(Optional.of(notification));
        given(notificationJpaRepository.countByAccountIdAndIsReadFalse("1001")).willReturn(2L);

        notificationReadService.markAsRead("notification-1", "1001");

        assertThat(notification.getIsRead()).isTrue();
        then(unreadCountService).should().setCount("1001", 2L);
    }

    @Test
    @DisplayName("이미 읽은 알림이면 unread 수를 다시 동기화하지 않는다")
    void markAsRead_doesNotSyncUnreadCount_whenNotificationAlreadyRead() {
        Notification notification = notification(true);
        given(notificationJpaRepository.findByNotificationIdAndAccountId("notification-1", "1001"))
                .willReturn(Optional.of(notification));

        notificationReadService.markAsRead("notification-1", "1001");

        then(notificationJpaRepository).should(never()).countByAccountIdAndIsReadFalse(anyString());
        then(unreadCountService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("읽지 않은 알림이 없으면 unread 수를 0으로 설정한다")
    void markAllAsRead_setsUnreadCountToZero_whenUnreadNotificationsDoNotExist() {
        given(notificationJpaRepository.findByAccountIdAndIsReadFalse("1001")).willReturn(List.of());

        notificationReadService.markAllAsRead("1001");

        then(unreadCountService).should().setCount("1001", 0L);
        then(notificationJpaRepository).should(never()).countByAccountIdAndIsReadFalse(anyString());
    }

    @Test
    @DisplayName("읽지 않은 알림이 있으면 모두 읽음 처리하고 unread 수를 동기화한다")
    void markAllAsRead_marksAllNotificationsAndSyncsUnreadCount() {
        Notification first = notification(false);
        Notification second = notification(false);
        given(notificationJpaRepository.findByAccountIdAndIsReadFalse("1001")).willReturn(List.of(first, second));
        given(notificationJpaRepository.countByAccountIdAndIsReadFalse("1001")).willReturn(0L);

        notificationReadService.markAllAsRead("1001");

        assertThat(first.getIsRead()).isTrue();
        assertThat(second.getIsRead()).isTrue();
        then(unreadCountService).should().setCount("1001", 0L);
    }

    private Notification notification(boolean isRead) {
        Notification notification = Notification.create(
                "1001",
                "ROLE_WH_WORKER",
                NotificationType.TASK_ASSIGNED,
                "작업 배정",
                "작업 3건이 배정되었습니다."
        );

        if (isRead) {
            notification.markAsRead();
        }

        return notification;
    }
}
