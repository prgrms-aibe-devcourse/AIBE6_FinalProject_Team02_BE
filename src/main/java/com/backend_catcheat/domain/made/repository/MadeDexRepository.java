package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDex;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MadeDexRepository extends JpaRepository<MadeDex, Long> {

    List<MadeDex> findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(Collection<Long> ids);

    Optional<MadeDex> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 참여 처리 전용. 그룹 행을 잠근 뒤 정원을 센다.
     * 잠금 없이 count만 비교하면 마지막 한 자리를 두 요청이 동시에 통과한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MadeDex m where m.id = :id and m.deletedAt is null")
    Optional<MadeDex> findActiveByIdForUpdate(@Param("id") Long id);
}
