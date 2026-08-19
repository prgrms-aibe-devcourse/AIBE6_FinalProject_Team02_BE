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

    @Column(name = "image_key", length = 512)
    private String imageKey;

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

    @Builder
    public ChallengeDex(
            Long ownerId, String name,
            String description,
            String imageKey,
            PeriodType periodType,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long rewardBadgeId,
            boolean event
    ) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.imageKey = imageKey;
        this.periodType = periodType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.rewardBadgeId = rewardBadgeId;
        this.event = event;
    }
    public void linkRewardBadge(Long badgeId){
        this.rewardBadgeId = badgeId;
    }
    public void changeImage(String imageKey){
        this.imageKey = imageKey;
    }
    // 수동 종료: 지금 시각으로 마감(상시도 종료 상태가 되어 참여 차단·종료 탭 이동)
    public void close(LocalDateTime now){
        this.periodType = PeriodType.LIMITED;
        this.endsAt = now;
    }

}
