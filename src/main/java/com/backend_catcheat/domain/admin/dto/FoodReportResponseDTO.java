package com.backend_catcheat.domain.admin.dto;

import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;

import java.time.LocalDateTime;

public record FoodReportResponseDTO(
        Long id,
        Long registrationId,
        String description,
        ReportStatus status,
        LocalDateTime createdAt

) {
    public static FoodReportResponseDTO from(UnidentifiedFoodReport report) {
        return new FoodReportResponseDTO(
                report.getId(),
                report.getRegistrationId(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
