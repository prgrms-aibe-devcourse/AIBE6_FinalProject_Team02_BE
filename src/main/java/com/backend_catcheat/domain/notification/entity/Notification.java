package com.backend_catcheat.domain.notification.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Column(name="recipient_id", nullable=false)
    private Long recipientId;

    @Column(name="actor_id", nullable=false)
    private Long actorId;

    // FOOD_REGISTRATION_REQUEST_RECEIVED가 34자라 30자 제한에 걸려 insert가 조용히(@Async라) 실패했었다
    @Enumerated(EnumType.STRING)
    @Column(name="type", nullable=false, length=50)
    private NotificationType type;

    @Column(name="target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="payload")
    private String payload;

    @Column(name="read_at")
    private LocalDateTime readAt;

    @Column(name="deleted_at")
    private LocalDateTime deletedAt;


    private Notification(Long recipientId, Long actorId, NotificationType type, Long targetId, String payload) {
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.type = type;
        this.targetId = targetId;
        this.payload = payload;
    }

    public static Notification create(Long recipientId, Long actorId, NotificationType type, Long targetId, String payload) {
        return new Notification(recipientId, actorId, type, targetId, payload);
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markAsRead(LocalDateTime now) {
        this.readAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

}
