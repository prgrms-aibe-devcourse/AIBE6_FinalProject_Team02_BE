package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodRegistrationRequestResponseDTO;
import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import com.backend_catcheat.domain.admin.repository.FoodRegistrationRequestRepository;
import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import com.backend_catcheat.domain.dex.collection.repository.CollectionCardRepository;
import com.backend_catcheat.domain.dex.collection.repository.UserCollectionRepository;
import com.backend_catcheat.domain.registration.repository.PhotoRepository;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.global.event.SlotsUnlockedEvent;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 음식 등록 요청(AI가 못 끝낸 등록) 도메인 서비스.
 * 완료(complete)는 검토 대기 카드를 도감 칸에 붙여 해금한다 — 별 랭크·수집률이 이때 반영된다.
 */
@Service
@RequiredArgsConstructor
public class RegistrationRequestService {

    private final FoodRegistrationRequestRepository requestRepository;
    private final CollectionCardRepository cardRepository;
    private final UserCollectionRepository userCollectionRepository;
    private final RegistrationRepository registrationRepository;
    private final PhotoRepository photoRepository;
    private final S3PresignedUrlService presignedUrlService;
    private final ApplicationEventPublisher eventPublisher;

    /** 대기 중(PENDING) 등록 요청 목록을 최신순으로. */
    @Transactional(readOnly = true)
    public List<FoodRegistrationRequestResponseDTO> getPendingRequests() {
        return requestRepository.findByStatusOrderByCreatedAtDesc(RegistrationRequestStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 등록 완료 — 검토 대기 카드를 칸에 붙여 해금한다(별 랭크·수집률 반영). */
    @Transactional
    public void completeRequest(Long requestId) {
        FoodRegistrationRequest request = loadPending(requestId);
        unlockCard(request.getCollectionCardId());
        request.complete();
    }

    /** 등록 요청 반려 (사유 포함). 카드는 칸에 붙지 않은 채 남는다(증빙 보존). */
    @Transactional
    public void rejectRequest(Long requestId, String reason) {
        loadPending(requestId).reject(reason);
    }

    /**
     * 검토 대기 카드를 해당 유저의 칸에 붙인다.
     * 랭크를 등록 시점이 아니라 여기서 매기는 이유: 대기 중 다른 등록으로 같은 칸이 먼저 열렸을 수 있다.
     */
    private void unlockCard(Long collectionCardId) {
        CollectionCard card = cardRepository.findById(collectionCardId)
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

        // 해금됐으니 수집 뱃지 평가 트리거 (커밋 후 별도 트랜잭션에서 지급)
        eventPublisher.publishEvent(new SlotsUnlockedEvent(userId));
    }

    private FoodRegistrationRequest loadPending(Long requestId) {
        FoodRegistrationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.REGISTRATION_REQUEST_NOT_FOUND));
        if (request.getStatus() != RegistrationRequestStatus.PENDING) {
            throw new CustomException(ErrorCode.ADMIN_ITEM_ALREADY_HANDLED);   // 중복 처리 방지
        }
        return request;
    }

    /** 엔티티 → 응답 DTO. 증빙 사진은 presigned URL로 변환한다. */
    private FoodRegistrationRequestResponseDTO toResponse(FoodRegistrationRequest r) {
        String evidenceUrl = r.getEvidencePhotoId() == null ? null
                : photoRepository.findById(r.getEvidencePhotoId())
                        .map(photo -> presignedUrlService.createDownloadUrl(photo.getUrl()))
                        .orElse(null);
        return new FoodRegistrationRequestResponseDTO(
                r.getId(), r.getRegistrationId(), r.getCollectionCardId(),
                r.getDescription(), r.getFailureReason(), r.getStatus(),
                evidenceUrl, r.getCreatedAt());
    }
}
