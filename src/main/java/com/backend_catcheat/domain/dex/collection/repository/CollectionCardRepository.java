package com.backend_catcheat.domain.dex.collection.repository;

import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectionCardRepository extends JpaRepository<CollectionCard, Long> {

    long countByUserCollectionId(Long userCollectionId);

    List<CollectionCard> findByUserCollectionIdOrderByCollectedAtDesc(Long userCollectionId);

}
