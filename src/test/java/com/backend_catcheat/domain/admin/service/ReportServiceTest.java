package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;
import com.backend_catcheat.domain.admin.repository.UnidentifiedFoodReportRepository;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReportService 단위 테스트 — 제보 생성(사용자) + 처리(관리자).
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    UnidentifiedFoodReportRepository reportRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ReportService reportService;

    @Test
    @DisplayName("정상 이름이면 PENDING 제보를 만들어 저장한다")
    void createReport_valid() {
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FoodReportResponseDTO result = reportService.createReport(10L, "  훠궈  ");

        assertThat(result.description()).isEqualTo("훠궈");   // trim
        assertThat(result.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(result.reporterId()).isEqualTo(10L);
        verify(reportRepository).save(any());
    }

    @Test
    @DisplayName("빈 이름이면 REPORT_NAME_REQUIRED, 저장하지 않는다")
    void createReport_blank() {
        assertThatThrownBy(() -> reportService.createReport(10L, "   "))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NAME_REQUIRED));
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("대기 제보 목록을 DTO로 변환해 돌려준다")
    void getPendingReports_returnsDto() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder()
                .description("테스트 제보")
                .build();
        when(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING))
                .thenReturn(List.of(report));

        List<FoodReportResponseDTO> result = reportService.getReports(ReportStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).isEqualTo("테스트 제보");
        assertThat(result.get(0).status()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("제보 채택 시 상태가 ACCEPTED로 바뀐다")
    void acceptReport_changesStatus() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder().description("x").build();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        reportService.acceptReport(100L, 1L);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.ACCEPTED);
    }

    @Test
    @DisplayName("제보 반려 시 REJECTED가 되고 사유가 저장된다")
    void rejectReport_setsReason() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder().description("x").build();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        reportService.rejectReport(100L, 1L, "부적절한 제보");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(report.getRejectReason()).isEqualTo("부적절한 제보");
    }

    @Test
    @DisplayName("없는 제보를 처리하면 REPORT_NOT_FOUND")
    void acceptReport_notFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.acceptReport(100L, 99L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_FOUND));
    }

    @Test
    @DisplayName("이미 처리된 제보를 또 처리하면 ADMIN_ITEM_ALREADY_HANDLED")
    void acceptReport_alreadyProcessed() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder().description("x").build();
        report.accept();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.acceptReport(100L, 1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ITEM_ALREADY_HANDLED));
    }
}
