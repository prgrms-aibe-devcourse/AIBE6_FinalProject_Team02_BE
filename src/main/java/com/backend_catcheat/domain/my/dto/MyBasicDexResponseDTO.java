package com.backend_catcheat.domain.my.dto;

import java.time.LocalDateTime;

public record MyBasicDexResponseDTO (
        Long id,
        String name,
        String category,
        String illustrationUrl,
        boolean unlocked,
        int rank,
        LocalDateTime firstCollectedAt,
        long cardCount,
        /**
         * 최근 24시간 안에 처음 열린 칸. 그리드에 New 스티커가 붙음
         */
        boolean recentlyUnlocked,
        /** 운영진 검토를 기다리는 칸. New와 같은 자리에 검토대기 스티커가 붙음 */
        boolean awaitingReview
){
}
