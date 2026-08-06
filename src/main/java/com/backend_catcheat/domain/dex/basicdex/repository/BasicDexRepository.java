package com.backend_catcheat.domain.dex.basicdex.repository;

import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity;
import com.backend_catcheat.domain.dex.basicdex.type.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BasicDexRepository extends JpaRepository<BasicDexEntity, Long> {
    List<BasicDexEntity> findAllByOrderByIdAsc();

    /** 카테고리별 전체 슬롯 수 */
    long countByCategory(Category category);
}
