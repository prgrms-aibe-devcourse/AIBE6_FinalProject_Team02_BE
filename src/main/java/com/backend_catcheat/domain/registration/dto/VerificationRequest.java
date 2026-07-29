package com.backend_catcheat.domain.registration.dto;

import java.util.List;

/**
 * @param registrationId     재시도면 기존 등록 건 id, 최초 검증이면 null
 * @param photoKeys          업로드된 사진들의 S3 key (1~5장)
 * @param analysisPhotoIndex photoKeys 중 AI에 보낼 1장의 위치. null이면 첫 장
 * @param slotIds            도감 칸 id (1~5개). 자유 타이핑이 아니라 검색해서 고른 칸만 온다
 */
public record VerificationRequest(
        Long registrationId,
        List<String> photoKeys,
        Integer analysisPhotoIndex,
        List<Long> slotIds
) {
}
