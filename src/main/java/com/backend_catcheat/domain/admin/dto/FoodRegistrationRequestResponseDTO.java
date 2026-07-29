package com.backend_catcheat.domain.admin.dto;

import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;

import java.time.LocalDateTime;

/**
 * 음식 등록 요청(AI가 못 끝낸 등록) 응답 DTO.
 * evidenceUrl은 증빙 사진의 presigned URL — 버킷이 비공개라 만료되는 주소로만 볼 수 있다.
 * (slotName은 description에 담겨 오므로 별도 필드를 두지 않는다)
 *
 * 카드/사진 조인이 필요해 엔티티만으로 못 만든다 → 정적 from() 대신 RegistrationRequestService에서 조립한다.
 */
public record FoodRegistrationRequestResponseDTO(
        Long id,
        Long registrationId,
        Long collectionCardId,
        String description,
        String failureReason,
        RegistrationRequestStatus status,
        String evidenceUrl,
        LocalDateTime createdAt
) {
}
