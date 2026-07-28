package com.backend_catcheat.domain.admin.repository;

import com.backend_catcheat.domain.admin.entity.ReviewQueueItem;
import com.backend_catcheat.domain.admin.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewQueueItemRepository extends JpaRepository<ReviewQueueItem, Long> {

    List<ReviewQueueItem> findByStatusOrderByIdAsc(ReviewStatus status);
}
