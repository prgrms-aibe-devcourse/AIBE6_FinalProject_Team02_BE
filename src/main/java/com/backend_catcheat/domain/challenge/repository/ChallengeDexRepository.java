package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeDex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
    List<ChallengeDex> searchByNameContaining(@Param("keyword") String keyword);

    /** 랭킹 목록 — 집계·정렬·페이징을 DB에서 끝낸다 */

    /** 진행중 전체 건수 — totalElements용으로 페이지 조회와 짝을 이룸 */
    @Query(nativeQuery = true, value = """
            select count(*)
            from challenge_dex c
            where c.deleted_at is null and c.is_event = false
              and c.starts_at <= :now and (c.ends_at is null or c.ends_at > :now)
            """)
    long countOngoing(@Param("now") LocalDateTime now);

    /** 최신순 한 페이지 */
    @Query(nativeQuery = true, value = """
            select c.id
            from challenge_dex c
            where c.deleted_at is null and c.is_event = false
              and c.starts_at <= :now and (c.ends_at is null or c.ends_at > :now)
            order by c.created_at desc, c.id desc
            limit :size offset :offset
            """)
    List<Long> findOngoingIdsLatest(@Param("now") LocalDateTime now,
                                    @Param("size") int size,
                                    @Param("offset") long offset);

    /** 최근 N일 조회수 순 한 페이지 */
    @Query(nativeQuery = true, value = """
            select c.id as dexId, coalesce(s.agg, 0)::bigint as score
            from challenge_dex c
            left join (
                select v.challenge_dex_id as dex_id, sum(v.view_count) as agg
                from challenge_view_daily v
                where v.view_date >= :sinceDate
                group by v.challenge_dex_id
            ) s on s.dex_id = c.id
            where c.deleted_at is null and c.is_event = false
              and c.starts_at <= :now and (c.ends_at is null or c.ends_at > :now)
            order by score desc, c.created_at desc, c.id desc
            limit :size offset :offset
            """)
    List<DexScore> findOngoingRankedByViews(@Param("now") LocalDateTime now,
                                            @Param("sinceDate") LocalDate sinceDate,
                                            @Param("size") int size,
                                            @Param("offset") long offset);

    /** 최근 N일 신규 참여 순 한 페이지 */
    @Query(nativeQuery = true, value = """
            select c.id as dexId, coalesce(s.agg, 0)::bigint as score
            from challenge_dex c
            left join (
                select p.challenge_dex_id as dex_id, count(*) as agg
                from challenge_participant p
                where p.joined_at >= :since
                group by p.challenge_dex_id
            ) s on s.dex_id = c.id
            where c.deleted_at is null and c.is_event = false
              and c.starts_at <= :now and (c.ends_at is null or c.ends_at > :now)
            order by score desc, c.created_at desc, c.id desc
            limit :size offset :offset
            """)
    List<DexScore> findOngoingRankedByParticipants(@Param("now") LocalDateTime now,
                                                    @Param("since") LocalDateTime since,
                                                    @Param("size") int size,
                                                    @Param("offset") long offset);

    /** 최근 N일 해금 순 한 페이지 */
    @Query(nativeQuery = true, value = """
            select c.id as dexId, coalesce(s.agg, 0)::bigint as score
            from challenge_dex c
            left join (
                select p.challenge_dex_id as dex_id, count(*) as agg
                from challenge_unlock u
                join challenge_participant p on u.challenge_participant_id = p.id
                where u.unlocked_at >= :since
                group by p.challenge_dex_id
            ) s on s.dex_id = c.id
            where c.deleted_at is null and c.is_event = false
              and c.starts_at <= :now and (c.ends_at is null or c.ends_at > :now)
            order by score desc, c.created_at desc, c.id desc
            limit :size offset :offset
            """)
    List<DexScore> findOngoingRankedByUnlocks(@Param("now") LocalDateTime now,
                                               @Param("since") LocalDateTime since,
                                               @Param("size") int size,
                                               @Param("offset") long offset);
}
