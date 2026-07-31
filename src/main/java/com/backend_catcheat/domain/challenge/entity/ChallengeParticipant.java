package com.backend_catcheat.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "challenge_participant")
public class ChallengeParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_dex_id", nullable = false)
    private Long challengeDexId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;          // 미완료면 null

    private ChallengeParticipant(Long challengeDexId, Long userId) {
        this.challengeDexId = challengeDexId;
        this.userId = userId;
        this.joinedAt = LocalDateTime.now();
    }

    public static ChallengeParticipant join(Long challengeDexId, Long userId) {
        return new ChallengeParticipant(challengeDexId, userId);
    }

    public void complete() {
        this.completedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return completedAt != null;
    }
}