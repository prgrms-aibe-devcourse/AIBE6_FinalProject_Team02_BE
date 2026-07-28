package com.backend_catcheat.domain.registration.dto;

import java.util.List;

/**
 * 음식별 기록을 마치고 도감을 여는 요청.
 *
 * @param cards    음식(도감 칸) 하나당 카드 하나
 * @param location 수집 위치. 같은 등록 건의 카드에 일괄 적용된다. 스킵 가능(null)
 */
public record RegistrationConfirmRequest(
        List<CardInput> cards,
        LocationInput location
) {

    /**
     * @param cardPhotoKeys 이 카드에 붙일 사진들. **비우면 분석 사진이 자동 첨부된다** —
     *                      아무것도 고르지 않아도 등록이 완료돼야 한다
     * @param thumbnailKey  카드 썸네일. 미지정 시 첫 번째 카드 사진
     * @param memo          선택, 100자
     */
    public record CardInput(
            Long slotId,
            List<String> cardPhotoKeys,
            String thumbnailKey,
            String memo
    ) {
    }

    public record LocationInput(String name, Double lat, Double lng) {
    }
}
