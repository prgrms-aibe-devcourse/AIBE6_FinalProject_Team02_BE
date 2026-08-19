package com.backend_catcheat.domain.notification.repository;

import com.backend_catcheat.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    // 한 번에 전부 읽음 처리 — 건마다 엔티티를 불러올 필요 없이 벌크 UPDATE 한 방으로 끝낸다
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllAsRead(@Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);
}
