package com.backend_catcheat.domain.friend.entity;

import com.backend_catcheat.domain.friend.entity.type.FriendshipStatus;
import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 양방향 친구 관계 */
@Entity
@Getter
@Table(name = "friendship")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship extends BaseEntity {
    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "addressee_id", nullable = false)
    private Long addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    private Friendship(Long requesterId, Long addresseeId) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
        this.status = FriendshipStatus.PENDING;
    }

    /** 새 친구 요청(PENDING) */
    public static Friendship request(Long requesterId, Long addresseeId) {
        return new Friendship(requesterId, addresseeId);
    }

    /** 수락 */
    public void accept(LocalDateTime now) {
        this.status = FriendshipStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public boolean isAccepted() {
        return status == FriendshipStatus.ACCEPTED;
    }
}
