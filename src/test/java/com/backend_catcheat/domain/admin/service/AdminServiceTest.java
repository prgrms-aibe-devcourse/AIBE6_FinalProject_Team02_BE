package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;
import com.backend_catcheat.domain.admin.repository.FoodRegistrationRequestRepository;
import com.backend_catcheat.domain.admin.repository.UnidentifiedFoodReportRepository;
import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import com.backend_catcheat.domain.dex.collection.repository.CollectionCardRepository;
import com.backend_catcheat.domain.dex.collection.repository.UserCollectionRepository;
import com.backend_catcheat.domain.registration.entity.Registration;
import com.backend_catcheat.domain.registration.repository.PhotoRepository;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminService 단위 테스트.
 * 진짜 DB 대신 Repository를 '가짜(mock)'로 갈아끼워, 순수 로직만 빠르게 검증한다.
 *  - @Mock          : 가짜 협력 객체를 만든다 (DB 접근 없음)
 *  - @InjectMocks   : 그 가짜들을 넣어 AdminService를 조립한다
 *  - when(...).thenReturn(...) : 가짜가 무엇을 돌려줄지 미리 정해둔다
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    UnidentifiedFoodReportRepository reportRepository;
    @Mock
    FoodRegistrationRequestRepository requestRepository;
    @Mock
    CollectionCardRepository cardRepository;
    @Mock
    UserCollectionRepository userCollectionRepository;
    @Mock
    RegistrationRepository registrationRepository;
    @Mock
    PhotoRepository photoRepository;
    @Mock
    S3PresignedUrlService presignedUrlService;

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
    @DisplayName("없는 제보를 처리하면 REPORT_NOT_FOUND")
    void acceptReport_notFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.acceptReport(99L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_FOUND));
    }

    @Test
    @DisplayName("이미 처리된 제보를 또 처리하면 ADMIN_ITEM_ALREADY_HANDLED")
    void acceptReport_alreadyProcessed() {
        UnidentifiedFoodReport report = UnidentifiedFoodReport.builder()
                .description("테스트 제보")
                .build();
        report.accept();   // 이미 ACCEPTED 상태로 만들어 둠
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> adminService.acceptReport(1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ITEM_ALREADY_HANDLED));
    }

    // ===== 등록 요청 큐 =====

    @Test
    @DisplayName("등록 완료 시 카드가 칸에 붙고(해금) 상태가 COMPLETED로 바뀐다")
    void completeRequest_unlocksAndCompletes() {
        // 검토 대기 상태의 카드 (칸에 아직 안 붙음)
        CollectionCard card = CollectionCard.awaitingReview(
                5L, 7L, 1L, null, null, null, null, LocalDateTime.now());
        FoodRegistrationRequest request = FoodRegistrationRequest.builder()
                .description("김치찌개")
                .collectionCardId(100L)
                .evidencePhotoId(1L)
                .build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(cardRepository.findById(100L)).thenReturn(Optional.of(card));

        Registration registration = mock(Registration.class);
        when(registration.getUserId()).thenReturn(10L);
        when(registrationRepository.findById(5L)).thenReturn(Optional.of(registration));

        // 아직 안 열린 칸 → 새로 해금. 저장된 컬렉션은 id를 부여받는다(DB가 하는 일 흉내).
        UserCollection unlocked = mock(UserCollection.class);
        when(unlocked.getId()).thenReturn(500L);
        when(userCollectionRepository.findByUserIdAndSlotId(10L, 7L)).thenReturn(Optional.empty());
        when(userCollectionRepository.save(any())).thenReturn(unlocked);

        adminService.completeRequest(1L);

        assertThat(request.getStatus()).isEqualTo(RegistrationRequestStatus.COMPLETED);
        assertThat(card.isAwaitingReview()).isFalse();   // 칸에 붙음 = 해금됨
        verify(userCollectionRepository).save(any());     // 최초 해금이므로 저장 호출
    }

    @Test
    @DisplayName("등록 요청 반려 시 rejectReason만 채워지고 failureReason은 유지된다")
    void rejectRequest_setsRejectReasonKeepsFailureReason() {
        FoodRegistrationRequest request = FoodRegistrationRequest.builder()
                .description("김치찌개")
                .failureReason("AI 판별 실패")
                .collectionCardId(100L)
                .build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        adminService.rejectRequest(1L, "등록 불가");

        assertThat(request.getStatus()).isEqualTo(RegistrationRequestStatus.REJECTED);
        assertThat(request.getRejectReason()).isEqualTo("등록 불가");
        assertThat(request.getFailureReason()).isEqualTo("AI 판별 실패");   // 안 지워짐
    }

    @Test
    @DisplayName("없는 등록 요청을 처리하면 REGISTRATION_REQUEST_NOT_FOUND")
    void completeRequest_notFound() {
        when(requestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.completeRequest(99L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REGISTRATION_REQUEST_NOT_FOUND));
    }
}
