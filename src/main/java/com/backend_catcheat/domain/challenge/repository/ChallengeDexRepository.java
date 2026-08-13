package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeDex;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChallengeDexRepository extends JpaRepository<ChallengeDex, Long> {
    Optional<ChallengeDex> findByIdAndDeletedAtIsNull(Long id);

    // 내가 개설한 챌린지
    List<ChallengeDex> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long ownerId);

    // 참여/완료 목록 — 참가 이력의 challengeDexId들로 한 번에 로드
    List<ChallengeDex> findByIdInAndDeletedAtIsNull(List<Long> ids);

    //진행중인 챌린지 조회
    @Query("""
        select c from ChallengeDex c
        where c.deletedAt is null and c.event = false
            and c.startsAt <= :now
            and (c.endsAt is null or c.endsAt > :now)
        order by c.createdAt desc
            """)
    List<ChallengeDex> findOngoing(@Param("now") LocalDateTime now);

    //완료된 챌린지 조회
    @Query("""
            select c from ChallengeDex c
            where c.deletedAt is null and c.event = false
              and c.endsAt is not null and c.endsAt <= :now
            order by c.endsAt desc
            """)
    List<ChallengeDex> findFinished(@Param("now") LocalDateTime now);
    //챌린지 검색
    @Query("""
        select c from ChallengeDex c
        where c.deletedAt is null and c.event = false
          and c.name like concat('%', :keyword, '%')
        order by c.createdAt desc
        """)
    List<ChallengeDex> searchByNameContaining(@Param("keyword") String keyword, Pageable pageable);




}
