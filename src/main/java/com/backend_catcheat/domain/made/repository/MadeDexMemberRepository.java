package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.dto.MadeDexMemberCountDTO;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MadeDexMemberRepository extends JpaRepository<MadeDexMember, Long> {

    List<MadeDexMember> findByUserId(Long userId);

    /** 그룹마다 count를 돌리면 목록 길이만큼 쿼리가 나가므로 한 번에 집계한다 */
    @Query("""
            select new com.backend_catcheat.domain.made.dto.MadeDexMemberCountDTO(m.madeDexId, count(m))
            from MadeDexMember m
            where m.madeDexId in :madeDexIds
            group by m.madeDexId
            """)
    List<MadeDexMemberCountDTO> countByMadeDexIds(@Param("madeDexIds") Collection<Long> madeDexIds);
}
