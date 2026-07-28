package com.backend_catcheat.domain.dex.collection.entity;

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
 * 등록 건 1건 × 음식 1개 = 카드 1장. 중복 수집하면 같은 칸에 카드가 쌓인다(캐러셀).
 */
@Entity
@Table(name = "collection_card")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionCard extends BaseEntity {

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    /**
     * 해금된 칸에 붙은 고리. **검토 대기 중이면 null이다** —
     * 수동 폴백 카드는 관리자가 수락해야 칸이 열리기 때문이다.
     * "해금됐는가"는 이 값의 유무로 판단하고, verificationStatus는 "어떻게 인증했는가"로 축이 다르다.
     */
    @Column(name = "user_collection_id")
    private Long userCollectionId;

    /** 해금 전에는 user_collection이 없어 어느 칸인지 알 수 없으므로 카드가 직접 들고 있는다 */
    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    /** 카드 썸네일 — 카드 상세의 첫 장. 도감 그리드 셀(일러스트)과는 무관하다 (§5.2) */
    @Column(name = "representative_photo_id", nullable = false)
    private Long representativePhotoId;

    /** 선택 입력, 100자 (§5.2 — 필수로 만들지 않는다) */
    @Column(name = "memo", length = 100)
    private String memo;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    private CollectionCard(
            Long registrationId, Long userCollectionId, Long slotId, Long representativePhotoId,
            String memo, String locationName, Double lat, Double lng,
            VerificationStatus verificationStatus, LocalDateTime collectedAt) {
        this.registrationId = registrationId;
        this.userCollectionId = userCollectionId;
        this.slotId = slotId;
        this.representativePhotoId = representativePhotoId;
        this.memo = memo;
        this.locationName = locationName;
        this.lat = lat;
        this.lng = lng;
        this.verificationStatus = verificationStatus;
        this.collectedAt = collectedAt;
    }

    /** AI 검증을 통과해 바로 해금되는 카드 */
    public static CollectionCard verified(
            Long registrationId, Long userCollectionId, Long slotId, Long thumbnailPhotoId,
            String memo, String locationName, Double lat, Double lng, LocalDateTime collectedAt) {
        return new CollectionCard(
                registrationId, userCollectionId, slotId, thumbnailPhotoId,
                memo, locationName, lat, lng, VerificationStatus.PHOTO_VERIFIED, collectedAt);
    }

    /** 재분석 상한을 넘겨 검토를 기다리는 카드. 칸에 붙지 않은 상태로 만들어진다 */
    public static CollectionCard awaitingReview(
            Long registrationId, Long slotId, Long thumbnailPhotoId,
            String memo, String locationName, Double lat, Double lng, LocalDateTime collectedAt) {
        return new CollectionCard(
                registrationId, null, slotId, thumbnailPhotoId,
                memo, locationName, lat, lng, VerificationStatus.MANUAL_PENDING, collectedAt);
    }

    public boolean isAwaitingReview() {
        return userCollectionId == null;
    }

    /** 관리자가 검토를 수락한 순간 칸에 붙는다 — 이때부터 수집률에 반영된다 */
    public void attachTo(Long unlockedUserCollectionId) {
        this.userCollectionId = unlockedUserCollectionId;
    }
}
