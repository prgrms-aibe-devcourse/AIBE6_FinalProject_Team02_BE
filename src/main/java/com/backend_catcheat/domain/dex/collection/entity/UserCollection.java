package com.backend_catcheat.domain.dex.collection.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유저가 해금한 도감 칸 하나. (user_id, slot_id)로 유일하다.
 *
 * 수집률은 이 행의 개수 / 200이다 — 중복 수집은 카드만 늘고 이 행은 늘지 않는다 (§5.1).
 */
@Entity
@Table(name = "user_collection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCollection extends BaseEntity {

    /** 별 최대 3개. 4회차부터는 랭크가 변하지 않는다 (§5.1) */
    public static final int MAX_RANK = 3;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    /** 별 랭크. 최초 수집 1 → 중복 2회차 2 → 3회차 3 */
    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "first_collected_at", nullable = false)
    private LocalDateTime firstCollectedAt;

    private UserCollection(Long userId, Long slotId, LocalDateTime collectedAt) {
        this.userId = userId;
        this.slotId = slotId;
        this.rank = 1;
        this.firstCollectedAt = collectedAt;
    }

    /** 최초 해금 — 별 1개로 시작한다 */
    public static UserCollection unlock(Long userId, Long slotId, LocalDateTime collectedAt) {
        return new UserCollection(userId, slotId, collectedAt);
    }

    /**
     * 중복 수집. 별이 하나 오르되 3에서 멈춘다.
     * 수집률은 건드리지 않는다 — 칸 기준이라 이미 열린 칸은 다시 세지 않는다.
     */
    public void collectAgain() {
        this.rank = Math.min(this.rank + 1, MAX_RANK);
    }

    public boolean isMaxRank() {
        return rank >= MAX_RANK;
    }
}
