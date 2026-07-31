package com.backend_catcheat.domain.dex.collection.repository;

import com.backend_catcheat.domain.dex.basicdex.type.Category;
import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCollectionRepository extends JpaRepository<UserCollection, Long> {

    Optional<UserCollection> findByUserIdAndSlotId(Long userId, Long slotId);

    long countByUserId(Long userId);

    List<UserCollection> findByUserId(Long userId);

    /** 유저가 특정 카테고리에서 수집한 슬롯 수 */
    @Query("select count(uc) from UserCollection uc, BasicDexEntity b"
            + " where uc.slotId = b.id and uc.userId = :userId and b.category = :category")
    long countByUserIdAndCategory(Long userId, Category category);

}
