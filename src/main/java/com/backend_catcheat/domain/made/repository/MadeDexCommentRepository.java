package com.backend_catcheat.domain.made.repository;


import com.backend_catcheat.domain.made.entity.MadeDexComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MadeDexCommentRepository extends JpaRepository<MadeDexComment, Long> {
    List<MadeDexComment> findByMadeDexRecordIdOrderByCreatedAtAsc(Long madeDexRecordId);

    /** 내가 쓴 댓글 — 마이 -> 내 활동 */
    List<MadeDexComment> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
}
