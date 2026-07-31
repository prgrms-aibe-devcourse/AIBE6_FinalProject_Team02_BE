package com.backend_catcheat.domain.challenge.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "challenge_dex")
public class ChallengeDex extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", nullable = false, length = 20)
    private ChallengeType challengeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private PeriodType periodType;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "reward_badge_id")
    private Long rewardBadgeId;

    @Column(name = "is_event", nullable = false)
    private boolean event;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public ChallengeDex(
            Long ownerId, String name,
            String description,
            ChallengeType challengeType,
            PeriodType periodType,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long rewardBadgeId,
            boolean event,
            LocalDateTime deletedAt
    ) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.challengeType = challengeType;
        this.periodType = periodType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.rewardBadgeId = rewardBadgeId;
        this.event = event;
        this.deletedAt = deletedAt;
    }

    public void linkRewardBadge(Long badgeId){
        this.rewardBadgeId = badgeId;
    }
    public void softDelete(){
        this.deletedAt = LocalDateTime.now();
    }


}
