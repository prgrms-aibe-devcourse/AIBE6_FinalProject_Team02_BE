package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MadeDexRecordRepository extends JpaRepository<MadeDexRecord, Long> {

    Optional<MadeDexRecord> findByIdAndDeletedAtIsNull(Long id);

    /** 홈 피드. 인덱스 idx_made_dex_record_feed를 탄다 */
    List<MadeDexRecord> findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long madeDexId, LocalDate loggedOn);
}
