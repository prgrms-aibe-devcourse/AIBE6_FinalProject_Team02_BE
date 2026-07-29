package com.backend_catcheat.domain.dex.basicdex.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("도감 별칭 사전")
class DexAliasServiceTest {

    @Autowired
    private DexAliasService dexAliasService;

    @Autowired
    private com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository slotRepository;

    @Test
    @DisplayName("별칭은 실제 도감 칸에만 붙는다 — 고아 별칭이 없어야 FE가 병합할 수 있다")
    void 고아_별칭이_없다() {
        List<Long> slotIds = slotRepository.findAllByOrderByIdAsc().stream()
                .map(com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity::getId)
                .toList();

        assertThat(dexAliasService.findAll().keySet()).isSubsetOf(slotIds);
    }
}
