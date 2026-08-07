package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MadeDexRecordPhotoRepository extends JpaRepository<MadeDexRecordPhoto, Long> {

    List<MadeDexRecordPhoto> findByRecordIdOrderBySortOrderAsc(Long recordId);

    /** 기록마다 조회하면 피드에서 기록 수만큼 쿼리가 나간다 */
    List<MadeDexRecordPhoto> findByRecordIdInOrderBySortOrderAsc(Collection<Long> recordIds);

    /**
     * 다른 살아 있는 기록이 같은 사진을 쓰고 있는지.
     * 확인 없이 지우면 남의 기록에 붙은 사진까지 사라진다.
     */
    @Query("""
            select count(p) > 0 from MadeDexRecordPhoto p
            where p.imageKey = :imageKey
              and p.recordId <> :recordId
              and p.recordId in (select r.id from MadeDexRecord r where r.deletedAt is null)
            """)
    boolean existsInOtherActiveRecord(@Param("imageKey") String imageKey,
                                      @Param("recordId") Long recordId);

    void deleteByRecordId(Long recordId);
}
