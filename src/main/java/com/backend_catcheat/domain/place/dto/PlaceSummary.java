package com.backend_catcheat.domain.place.dto;

/**
 * 검색 결과 한 줄.
 *
 * @param placeId     카카오 장소 ID. 저장하지 않고 목록 key로만 쓴다
 * @param category    분류의 마지막 조각 (예: "음식점 &gt; 한식 &gt; 육류,고기" → "육류,고기")
 * @param roadAddress 없는 장소가 있어 null 가능 — 그때는 address를 쓴다
 */
public record PlaceSummary(
        String placeId,
        String name,
        String category,
        String roadAddress,
        String address,
        Double lat,
        Double lng
) {
}
