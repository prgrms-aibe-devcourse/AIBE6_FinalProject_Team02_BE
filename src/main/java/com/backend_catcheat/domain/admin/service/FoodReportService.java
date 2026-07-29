package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;
import com.backend_catcheat.domain.admin.repository.UnidentifiedFoodReportRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FoodReportService {
    private static final int NAME_MAX_LENGTH = 200;

    private final UnidentifiedFoodReportRepository reportRepository;

    @Transactional
    public FoodReportResponseDTO createReport(Long reporterId, String name){
        String trimmed = name == null ? "" : name.trim();
        if(trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new CustomException(ErrorCode.REPORT_NAME_REQUIRED);
        }
        UnidentifiedFoodReport saved = reportRepository.save(
                UnidentifiedFoodReport.builder()
                        .description(trimmed)
                        .reporterId(reporterId)
                        .build()
        );

        return FoodReportResponseDTO.from(saved);
    }
}
