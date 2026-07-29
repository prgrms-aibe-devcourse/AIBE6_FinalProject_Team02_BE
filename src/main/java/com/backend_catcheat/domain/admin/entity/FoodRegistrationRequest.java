package com.backend_catcheat.domain.admin.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "food_registration_requests")
public class FoodRegistrationRequest extends BaseEntity {
    @Column(name = "registration_id")
    private Long registrationId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(length = 200)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationRequestStatus status;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    /** 검토 대기 상태로 만들어진 수집 카드. 완료(complete) 시 이 카드를 칸에 붙인다. */
    @Column(name = "collection_card_id")
    private Long collectionCardId;

    /** AI가 판정했던 증빙 사진. 관리자가 목록에서 확인용으로 본다. */
    @Column(name = "evidence_photo_id")
    private Long evidencePhotoId;

    @Builder
    private FoodRegistrationRequest(Long registrationId, String description, String failureReason,
                                    Long collectionCardId, Long evidencePhotoId) {
        this.registrationId = registrationId;
        this.description = description;
        this.failureReason = failureReason;
        this.collectionCardId = collectionCardId;
        this.evidencePhotoId = evidencePhotoId;
        this.status = RegistrationRequestStatus.PENDING;
    }
    public void complete() {
        this.status = RegistrationRequestStatus.COMPLETED;
    }


    public void reject(String reason) {
        this.status = RegistrationRequestStatus.REJECTED;
        this.rejectReason = reason;   // failureReason 말고 rejectReason에
    }

}
