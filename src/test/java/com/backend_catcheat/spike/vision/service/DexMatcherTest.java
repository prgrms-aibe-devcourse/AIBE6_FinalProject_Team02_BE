package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.service.DexMatcher.MatchResult;
import com.backend_catcheat.spike.vision.service.DexMatcher.MatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("도감 칸 매핑")
class DexMatcherTest {

    private DexMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new DexMatcher(new DexCatalog(new ObjectMapper()));
    }

    @Test
    @DisplayName("칸 이름과 정확히 일치하면 EXACT")
    void 정확히_일치하면_EXACT() {
        MatchResult result = matcher.match("김치찌개");

        assertThat(result.matchType()).isEqualTo(MatchType.EXACT);
        assertThat(result.slot().name()).isEqualTo("김치찌개");
        assertThat(result.slot().category()).isEqualTo("찌개·전골");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "돼지김치찌개, 김치찌개",
            "김치찌게,     김치찌개",
            "자장면,       짜장면",
            "돈가스,       돈까스",
            "물냉면,       냉면",
    })
    @DisplayName("alias 사전에 있으면 ALIAS로 흡수한다")
    void alias로_흡수한다(String aiName, String expectedSlot) {
        MatchResult result = matcher.match(aiName);

        assertThat(result.matchType()).isEqualTo(MatchType.ALIAS);
        assertThat(result.slot().name()).isEqualTo(expectedSlot);
    }

    @Test
    @DisplayName("수식어가 붙어도 칸 이름을 포함하면 CONTAINS")
    void 수식어가_붙으면_CONTAINS() {
        MatchResult result = matcher.match("돼지고기 김치 찌개");

        assertThat(result.matchType()).isEqualTo(MatchType.CONTAINS);
        assertThat(result.slot().name()).isEqualTo("김치찌개");
    }

    @Test
    @DisplayName("CONTAINS는 더 구체적인(긴) 칸을 고른다")
    void CONTAINS는_구체적인_칸을_고른다() {
        // "해물 순두부찌개"는 "순두부찌개"와 "김치찌개" 중 전자
        MatchResult result = matcher.match("얼큰 해물 순두부찌개");

        assertThat(result.slot().name()).isEqualTo("순두부찌개");
    }

    @ParameterizedTest
    @ValueSource(strings = {"마라탕", "쌀국수", "타코", ""})
    @DisplayName("도감에 없는 음식은 UNMAPPED — 수동 검색 폴백 대상")
    void 도감에_없으면_UNMAPPED(String aiName) {
        MatchResult result = matcher.match(aiName);

        assertThat(result.matchType()).isEqualTo(MatchType.UNMAPPED);
        assertThat(result.isMapped()).isFalse();
    }

    @Test
    @DisplayName("null 이름도 UNMAPPED로 처리한다")
    void null이면_UNMAPPED() {
        assertThat(matcher.match(null).matchType()).isEqualTo(MatchType.UNMAPPED);
    }

    @Test
    @DisplayName("공백·가운뎃점 차이는 무시한다")
    void 공백과_구분자를_무시한다() {
        assertThat(matcher.match(" 김치 찌개 ").slot().name()).isEqualTo("김치찌개");
        assertThat(matcher.match("김치·찌개").slot().name()).isEqualTo("김치찌개");
    }
}
