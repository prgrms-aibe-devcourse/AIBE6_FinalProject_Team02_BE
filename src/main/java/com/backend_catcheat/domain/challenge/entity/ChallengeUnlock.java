package com.backend_catcheat.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "challenge_unlock")
public class ChallengeUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_participant_id", nullable = false)
    private Long challengeParticipantId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "image_key", length = 512)
    private String imageKey;

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;

    @Column(name = "review", length = 500)
    private String review;                       // 해금 시 남긴 리뷰(선택)

    @Column(name = "rating")
    private Integer rating;                       // 별점 1~5(선택)

    private ChallengeUnlock(Long challengeParticipantId, Long slotId, String imageKey, String review, Integer rating) {
        this.challengeParticipantId = challengeParticipantId;
        this.slotId = slotId;
        this.imageKey = imageKey;
        this.unlockedAt = LocalDateTime.now();
        this.review = review;
        this.rating = rating;
    }

    public static ChallengeUnlock of(Long participantId, Long slotId, String imageKey, String review, Integer rating) {
        return new ChallengeUnlock(participantId, slotId, imageKey, review, rating);
    }
}