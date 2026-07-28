package com.backend_catcheat.domain.admin.dto;

import java.time.LocalDateTime;

/**
 * 검토 큐 항목 1건. 관리자가 수락/반려를 판단할 최소 정보만 담는다.
 *
 * @param evidenceUrl 증빙 사진의 presigned URL — 버킷이 비공개라 만료되는 주소로만 볼 수 있다
 */
public record ReviewItemResponse(
        Long reviewItemId,
        Long cardId,
        Long slotId,
        String slotName,
        String memo,
        String locationName,
        LocalDateTime collectedAt,
        String evidenceUrl
) {
}
