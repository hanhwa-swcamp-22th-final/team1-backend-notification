package com.conk.notification.query.service;

import com.conk.notification.command.domain.aggregate.Notification;
import com.conk.notification.command.domain.enums.NotificationType;
import com.conk.notification.command.infrastructure.repository.NotificationJpaRepository;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import com.conk.notification.query.dto.NotificationListItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationQueryService 단위 테스트")
class NotificationQueryServiceTest {

    @Mock
    private NotificationJpaRepository notificationJpaRepository;

    @InjectMocks
    private NotificationQueryService notificationQueryService;

    @Test
    @DisplayName("type과 isRead가 모두 있으면 해당 조건으로 조회한다")
    void findNotifications_queriesByTypeAndReadStatus() {
        Notification notification = notification(true, LocalDateTime.of(2026, 4, 10, 16, 30));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        given(notificationJpaRepository.findByAccountIdAndNotificationTypeAndIsRead(
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq(NotificationType.TASK_ASSIGNED),
                org.mockito.ArgumentMatchers.eq(true),
                pageableCaptor.capture()
        )).willReturn(new PageImpl<>(List.of(notification)));

        List<NotificationListItemResponse> result = notificationQueryService.findNotifications(
                "1001",
                -1,
                500,
                "TASK_ASSIGNED",
                true,
                "createdAt,asc"
        );

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNotificationId()).isEqualTo(notification.getNotificationId());
        assertThat(result.get(0).getType()).isEqualTo("TASK_ASSIGNED");
        assertThat(result.get(0).getIsRead()).isTrue();
        assertThat(result.get(0).getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 10, 16, 30));
    }

    @Test
    @DisplayName("type만 있으면 type 조건으로 조회한다")
    void findNotifications_queriesByType_whenOnlyTypeIsProvided() {
        given(notificationJpaRepository.findByAccountIdAndNotificationType(
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq(NotificationType.TASK_ASSIGNED),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of()));

        notificationQueryService.findNotifications("1001", 0, 20, "TASK_ASSIGNED", null, "createdAt,desc");

        then(notificationJpaRepository).should().findByAccountIdAndNotificationType(
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq(NotificationType.TASK_ASSIGNED),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    @Test
    @DisplayName("isRead만 있으면 읽음 여부 조건으로 조회한다")
    void findNotifications_queriesByReadStatus_whenOnlyIsReadIsProvided() {
        given(notificationJpaRepository.findByAccountIdAndIsRead(
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of()));

        notificationQueryService.findNotifications("1001", 0, 20, null, false, "createdAt,desc");

        then(notificationJpaRepository).should().findByAccountIdAndIsRead(
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    @Test
    @DisplayName("필터가 없으면 accountId 기준으로 조회한다")
    void findNotifications_queriesByAccountId_whenFiltersAreNotProvided() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        given(notificationJpaRepository.findByAccountId(
                org.mockito.ArgumentMatchers.eq("1001"),
                pageableCaptor.capture()
        )).willReturn(new PageImpl<>(List.of()));

        notificationQueryService.findNotifications("1001", 1, 10, null, null, null);

        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("지원하지 않는 알림 타입이면 BusinessException을 던진다")
    void findNotifications_throwsBusinessException_whenTypeIsInvalid() {
        assertThatThrownBy(() -> notificationQueryService.findNotifications(
                "1001",
                0,
                20,
                "INVALID_TYPE",
                null,
                "createdAt,desc"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_NOTIFICATION_TYPE);
    }

    @Test
    @DisplayName("읽지 않은 알림 수 조회는 repository에 위임한다")
    void countUnreadNotifications_delegatesToRepository() {
        given(notificationJpaRepository.countByAccountIdAndIsReadFalse("1001")).willReturn(4L);

        long result = notificationQueryService.countUnreadNotifications("1001");

        assertThat(result).isEqualTo(4L);
    }

    private Notification notification(boolean isRead, LocalDateTime createdAt) {
        Notification notification = Notification.create(
                "1001",
                "ROLE_WH_WORKER",
                NotificationType.TASK_ASSIGNED,
                "작업 배정",
                "작업 3건이 배정되었습니다."
        );
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);

        if (isRead) {
            notification.markAsRead();
        }

        return notification;
    }
}
