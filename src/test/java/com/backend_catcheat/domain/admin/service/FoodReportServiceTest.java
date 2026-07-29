package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.repository.UnidentifiedFoodReportRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FoodReportService 단위 테스트 — 사용자 제보 생성.
 */
@ExtendWith(MockitoExtension.class)
class FoodReportServiceTest {

    @Mock
    UnidentifiedFoodReportRepository reportRepository;

    @InjectMocks
    FoodReportService foodReportService;

    @Test
    @DisplayName("정상 이름이면 PENDING 제보를 만들어 저장한다")
    void createReport_valid() {
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FoodReportResponseDTO result = foodReportService.createReport("  훠궈  ");

        assertThat(result.description()).isEqualTo("훠궈");   // 앞뒤 공백 제거됨
        assertThat(result.status()).isEqualTo(ReportStatus.PENDING);
        verify(reportRepository).save(any());
    }

    @Test
    @DisplayName("빈 이름이면 REPORT_NAME_REQUIRED, 저장하지 않는다")
    void createReport_blank() {
        assertThatThrownBy(() -> foodReportService.createReport("   "))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NAME_REQUIRED));

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("null 이름이면 REPORT_NAME_REQUIRED")
    void createReport_null() {
        assertThatThrownBy(() -> foodReportService.createReport(null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NAME_REQUIRED));
    }

    @Test
    @DisplayName("200자를 넘으면 REPORT_NAME_REQUIRED")
    void createReport_tooLong() {
        String tooLong = "가".repeat(201);

        assertThatThrownBy(() -> foodReportService.createReport(tooLong))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NAME_REQUIRED));
    }
}
