package com.backend_catcheat.domain.dex.basicdex.service;

import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexAliasEntity;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexAliasRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 도감 칸 별칭 사전 제공.
 *
 * 매칭(초성·별칭 검색)은 서버가 하지 않는다 — 도감 200칸은 마이그레이션으로만 바뀌는
 * 마스터 데이터이고 전체가 gzip 4KB라, 클라이언트가 통째로 들고 즉시 거르는 편이
 * 타이핑마다 왕복하는 것보다 빠르고 싸다. 서버는 사전을 넘겨주기만 한다.
 *
 * 신뢰 경계는 검색이 아니라 등록에 있다 — 등록 확정 시 slotId가 실제 존재하는 칸인지,
 * 그 칸의 AI 검증이 통과했는지는 서버가 다시 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DexAliasService {

    private final BasicDexAliasRepository aliasRepository;

    /** 마이그레이션으로만 바뀌므로 기동 시 한 번만 읽는다. 도감 확장은 배포를 동반한다. */
    private volatile Map<Long, List<String>> aliasesBySlotId = Map.of();

    @PostConstruct
    void loadAliases() {
        this.aliasesBySlotId = aliasRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        BasicDexAliasEntity::getBasicDexId,
                        Collectors.mapping(BasicDexAliasEntity::getAlias, Collectors.toUnmodifiableList())));

        log.info("도감 별칭 사전 로드 — {}칸 / 별칭 {}개",
                aliasesBySlotId.size(),
                aliasesBySlotId.values().stream().mapToInt(List::size).sum());
    }

    /** 도감 칸 id → 별칭 목록. 별칭이 없는 칸은 키 자체가 없다(이름으로만 검색된다). */
    public Map<Long, List<String>> findAll() {
        return aliasesBySlotId;
    }
}
