package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodRegistrationRequestResponseDTO;
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
import com.backend_catcheat.domain.registration.repository.PhotoRepository;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 콘솔 (ADM).
 *  1) 미확인 음식 제보 큐  : 사용자 제보 → 채택(accept)/반려(reject)
 *  2) 음식 등록 요청 큐   : AI가 못 끝낸 등록 → 완료(complete: 칸 해금)/반려(reject)
 *
 * 완료(complete)는 검토 대기 카드를 도감 칸에 붙여 해금한다 —
 * 별 랭크가 정해지고 수집률에 반영되는 시점이 바로 여기다.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UnidentifiedFoodReportRepository reportRepository;
    private final FoodRegistrationRequestRepository requestRepository;

    // 등록 요청 완료 시 칸을 해금하기 위한 dex/registration 협력 객체
    private final CollectionCardRepository cardRepository;
    private final UserCollectionRepository userCollectionRepository;
    private final RegistrationRepository registrationRepository;
    private final PhotoRepository photoRepository;
    private final S3PresignedUrlService presignedUrlService;

    // ===== 1) 미확인 음식 제보 큐 =====

    @Transactional(readOnly = true)
    public List<FoodReportResponseDTO> getPendingReports() {
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING)
                .stream()
                .map(FoodReportResponseDTO::from)
                .toList();
    }

    /** 제보 채택 (→ 신규 도감 칸 생성 대상). */
    @Transactional
    public void acceptReport(Long reportId) {
        loadPendingReport(reportId).accept();   // 더티 체킹 → 자동 UPDATE
    }

    /** 제보 반려 (사유 포함). */
    @Transactional
    public void rejectReport(Long reportId, String reason) {
        loadPendingReport(reportId).reject(reason);
    }

    // ===== 2) 음식 등록 요청 큐 =====

    @Transactional(readOnly = true)
    public List<FoodRegistrationRequestResponseDTO> getPendingRequests() {
        return requestRepository.findByStatusOrderByCreatedAtDesc(RegistrationRequestStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 등록 완료 — 검토 대기 카드를 칸에 붙여 해금한다.
     * (기존 ReviewService.approve가 하던 일: 별 랭크 부여 + 수집률 반영)
     * 랭크를 등록 시점이 아니라 여기서 매기는 이유: 대기 중 다른 등록으로 같은 칸이 먼저 열렸을 수 있다.
     */
    @Transactional
    public void completeRequest(Long requestId) {
        FoodRegistrationRequest request = loadPendingRequest(requestId);

        CollectionCard card = cardRepository.findById(request.getCollectionCardId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGISTRATION_NOT_FOUND));

        Long userId = registrationRepository.findById(card.getRegistrationId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGISTRATION_NOT_FOUND))
                .getUserId();

        // 이미 열린 칸이면 별만 올리고(중복 수집), 없으면 새로 해금한다
        UserCollection collection = userCollectionRepository
                .findByUserIdAndSlotId(userId, card.getSlotId())
                .map(existing -> {
                    existing.collectAgain();
                    return existing;
                })
                .orElseGet(() -> userCollectionRepository.save(
                        UserCollection.unlock(userId, card.getSlotId(), card.getCollectedAt())));

        card.attachTo(collection.getId());   // 이 순간 칸에 붙는다 = 해금
        request.complete();
    }

    /** 등록 요청 반려 (사유 포함). 카드는 칸에 붙지 않은 채로 남는다(증빙 보존). */
    @Transactional
    public void rejectRequest(Long requestId, String reason) {
        loadPendingRequest(requestId).reject(reason);
    }

    // ===== 공통 헬퍼 =====

    private UnidentifiedFoodReport loadPendingReport(Long reportId) {
        UnidentifiedFoodReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
        requirePending(report.getStatus() == ReportStatus.PENDING);
        return report;
    }

    private FoodRegistrationRequest loadPendingRequest(Long requestId) {
        FoodRegistrationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.REGISTRATION_REQUEST_NOT_FOUND));
        requirePending(request.getStatus() == RegistrationRequestStatus.PENDING);
        return request;
    }

    /** PENDING이 아니면(이미 처리됨) 409로 막아 중복 처리 방지. */
    private void requirePending(boolean isPending) {
        if (!isPending) {
            throw new CustomException(ErrorCode.ADMIN_ITEM_ALREADY_HANDLED);
        }
    }

    private FoodRegistrationRequestResponseDTO toResponse(FoodRegistrationRequest r) {
        String evidenceUrl = r.getEvidencePhotoId() == null ? null
                : photoRepository.findById(r.getEvidencePhotoId())
                        .map(photo -> presignedUrlService.createDownloadUrl(photo.getUrl()))
                        .orElse(null);

        return new FoodRegistrationRequestResponseDTO(
                r.getId(),
                r.getRegistrationId(),
                r.getCollectionCardId(),
                r.getDescription(),
                r.getFailureReason(),
                r.getStatus(),
                evidenceUrl,
                r.getCreatedAt());
    }
}
