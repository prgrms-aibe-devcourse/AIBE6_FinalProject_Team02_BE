package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MadeDexRecordPhotoRepository extends JpaRepository<MadeDexRecordPhoto, Long> {

    List<MadeDexRecordPhoto> findByRecordIdOrderBySortOrderAsc(Long recordId);

    /** 기록마다 조회하면 피드에서 기록 수만큼 쿼리가 나간다 */
    List<MadeDexRecordPhoto> findByRecordIdInOrderBySortOrderAsc(Collection<Long> recordIds);

    void deleteByRecordId(Long recordId);
}
