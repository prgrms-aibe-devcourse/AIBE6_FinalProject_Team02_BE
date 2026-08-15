package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MadeDexCommentLikeRepository extends JpaRepository<MadeDexCommentLike, Long> {
    Optional<MadeDexCommentLike> findByCommentIdAndUserId(Long commentId,Long userId);
    void deleteByCommentId(Long commentId);

    List<MadeDexCommentLike> findByCommentIdInAndUserId(List<Long> commentIds, Long userId);
}
