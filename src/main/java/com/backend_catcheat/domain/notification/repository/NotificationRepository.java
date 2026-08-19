package com.backend_catcheat.domain.notification.repository;

import com.backend_catcheat.domain.notification.entity.Notification;
import com.backend_catcheat.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    // 좋아요 토글 반복 시 안 읽은 알림이 계속 쌓이지 않도록, 만들기 전에 같은 조합의 안 읽은 알림이 있는지 본다
    Optional<Notification> findByRecipientIdAndActorIdAndTypeAndTargetIdAndReadAtIsNull(
            Long recipientId, Long actorId, NotificationType type, Long targetId);

    // 한 번에 전부 읽음 처리 — 건마다 엔티티를 불러올 필요 없이 벌크 UPDATE 한 방으로 끝낸다
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllAsRead(@Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);
}
