package com.backend_catcheat.domain.dex.basicdex.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 별칭 사전은 FE 검색의 유일한 입력이라, 시드가 통째로 빠져도 화면은 조용히 "검색이 좀 안 되네"로만
 * 보인다. 그래서 실제 시드(V6)가 들어와 있는지를 서버 쪽에서 못박아 둔다.
 */
@SpringBootTest
@DisplayName("도감 별칭 사전")
class DexAliasServiceTest {

    @Autowired
    private DexAliasService dexAliasService;

    @Autowired
    private com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository slotRepository;

    @Test
    @DisplayName("200칸 중 대부분에 별칭이 붙어 있다")
    void 별칭이_시드되어_있다() {
        Map<Long, List<String>> aliases = dexAliasService.findAll();

        assertThat(aliases).hasSizeGreaterThanOrEqualTo(190);
        assertThat(aliases.values().stream().mapToInt(List::size).sum())
                .isGreaterThanOrEqualTo(380);
    }

    @Test
    @DisplayName("별칭은 실제 도감 칸에만 붙는다 — 고아 별칭이 없어야 FE가 병합할 수 있다")
    void 고아_별칭이_없다() {
        List<Long> slotIds = slotRepository.findAllByOrderByIdAsc().stream()
                .map(com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity::getId)
                .toList();

        assertThat(slotIds).hasSize(200);
        assertThat(dexAliasService.findAll().keySet()).isSubsetOf(slotIds);
    }

    @Test
    @DisplayName("표기 변형이 별칭으로 들어 있다 — 이게 없으면 검색이 오타를 못 흡수한다")
    void 표기_변형이_들어있다() {
        Map<Long, List<String>> aliases = dexAliasService.findAll();
        List<String> all = aliases.values().stream().flatMap(List::stream).toList();

        assertThat(all).contains("돈가스", "김치찌게", "자장면", "오뎅", "돼지김치찌개");
    }

    @Test
    @DisplayName("V5에서 지운 칸 이름이 부모 칸의 별칭으로 남아 있다")
    void 지운_칸_이름이_별칭으로_남아있다() {
        List<String> all = dexAliasService.findAll().values().stream().flatMap(List::stream).toList();

        assertThat(all).contains("치즈돈까스", "화덕피자", "뼈해장국밥", "컵떡볶이");
    }
}
