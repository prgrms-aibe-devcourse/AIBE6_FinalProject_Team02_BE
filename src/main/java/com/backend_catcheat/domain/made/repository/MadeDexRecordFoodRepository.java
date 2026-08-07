package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexRecordFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MadeDexRecordFoodRepository extends JpaRepository<MadeDexRecordFood, Long> {

    List<MadeDexRecordFood> findByRecordIdOrderBySortOrderAsc(Long recordId);

    List<MadeDexRecordFood> findByRecordIdInOrderBySortOrderAsc(Collection<Long> recordIds);

    void deleteByRecordId(Long recordId);
}
