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

    @Builder
    private FoodRegistrationRequest(Long registrationId, String description, String failureReason, RegistrationRequestStatus status) {
        this.registrationId = registrationId;
        this.description = description;
        this.failureReason = failureReason;
        this.status = status;
    }
    public void complete() {
        this.status = RegistrationRequestStatus.COMPLETED;
    }
    public void reject(String failureReason) {
        this.status = RegistrationRequestStatus.REJECTED;
        this.failureReason = failureReason;
    }

}
