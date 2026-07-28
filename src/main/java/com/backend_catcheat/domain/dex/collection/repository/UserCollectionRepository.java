package com.backend_catcheat.domain.dex.collection.repository;

import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCollectionRepository extends JpaRepository<UserCollection, Long> {

    Optional<UserCollection> findByUserIdAndSlotId(Long userId, Long slotId);

    long countByUserId(Long userId);
}
