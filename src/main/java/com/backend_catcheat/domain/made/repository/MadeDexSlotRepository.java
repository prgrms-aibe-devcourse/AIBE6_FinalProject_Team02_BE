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

    @Query("""
            select count(r) from MadeDexRecord r
            where r.slotId = :slotId and r.deletedAt is null
            """)
    long countRecords(@Param("slotId") Long slotId);

    // 지운 기록도 slot_id를 잡고 있어, deletedAt을 걸러 세면 hard delete가 FK 위반으로 터진다
    @Query("""
            select count(r) > 0 from MadeDexRecord r
            where r.slotId = :slotId
            """)
    boolean existsRecordReferencing(@Param("slotId") Long slotId);

    // 슬롯마다 count를 돌리지 않으려고 한 번에 가져온다
    @Query("""
            select distinct r.slotId from MadeDexRecord r
            where r.madeDexId = :madeDexId and r.deletedAt is null
            """)
    List<Long> findSlotIdsWithRecords(@Param("madeDexId") Long madeDexId);
}
