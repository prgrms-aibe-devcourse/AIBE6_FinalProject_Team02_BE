package com.backend_catcheat.domain.admin.dto;

import com.backend_catcheat.domain.admin.entity.ReportStatus;

import java.time.LocalDateTime;

/**
 * 미확인 음식 제보 응답 DTO.
 * 제보자 이름(reporterName) 조인이 필요해 엔티티만으로 못 만든다 → ReportService에서 조립한다.
 */
public record FoodReportResponseDTO(
        Long id,
        Long registrationId,
        String description,
        ReportStatus status,
        LocalDateTime createdAt,
        Long reporterId,
        String reporterName
) {
}
