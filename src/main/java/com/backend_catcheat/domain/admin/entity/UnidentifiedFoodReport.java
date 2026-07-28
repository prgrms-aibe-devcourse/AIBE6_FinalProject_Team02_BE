package com.backend_catcheat.domain.admin.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "unidentified_food_reports")
public class UnidentifiedFoodReport extends BaseEntity {
    //제보가 발생한 등록건 Id
    @Column(name = "registration_id")
    private Long registrationId;

    //제보내용
    @Column(nullable = false, length = 200)
    private String description;

    //처리상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    //채택처리
    public void accept() {
        this.status = ReportStatus.ACCEPTED;
    }
    //반려처리
    public void reject(String reason) {
        this.status = ReportStatus.REJECTED;
        this.rejectReason = reason;
    }

}
