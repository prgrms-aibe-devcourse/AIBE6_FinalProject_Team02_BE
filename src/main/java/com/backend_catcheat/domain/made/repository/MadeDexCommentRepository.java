package com.backend_catcheat.domain.made.repository;


import com.backend_catcheat.domain.made.entity.MadeDexComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MadeDexCommentRepository extends JpaRepository<MadeDexComment, Long> {
    List<MadeDexComment> findByMadeDexRecordIdOrderByCreatedAtAsc(Long madeDexRecordId);
}
