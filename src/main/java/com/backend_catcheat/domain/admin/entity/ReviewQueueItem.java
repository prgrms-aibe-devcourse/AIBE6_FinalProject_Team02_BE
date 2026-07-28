package com.backend_catcheat.domain.admin.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관리자 검토 큐의 항목 하나. 카드 단위다 —
 * 한 등록 건에서 일부 음식은 AI를 통과하고 일부만 대기할 수 있기 때문이다.
 */
@Entity
@Table(name = "review_queue_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewQueueItem extends BaseEntity {

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    @Column(name = "collection_card_id", nullable = false)
    private Long collectionCardId;

    /** 증빙 사진 — AI가 판정했던 바로 그 분석 사진 */
    @Column(name = "evidence_photo_id", nullable = false)
    private Long evidencePhotoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewStatus status;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    private ReviewQueueItem(Long registrationId, Long collectionCardId, Long evidencePhotoId) {
        this.registrationId = registrationId;
        this.collectionCardId = collectionCardId;
        this.evidencePhotoId = evidencePhotoId;
        this.status = ReviewStatus.PENDING;
    }

    public static ReviewQueueItem pending(Long registrationId, Long collectionCardId, Long evidencePhotoId) {
        return new ReviewQueueItem(registrationId, collectionCardId, evidencePhotoId);
    }

    public boolean isPending() {
        return status == ReviewStatus.PENDING;
    }

    public void approve(LocalDateTime at) {
        this.status = ReviewStatus.APPROVED;
        this.reviewedAt = at;
    }

    public void reject(LocalDateTime at) {
        this.status = ReviewStatus.REJECTED;
        this.reviewedAt = at;
    }
}
