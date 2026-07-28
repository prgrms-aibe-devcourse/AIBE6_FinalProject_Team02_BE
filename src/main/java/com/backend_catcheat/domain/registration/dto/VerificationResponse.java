package com.backend_catcheat.domain.registration.dto;

import java.util.List;

/**
 * @param registrationId 재시도 때 그대로 돌려보내야 상한이 이어서 세어진다
 * @param verdicts       요청한 칸 순서와 같은 순서의 판정 결과
 * @param allMatched     전부 통과했는지. false면 재시도 또는 수동 폴백으로 간다
 * @param retriesLeft    남은 재분석 횟수 (상한 2회)
 */
public record VerificationResponse(
        Long registrationId,
        List<SlotVerdict> verdicts,
        boolean allMatched,
        int retriesLeft
) {

    /**
     * @param matched    사진에 그 음식이 있는지. false면 이 칸은 해금되지 않는다
     * @param confidence 판정 확신도 0.0 ~ 1.0
     * @param reason     불일치 사유. 통과면 빈 문자열
     */
    public record SlotVerdict(
            Long slotId,
            String slotName,
            String category,
            boolean matched,
            double confidence,
            String reason
    ) {
    }
}
