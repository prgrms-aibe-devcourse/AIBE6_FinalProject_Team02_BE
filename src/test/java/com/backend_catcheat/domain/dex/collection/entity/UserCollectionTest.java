package com.backend_catcheat.domain.dex.collection.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 별 랭크는 §5.1의 확정 사항이다 —
 * "최초 수집 별1 → 중복 2회차 별2 → 3회차 별3. 최대 별 3개, 4회차부터 랭크 변화 없음."
 */
@DisplayName("도감 칸 해금 — 별 랭크")
class UserCollectionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Test
    @DisplayName("최초 수집은 별 1개")
    void 최초_수집은_별_하나() {
        UserCollection collection = UserCollection.unlock(1L, 10L, NOW);

        assertThat(collection.getRank()).isEqualTo(1);
        assertThat(collection.getFirstCollectedAt()).isEqualTo(NOW);
        assertThat(collection.isMaxRank()).isFalse();
    }

    @Test
    @DisplayName("1 → 2 → 3으로 오르고 3에서 멈춘다")
    void 랭크_전이() {
        UserCollection collection = UserCollection.unlock(1L, 10L, NOW);

        collection.collectAgain();
        assertThat(collection.getRank()).isEqualTo(2);

        collection.collectAgain();
        assertThat(collection.getRank()).isEqualTo(3);
        assertThat(collection.isMaxRank()).isTrue();

        // 4회차 이후로는 아무리 먹어도 변하지 않는다
        collection.collectAgain();
        collection.collectAgain();
        collection.collectAgain();
        assertThat(collection.getRank()).isEqualTo(3);
    }

    @Test
    @DisplayName("최초 수집 시각은 중복 수집에도 바뀌지 않는다 — 카드에 '첫 수집일'로 남는다")
    void 최초_수집_시각은_고정된다() {
        UserCollection collection = UserCollection.unlock(1L, 10L, NOW);

        collection.collectAgain();
        collection.collectAgain();

        assertThat(collection.getFirstCollectedAt()).isEqualTo(NOW);
    }
}
