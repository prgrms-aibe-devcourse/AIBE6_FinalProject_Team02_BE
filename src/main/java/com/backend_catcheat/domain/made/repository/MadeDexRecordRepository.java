package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MadeDexRecordRepository extends JpaRepository<MadeDexRecord, Long> {

    Optional<MadeDexRecord> findByIdAndDeletedAtIsNull(Long id);

    // 수정과 삭제가 엇갈리면 지운 뒤에 사진이 다시 붙어 S3 객체가 주인 없이 남는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MadeDexRecord r where r.id = :id and r.deletedAt is null")
    Optional<MadeDexRecord> findActiveByIdForUpdate(@Param("id") Long id);

    /** 홈 피드. 인덱스 idx_made_dex_record_feed를 탄다 */
    List<MadeDexRecord> findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long madeDexId, LocalDate loggedOn);

    /** 캘린더 마커. 인덱스 idx_made_dex_record_feed를 탄다 */
    @Query("""
            select distinct r.loggedOn from MadeDexRecord r
            where r.madeDexId = :madeDexId
              and r.loggedOn between :from and :to
              and r.deletedAt is null
            """)
    List<LocalDate> findLoggedOnBetween(
            @Param("madeDexId") Long madeDexId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
