package com.backend_catcheat.domain.registration.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "verification_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationAttempt extends BaseEntity {

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "reason")
    private String reason;

    private VerificationAttempt(
            Long registrationId, int attemptNo, Long slotId,
            boolean matched, double confidence, String reason) {
        this.registrationId = registrationId;
        this.attemptNo = attemptNo;
        this.slotId = slotId;
        this.matched = matched;
        this.confidence = confidence;
        this.reason = reason;
    }

    public static VerificationAttempt of(
            Long registrationId, int attemptNo, Long slotId,
            boolean matched, double confidence, String reason) {
        return new VerificationAttempt(registrationId, attemptNo, slotId, matched, confidence, reason);
    }
}
