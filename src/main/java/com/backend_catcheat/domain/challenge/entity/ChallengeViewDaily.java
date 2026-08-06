package com.backend_catcheat.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "challenge_view_daily")
public class ChallengeViewDaily {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_dex_id", nullable = false)
    private Long challengeDexId;

    @Column(name = "view_date", nullable = false)
    private LocalDate viewDate;

    @Column(name = "view_count", nullable = false)
    private long viewCount;
}
