package com.backend_catcheat.domain.registration.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 등록 건 하나. 사진 1~5장과 음식 이름 1~5개가 여기에 묶인다.
 *
 * 검증을 여러 번 시도해도 등록 건은 하나다 — 재분석 상한을 셀 수 있어야 하기 때문이다.
 */
@Entity
@Table(name = "registration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Registration extends BaseEntity {

    /** 재분석 상한 2회. 초과하면 수동 폴백으로 보낸다 */
    public static final int MAX_RETRIES = 2;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RegistrationStatus status;

    /** AI에 보낸 단 한 장의 S3 key */
    @Column(name = "analysis_photo_key")
    private String analysisPhotoKey;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    private Registration(Long userId, String analysisPhotoKey) {
        this.userId = userId;
        this.status = RegistrationStatus.DRAFT;
        this.analysisPhotoKey = analysisPhotoKey;
        this.retryCount = 0;
    }

    public static Registration start(Long userId, String analysisPhotoKey) {
        return new Registration(userId, analysisPhotoKey);
    }

    public boolean isOwnedBy(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }

    public boolean canRetry() {
        return retryCount < MAX_RETRIES;
    }

    public int retriesLeft() {
        return Math.max(0, MAX_RETRIES - retryCount);
    }

    /** 최초 검증은 1회차, 이후 재시도마다 증가한다 */
    public int currentAttemptNo() {
        return retryCount + 1;
    }

    public void retryWith(String newAnalysisPhotoKey) {
        this.retryCount += 1;
        this.analysisPhotoKey = newAnalysisPhotoKey;
    }

    public boolean isCompleted() {
        return status == RegistrationStatus.COMPLETED;
    }

    public void complete() {
        this.status = RegistrationStatus.COMPLETED;
    }
}
