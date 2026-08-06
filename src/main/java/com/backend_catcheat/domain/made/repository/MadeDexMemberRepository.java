package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.dto.MadeDexMemberCountDTO;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MadeDexMemberRepository extends JpaRepository<MadeDexMember, Long> {

    List<MadeDexMember> findByUserId(Long userId);

    Optional<MadeDexMember> findByMadeDexIdAndUserId(Long madeDexId, Long userId);

    boolean existsByMadeDexIdAndUserId(Long madeDexId, Long userId);

    long countByMadeDexId(Long madeDexId);

    // 그룹장 우선 정렬은 서비스에서 얹는다
    List<MadeDexMember> findByMadeDexIdOrderByJoinedAtAscIdAsc(Long madeDexId);

    // 지운 행 수를 돌려주므로 "이미 나간 사람"을 호출부에서 구분할 수 있다
    long deleteByMadeDexIdAndUserId(Long madeDexId, Long userId);

    /** 그룹마다 count를 돌리면 목록 길이만큼 쿼리가 나가므로 한 번에 집계한다 */
    @Query("""
            select new com.backend_catcheat.domain.made.dto.MadeDexMemberCountDTO(m.madeDexId, count(m))
            from MadeDexMember m
            where m.madeDexId in :madeDexIds
            group by m.madeDexId
            """)
    List<MadeDexMemberCountDTO> countByMadeDexIds(@Param("madeDexIds") Collection<Long> madeDexIds);
}
