package com.backend_catcheat.spike.vision.dto;

import java.util.List;

/**
 * @param verdicts 유저가 제시한 음식 이름별 검증 결과. 요청한 이름 수와 같아야 한다
 */
public record VisionAnalysisResponse(
        List<FoodVerdict> verdicts,
        SpikeMetrics metrics,
        String rawAiText
) {

    /**
     * 음식 이름 하나에 대한 검증 결과.
     *
     * @param requestedName 유저가 입력한 음식 이름
     * @param matched       사진에 그 음식이 있는지. false면 해당 칸을 해금하지 않는다
     * @param confidence    판정 확신도 0.0 ~ 1.0
     * @param reason        불일치 사유 (matched=true면 빈 문자열)
     * @param slotId        매핑된 도감 칸 id. 도감에 없으면 null
     * @param slotName      매핑된 도감 칸 이름
     * @param category      도감 칸 카테고리
     * @param matchType     매핑 경로 (EXACT / ALIAS / CONTAINS / UNMAPPED)
     * @param unlockable    해금 가능 여부 — 검증을 통과했고 도감에도 있는 경우에만 true
     */
    public record FoodVerdict(
            String requestedName,
            boolean matched,
            double confidence,
            String reason,
            Long slotId,
            String slotName,
            String category,
            String matchType,
            boolean unlockable
    ) {
    }
}
