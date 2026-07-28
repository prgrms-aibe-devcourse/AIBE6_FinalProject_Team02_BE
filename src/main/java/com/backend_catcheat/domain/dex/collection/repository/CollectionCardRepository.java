package com.backend_catcheat.domain.dex.collection.repository;

import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionCardRepository extends JpaRepository<CollectionCard, Long> {
}
