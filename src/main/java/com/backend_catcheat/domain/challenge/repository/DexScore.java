package com.backend_catcheat.domain.challenge.repository;

// dex별 랭킹 점수 집계 결과(조회 합/참여 수/해금 수 공용). 쿼리 별칭 dexId, score와 매칭
public interface DexScore {
    Long getDexId();
    long getScore();
}
