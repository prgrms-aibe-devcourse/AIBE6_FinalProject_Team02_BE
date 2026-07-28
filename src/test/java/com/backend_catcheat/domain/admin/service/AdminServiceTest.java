package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;
import com.backend_catcheat.domain.admin.repository.FoodRegistrationRequestRepository;
import com.backend_catcheat.domain.admin.repository.UnidentifiedFoodReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * AdminService 단위 테스트.
 * 진짜 DB 대신 Repository를 '가짜(mock)'로 갈아끼워, 순수 로직만 빠르게 검증한다.
 *  - @Mock          : 가짜 Repository를 만든다 (DB 접근 없음)
 *  - @InjectMocks   : 그 가짜들을 넣어 AdminService를 조립한다
 *  - when(...).thenReturn(...) : 가짜가 무엇을 돌려줄지 미리 정해둔다
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    UnidentifiedFoodReportRepository reportRepository;
    @Mock
    FoodRegistrationRequestRepository requestRepository;

    @InjectMocks
    AdminService adminService;

    // ===== 제보 큐 =====

    @Test
    @DisplayName("대기 제보 목록을 DTO로 변환해 돌려준다")
    void getPendingReports_returnsDto() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder()
                .description("테스트 제보")
                .build();
        when(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING))
                .thenReturn(List.of(report));

        List<FoodReportResponseDTO> result = adminService.getPendingReports();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).isEqualTo("테스트 제보");
        assertThat(result.get(0).status()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("제보 채택 시 상태가 ACCEPTED로 바뀐다")
    void acceptReport_changesStatus() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder()
                .description("테스트 제보")
                .build();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        adminService.acceptReport(1L);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.ACCEPTED);
    }

    @Test
    @DisplayName("제보 반려 시 REJECTED가 되고 사유가 저장된다")
    void rejectReport_setsReason() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder()
                .description("테스트 제보")
                .build();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        adminService.rejectReport(1L, "부적절한 제보");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(report.getRejectReason()).isEqualTo("부적절한 제보");
    }

    @Test
    @DisplayName("없는 제보를 처리하면 404")
    void acceptReport_notFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.acceptReport(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("이미 처리된 제보를 또 처리하면 409")
    void acceptReport_alreadyProcessed() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder()
                .description("테스트 제보")
                .build();
        report.accept();   // 이미 ACCEPTED 상태로 만들어 둠
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> adminService.acceptReport(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ===== 등록 승인 큐 =====

    @Test
    @DisplayName("등록 요청 완료 시 상태가 COMPLETED로 바뀐다")
    void completeRequest_changesStatus() {
        FoodRegistrationRequest request = FoodRegistrationRequest.builder()
                .description("테스트 등록요청")
                .failureReason("AI 판별 실패")
                .build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        adminService.completeRequest(1L);

        assertThat(request.getStatus()).isEqualTo(RegistrationRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("등록 요청 반려 시 rejectReason만 채워지고 failureReason은 유지된다")
    void rejectRequest_setsRejectReasonKeepsFailureReason() {
        FoodRegistrationRequest request = FoodRegistrationRequest.builder()
                .description("테스트 등록요청")
                .failureReason("AI 판별 실패")
                .build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        adminService.rejectRequest(1L, "등록 불가");

        assertThat(request.getStatus()).isEqualTo(RegistrationRequestStatus.REJECTED);
        assertThat(request.getRejectReason()).isEqualTo("등록 불가");
        assertThat(request.getFailureReason()).isEqualTo("AI 판별 실패");   // 안 지워짐
    }
}
