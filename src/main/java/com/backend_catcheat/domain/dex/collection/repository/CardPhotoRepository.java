package com.backend_catcheat.domain.dex.collection.repository;

import com.backend_catcheat.domain.dex.collection.entity.CardPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardPhotoRepository extends JpaRepository<CardPhoto, Long> {
    List<CardPhoto> findByCollectionCardIdOrderBySortOrderAsc(Long collectionCardId);
}
