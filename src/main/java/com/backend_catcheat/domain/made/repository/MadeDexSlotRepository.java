package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MadeDexSlotRepository extends JpaRepository<MadeDexSlot, Long> {

    List<MadeDexSlot> findByMadeDexIdOrderBySortOrderAscIdAsc(Long madeDexId);

    List<MadeDexSlot> findByMadeDexIdAndHiddenAtIsNullOrderBySortOrderAscIdAsc(Long madeDexId);

    long countByMadeDexIdAndHiddenAtIsNull(Long madeDexId);

    boolean existsByMadeDexIdAndNameAndHiddenAtIsNull(Long madeDexId, String name);

    // 아래 셋은 made_dex_record 엔티티가 아직 없어 네이티브로 둔다. TODO(CATCHEAT-34): JPQL로 교체

    @Query(value = """
            select count(*) from made_dex_record
            where slot_id = :slotId and deleted_at is null
            """, nativeQuery = true)
    long countRecords(@Param("slotId") Long slotId);

    // 지운 기록도 slot_id를 잡고 있어, deleted_at을 걸러 세면 hard delete가 FK 위반으로 터진다
    @Query(value = """
            select exists(select 1 from made_dex_record where slot_id = :slotId)
            """, nativeQuery = true)
    boolean existsRecordReferencing(@Param("slotId") Long slotId);

    // 슬롯마다 count를 돌리지 않으려고 한 번에 가져온다
    @Query(value = """
            select distinct r.slot_id from made_dex_record r
            where r.made_dex_id = :madeDexId and r.deleted_at is null
            """, nativeQuery = true)
    List<Long> findSlotIdsWithRecords(@Param("madeDexId") Long madeDexId);
}
