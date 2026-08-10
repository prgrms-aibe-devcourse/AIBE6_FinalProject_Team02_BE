package com.backend_catcheat.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "review_like",
        uniqueConstraints = @UniqueConstraint(name = "uq_review_like",
                columnNames = {"review_id", "user_id"}))
public class ReviewLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private ReviewLike(Long reviewId, Long userId) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }

    public static ReviewLike of(Long reviewId, Long userId) {
        return new ReviewLike(reviewId, userId);
    }
}