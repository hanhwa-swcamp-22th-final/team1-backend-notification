package com.conk.notification.command.domain.repository;

import com.conk.notification.command.domain.aggregate.Notification;

/**
 * 알림 도메인 레포지토리 인터페이스
 *
 * 도메인 레이어는 JPA나 특정 기술에 의존하지 않는다.
 * 이 인터페이스를 통해 저장 방식(JPA, MyBatis 등)을 추상화한다.
 * 실제 구현은 infrastructure 레이어의 NotificationJpaRepository가 담당한다.
 */
public interface NotificationRepository {

    /**
     * 알림을 저장한다.
     *
     * @param notification 저장할 알림 엔티티
     * @return 저장된 알림 엔티티 (notificationId, createdAt 등이 세팅된 상태)
     */
    Notification save(Notification notification);
}
